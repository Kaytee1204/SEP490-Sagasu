package com.example.base.service.claim;

import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.dto.claim.ClaimDetailDto;
import com.example.base.dto.claim.ClaimQuestionnaireDto;
import com.example.base.dto.claim.SubmitClaimRequest;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.*;
import com.example.base.service.report.SubmissionRestrictions;
import com.example.base.storage.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.service.office.OfficeManagementService.*;
import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.entity.AccountRole.APPROVER;

@Service @RequiredArgsConstructor
public class OfficeClaimService {
    private final ClaimRepository claims;
    private final ClaimDailyLimit dailyLimit;
    private final SubmissionRestrictions restrictions;
    private final ClaimQuestionnaireService questionnaires;
    private final OfficeClaimProgress progress;
    private final PostRepository posts;
    private final AccountRepository accounts;
    private final ItemReceiptRepository receipts;
    private final ClaimActivityRepository activities;
    private final AppointmentRepository appointments;
    private final OfficeAccess access;
    private final OfficeManagementService management;
    private final OfficeInvitationService invitations;
    private final ImageStorage storage;
    private final ImageValidator images;
    private final Validator validator;
    private final PlatformTransactionManager transactions;
    private final EntityManager em;
    private final com.example.base.service.notification.NotificationService notifications;
    private final P2pClaimAppealService appeals;

    @Transactional(readOnly=true)
    public List<ClaimantOption> findClaimants(String q, CustomUserDetails user) {
        access.require(user,APPROVER);
        String keyword=q.strip();
        if(keyword.length()<3) return List.of();
        if(keyword.length()>255) throw bad("Email tìm kiếm tối đa 255 ký tự.");
        return accounts.findTop10ByEmailContainingIgnoreCaseAndStatusOrderByEmailAsc(keyword,AccountStatus.ACTIVE)
            .stream().map(a->new ClaimantOption(a.getId(),person(a),a.getEmail())).toList();
    }

    private Account counterClaimant(Submission request) {
        if(request.claimantId()==null) throw bad("Chọn tài khoản người yêu cầu nhận đồ từ kết quả tìm kiếm.");
        Account account=accounts.findById(request.claimantId()).orElseThrow(()->bad("Không tìm thấy tài khoản người yêu cầu nhận đồ."));
        if(account.getStatus()!=AccountStatus.ACTIVE) throw bad("Tài khoản người yêu cầu nhận đồ không còn hoạt động.");
        return account;
    }

