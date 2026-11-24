package com.example.base.service.office;

import com.example.base.dto.office.OfficeWorkflowDtos.*;
import com.example.base.dto.office.OfficeDtos.ReviewResult;
import com.example.base.entity.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.claim.P2pClaimAppealService;
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
    // DaiHV integration: investigation holds apply to physical handover, not to reading the dossier.
    private final com.example.base.service.report.ReportHoldGuard reportHolds;
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
    private final com.example.base.service.notification.NotificationService notifications;
    private final P2pClaimAppealService appeals;

    @Transactional(readOnly=true)
    public Workflow detail(long id,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=claim(id); participant(c,actor);
        return view(c,actor);
    }

    public ReviewResult schedule(long id, Schedule request, CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
            requireAvailable(c);
            if (activeAppointment(id)!=null) throw bad("Yêu cầu đã có lịch hẹn. Hãy tải lại hồ sơ.");
            OffsetDateTime now=OffsetDateTime.now();
            if (!request.scheduledAt().isAfter(now.plusHours(12).plusMinutes(1)))
                throw bad("Giờ hẹn cần cách hiện tại hơn 12 giờ để người nhận phản hồi.");
            c.contactEmail(request.email().strip().toLowerCase(Locale.ROOT));
            appointments.save(Appointment.schedule(c,actor,request.scheduledAt(),request.location().strip(),request.note(),now));
            c.touchWorkflow();
            claims.flush();
            if (c.getClaimant()!=null) notifications.notifyUser(c.getClaimant().getId(), "APPOINTMENT_SCHEDULED",
                "Văn phòng đã lập lịch nhận tài sản", "Hãy phản hồi lịch của yêu cầu #" + id + " trong 12 giờ. Quá hạn, lịch sẽ bị hủy và yêu cầu nhận đồ sẽ bị từ chối.",
                "/van-phong/claims/" + id, false);
        });
        return sendInvitation(id);
    }

    public ReviewResult reschedule(long id,Reschedule request,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
            requireAvailable(c);
            OffsetDateTime now=OffsetDateTime.now();
            if(!request.scheduledAt().isAfter(now.plusHours(12).plusMinutes(1))) throw bad("Lịch mới cần cách hiện tại hơn 12 giờ để người nhận phản hồi.");
            Appointment old=scheduled(c);
            requireNotVerified(c,old);
            old.cancelByOffice(request.reason().strip());
            appointments.flush();
            appointments.save(Appointment.schedule(c,actor,request.scheduledAt(),request.location().strip(),request.note(),now));
            c.touchWorkflow();
            activities.save(new ClaimActivity(c,actor,"RESCHEDULE","APPROVED",request.reason().strip()));
            claims.flush();
            if(c.getClaimant()!=null) {
                notifications.notifyUser(c.getClaimant().getId(),"APPOINTMENT_RESCHEDULED",
                    "Văn phòng đã đổi lịch hẹn nhận đồ #" + c.getId(),
                    "Lịch hẹn mới cho \"" + c.getPost().getTitle() + "\" tại " + request.location().strip()
                        + ". Hãy phản hồi trong 12 giờ. Quá hạn, lịch sẽ bị hủy và yêu cầu nhận đồ sẽ bị từ chối. Lý do đổi lịch: " + request.reason().strip(),
                    "/van-phong/claims/" + c.getId(),false);
            }
        });
        return sendInvitation(id);
    }

    private ReviewResult sendInvitation(long id) {
        String delivery;
        try { delivery=invitations.send(id)?"SENT":"FAILED"; }
        catch(RuntimeException failure) { delivery="FAILED"; }
        return new ReviewResult(id,"APPROVED",delivery);
    }

    @Transactional
    public Workflow cancel(long id,VersionedReason request,CustomUserDetails user) {
        Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
        requireAvailable(c);
        Appointment appointment=scheduled(c);
        requireNotVerified(c,appointment);
        appointment.cancelByOffice(request.reason().strip());
        c.review("REJECTED"); c.getPost().reopenAfterClaimCancellation();
        appeals.reopen(c);
        activities.save(new ClaimActivity(c,actor,"OFFICE_CANCEL","APPROVED",request.reason().strip()));
        claims.flush();
        if(c.getClaimant()!=null) {
            notifications.notifyUser(c.getClaimant().getId(),"CLAIM_REJECTED",
                "Văn phòng đã hủy lịch hẹn & hồ sơ nhận đồ #" + c.getId(),
                "Hồ sơ nhận đồ \"" + c.getPost().getTitle() + "\" đã bị hủy. Lý do: " + request.reason().strip(),
                "/van-phong/claims/" + c.getId(),true);
        }
        return view(c,actor);
    }

    @Transactional
    public Workflow revoke(long id, VersionedReason request, CustomUserDetails user) {
        Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
        if (appeals.hasUnresolvedAppeal(c.getPost().getId())) throw bad("Quản lý đang xử lý tranh chấp cho lần duyệt này.");
        reportHolds.requireAvailable(c.getPost().getId());
        reopenApproval(c,actor,request.reason().strip());
        return view(c,actor);
    }

    private record AppealDecision(long approvalId, long claimId, long reportId, long decisionId,
                                  long managerId, String conclusion) {}

    // Both a Manager's conclusion and the withdrawal of the last open appeal can finish a round.
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void finishAppealReview(long postId) {
        var decision = jdbc.query("""
            select a.approval_id, a.approved_claim_id, r.report_id, d.decision_id, d.actor_id, d.conclusion
            from p2p_claim_approval a join claim chosen on chosen.claim_id=a.approved_claim_id
            join p2p_claim_appeal appeal using(approval_id) join report r using(report_id)
            join report_decision d using(report_id)
            where chosen.post_id=? and a.custody_mode='OFFICE' and a.closed_at is null
              and r.status='RESOLVED' and d.action='APPEAL_WRONG_RECIPIENT'
              and not exists (select 1 from p2p_claim_appeal other join report pending using(report_id)
                  where other.approval_id=a.approval_id and pending.status in ('OPEN','IN_REVIEW'))
            order by d.decision_id desc limit 1
            """, (row, index) -> new AppealDecision(row.getLong("approval_id"), row.getLong("approved_claim_id"),
                row.getLong("report_id"), row.getLong("decision_id"), row.getLong("actor_id"), row.getString("conclusion")), postId)
            .stream().findFirst().orElse(null);
        if (decision == null) return;

        Claim claim = lock(decision.claimId());
        Account manager = em.getReference(Account.class, decision.managerId());
        manageable(claim, manager);
        reportHolds.requireAvailable(postId);
        if (!appeals.canReopenFromDecision(claim, decision.approvalId()))
            throw bad("Lần duyệt đã thay đổi. Hãy tải lại.");
        String conclusion = decision.conclusion();
        reopenApproval(claim, manager, "Báo cáo #" + decision.reportId() + ": " + conclusion.substring(0, Math.min(1800, conclusion.length())));
        jdbc.update("""
            update report_decision set after_state=after_state||'{"approvalReopened":true}'::jsonb where decision_id=?
            """, decision.decisionId());
        notifications.notifyRoles(List.of(APPROVER), decision.managerId(), "CLAIM_REVIEW_REOPENED",
            "Yêu cầu nhận tài sản được mở lại để xét duyệt", "Quản lý đã kết luận tranh chấp cho lần duyệt trước.",
            "/approver/office-claims/post/" + postId, false);
    }

    private void reopenApproval(Claim c, Account actor, String reason) {
        Appointment appointment=activeAppointment(c.getId());
        if (appointment!=null) {
            requireNotVerified(c,appointment);
            appointment.cancelByOffice(reason);
        }
        c.review("PENDING");
        c.getPost().reopenAfterClaimCancellation();
        activities.save(new ClaimActivity(c,actor,"REVOKE_APPROVAL","APPROVED",reason));
        claims.flush();
        appeals.reopen(c);
        if (c.getClaimant()!=null) notifications.notifyUser(c.getClaimant().getId(),"CLAIM_APPROVAL_REVOKED",
            "Yêu cầu nhận tài sản được đưa về xét duyệt", reason, "/van-phong/claims/"+c.getId(),false);
    }

    @Transactional
    public Workflow verify(long id,Verify request,CustomUserDetails user) {
        Account actor=approver(user); Claim c=lock(id); expected(c,request.expectedVersion()); manageable(c,actor);
        requireAvailable(c);
        Appointment a=scheduled(c);
        if(!"CONFIRMED".equals(a.getResponseStatus())) throw bad("Người nhận cần đồng ý lịch hẹn trước khi xác minh tại quầy.");
        jdbc.update("insert into office_claim_verification(claim_id,appointment_id,verified_by,result,note) values(?,?,?,?,?)",
                id,a.getId(),actor.getId(),request.result(),request.note().strip());
        if("FAILED".equals(request.result())) {
            String reason="Không đạt xác minh người nhận tại quầy: "+request.note().strip();
            reason=reason.substring(0,Math.min(2000,reason.length()));
            a.cancelByOffice(reason);
            c.review("REJECTED");
            c.getPost().reopenAfterClaimCancellation();
            appeals.reopen(c);
            activities.save(new ClaimActivity(c,actor,"OFFICE_CANCEL","APPROVED",reason));
            if(c.getClaimant()!=null) notifications.notifyUser(c.getClaimant().getId(),"CLAIM_REJECTED",
                "Yêu cầu nhận đồ #"+id+" bị từ chối sau xác minh",
                reason+". Lịch hẹn đã hủy. Bạn không được gửi lại yêu cầu cho món đồ này.","/van-phong/claims/"+id,false);
        }
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
                activities.save(new ClaimActivity(c,current,"HANDOVER","APPROVED","Văn phòng đã giao đủ đồ và ghi nhận biên bản trao trả."));
                finish(c,current);
                claims.flush();inserted[0]=true;return view(c,current);
            });
            if(!inserted[0]) images.cleanup(uploaded);
            return result;
        } catch(RuntimeException failure) {images.cleanup(uploaded);throw failure;}
    }

    /** Office-only completion for records created under the previous waiting-confirmation flow. */
    @Transactional
    public Workflow complete(long id,Confirm request,CustomUserDetails user) {
        Account actor=approver(user); Claim c=lock(id);
        if(c.getClaimant()!=null&&c.getClaimant().getId().equals(actor.getId()))throw bad("Nhờ nhân viên khác xử lý yêu cầu của bạn.");
        Handover h=handover(id);
        if(h==null)throw bad("Chưa có biên bản giao đồ.");
        if("COMPLETED".equals(h.status()))return view(c,actor);
        responseCheck(c,h,request.expectedVersion());
        finish(c,actor);claims.flush();return view(c,actor);
    }
    private void finish(Claim c,Account actor) {
        requireAvailable(c);
        jdbc.update("update office_handover set status='COMPLETED',confirmed_at=now(),version=version+1 where claim_id=?",c.getId());
        c.completeReceipt(); c.getPost().markReturnedFromOffice();
        appeals.close(c);
        activities.save(new ClaimActivity(c,actor,"CONFIRM_RECEIPT","APPROVED","Văn phòng xác nhận đã giao đủ đồ cho người nhận. Hoàn tất trao trả."));
        if(c.getClaimant()!=null) {
            notifications.notifyUser(c.getClaimant().getId(),"HANDOVER_COMPLETED",
                "Hoàn tất bàn giao đồ vật tại văn phòng (#" + c.getId() + ")",
                "Văn phòng xác nhận đã bàn giao thành công đồ vật \"" + c.getPost().getTitle() + "\" cho bạn.",
                "/van-phong/claims/" + c.getId(),true);
        }
        for(Claim other:claims.findAllForOfficeTransfer(c.getPost().getId())) {
            if(!other.getId().equals(c.getId()) && Set.of("PENDING","NEEDS_INFO").contains(other.getStatus())) {
                String previous=other.getStatus();other.closeAfterReturn();
                activities.save(new ClaimActivity(other,actor,"AUTO_CLOSE",previous,"Đồ đã được văn phòng trao trả."));
            }
        }
    }

    private Workflow recordCheck(Claim c,Account actor,RecordHandover request,String hash) {
        Handover existing=handover(c.getId());
        if(existing!=null) {
            Boolean same=jdbc.queryForObject("select exists(select 1 from office_handover where claim_id=? and recorded_by=? and client_request_id=? and request_hash=?)",Boolean.class,c.getId(),actor.getId(),request.clientRequestId(),hash);
            if(Boolean.TRUE.equals(same))return view(c,actor);
            throw bad("Đồ đã được ghi nhận giao. Hãy tải lại hồ sơ.");
        }
        expected(c,request.expectedVersion());manageable(c,actor);
        requireAvailable(c);
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
        Appointment a=Optional.ofNullable(activeAppointment(c.getId())).orElseThrow(()->bad("Không có lịch hẹn đang hiệu lực."));
        em.refresh(a,LockModeType.PESSIMISTIC_WRITE);return a;
    }
    private Appointment activeAppointment(long id) {
        return appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(id,"SCHEDULED").orElse(null);
    }
    private void requireAvailable(Claim claim) {
        reportHolds.requireAvailable(claim.getPost().getId());
        appeals.requireOfficeClearance(claim);
    }
    private void requireNotVerified(Claim c,Appointment appointment) {
        var history=verifications(c.getId());
        if(!history.isEmpty() && history.getFirst().appointmentId()==appointment.getId() && "PASSED".equals(history.getFirst().result()))
            throw bad("Người nhận đã xác minh đạt tại quầy. Không thể đổi lịch hoặc thu hồi duyệt ở bước này.");
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
        Appointment a=activeAppointment(c.getId());
        boolean officer=actor.getRole()==APPROVER && (c.getClaimant()==null||!actor.getId().equals(c.getClaimant().getId()));
        var filedAppeal = officer ? appeals.latestOfficeAppeal(c.getId()) : null;
        boolean approved="APPROVED".equals(c.getStatus()) && "RESERVED".equals(c.getPost().getStatus());
        boolean inStorage=c.getReceipt()!=null && "IN_STORAGE".equals(c.getReceipt().getStatus());
        var window=approved?appeals.handoverWindow(c):null;
        boolean blocked=window!=null&&window.blocksHandover();
        boolean onHold=approved&&reportHolds.held(c.getPost().getId());
        boolean preparing=officer && approved && h==null && a!=null && inStorage && !blocked && !onHold;
        boolean confirmed=a!=null&&"CONFIRMED".equals(a.getResponseStatus());
        boolean verified=confirmed&&!history.isEmpty()&&history.getFirst().appointmentId()==a.getId()&&"PASSED".equals(history.getFirst().result());
        boolean canComplete=officer&&h!=null&&approved&&"WAITING_CONFIRMATION".equals(h.status());
        boolean rejectedAtCounter="REJECTED".equals(c.getStatus())&&!history.isEmpty()&&"FAILED".equals(history.getFirst().result());
        String next=h!=null?switch(h.status()) {case "COMPLETED"->"Đã hoàn tất trao trả.";case "DISPUTED"->"Đã ghi nhận phản ánh. Vui lòng liên hệ văn phòng.";default->"Đã có biên bản giao đồ. Nhân viên văn phòng cần xác nhận hoàn tất.";}
                :rejectedAtCounter?"Không đạt xác minh tại quầy. Yêu cầu đã bị từ chối, lịch hẹn đã hủy; tài khoản này không được yêu cầu lại món đồ này."
                :Set.of("REJECTED","CANCELLED","CLOSED").contains(c.getStatus())?"Hồ sơ đã kết thúc. Xem lý do trong lịch sử xử lý.":!approved?"Hồ sơ cần được duyệt trước khi thực hiện trao trả."
                :blocked?switch(window.state()) {
                    case OPEN -> "Văn phòng sẽ lập lịch sau khi hết thời hạn gửi tranh chấp.";
                    case UNDER_REVIEW -> "Quản lý đang xử lý tranh chấp. Việc lập lịch và trao trả đang tạm dừng.";
                    case REVIEW_REQUIRED -> "Kết luận tranh chấp yêu cầu xét duyệt lại.";
                    case CLEARED -> throw new IllegalStateException();
                }:onHold?"Hồ sơ đang tạm dừng để xử lý báo cáo."
                :a==null?"Đủ điều kiện lập lịch nhận tài sản.":!confirmed?OfficeAppointmentGuidance.pendingMessage(a,OffsetDateTime.now()):!verified?"Đối chiếu danh tính và căn cứ sở hữu tại quầy.":"Đã xác minh. Có thể ghi nhận giao đồ.";
        return new Workflow(history,h,preparing&&confirmed,preparing&&verified,preparing&&!verified,preparing&&!verified,
            canComplete&&!blocked&&!onHold,next,officer&&approved&&h==null&&a==null&&inStorage&&!blocked&&!onHold,
            officer&&approved&&h==null&&!verified&&inStorage&&!onHold
                && (window==null||window.state()==P2pClaimAppealService.State.OPEN||window.state()==P2pClaimAppealService.State.CLEARED),
            officer&&"PENDING".equals(c.getStatus())&&appeals.hasCompetitors(c),blocked?window.deadline():null,
            filedAppeal==null?null:new FiledAppeal(filedAppeal.reportId(),filedAppeal.reportStatus()));
    }
}
