package com.example.base.service.office;

import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.dto.office.OfficeDtos.ReviewResult;
import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.report.WorkflowImages;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.entity.AccountRole.APPROVER;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeReturnService {
    private final OfficeAccess access;
    private final ClaimRepository claims;
    private final PostRepository posts;
    private final AppointmentRepository appointments;
    private final ClaimActivityRepository activities;
    private final OfficeInvitationService invitations;
    private final WorkflowImages images;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;

    @Transactional(readOnly=true)
    public Workflow detail(long id,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=claim(id); participant(c,actor);
        return view(c,actor);
    }

    public ReviewResult reschedule(long id,Reschedule request,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
            if(!request.scheduledAt().isAfter(OffsetDateTime.now().plusHours(12).plusMinutes(1))) throw bad("Lịch mới cần cách hiện tại hơn 12 giờ để người nhận phản hồi.");
            Appointment old=scheduled(c);
            old.cancelByOffice(request.reason().strip());
            appointments.flush();
            appointments.save(Appointment.schedule(c,actor,request.scheduledAt(),request.location().strip(),request.note()));
            c.touchWorkflow();
            activities.save(new ClaimActivity(c,actor,"RESCHEDULE","APPROVED",request.reason().strip()));
            claims.flush();
        });
        String delivery;
        try { delivery=invitations.send(id)?"SENT":"FAILED"; }
        catch(RuntimeException failure) { delivery="FAILED"; }
        return new ReviewResult(id,"APPROVED",delivery);
    }

    @Transactional
    public Workflow cancel(long id,VersionedReason request,CustomUserDetails user) {
        Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
        scheduled(c).cancelByOffice(request.reason().strip());
        c.review("REJECTED"); c.getPost().reopenAfterClaimCancellation();
        activities.save(new ClaimActivity(c,actor,"OFFICE_CANCEL","APPROVED",request.reason().strip()));
        claims.flush();return view(c,actor);
    }

    @Transactional
    public Workflow verify(long id,Verify request,CustomUserDetails user) {
        Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
        Appointment a=scheduled(c);
        if(!"CONFIRMED".equals(a.getResponseStatus())) throw bad("Người nhận cần đồng ý lịch hẹn trước khi xác minh tại quầy.");
        jdbc.update("insert into office_claim_verification(claim_id,appointment_id,verified_by,result,note) values(?,?,?,?,?)",
                id,a.getId(),actor.getId(),request.result(),request.note().strip());
        c.touchWorkflow();claims.flush();return view(c,actor);
    }

    public Workflow record(long id,RecordHandover request,List<MultipartFile> files,CustomUserDetails user) {
        Account actor=approver(user);
        // Reuse the team's image validation/storage API; no change to its P2P behavior.
        var prepared=images.prepare("OFFICE:"+id+":"+request.condition().strip(),files);
        if(prepared.images().isEmpty()) throw bad("Thêm ít nhất một ảnh chứng minh việc giao đồ.");
        var tx=new TransactionTemplate(transactions);
        Workflow retry=tx.execute(status->{Claim c=lock(id); return recordCheck(c,actor,request,prepared.hash());});
        if(retry!=null)return retry;
        var uploaded=images.upload("office-handovers/"+id,prepared);
        boolean[] inserted={false};
        try {
            Workflow result=tx.execute(status->{
                Account current=approver(user); Claim c=lock(id);
                Workflow existing=recordCheck(c,current,request,prepared.hash());if(existing!=null)return existing;
                Appointment appointment=scheduled(c);
                Verification verification=verifications(id).getFirst();
                Long handoverId=jdbc.queryForObject("""
                    insert into office_handover(claim_id,item_receipt_id,verification_id,recorded_by,recipient_id,condition_note,client_request_id,request_hash)
                    values(?,?,?,?,?,?,?,?) returning handover_id
                    """,Long.class,id,c.getReceipt().getId(),verification.id(),current.getId(),c.getClaimant().getId(),request.condition().strip(),request.clientRequestId(),prepared.hash());
                for(var image:uploaded) jdbc.update("insert into office_handover_evidence(handover_id,storage_key,original_name,mime_type,size_bytes,content_hash,sort_order) values(?,?,?,?,?,?,?)",
                        handoverId,image.key(),image.name(),image.mime(),image.size(),image.hash(),image.order());
                c.getReceipt().returnedByOffice(); appointment.completeAtOffice(); c.touchWorkflow();
                activities.save(new ClaimActivity(c,current,"HANDOVER","APPROVED","Văn phòng đã giao đồ, chờ người nhận xác nhận."));
                claims.flush();inserted[0]=true;return view(c,current);
            });
            if(!inserted[0]) images.cleanup(uploaded);
            return result;
        } catch(RuntimeException failure) {images.cleanup(uploaded);throw failure;}
    }

    @Transactional
    public Workflow confirm(long id,Confirm request,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=lock(id); recipient(c,actor);
        Handover h=handover(id);
        if(h==null)throw bad("Văn phòng chưa ghi nhận giao đồ.");
        if("COMPLETED".equals(h.status()))return view(c,actor);
        responseCheck(c,h,request.expectedVersion());
        jdbc.update("update office_handover set status='COMPLETED',confirmed_at=now(),version=version+1 where handover_id=?",h.id());
        c.completeReceipt(); c.getPost().markReturnedFromOffice();
        activities.save(new ClaimActivity(c,actor,"CONFIRM_RECEIPT","APPROVED","Người nhận xác nhận đã nhận đồ tại văn phòng."));
        for(Claim other:claims.findAllForOfficeTransfer(c.getPost().getId())) {
            if(!other.getId().equals(c.getId()) && Set.of("PENDING","NEEDS_INFO").contains(other.getStatus())) {
                String previous=other.getStatus(); other.closeAfterReturn();
                activities.save(new ClaimActivity(other,actor,"AUTO_CLOSE",previous,"Đồ đã được trao trả và người nhận xác nhận."));
            }
        }
        claims.flush();return view(c,actor);
    }

    @Transactional
    public Workflow issue(long id,VersionedReason request,CustomUserDetails user) {
        Account actor=access.require(user);Claim c=lock(id);recipient(c,actor);
        Handover h=handover(id);
        if(h==null)throw bad("Chưa có biên bản giao đồ.");
        responseCheck(c,h,request.expectedVersion());
        // Resolution belongs to the later report feature. Preserve the issue and block completion.
        jdbc.update("update office_handover set status='DISPUTED',issue_note=?,version=version+1 where handover_id=?",request.reason().strip(),h.id());
        c.touchWorkflow();activities.save(new ClaimActivity(c,actor,"REPORT_ISSUE","APPROVED",request.reason().strip()));
        claims.flush();return view(c,actor);
    }

    private Workflow recordCheck(Claim c,Account actor,RecordHandover request,String hash) {
        Handover existing=handover(c.getId());
        if(existing!=null) {
            Boolean same=jdbc.queryForObject("select exists(select 1 from office_handover where claim_id=? and recorded_by=? and client_request_id=? and request_hash=?)",Boolean.class,c.getId(),actor.getId(),request.clientRequestId(),hash);
            if(Boolean.TRUE.equals(same))return view(c,actor);
            throw bad("Đồ đã được ghi nhận giao. Hãy tải lại hồ sơ.");
        }
        expected(c,request.expectedVersion());manageable(c,actor);
        if(c.getClaimant()==null)throw bad("Cần liên kết tài khoản người nhận trước khi trao trả.");
        Appointment a=scheduled(c);var verification=verifications(c.getId());
        if(!"CONFIRMED".equals(a.getResponseStatus()) || verification.isEmpty() || verification.getFirst().appointmentId()!=a.getId() || !"PASSED".equals(verification.getFirst().result()))
            throw bad("Cần lịch hẹn đã xác nhận và kết quả xác minh đạt cho lịch hẹn hiện tại.");
        return null;
    }
    private void manageable(Claim c,Account actor) {
        if(c.getClaimant()!=null && c.getClaimant().getId().equals(actor.getId()))throw bad("Không được tự xử lý hồ sơ nhận đồ của mình.");
        if(!"APPROVED".equals(c.getStatus()) || !"OFFICE".equals(c.getPost().getCustodyMode()) || !"RESERVED".equals(c.getPost().getStatus())
                || c.getPost().getDeletedAt()!=null || c.getReceipt()==null || !"IN_STORAGE".equals(c.getReceipt().getStatus()) || handover(c.getId())!=null)
            throw bad("Hồ sơ không còn ở bước chuẩn bị trao trả. Hãy tải lại.");
    }
    private void responseCheck(Claim c,Handover h,long version) {
        if(!"WAITING_CONFIRMATION".equals(h.status()) || h.version()!=version || !"APPROVED".equals(c.getStatus()) || !"RESERVED".equals(c.getPost().getStatus()))throw bad("Biên bản đã thay đổi hoặc đang chờ xử lý phản ánh. Hãy tải lại.");
    }
    private Appointment scheduled(Claim c) {
        Appointment a=appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(c.getId(),"SCHEDULED").orElseThrow(()->bad("Không có lịch hẹn đang hiệu lực."));
        em.refresh(a,LockModeType.PESSIMISTIC_WRITE);return a;
    }
    private Account approver(CustomUserDetails user) { return access.require(user,APPROVER); }
    private Claim claim(long id) {return claims.findById(id).filter(Claim::isOffice).orElseThrow(()->bad("Không tìm thấy hồ sơ văn phòng."));}
    private Claim lock(long id) {
        long postId=claims.findPostId(id).orElseThrow(()->bad("Không tìm thấy hồ sơ."));
        Post p=posts.findForClaimUpdate(postId).orElseThrow();em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        Claim c=claims.findForUpdate(id).orElseThrow();em.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(!c.isOffice())throw bad("Hồ sơ không thuộc văn phòng.");
        if(c.getReceipt()!=null)em.refresh(c.getReceipt(),LockModeType.PESSIMISTIC_WRITE);
        return c;
    }
    private void expected(Claim c,long version) {if(c.getVersion()!=version)throw bad("Hồ sơ đã thay đổi. Hãy tải lại trước khi thao tác.");}
    private void participant(Claim c,Account actor) {if(actor.getRole()!=APPROVER)recipient(c,actor);}
    private void recipient(Claim c,Account actor) {if(c.getClaimant()==null||!c.getClaimant().getId().equals(actor.getId()))throw bad("Chỉ người khai nhận được thực hiện thao tác này.");}
    private List<Verification> verifications(long id) {
        return jdbc.query("select v.*,a.full_name from office_claim_verification v join accounts a on a.id=v.verified_by where claim_id=? order by verification_id desc",
                (rs,n)->new Verification(rs.getLong("verification_id"),rs.getLong("appointment_id"),rs.getString("result"),rs.getString("note"),rs.getString("full_name"),rs.getObject("verified_at",OffsetDateTime.class)),id);
    }
    private Handover handover(long id) {
        return jdbc.query("select h.*,a.full_name as officer,b.full_name as recipient from office_handover h join accounts a on a.id=h.recorded_by join accounts b on b.id=h.recipient_id where claim_id=?",
                (rs,n)->new Handover(rs.getLong("handover_id"),rs.getString("status"),rs.getString("condition_note"),rs.getString("officer"),rs.getString("recipient"),rs.getObject("recorded_at",OffsetDateTime.class),rs.getObject("confirmed_at",OffsetDateTime.class),rs.getString("issue_note"),rs.getLong("version"),evidence(rs.getLong("handover_id"))),id).stream().findFirst().orElse(null);
    }
    private List<Evidence> evidence(long id) {
        return jdbc.query("select * from office_handover_evidence where handover_id=? order by sort_order",(rs,n)->new Evidence(rs.getLong("evidence_id"),images.read(rs.getString("storage_key")),rs.getString("original_name")),id);
    }
    private Workflow view(Claim c,Account actor) {
        var history=verifications(c.getId()); Handover h=handover(c.getId());
        Appointment a=appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(c.getId(),"SCHEDULED").orElse(null);
        boolean officer=actor.getRole()==APPROVER && (c.getClaimant()==null||!actor.getId().equals(c.getClaimant().getId()));
        boolean approved="APPROVED".equals(c.getStatus()) && "RESERVED".equals(c.getPost().getStatus());
        boolean preparing=officer && approved && h==null && a!=null && c.getReceipt()!=null && "IN_STORAGE".equals(c.getReceipt().getStatus());
        boolean confirmed=a!=null&&"CONFIRMED".equals(a.getResponseStatus());
        boolean verified=confirmed&&!history.isEmpty()&&history.getFirst().appointmentId()==a.getId()&&"PASSED".equals(history.getFirst().result());
        boolean canConfirm=h!=null&&approved&&"WAITING_CONFIRMATION".equals(h.status())&&c.getClaimant()!=null&&actor.getId().equals(c.getClaimant().getId());
        String next=h!=null?switch(h.status()) {case "COMPLETED"->"Đã hoàn tất trao trả.";case "DISPUTED"->"Đã ghi nhận phản ánh. Tạm dừng hoàn tất; vui lòng liên hệ văn phòng.";default->"Văn phòng đã giao đồ. Chờ người nhận xác nhận.";}
                :Set.of("REJECTED","CANCELLED","CLOSED").contains(c.getStatus())?"Hồ sơ đã kết thúc. Xem lý do trong lịch sử xử lý.":!approved?"Hồ sơ cần được duyệt trước khi thực hiện trao trả.":!confirmed?"Chờ người nhận đồng ý lịch hẹn qua email.":!verified?"Đối chiếu danh tính và căn cứ sở hữu tại quầy.":"Đã xác minh. Có thể ghi nhận giao đồ.";
        return new Workflow(history,h,preparing&&confirmed,preparing&&verified,preparing,preparing,canConfirm,next);
    }
}
