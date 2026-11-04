package com.example.base.service.claim;

import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.dto.claim.ClaimDetailDto;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.service.office.*;
import com.example.base.storage.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.OffsetDateTime;
import java.util.*;
import static com.example.base.service.office.OfficeManagementService.*;
import static com.example.base.service.office.OfficeAccess.bad;
import static com.example.base.entity.AccountRole.MANAGER;

@Service @RequiredArgsConstructor
public class OfficeClaimService {
    private final ClaimRepository claims;
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

    @Transactional(readOnly=true)
    public List<ClaimantOption> findClaimants(String q, CustomUserDetails user) {
        access.require(user,MANAGER);
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
    public PageData<ClaimView> list(int page,String status,String q,boolean mine,CustomUserDetails user) {
        Account actor=mine?access.require(user):access.require(user,MANAGER);
        if(!Set.of("ALL","PENDING","NEEDS_INFO","APPROVED","REJECTED","CANCELLED").contains(status)) throw bad("Trạng thái không hợp lệ.");
        String keyword=q==null?"":q.strip().toLowerCase(Locale.ROOT);
        if(keyword.length()>200) throw bad("Từ khóa tối đa 200 ký tự.");
        Page<Claim> data=claims.findAll((root,cq,cb)-> {
            var filters=new ArrayList<jakarta.persistence.criteria.Predicate>();
            filters.add(cb.equal(root.get("claimType"),"OFFICE"));
            if(mine) filters.add(cb.equal(root.get("claimant").get("id"),actor.getId()));
            if(!"ALL".equals(status)) filters.add(cb.equal(root.get("status"),status));
            filters.add(cb.or(cb.gt(cb.locate(cb.lower(root.get("post").get("title")),keyword),0),
                cb.gt(cb.locate(cb.lower(cb.coalesce(root.<String>get("claimantName"),"")),keyword),0)));
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },page(page));
        return result(data,data.stream().map(c->view(c,false,actor.getRole()==MANAGER)).toList());
    }
    @Transactional(readOnly=true)
    public ClaimView detail(long id,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=office(id); participant(c,actor);
        return view(c,true,actor.getRole()==MANAGER);
    }
    public Long submit(Submission request,List<MultipartFile> files,boolean counter,CustomUserDetails user) {
        new TransactionTemplate(transactions).executeWithoutResult(tx->validateSubmission(request,counter,user,false));
        var validated=images.validate(files); var keys=new ArrayList<String>();
        try {
            for(var image:validated) {
                String key="claims/office/"+UUID.randomUUID()+"."+image.extension(); keys.add(key);
                storage.put(key,image.bytes(),image.mime());
            }
            return new TransactionTemplate(transactions).execute(tx->{
                Post post=validateSubmission(request,counter,user,true);
                Account actor=counter?access.require(user,MANAGER):access.require(user);
                String contact=clean(request.claimantEmail());
                Account claimant=counter?counterClaimant(request):actor;
                if(counter) contact=claimant.getEmail();
                if(claimant!=null && claims.existsByPost_IdAndClaimant_IdAndStatusIn(post.getId(),claimant.getId(),List.of("PENDING","NEEDS_INFO","APPROVED")))
                    throw new AppException(ErrorCode.CLAIM_DUPLICATE);
                String name=person(claimant);
                if(name==null) throw bad("Nhập họ tên người yêu cầu nhận đồ.");
                if(contact==null&&claimant!=null) contact=claimant.getEmail();
                Claim c=Claim.office(post,claimant,actor,counter?"COUNTER":"ONLINE",name,
                    contact==null?null:contact.toLowerCase(Locale.ROOT),clean(request.claimantPhone()),request.description(),request.evidenceNote());
                for(int i=0;i<validated.size();i++) {
                    var image=validated.get(i); String filename=files.get(i).getOriginalFilename();
                    if(filename!=null) { filename=filename.replace('\\','/'); filename=filename.substring(filename.lastIndexOf('/')+1); if(filename.length()>255) filename=filename.substring(filename.length()-255); }
                    c.addEvidence(new ClaimEvidence(keys.get(i),filename,image.mime(),image.bytes().length,image.hash(),i));
                }
                claims.saveAndFlush(c); activities.save(new ClaimActivity(c,actor,"SUBMIT",null,null)); return c.getId();
            });
        } catch(RuntimeException failure) {
            for(String key:keys) try { storage.delete(key); } catch(RuntimeException ignored) { /* Preserve original failure. */ }
            throw failure;
        }
    }
    private Post validateSubmission(Submission r,boolean counter,CustomUserDetails user,boolean lock) {
        Account actor=counter?access.require(user,MANAGER):access.require(user);
        if(counter) counterClaimant(r);
        Post p=(lock?posts.findForClaimUpdate(r.postId()):posts.findById(r.postId())).orElseThrow(()->bad("Không tìm thấy bài đăng."));
        if(lock) em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        eligible(p);
        if(!"OPEN".equals(p.getStatus())||!"VISIBLE".equals(p.getModerationStatus())) throw bad("Bài đăng hiện không nhận thêm yêu cầu.");
        if(!counter&&p.getAccount().getId().equals(actor.getId())) throw new AppException(ErrorCode.CLAIM_SELF_NOT_ALLOWED);
        return p;
    }
    private void eligible(Post p) {
        if(p.getDeletedAt()!=null||!"FOUND".equals(p.getPostType())||!"OFFICE".equals(p.getCustodyMode())
            ||!Set.of("OPEN","RESERVED").contains(p.getStatus())) throw bad("Đồ vật không còn thuộc luồng xử lý tại văn phòng.");
        ItemReceipt r=receipts.findByPostId(p.getId()).orElseThrow(()->bad("Chưa có hồ sơ tiếp nhận."));
        if(!"IN_STORAGE".equals(r.getStatus())||!"ACCEPTED".equals(r.getReviewStatus())||r.getReceivedAt()==null)
            throw bad("Văn phòng chưa tiếp nhận hoặc đã trao trả đồ vật.");
    }
    private record Committed(Long id,String status,boolean sendInvitation) {}
    private record EmailCheck(@NotBlank @Email String email) {}
    public ReviewResult review(long id,Decision request,CustomUserDetails user) {
        Committed committed=new TransactionTemplate(transactions).execute(tx->{
            Account actor=access.require(user,MANAGER); Claim c=locked(id); eligible(c.getPost()); version(c,request.expectedVersion());
            if(!Set.of("PENDING","NEEDS_INFO").contains(c.getStatus())) throw bad("Hồ sơ này đã được xử lý. Hãy tải lại.");
            if(!"PENDING".equals(c.getStatus())&&!"REJECTED".equals(request.decision())) throw bad("Chờ bổ sung thông tin trước khi duyệt.");
            String previous=c.getStatus(); Appointment a=null;
            if("APPROVED".equals(request.decision())) {
                if(claims.existsByPost_IdAndStatusIn(c.getPost().getId(),List.of("APPROVED"))) throw bad("Đồ vật đã có yêu cầu được duyệt.");
                if(request.scheduledAt()==null||!request.scheduledAt().isAfter(OffsetDateTime.now().plusHours(12).plusMinutes(1))) throw bad("Chọn giờ hẹn sau ít nhất 12 giờ và 1 phút để dành đủ thời gian phản hồi.");
                if(clean(request.location())==null) throw bad("Nhập địa điểm nhận đồ.");
                String recipient=clean(request.email()); if(recipient==null) recipient=recipient(c);
                if(!validator.validate(new EmailCheck(recipient)).isEmpty()) throw bad("Cần email hợp lệ của người nhận để gửi lịch hẹn.");
                c.contactEmail(recipient.toLowerCase(Locale.ROOT));
                a=appointments.save(Appointment.schedule(c,actor,request.scheduledAt(),request.location().strip(),clean(request.appointmentNote())));
                c.getPost().reserveForClaim();
            }
            c.review(request.decision());
            String action=switch(request.decision()) { case "APPROVED"->"APPROVE"; case "REJECTED"->"REJECT"; default->"REQUEST_INFO"; };
            activities.save(new ClaimActivity(c,actor,action,previous,request.reason())); claims.flush();
            return new Committed(c.getId(),c.getStatus(),a!=null);
        });
        String delivery="NOT_REQUIRED";
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
            access.require(user,MANAGER); Claim c=locked(id); eligible(c.getPost());
            if(!"APPROVED".equals(c.getStatus())) throw bad("Yêu cầu chưa được duyệt.");
            Appointment a=appointments.findFirstByClaim_IdAndStatusOrderByIdDesc(id,"SCHEDULED").orElseThrow(()->bad("Chưa có lịch hẹn."));
            if(!a.getScheduledAt().isAfter(OffsetDateTime.now())) throw bad("Lịch hẹn đã qua, không gửi lại email cũ.");
        });
        return new ReviewResult(id,"APPROVED",invitations.send(id)?"SENT":"FAILED");
    }
    @Transactional
    public void supplement(long id,Supplement request,CustomUserDetails user) {
        Account actor=access.require(user); Claim c=locked(id); participant(c,actor); version(c,request.expectedVersion()); eligible(c.getPost());
        if(!"NEEDS_INFO".equals(c.getStatus())) throw bad("Hồ sơ không ở bước chờ bổ sung.");
        c.review("PENDING"); activities.save(new ClaimActivity(c,actor,"RESUBMIT","NEEDS_INFO",request.information()));
    }
    private String recipient(Claim c) { return c.getClaimantEmail()!=null?c.getClaimantEmail():c.getClaimant()==null?null:c.getClaimant().getEmail(); }
    private Claim office(long id) { return claims.findById(id).filter(c->"OFFICE".equals(c.getClaimType())).orElseThrow(()->new AppException(ErrorCode.CLAIM_NOT_FOUND)); }
    private Claim locked(long id) {
        Long postId=claims.findPostId(id).orElseThrow(()->new AppException(ErrorCode.CLAIM_NOT_FOUND));
        Post p=posts.findForClaimUpdate(postId).orElseThrow(); em.refresh(p,LockModeType.PESSIMISTIC_WRITE);
        Claim c=claims.findForUpdate(id).orElseThrow(); em.refresh(c,LockModeType.PESSIMISTIC_WRITE);
        if(!"OFFICE".equals(c.getClaimType())) throw new AppException(ErrorCode.CLAIM_NOT_FOUND); return c;
    }
    private void participant(Claim c,Account actor) { if(actor.getRole()!=MANAGER&&(c.getClaimant()==null||!c.getClaimant().getId().equals(actor.getId()))) throw new AppException(ErrorCode.FORBIDDEN); }
    private void version(Claim c,Long v) { if(v==null||v!=c.getVersion()) throw new AppException(ErrorCode.CLAIM_STALE); }
    private ClaimView view(Claim c,boolean detail,boolean manager) {
        var appointment=detail?appointments.findFirstByClaim_IdOrderByIdDesc(c.getId()).map(a->new AppointmentView(a.getId(),a.getScheduledAt(),a.getLocation(),a.getNote(),a.getStatus(),a.getResponseStatus(),a.getResponseDeadline(),a.getCancellationReason())).orElse(null):null;
        return new ClaimView(c.getId(),c.getPost().getId(),c.getPost().getTitle(),c.getStatus(),c.getSubmissionChannel(),
            c.getClaimant()==null?null:c.getClaimant().getId(),c.getClaimantName(),detail?recipient(c):null,detail?c.getClaimantPhone():null,
            person(c.getCreatedBy()),detail?c.getDescription():null,detail?c.getEvidenceNote():null,detail&&manager?c.getPost().getPrivateIdentifiers():null,c.getVersion(),c.getCreatedAt(),
            detail&&manager?receipts.findByPostId(c.getPost().getId()).map(management::receiptView).orElse(null):null,
            detail?c.getEvidence().stream().map(e->new ClaimDetailDto.EvidenceDto(e.getId(),storage.readUrl(e.getStorageKey(),null),e.getOriginalName(),e.getMimeType(),e.getSizeBytes())).toList():List.of(),
            detail?activities.findByClaim_IdOrderByCreatedAtAscIdAsc(c.getId()).stream().map(e->new ClaimDetailDto.ActivityDto(e.getId(),e.getAction(),person(e.getActor()),e.getFromStatus(),e.getToStatus(),e.getReason(),e.getCreatedAt())).toList():List.of(),appointment);
    }
}

