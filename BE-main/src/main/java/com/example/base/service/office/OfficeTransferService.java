package com.example.base.service.office;

import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.dto.office.OfficeDtos.PageData;
import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.service.chat.ChatConversationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.entity.AccountRole.*;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeTransferService {
    private final OfficeAccess access;
    private final PostRepository posts;
    private final ClaimRepository claims;
    private final ClaimActivityRepository activities;
    private final ItemReceiptRepository receipts;
    private final StorageLocationRepository locations;
    private final AppointmentRepository appointments;
    private final ChatConversationService conversations;
    private final ImageStorage images;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    @Transactional
    public Post request(long postId, CustomUserDetails user) {
        Account actor = access.require(user);
        Post post = lock(postId);
        if (!post.getAccount().getId().equals(actor.getId())) throw bad("Chỉ người đăng được yêu cầu chuyển giao.");
        if (pending(postId)) return post;
        if (!post.canRequestOfficeHandover() || receipts.existsByPostId(postId)) throw bad("Bài đăng không còn đủ điều kiện chuyển giao.");
        jdbc.update("insert into office_transfer(post_id,requested_by) values(?,?)",postId,actor.getId());
        post.requestOfficeHandover();
        return post;
    }

    @Transactional(readOnly=true)
    public PageData<TransferView> list(int page, String status, String q, boolean mine, CustomUserDetails user) {
        Account actor = mine ? access.require(user) : access.require(user, APPROVER, MANAGER);
        if (page<1 || !Set.of("ALL","PENDING","RECEIVED","REJECTED","CANCELLED").contains(status)) throw bad("Bộ lọc không hợp lệ.");
        String filter=" from office_transfer t join post p on p.post_id=t.post_id where (?='ALL' or t.status=?) and (?=false or t.requested_by=?) and p.title ilike ?";
        String search="%"+Objects.toString(q,"").strip()+"%";
        Long count=jdbc.queryForObject("select count(*)"+filter,Long.class,status,status,mine,actor.getId(),search);
        List<Long> ids=jdbc.query("select t.transfer_id"+filter+" order by t.requested_at desc,t.transfer_id desc limit 12 offset ?",
                (rs,n)->rs.getLong(1),status,status,mine,actor.getId(),search,(page-1)*12L);
        return new PageData<>(ids.stream().map(this::view).toList(),page,count==null?0:count,Math.max(1,(int)Math.ceil((count==null?0:count)/12.0)));
    }

    @Transactional(readOnly=true)
    public TransferView detail(long id,CustomUserDetails user) {
        Account actor=access.require(user);
        Map<String,Object> row=row(id);
        if (actor.getRole()!=APPROVER && actor.getRole()!=MANAGER && number(row,"requested_by")!=actor.getId()) throw bad("Không có quyền xem yêu cầu này.");
        return view(id);
    }

    @Transactional
    public TransferView decide(long id,TransferDecision request,CustomUserDetails user) {
        Account actor=access.require(user);
        Map<String,Object> initial=row(id);
        Post post=lock(number(initial,"post_id"));
        Map<String,Object> current=jdbc.queryForMap("select * from office_transfer where transfer_id=? for update",id);
        if (!"PENDING".equals(current.get("status")) || number(current,"version")!=request.expectedVersion()) throw bad("Yêu cầu đã thay đổi. Hãy tải lại.");
        if ("CANCELLED".equals(request.decision())) {
            if (number(current,"requested_by")!=actor.getId()) throw bad("Chỉ người gửi được rút yêu cầu.");
        } else if(actor.getRole()!=APPROVER) throw bad("Chỉ nhân viên tiếp nhận được xử lý việc nhận đồ.");
        Long receiptId=null;
        if ("RECEIVED".equals(request.decision())) {
            if (actor.getId().equals(post.getAccount().getId())) throw bad("Người giao không được tự xác nhận tiếp nhận đồ của mình.");
            if (post.getDeletedAt()!=null || !Set.of("OPEN","RESERVED").contains(post.getStatus()) || receipts.existsByPostId(post.getId())) throw bad("Đồ vật đã được xử lý hoặc không còn có thể tiếp nhận.");
            String condition=required(request.condition(),"Ghi tình trạng đồ khi nhận.");
            var dossiers=claims.findAllForOfficeTransfer(post.getId());
            for(Claim claim:dossiers) em.refresh(claim,LockModeType.PESSIMISTIC_WRITE);
            if(dossiers.stream().anyMatch(c->"COMPLETED".equals(c.getStatus()))) throw bad("Đồ đã hoàn tất trao trả, không thể chuyển giao.");
            long oldHandovers=handoverCount(post.getId());
            if(oldHandovers>0) required(request.reconciliationNote(),"Đã có biên bản trao trả trước đó. Cần ghi rõ kết quả đối chiếu và xác nhận đồ thực tế đang được giao cho văn phòng.");
            StorageLocation location=locations.findById(Objects.requireNonNullElse(request.locationId(),-1L)).orElseThrow(()->bad("Chọn vị trí lưu kho."));
            em.refresh(location,LockModeType.PESSIMISTIC_WRITE);
            if(!location.isActive()) throw bad("Vị trí lưu kho đã ngừng sử dụng.");
            ItemReceipt receipt=receipts.saveAndFlush(ItemReceipt.transferred("VP-"+UUID.randomUUID().toString().substring(0,18),actor,post,condition,location));
            receiptId=receipt.getId();
            jdbc.update("update item_receipt set finder_name=?,finder_contact=? where item_receipt_id=?",
                    post.getAccount().getFullName(),post.getAccount().getEmail(),receiptId);
            for(Claim claim:dossiers) {
                String previous=claim.getStatus();
                claim.transferToOffice(receipt);
                appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(claim.getId(),"SCHEDULED")
                        .ifPresent(a->a.cancelByOffice("Chuyển đồ sang văn phòng; hồ sơ cần được xét duyệt lại."));
                activities.save(new ClaimActivity(claim,actor,"TRANSFER_OFFICE",previous,"Văn phòng đã tiếp nhận. Quyết định của người giữ đồ trước đó không còn hiệu lực."));
                if (claim.getClaimant()!=null) conversations.notifyChanged(claim);
            }
            post.receiveAtOffice();
            claims.flush();
        } else {
            required(request.reason(),"Nhập lý do kết thúc yêu cầu chuyển giao.");
            post.cancelOfficeRequest();
        }
        jdbc.update("update office_transfer set status=?,processed_by=?,processed_at=now(),item_receipt_id=?,reason=?,reconciliation_note=?,version=version+1 where transfer_id=?",
                request.decision(),actor.getId(),receiptId,request.reason(),request.reconciliationNote(),id);
        posts.flush();
        return view(id);
    }

    private boolean pending(long postId) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from office_transfer where post_id=? and status='PENDING')",Boolean.class,postId)); }
    private Map<String,Object> row(long id) { return jdbc.queryForList("select * from office_transfer where transfer_id=?",id).stream().findFirst().orElseThrow(()->bad("Không tìm thấy yêu cầu chuyển giao.")); }
    private Post lock(long id) { Post p=posts.findForClaimUpdate(id).orElseThrow(()->bad("Không tìm thấy bài đăng.")); em.refresh(p,LockModeType.PESSIMISTIC_WRITE);return p; }
    private long handoverCount(long postId) { return jdbc.queryForObject("select count(*) from p2p_handover h join claim c on c.claim_id=h.claim_id where c.post_id=?",Long.class,postId); }
    private TransferView view(long id) {
        Map<String,Object> t=row(id); Post p=posts.findById(number(t,"post_id")).orElseThrow();
        return new TransferView(id,p.getId(),p.getTitle(),p.getDescription(),p.getAccount().getFullName(),(String)t.get("status"),
                time(t.get("requested_at")),time(t.get("processed_at")),(String)t.get("reason"),(String)t.get("reconciliation_note"),
                t.get("item_receipt_id")==null?null:number(t,"item_receipt_id"),number(t,"version"),
                jdbc.queryForObject("select count(*) from claim where post_id=? and status in ('PENDING','NEEDS_INFO','APPROVED')",Long.class,p.getId()),
                handoverCount(p.getId()),p.getImages().stream().map(i->images.readUrl(i.getStorageKey(),i.getImageUrl())).toList());
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static OffsetDateTime time(Object value) { return value==null?null:((java.sql.Timestamp)value).toInstant().atOffset(java.time.ZoneOffset.UTC); }
    private static String required(String value,String message) { if(value==null||value.isBlank()) throw bad(message);return value.strip(); }
}