    @Transactional(readOnly=true)
    public PageData<ClaimView> list(int page,String status,String q,String stage,boolean mine,CustomUserDetails user) {
        Account actor=mine?access.require(user):access.require(user,APPROVER);
        String keyword=listKeyword(status,q);
        var data=progress.list(page,status,stage,keyword,mine?actor.getId():null);
        return claimPage(data,actor);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GroupedClaims groups(int page,String status,String q,String stage,String channel,OffsetDateTime since,
                                boolean oldest,CustomUserDetails user) {
        access.require(user,APPROVER);
        var grouped=progress.groups(page,status,stage,listKeyword(status,q),validateChannel(channel),since,oldest);
        var data=grouped.page();
        return new GroupedClaims(new PageData<>(data.content().stream().map(row->new ClaimGroup(row.postId(),row.title(),
            storage.readUrl(row.storageKey(),row.imageUrl()),row.claims(),row.people(),row.waiting(),row.latest(),row.stage())).toList(),
            data.page(),data.totalElements(),data.totalPages()),grouped.counts());
    }

    @Transactional(readOnly=true)
    public PageData<ClaimView> forPost(long postId,int page,String status,String q,String stage,String channel,
                                     OffsetDateTime since,boolean oldest,CustomUserDetails user) {
        Account actor=access.require(user,APPROVER);
        if(postId<1)throw bad("Mã bài đăng không hợp lệ.");
        return claimPage(progress.list(page,status,stage,listKeyword(status,q),null,postId,validateChannel(channel),since,oldest),actor);
    }

    private String listKeyword(String status,String q) {
        if(!Set.of("ALL","PENDING","NEEDS_INFO","APPROVED","REJECTED","CANCELLED","COMPLETED","CLOSED").contains(status)) throw bad("Trạng thái không hợp lệ.");
        String keyword=q==null?"":q.strip().toLowerCase(Locale.ROOT);
        if(keyword.length()>200) throw bad("Từ khóa tối đa 200 ký tự.");
        return keyword;
    }
    private String validateChannel(String channel) {
        if(!Set.of("ALL","ONLINE","COUNTER").contains(channel)) throw bad("Kênh gửi không hợp lệ.");
        return channel;
    }
    private PageData<ClaimView> claimPage(PageData<OfficeClaimProgress.Progress> data,Account actor) {
        var content=data.content().stream().map(row->view(claims.findById(row.id()).orElseThrow(),false,actor.getRole()==APPROVER,actor.getId(),row)).toList();
        return new PageData<>(content,data.page(),data.totalElements(),data.totalPages());
    }
    @Transactional(readOnly=true)
    public ClaimView detail(long id,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=office(id); participant(c,actor);
        return view(c,true,actor.getRole()==APPROVER,actor.getId(),progress.of(c.getId()));
    }
    @Transactional(readOnly=true)
    public ClaimView personalDetail(long id,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=office(id);
        if(c.getClaimant()==null || !c.getClaimant().getId().equals(actor.getId()))
            throw new AppException(ErrorCode.FORBIDDEN);
        return view(c,true,false,actor.getId(),progress.of(c.getId()));
    }
    public Long submit(Submission request,List<MultipartFile> files,boolean counter,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->validateQuestionnaire(validateSubmission(request,counter,user,false), request));
        var validated=images.validate(files); var keys=new ArrayList<String>();
        try {
            for(var image:validated) {
                String key="claims/office/"+UUID.randomUUID()+"."+image.extension(); keys.add(key);
                storage.put(key,image.bytes(),image.mime());
            }
            return new TransactionTemplate(transactions).execute(tx->{
                Post post=validateSubmission(request,counter,user,true);
                var answers=validateQuestionnaire(post,request);
                Account actor=counter?access.require(user,APPROVER):access.require(user);
                String contact=clean(request.claimantEmail());
                Account claimant=counter?counterClaimant(request):actor;
                if(counter) contact=claimant.getEmail();
                if(claimant!=null && claims.existsByPost_IdAndClaimant_IdAndStatusIn(post.getId(),claimant.getId(),List.of("PENDING","NEEDS_INFO","APPROVED")))
                    throw new AppException(ErrorCode.CLAIM_DUPLICATE);
                String name=person(claimant);
                dailyLimit.requireAvailable(claimant.getId());
                if(name==null) throw bad("Nhập họ tên người yêu cầu nhận đồ.");
                if(contact==null&&claimant!=null) contact=claimant.getEmail();
                Claim c=Claim.office(post,claimant,actor,counter?"COUNTER":"ONLINE",name,
                    contact==null?null:contact.toLowerCase(Locale.ROOT),clean(request.claimantPhone()),request.description(),request.evidenceNote(),receipts.findByPostId(post.getId()).orElseThrow());
                answers.forEach(c::addAnswer);
                for(int i=0;i<validated.size();i++) {
                    var image=validated.get(i); String filename=files.get(i).getOriginalFilename();
                    if(filename!=null) { filename=filename.replace('\\','/'); filename=filename.substring(filename.lastIndexOf('/')+1); if(filename.length()>255) filename=filename.substring(filename.length()-255); }
                    c.addEvidence(new ClaimEvidence(keys.get(i),filename,image.mime(),image.bytes().length,image.hash(),i));
                }
                claims.saveAndFlush(c); activities.save(new ClaimActivity(c,actor,"SUBMIT",null,null));
                if(counter && claimant!=null) {
                    notifications.notifyUser(claimant.getId(),"OFFICE_CLAIM_SUBMITTED",
                        "Văn phòng đã tạo hồ sơ nhận đồ #" + c.getId(),
                        "Hồ sơ nhận đồ tại quầy cho \"" + post.getTitle() + "\" đã được ghi nhận.",
                        "/van-phong/claims/" + c.getId(),false);
                } else if(!counter) {
                    notifications.notifyRoles(List.of(APPROVER),actor.getId(),"OFFICE_CLAIM_SUBMITTED",
                        "Hồ sơ nhận đồ văn phòng mới #" + c.getId(),
                        name + " vừa gửi hồ sơ nhận đồ văn phòng cho \"" + post.getTitle() + "\".",
                        "/approver/office-claims/" + c.getId(),false);
                }
                return c.getId();
            });
        } catch(RuntimeException failure) {
            for(String key:keys) try { storage.delete(key); } catch(RuntimeException ignored) { /* Preserve original failure. */ }
            throw failure;
        }
    }
    private List<ClaimAnswer> validateQuestionnaire(Post post, Submission r) {
        if (questionnaires == null || (r.questionnaireVersion() == null && r.answers() == null)) {
            return List.of();
        }
        return questionnaires.validateAndSnapshot(post, questionnaireRequest(r));
    }
    private SubmitClaimRequest questionnaireRequest(Submission r) {
        return new SubmitClaimRequest(r.description(),r.evidenceNote(),r.questionnaireVersion(),r.answers());
    }
    @Transactional(readOnly=true)
    public ClaimQuestionnaireDto questionnaire(long postId,boolean counter,CustomUserDetails user) {
        if(counter) access.require(user,APPROVER); else access.require(user);
        Post post=posts.findById(postId).orElseThrow(()->bad("Không tìm thấy bài đăng."));
        eligible(post);
        // Office integration: share the P2P template and snapshot rules without changing P2P authorization.
        return questionnaires.forCategory(post.getCategory());
    }
    private Post validateSubmission(Submission r,boolean counter,CustomUserDetails user,boolean lock) {
        Account actor=counter?access.require(user,APPROVER):access.require(user);
        if(counter) counterClaimant(r);
        Post p=(lock?posts.findForClaimUpdate(r.postId()):posts.findById(r.postId())).orElseThrow(()->bad("Không tìm thấy bài đăng."));
        if(lock) em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        eligible(p);
        if(!"OPEN".equals(p.getStatus())||!"VISIBLE".equals(p.getModerationStatus())) throw bad("Bài đăng hiện không nhận thêm yêu cầu.");
        Account claimant = counter ? counterClaimant(r) : actor;
        if (claims.hasResubmissionBlock(p.getId(), claimant.getId()))
            throw new AppException(ErrorCode.CLAIM_RESUBMISSION_BLOCKED);
        if (lock) restrictions.requireAvailableForSubmission(claimant.getId(), SubmissionRestrictions.Action.CLAIM);
        else restrictions.checkAvailable(claimant.getId(), SubmissionRestrictions.Action.CLAIM);
        if(claims.hasFailedOfficeVerification(p.getId(),claimant.getId()))
            throw bad("Người nhận đã không đạt xác minh tại quầy và không được yêu cầu nhận lại món đồ này.");
        if (claimant != null && (Objects.equals(p.getAccount().getId(), claimant.getId()) || (p.getCreatedBy() != null && Objects.equals(p.getCreatedBy().getId(), claimant.getId())))) {
            throw new AppException(ErrorCode.CLAIM_SELF_NOT_ALLOWED);
        }
        return p;
    }
    private void eligible(Post p) {
        if(p.getDeletedAt()!=null||!"FOUND".equals(p.getPostType())||!"OFFICE".equals(p.getCustodyMode())
            ||!Set.of("OPEN","RESERVED").contains(p.getStatus())) throw bad("Đồ vật không còn thuộc luồng xử lý tại văn phòng.");
        ItemReceipt r=receipts.findByPostId(p.getId()).orElseThrow(()->bad("Chưa có hồ sơ tiếp nhận."));
        if(!"IN_STORAGE".equals(r.getStatus())||!"ACCEPTED".equals(r.getReviewStatus())||r.getReceivedAt()==null)
            throw bad("Văn phòng chưa tiếp nhận hoặc đã trao trả đồ vật.");
    }
    private record Committed(Long id,String status,boolean sendInvitation,boolean appealWindow) {}
    private record EmailCheck(@NotBlank @Email String email) {}
    public ReviewResult review(long id,Decision request,CustomUserDetails user) {
        if (!Set.of("APPROVED", "REJECTED").contains(request.decision())) throw bad("Quyết định xét duyệt không hợp lệ.");
        Committed committed=new TransactionTemplate(transactions).execute(tx->{
            Account actor=access.require(user,APPROVER); Claim c=locked(id); eligible(c.getPost()); version(c,request.expectedVersion());
            if(c.getClaimant()!=null && c.getClaimant().getId().equals(actor.getId())) throw bad("Không được tự xét duyệt hồ sơ nhận đồ của mình.");
            if(!"PENDING".equals(c.getStatus())) throw bad("Hồ sơ này đã được xử lý. Hãy tải lại.");
            if ("RESERVED".equals(c.getPost().getStatus())) {
                throw new AppException(ErrorCode.CLAIM_STATE_INVALID,
                    "Bài đăng này đã có yêu cầu được duyệt. Hồ sơ hiện tại được giữ để xét tiếp nếu quyết định duyệt được thu hồi.");
            }
            if(!"PENDING".equals(c.getStatus())&&!"REJECTED".equals(request.decision())) throw bad("Chờ bổ sung thông tin trước khi duyệt.");
            String previous=c.getStatus(); Appointment a=null;
            OffsetDateTime deadline=null;
            if("APPROVED".equals(request.decision())) {
                if (claims.lastWrongRecipientConclusion(c.getId()) != null && clean(request.reason()) == null)
                    throw bad("Cần ghi căn cứ mới để chấp thuận lại yêu cầu này.");
                if(c.getClaimant()!=null && claims.hasFailedOfficeVerification(c.getPost().getId(),c.getClaimant().getId()))
                    throw bad("Không thể duyệt: người nhận đã không đạt xác minh tại quầy cho món đồ này.");
                if(claims.existsByPost_IdAndStatusIn(c.getPost().getId(),List.of("APPROVED"))) throw bad("Đồ vật đã có yêu cầu được duyệt.");
                c.getPost().reserveForClaim();
                deadline=appeals.open(c);
                if (deadline==null && request.scheduledAt()!=null) {
                    OffsetDateTime now=OffsetDateTime.now();
                    invitations.validatePickupSchedule(request.scheduledAt(),now);
                    if (clean(request.location())==null) throw bad("Nhập địa điểm nhận đồ.");
                    String recipient=clean(request.email());
                    if (recipient==null) recipient=recipient(c);
                    if (!validator.validate(new EmailCheck(recipient)).isEmpty())
                        throw bad("Cần email hợp lệ của người nhận để gửi lịch hẹn.");
                    c.contactEmail(recipient.toLowerCase(Locale.ROOT));
                    a=appointments.save(Appointment.schedule(c,actor,request.scheduledAt(),
                        request.location().strip(),clean(request.appointmentNote()),now));
                }
            }
            c.review(request.decision());
            String action="APPROVED".equals(request.decision()) ? "APPROVE" : "REJECT";
            activities.save(new ClaimActivity(c,actor,action,previous,request.reason())); claims.flush();
            if(c.getClaimant()!=null) {
                if("APPROVED".equals(request.decision()) && a==null) {
                    notifications.notifyUser(c.getClaimant().getId(),"CLAIM_APPROVED",
                        "Yêu cầu nhận tài sản #" + c.getId() + " đã được duyệt",
                        deadline!=null ? "Văn phòng sẽ lập lịch sau khi hết thời hạn gửi tranh chấp và các tranh chấp đã được xử lý."
                            : "Văn phòng sẽ thông báo khi lập lịch nhận tài sản.",
                        "/van-phong/claims/" + c.getId(),false);
                } else if("APPROVED".equals(request.decision())) {
                    notifications.notifyUser(c.getClaimant().getId(),"APPOINTMENT_SCHEDULED",
                        "Yêu cầu nhận đồ #" + c.getId() + " được duyệt & có lịch hẹn mới",
                        "Văn phòng đã lập lịch tại " + a.getLocation() + ". Hãy phản hồi trong 12 giờ. Quá hạn, lịch sẽ bị hủy và yêu cầu nhận đồ sẽ bị từ chối.",
                        "/van-phong/claims/" + c.getId(),false);
                } else if("REJECTED".equals(request.decision())) {
                    notifications.notifyUser(c.getClaimant().getId(),"CLAIM_REJECTED",
                        "Hồ sơ nhận đồ văn phòng #" + c.getId() + " bị từ chối",
                        "Văn phòng đã từ chối hồ sơ nhận đồ \"" + c.getPost().getTitle() + "\". Lý do: " + request.reason(),
                        "/van-phong/claims/" + c.getId(),true);
                }
            }
            if("APPROVED".equals(request.decision()) && deadline==null && a!=null) {
                notifyOtherOfficeClaimants(c);
            }
            return new Committed(c.getId(),c.getStatus(),a!=null,deadline!=null);
        });
        String delivery=committed.appealWindow()?"APPEAL_WINDOW":"NOT_REQUIRED";
        if(committed.sendInvitation()) {
            try { delivery=invitations.send(committed.id())?"SENT":"FAILED"; }
            catch(RuntimeException failure) {
                // Approval has committed: report mail failure, not a misleading failed approval.
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Invitation delivery failed after approval for claim {} ({})",committed.id(),failure.getClass().getSimpleName());
                delivery="FAILED";
            }
        }
        return new ReviewResult(committed.id(),committed.status(),delivery);
    }
    public ReviewResult resend(long id,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            access.require(user,APPROVER); Claim c=locked(id); eligible(c.getPost());
            if(!"APPROVED".equals(c.getStatus())) throw bad("Yêu cầu chưa được duyệt.");
            Appointment a=appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(id,"SCHEDULED").orElseThrow(()->bad("Chưa có lịch hẹn."));
            if(!a.responseExpired(OffsetDateTime.now()) && !a.getScheduledAt().isAfter(OffsetDateTime.now())) throw bad("Lịch hẹn đã qua, không gửi lại email cũ.");
        });
        return invitations.sendWithResult(id);
    }
    private void notifyOtherOfficeClaimants(Claim approved) {
        List<Claim> others = claims.findOtherActiveClaims(approved.getPost().getId(), approved.getId(), List.of("PENDING", "NEEDS_INFO"));
        if (others == null || others.isEmpty()) return;
        Long approvedClaimantId = approved.getClaimant() != null ? approved.getClaimant().getId() : null;
        Set<Long> notified = new HashSet<>();
        for (Claim other : others) {
            if (other.getClaimant() == null) continue;
            Long recipientId = other.getClaimant().getId();
            if (recipientId == null || Objects.equals(recipientId, approvedClaimantId) || !notified.add(recipientId)) {
                continue;
            }
            notifications.notifyUser(
                    recipientId,
                    "CLAIM_ITEM_RESERVED",
                    "Đồ vật bạn yêu cầu đang được văn phòng hẹn trao trả (#" + other.getId() + ")",
                    "Món đồ \"" + approved.getPost().getTitle()
                            + "\" hiện đang trong quá trình hẹn xác minh và trao trả cho một người yêu cầu khác tại văn phòng. Hồ sơ #"
                            + other.getId() + " của bạn vẫn được giữ nguyên nếu lịch hẹn hiện tại không hoàn tất.",
                    "/van-phong/claims/" + other.getId(),
                    false
            );
        }
    }

    private String recipient(Claim c) { return c.getClaimantEmail()!=null?c.getClaimantEmail():c.getClaimant()==null?null:c.getClaimant().getEmail(); }
    private Claim office(long id) { return claims.findById(id).filter(c->"OFFICE".equals(c.getClaimType())).orElseThrow(()->new AppException(ErrorCode.CLAIM_NOT_FOUND)); }
    private Claim locked(long id) {
        Long postId=claims.findPostId(id).orElseThrow(()->new AppException(ErrorCode.CLAIM_NOT_FOUND));
        Post p=posts.findForClaimUpdate(postId).orElseThrow(); em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        Claim c=claims.findForUpdate(id).orElseThrow(); em.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(!"OFFICE".equals(c.getClaimType())) throw new AppException(ErrorCode.CLAIM_NOT_FOUND); return c;
    }
    private void participant(Claim c,Account actor) { if(actor.getRole()!=APPROVER&&(c.getClaimant()==null||!c.getClaimant().getId().equals(actor.getId()))) throw new AppException(ErrorCode.FORBIDDEN); }
    private void version(Claim c,Long v) { if(v==null||v!=c.getVersion()) throw new AppException(ErrorCode.CLAIM_STALE); }
    private ClaimView view(Claim c,boolean detail,boolean reviewer,long actorId,OfficeClaimProgress.Progress phase) {
        var appointment=detail?appointments.findFirstByClaim_IdOrderByIdDesc(c.getId()).map(a->new AppointmentView(a.getId(),a.getScheduledAt(),a.getLocation(),a.getNote(),a.getStatus(),a.getResponseStatus(),a.getResponseDeadline(),a.getCancellationReason(),a.getEmailSentAt())).orElse(null):null;
        boolean ownRequest = c.getClaimant() != null && c.getClaimant().getId() == actorId;
        boolean canReview = reviewer && !ownRequest && "PENDING".equals(c.getStatus())
            && !"RESERVED".equals(c.getPost().getStatus());
        boolean canInspect = detail && reviewer && !ownRequest;
        ItemReceipt receipt = canInspect ? receipts.findByPostId(c.getPost().getId()).orElse(null) : null;
        String receiptIdentifiers = receipt == null ? null : clean(receipt.getPrivateIdentifiers());
        String identifiers = canInspect && receiptIdentifiers == null ? clean(c.getPost().getPrivateIdentifiers()) : receiptIdentifiers;
        return new ClaimView(c.getId(),c.getPost().getId(),c.getPost().getTitle(),c.getStatus(),c.getSubmissionChannel(),
            c.getClaimant()==null?null:c.getClaimant().getId(),c.getClaimantName(),detail?recipient(c):null,detail?c.getClaimantPhone():null,
            person(c.getCreatedBy()),detail?c.getDescription():null,detail?c.getEvidenceNote():null,identifiers,c.getVersion(),c.getCreatedAt(),
            receipt==null?null:management.receiptView(receipt),
            detail?c.getEvidence().stream().map(e->new ClaimDetailDto.EvidenceDto(e.getId(),storage.readUrl(e.getStorageKey(),null),e.getOriginalName(),e.getMimeType(),e.getSizeBytes())).toList():List.of(),
            detail?activities.findByClaim_IdOrderByCreatedAtAscIdAsc(c.getId()).stream().map(e->new ClaimDetailDto.ActivityDto(e.getId(),e.getAction(),person(e.getActor()),e.getFromStatus(),e.getToStatus(),e.getReason(),e.getCreatedAt())).toList():List.of(),appointment,detail?c.getAnswers().stream().map(a->new AnswerView(a.getId(),a.getQuestionLabel(),a.getAnswerText(),a.getSectionTitle())).toList():List.of(),phase.stage(),phase.scheduledAt(),canReview,ownRequest,
            detail && ownRequest && ClaimService.canWithdrawOfficeRequest(c),
            detail && canReview ? claims.lastWrongRecipientConclusion(c.getId()) : null,
            c.getCloseReason());
    }
}

