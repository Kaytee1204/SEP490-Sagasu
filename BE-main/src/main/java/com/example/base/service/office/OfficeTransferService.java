package com.example.base.service.office;

import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.dto.office.OfficeDtos.PageData;
import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import com.example.base.storage.ImageValidator;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.base.service.chat.ChatConversationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.entity.AccountRole.*;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeTransferService {
    // DaiHV integration: do not transfer custody while a Manager is verifying a reported incident.
    private final com.example.base.service.report.ReportHoldGuard reportHolds;
    private final OfficeAccess access;
    private final PostRepository posts;
    private final CategoryRepository categories;
    private final ClaimRepository claims;
    private final ClaimActivityRepository activities;
    private final ItemReceiptRepository receipts;
    private final StorageLocationRepository locations;
    private final AppointmentRepository appointments;
    private final ChatConversationService conversations;
    private final ImageStorage images;
    private final ImageValidator imageValidator;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final com.example.base.service.notification.NotificationService notifications;
    private final PlatformTransactionManager transactions;

    private static final String ELIGIBLE = " p.post_type='FOUND' and p.custody_mode='P2P' and p.status in ('OPEN','RESERVED') and p.moderation_status='VISIBLE' and p.deleted_at is null and not exists(select 1 from item_receipt r where r.post_id=p.post_id) and not exists(select 1 from claim c where c.post_id=p.post_id and c.status='COMPLETED') ";
    private static final String EXPIRED_REASON = "Văn phòng chưa tiếp nhận đồ trong thời hạn.";

    @Transactional(readOnly=true)
    public List<CounterPerson> counterPeople(String query,CustomUserDetails user) {
        Account actor=access.require(user,APPROVER);
        String q=Objects.toString(query,"").strip();if(q.length()<2)return List.of();
        String pattern="%"+q.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        return jdbc.query("select a.id,a.full_name,a.email from accounts a where a.id<>? and (a.full_name ilike ? or a.email ilike ?) and exists(select 1 from post p where p.account_id=a.id and "+ELIGIBLE+") order by a.full_name,a.id limit 20",(rs,n)->new CounterPerson(rs.getLong(1),rs.getString(2),rs.getString(3)),actor.getId(),pattern,pattern);
    }

    @Transactional(readOnly=true)
    public List<CounterPost> counterPosts(long accountId,CustomUserDetails user) {
        access.require(user,APPROVER);
        String query = """
            select p.post_id,t.transfer_id,t.original_post_title,t.original_post_description,
                t.original_category_id,t.original_category_name
            from post p left join lateral (
                select * from office_transfer where post_id=p.post_id and status='PENDING'
                order by transfer_id desc limit 1
            ) t on true where p.account_id=? and
            """ + ELIGIBLE + " order by p.created_at desc";
        return jdbc.queryForList(query,accountId).stream().map(row->{
            Long id=number(row,"post_id");
            Post p=posts.findById(id).orElseThrow();
            IntakePost current=intakePost(p);
            Number transferId=(Number)row.get("transfer_id");
            return new CounterPost(id,p.getTitle(),p.getDescription(),current.images().stream().map(IntakePostImage::url).toList(),
                handoverCount(id),current,transferId==null?null:originalPost(transferId.longValue(),row));
        }).toList();
    }

    @Transactional
    public Long receiveAtCounter(CounterIntake request,CustomUserDetails user) {
        return receiveAtCounter(request,user,null,null);
    }
    @Transactional
    public Long receiveAtCounter(CounterIntake request,CustomUserDetails user,List<MultipartFile> files,List<MultipartFile> publicImages) {
        Account actor=access.require(user,APPROVER);Post post=lock(request.postId());
        if(!post.getAccount().getId().equals(request.accountId()))throw bad("Bài đăng không thuộc người giao đã chọn.");
        if(!request.physicallyReceived())throw bad("Cần xác nhận đã trực tiếp nhận và kiểm tra đồ.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from post p where p.post_id=? and "+ELIGIBLE+")",Boolean.class,post.getId())))throw bad("Bài đăng không còn đủ điều kiện tiếp nhận. Hãy tải lại danh sách.");
        expireDueForPost(post);
        TransferDecision decision=new TransferDecision(0L,"RECEIVED",request.locationId(),request.condition(),null,
            request.reconciliationNote(),request.items(),request.postUpdate());
        Long receiptId=receive(post,actor,decision,files,publicImages);
        jdbc.update("update item_receipt set note=? where item_receipt_id=?","Tiếp nhận trực tiếp tại quầy từ bài đăng có sẵn."+(request.reconciliationNote()==null?"":" Đối chiếu: "+request.reconciliationNote()),receiptId);
        List<Long> pendingIds=jdbc.queryForList("select transfer_id from office_transfer where post_id=? and status='PENDING' for update",Long.class,post.getId());
        for(Long id:pendingIds) {
            jdbc.update("update office_transfer set status='RECEIVED',processed_by=?,processed_at=now(),item_receipt_id=?,reconciliation_note=?,version=version+1 where transfer_id=?",actor.getId(),receiptId,request.reconciliationNote(),id);
            jdbc.update("update office_intake_appointment set status='COMPLETED',cancellation_reason=null,response_token_hash=null where transfer_id=? and status='SCHEDULED'",id);
        }
        posts.flush();
        String targetUrl = pendingIds.isEmpty() ? "/chi-tiet/" + post.getId()
            : "/van-phong/chuyen-giao/" + pendingIds.getFirst();
        notifications.notifyUser(request.accountId(), "OFFICE_TRANSFER_RECEIVED",
            "Văn phòng đã tiếp nhận tài sản",
            "Tài sản từ bài đăng #" + post.getId() + " đã được văn phòng tiếp nhận tại quầy và đưa vào lưu giữ.",
            targetUrl, false);
        return receiptId;
    }

    @Transactional
    public Post request(long postId, CustomUserDetails user) {
        Account actor = access.require(user);
        Post post = lock(postId);
        if (!post.getAccount().getId().equals(actor.getId())) throw bad("Chỉ người đăng được yêu cầu chuyển giao.");
        expireDueForPost(post);
        if (pending(postId)) return post;
        if (!post.canRequestOfficeHandover() || receipts.existsByPostId(postId)) throw bad("Bài đăng không còn đủ điều kiện chuyển giao.");
        Long transferId = jdbc.queryForObject("""
            insert into office_transfer(post_id,requested_by,original_post_title,original_post_description,
                original_category_id,original_category_name)
            values(?,?,?,?,?,?) returning transfer_id
            """,Long.class,postId,actor.getId(),post.getTitle(),post.getDescription(),
                post.getCategory().getCategoryId(),post.getCategory().getName());
        jdbc.update("""
            insert into office_transfer_post_image(transfer_id,post_image_id,storage_key,image_url,sort_order)
            select ?,post_image_id,storage_key,image_url,sort_order from post_image where post_id=?
            """,transferId,postId);
        post.requestOfficeHandover();
        notifications.notifyRoles(List.of(APPROVER),actor.getId(),"OFFICE_TRANSFER_REQUESTED",
            "Yêu cầu chuyển giao đồ cho văn phòng mới",
            actor.getFullName() + " vừa gửi yêu cầu bàn giao đồ vật \"" + post.getTitle() + "\" cho văn phòng lưu giữ.",
            "/approver/transfers",false);
        notifications.notifyRoles(List.of(MANAGER),actor.getId(),"OFFICE_TRANSFER_REQUESTED",
            "Yêu cầu chuyển giao đồ cho văn phòng mới",
            actor.getFullName() + " vừa gửi yêu cầu bàn giao đồ vật \"" + post.getTitle() + "\" cho văn phòng lưu giữ.",
            "/admin/transfers",false);
        return post;
    }

    @Transactional(readOnly=true)
    public PageData<TransferView> list(int page, String status, String q, boolean mine, CustomUserDetails user) {
        Account actor = mine ? access.require(user) : access.require(user, APPROVER, MANAGER);
        if (Set.of("AWAITING_REPLY", "CONFIRMED").contains(status)) status="SCHEDULED";
        if (page<1 || !Set.of("ALL","PENDING","RECEIVED","REJECTED","CANCELLED","UNSCHEDULED","SCHEDULED").contains(status)) throw bad("Bộ lọc không hợp lệ.");
        String filter=" from office_transfer t join post p on p.post_id=t.post_id where "
                +"(?='ALL' or t.status=? or (t.status='PENDING' and ? in ('UNSCHEDULED','SCHEDULED') and "
                +"(?='SCHEDULED')=exists(select 1 from office_intake_appointment a where a.transfer_id=t.transfer_id and a.status='SCHEDULED')))"
                +" and (?=false or t.requested_by=?) and p.title ilike ?";
        String search="%"+Objects.toString(q,"").strip()+"%";
        Long count=jdbc.queryForObject("select count(*)"+filter,Long.class,status,status,status,status,mine,actor.getId(),search);
        List<Long> ids=jdbc.query("select t.transfer_id"+filter+" order by t.requested_at desc,t.transfer_id desc limit 12 offset ?",
                (rs,n)->rs.getLong(1),status,status,status,status,mine,actor.getId(),search,(page-1)*12L);
        return new PageData<>(ids.stream().map(id -> view(id,false)).toList(),page,count==null?0:count,Math.max(1,(int)Math.ceil((count==null?0:count)/12.0)));
    }

    @Transactional(readOnly=true)
    public Long myTransferId(long postId, CustomUserDetails user) {
        Account actor = access.require(user);
        return jdbc.queryForList("""
                select transfer_id from office_transfer
                where post_id = ? and requested_by = ?
                order by (status = 'PENDING') desc, requested_at desc, transfer_id desc
                limit 1
                """, Long.class, postId, actor.getId()).stream().findFirst()
                .orElseThrow(() -> bad("Không tìm thấy yêu cầu giao đồ của bài đăng này."));
    }

    @Transactional(readOnly = true)
    public TransferView detail(long id,CustomUserDetails user) {
        Account actor=access.require(user);
        Map<String,Object> row=row(id);
        if (actor.getRole()!=APPROVER && actor.getRole()!=MANAGER && number(row,"requested_by")!=actor.getId()) throw bad("Không có quyền xem yêu cầu này.");
        return view(id,true);
    }

    @Transactional
    public TransferView decide(long id,TransferDecision request,CustomUserDetails user) {
        return decide(id,request,user,null,null);
    }
    @Transactional
    public TransferView decide(long id,TransferDecision request,CustomUserDetails user,List<MultipartFile> files,List<MultipartFile> publicImages) {
        Account actor=access.require(user);
        Map<String,Object> initial=row(id);
        Post post=lock(number(initial,"post_id"));
        Map<String,Object> current=jdbc.queryForMap("select * from office_transfer where transfer_id=? for update",id);
        if (!"PENDING".equals(current.get("status")) || number(current,"version")!=request.expectedVersion()) throw bad("Yêu cầu đã thay đổi. Hãy tải lại.");
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from office_intake_appointment where transfer_id=? and status='SCHEDULED' and intake_deadline<=clock_timestamp())
            """, Boolean.class, id))) throw bad("Yêu cầu đã quá hạn tiếp nhận. Hãy tải lại.");
        if ("CANCELLED".equals(request.decision())) {
            if (number(current,"requested_by")!=actor.getId()) throw bad("Chỉ người gửi được rút yêu cầu.");
        } else if(actor.getRole()!=APPROVER) throw bad("Chỉ nhân viên tiếp nhận được xử lý việc nhận đồ.");
        Long receiptId=null;
        if ("RECEIVED".equals(request.decision())) {
            receiptId=receive(post,actor,request,files,publicImages);
        } else {
            required(request.reason(),"Nhập lý do kết thúc yêu cầu chuyển giao.");
            post.cancelOfficeRequest();
        }
        jdbc.update("update office_transfer set status=?,processed_by=?,processed_at=now(),item_receipt_id=?,reason=?,reconciliation_note=?,version=version+1 where transfer_id=?",
                request.decision(),actor.getId(),receiptId,request.reason(),request.reconciliationNote(),id);
        jdbc.update("update office_intake_appointment set status=?,cancellation_reason=?,response_token_hash=null where transfer_id=? and status='SCHEDULED'",
                "RECEIVED".equals(request.decision())?"COMPLETED":"CANCELLED","RECEIVED".equals(request.decision())?null:request.reason(),id);
        posts.flush();
        long requestedBy = number(current,"requested_by");
        if ("RECEIVED".equals(request.decision())) {
            notifications.notifyUser(requestedBy,"OFFICE_TRANSFER_RECEIVED",
                "Văn phòng đã tiếp nhận đồ vật từ bài đăng \"" + post.getTitle() + "\"",
                "Cảm ơn bạn! Văn phòng đã chính thức tiếp nhận và lưu kho đồ vật từ bài đăng của bạn.",
                "/van-phong/chuyen-giao/" + id,true);
        } else if ("REJECTED".equals(request.decision())) {
            notifications.notifyUser(requestedBy,"OFFICE_TRANSFER_REJECTED",
                "Yêu cầu chuyển giao đồ cho văn phòng đã kết thúc",
                "Yêu cầu chuyển giao bài đăng \"" + post.getTitle() + "\" đã kết thúc. Lý do: " + request.reason(),
                "/van-phong/chuyen-giao/" + id,true);
        }
        return view(id,true);
    }

    private Long receive(Post post,Account actor,TransferDecision request,List<MultipartFile> files,List<MultipartFile> publicImages) {
            reportHolds.requireAvailable(post.getId());
            if (actor.getId().equals(post.getAccount().getId())) throw bad("Người giao không được tự xác nhận tiếp nhận đồ của mình.");
            if (post.getDeletedAt()!=null || !Set.of("OPEN","RESERVED").contains(post.getStatus()) || receipts.existsByPostId(post.getId())) throw bad("Đồ vật đã được xử lý hoặc không còn có thể tiếp nhận.");
            String items=required(request.items(),"Vui lòng ghi danh sách tài sản và phụ kiện tiếp nhận.");
            String condition=required(request.condition(),"Vui lòng ghi tình trạng tài sản khi tiếp nhận.");
            var dossiers=claims.findAllForOfficeTransfer(post.getId());
            for(Claim claim:dossiers) em.refresh(claim,LockModeType.PESSIMISTIC_WRITE);
            if(dossiers.stream().anyMatch(c->"COMPLETED".equals(c.getStatus()))) throw bad("Đồ đã hoàn tất trao trả, không thể chuyển giao.");
            long oldHandovers=handoverCount(post.getId());
            if(oldHandovers>0) required(request.reconciliationNote(),"Đã có biên bản trao trả trước đó. Cần ghi rõ kết quả đối chiếu và xác nhận đồ thực tế đang được giao cho văn phòng.");
            StorageLocation location=locations.findById(Objects.requireNonNullElse(request.locationId(),-1L)).orElseThrow(()->bad("Chọn vị trí lưu kho."));
            em.refresh(location,LockModeType.PESSIMISTIC_WRITE);
            if(!location.isActive()) throw bad("Vị trí lưu kho đã ngừng sử dụng.");
            ItemReceipt receipt=ItemReceipt.transferred("VP-"+UUID.randomUUID().toString().substring(0,18),actor,post,items,condition,location);
            updatePublishedPost(post,actor,request.postUpdate(),publicImages);
            attachReceiptEvidence(receipt,files);
            receipts.saveAndFlush(receipt);
            Long receiptId=receipt.getId();
            jdbc.update("update item_receipt set finder_name=?,finder_contact=? where item_receipt_id=?",
                    post.getAccount().getFullName(),post.getAccount().getEmail(),receiptId);
            jdbc.update("""
                update p2p_claim_approval a set closed_at=now()
                from claim c where c.claim_id=a.approved_claim_id and c.post_id=?
                  and a.custody_mode='P2P' and a.closed_at is null
                """,post.getId());
            for(Claim claim:dossiers) {
                String previous=claim.getStatus();
                claim.transferToOffice(receipt);
                appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(claim.getId(),"SCHEDULED")
                        .ifPresent(a->a.cancelByOffice("Chuyển đồ sang văn phòng; hồ sơ cần được xét duyệt lại."));
                activities.save(new ClaimActivity(claim,actor,"TRANSFER_OFFICE",previous,"Văn phòng đã tiếp nhận. Quyết định của người giữ đồ trước đó không còn hiệu lực."));
                if (claim.getClaimant()!=null) {
                    conversations.notifyChanged(claim);
                    if (Set.of("PENDING","APPROVED","NEEDS_INFO").contains(previous)) {
                        String message = "APPROVED".equals(previous)
                            ? "Văn phòng đã tiếp nhận tài sản. Văn phòng sẽ xét duyệt lại yêu cầu nhận đồ của bạn."
                            : "Văn phòng đã tiếp nhận tài sản và sẽ tiếp tục xét duyệt yêu cầu nhận đồ của bạn.";
                        notifications.notifyUser(claim.getClaimant().getId(), "OFFICE_CLAIM_TRANSFERRED",
                            "Văn phòng tiếp nhận xử lý yêu cầu #" + claim.getId(), message,
                            "/van-phong/claims/" + claim.getId(), false);
                    }
                }
            }
            post.receiveAtOffice();
            claims.flush();
            return receiptId;
    }

    private void updatePublishedPost(Post post, Account actor, IntakePostUpdate update, List<MultipartFile> files) {
        var newImages = imageValidator.validate(files);
        if (update == null) {
            if (!newImages.isEmpty()) throw bad("Vui lòng cung cấp thông tin cập nhật bài đăng.");
            return;
        }
        if (!post.getUpdatedAt().isEqual(update.expectedUpdatedAt()))
            throw bad("Bài đăng đã thay đổi. Vui lòng tải lại và đối chiếu trước khi tiếp nhận.");
        List<PostImage> existing = post.getImages();
        List<Long> keptIds = update.keptImageIds();
        Set<Long> kept = new HashSet<>(keptIds);
        if (kept.size() != keptIds.size() || !existing.stream().map(PostImage::getId).toList().containsAll(kept))
            throw bad("Danh sách ảnh bài đăng đã thay đổi. Vui lòng tải lại.");
        if (kept.size() + newImages.size() > 5) throw bad("Bài đăng có thể lưu tối đa 5 ảnh.");
        Category category = post.getCategory().getCategoryId().equals(update.categoryId()) ? post.getCategory()
            : categories.findActiveForPostCreation(update.categoryId())
                .orElseThrow(() -> bad("Danh mục đã ngừng sử dụng. Vui lòng chọn danh mục khác."));
        post.editAtOfficeIntake(category,update.title(),update.description());

        if (newImages.isEmpty() && existing.stream().map(PostImage::getId).toList().equals(keptIds)) return;
        var removed = existing.stream().filter(image -> !kept.contains(image.getId()))
            .map(PostImage::getStorageKey).filter(Objects::nonNull).toList();
        var uploaded = new ArrayList<String>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                // Cleanup reads retained references through a fresh transaction after the write commits.
                var cleanup = new TransactionTemplate(transactions);
                cleanup.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                cleanup.executeWithoutResult(tx -> {
                    for (String key : status == STATUS_COMMITTED ? removed : uploaded) deleteIntakeImage(key);
                });
            }
        });
        int temporaryPosition = Math.max(kept.size() + newImages.size(),
            existing.stream().mapToInt(PostImage::getSortOrder).max().orElse(0) + 1);
        for (PostImage image : existing) image.reorder(temporaryPosition++);
        posts.flush();
        post.retainImages(keptIds);
        for (var image : newImages) {
            String key = "posts/" + actor.getId() + "/" + UUID.randomUUID() + "." + image.extension();
            uploaded.add(key);
            images.put(key,image.bytes(),image.mime());
            post.addImage(new PostImage(key,kept.size() + uploaded.size() - 1,image.mime(),
                image.bytes().length,image.width(),image.height(),image.hash()));
        }
    }

    private void deleteIntakeImage(String key) {
        try { images.delete(key); }
        catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(OfficeTransferService.class).warn("Intake image cleanup failed for {}",key);
        }
    }

    private IntakePost intakePost(Post post) {
        return new IntakePost(post.getTitle(),post.getDescription(),post.getCategory().getCategoryId(),
            post.getCategory().getName(),post.getUpdatedAt(),post.getImages().stream()
                .map(image -> new IntakePostImage(image.getId(),images.readUrl(image.getStorageKey(),image.getImageUrl()))).toList());
    }

    private IntakePost originalPost(long transferId, Map<String,Object> transfer) {
        if (transfer.get("original_post_title") == null) return null;
        var photos = jdbc.query("""
            select post_image_id,storage_key,image_url from office_transfer_post_image where transfer_id=?
            order by sort_order,post_image_id
            """,(row,index) -> new IntakePostImage(row.getLong(1),images.readUrl(row.getString(2),row.getString(3))),transferId);
        return new IntakePost((String)transfer.get("original_post_title"),(String)transfer.get("original_post_description"),
            number(transfer,"original_category_id"),(String)transfer.get("original_category_name"),null,photos);
    }

    private void attachReceiptEvidence(ItemReceipt receipt,List<MultipartFile> files) {
        if(files==null && imageValidator==null) return;
        if(files==null || files.isEmpty()) throw bad("Cần thêm ít nhất một ảnh chụp đồ vật khi văn phòng tiếp nhận.");
        var validated=imageValidator.validate(files);
        var keys=new ArrayList<String>();
        // Receipt evidence belongs to the transaction; remove uploads even on commit-time rollback.
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if(status!=STATUS_COMMITTED) for(String key:keys) {
                        try { images.delete(key); } catch(RuntimeException failure) {
                            org.slf4j.LoggerFactory.getLogger(OfficeTransferService.class).warn("Receipt evidence cleanup failed for {}",key);
                        }
                    }
                }
            });
        for(var image:validated) {
            String key="receipts/intake/"+UUID.randomUUID()+"."+image.extension();
            keys.add(key); images.put(key,image.bytes(),image.mime());
            receipt.addImage(ItemReceiptImage.intakeEvidence(key,keys.size()-1,image.mime(),image.bytes().length));
        }
    }

    private boolean pending(long postId) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from office_transfer where post_id=? and status='PENDING')",Boolean.class,postId)); }
    private Map<String,Object> row(long id) { return jdbc.queryForList("select * from office_transfer where transfer_id=?",id).stream().findFirst().orElseThrow(()->bad("Không tìm thấy yêu cầu chuyển giao.")); }
    private Post lock(long id) { Post p=posts.findForClaimUpdate(id).orElseThrow(()->bad("Không tìm thấy bài đăng.")); em.refresh(p,LockModeType.PESSIMISTIC_WRITE);return p; }
    private long handoverCount(long postId) { return jdbc.queryForObject("select count(*) from p2p_handover h join claim c on c.claim_id=h.claim_id where c.post_id=?",Long.class,postId); }
    private TransferView view(long id, boolean includePostDetails) {
        Map<String,Object> t=row(id); Post p=posts.findById(number(t,"post_id")).orElseThrow();
        Long receiptId=t.get("item_receipt_id")==null ? null : number(t,"item_receipt_id");
        ItemReceipt receipt=receiptId==null ? null : receipts.findById(receiptId).orElse(null);
        return new TransferView(id,p.getId(),p.getTitle(),p.getDescription(),p.getAccount().getFullName(),(String)t.get("status"),
                time(t.get("requested_at")),time(t.get("processed_at")),(String)t.get("reason"),(String)t.get("reconciliation_note"),
                receiptId,number(t,"version"),
                jdbc.queryForObject("select count(*) from claim where post_id=? and status in ('PENDING','NEEDS_INFO','APPROVED')",Long.class,p.getId()),
                handoverCount(p.getId()),p.getImages().stream().map(i->images.readUrl(i.getStorageKey(),i.getImageUrl())).toList(),intakeAppointment(id),number(t,"requested_by"),
                receipt==null ? List.of() : receipt.getImages().stream()
                    .filter(i->i.getStorageKey()!=null && i.getStorageKey().startsWith("receipts/intake/"))
                    .map(i->images.readUrl(i.getStorageKey(),i.getImageUrl())).toList(),
                receipt==null?null:receipt.getItemSummary(),receipt==null?null:receipt.getConditionAtReceipt(),
                includePostDetails ? intakePost(p) : null,includePostDetails ? originalPost(id,t) : null);
    }
    private IntakeAppointment intakeAppointment(long id) {
        return jdbc.query("select * from office_intake_appointment where transfer_id=? order by appointment_id desc limit 1",(rs,n)->
            new IntakeAppointment(rs.getLong("appointment_id"),rs.getObject("scheduled_at",OffsetDateTime.class),rs.getString("location"),rs.getString("note"),rs.getString("status"),rs.getString("email_status"),rs.getObject("intake_deadline",OffsetDateTime.class),rs.getString("cancellation_reason")),
            id).stream().findFirst().orElse(null);
    }

    public void expireDue() {
        var postIds=jdbc.queryForList("""
            select t.post_id from office_intake_appointment a join office_transfer t using(transfer_id)
            where a.status='SCHEDULED' and a.intake_deadline<=now() and t.status='PENDING'
            order by a.intake_deadline, a.transfer_id limit 100
            """,Long.class);
        var tx=new TransactionTemplate(transactions);
        for (long postId : postIds) {
            try { tx.executeWithoutResult(ignored -> expireDueForPost(lock(postId))); }
            catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not expire office intake for post {}",postId,failure);
            }
        }
    }

    private void expireDueForPost(Post post) {
        var expired=jdbc.queryForList("""
            update office_transfer t set status='CANCELLED', processed_at=now(), reason=?, version=version+1
            from office_intake_appointment a where t.post_id=? and t.status='PENDING' and a.transfer_id=t.transfer_id
              and a.status='SCHEDULED' and a.intake_deadline<=clock_timestamp()
            returning t.transfer_id,t.requested_by
            """,EXPIRED_REASON,post.getId());
        if (expired.isEmpty()) return;
        post.cancelOfficeRequest();
        for (var transfer : expired) {
            long id=number(transfer,"transfer_id");
            jdbc.update("update office_intake_appointment set status='CANCELLED',cancellation_reason=?,response_token_hash=null where transfer_id=? and status='SCHEDULED'",EXPIRED_REASON,id);
            notifications.notifyUser(number(transfer,"requested_by"),"OFFICE_TRANSFER_EXPIRED",
                "Yêu cầu tiếp nhận đồ đã hết hạn",EXPIRED_REASON + " Bạn vẫn giữ đồ và có thể gửi yêu cầu mới hoặc mang đồ đến quầy.",
                "/van-phong/chuyen-giao/"+id,false);
        }
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static OffsetDateTime time(Object value) { return value==null?null:((java.sql.Timestamp)value).toInstant().atOffset(java.time.ZoneOffset.UTC); }
    private static String required(String value,String message) { if(value==null||value.isBlank()) throw bad(message);return value.strip(); }
}
