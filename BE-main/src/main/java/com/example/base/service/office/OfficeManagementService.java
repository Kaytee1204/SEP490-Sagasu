package com.example.base.service.office;

import com.example.base.dto.office.OfficeDtos.*;
import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import com.example.base.storage.ImageStorage;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.example.base.entity.AccountRole.*;
import static com.example.base.service.office.OfficeAccess.bad;

@Service @RequiredArgsConstructor
public class OfficeManagementService {
    private final OfficeAccess access;
    private final PostRepository posts;
    private final ItemReceiptRepository receipts;
    private final StorageLocationRepository locations;
    private final ImageStorage storage;

    public static PageRequest page(int page) {
        if(page<1) throw bad("Trang không hợp lệ.");
        return PageRequest.of(page-1,12,Sort.by(Sort.Direction.DESC,"id"));
    }
    public static <T> PageData<T> result(Page<?> page, List<T> content) {
        return new PageData<>(content,page.getNumber()+1,page.getTotalElements(),page.getTotalPages());
    }
    public static String clean(String text) { return text==null || text.isBlank()?null:text.strip(); }
    public static String person(Account account) { return account==null?"—":Objects.requireNonNullElse(clean(account.getFullName()),account.getEmail()); }
    private String query(String q) {
        String value=q==null?"":q.strip().toLowerCase(Locale.ROOT);
        if(value.length()>200) throw bad("Từ khóa tối đa 200 ký tự.");
        return value;
    }
    @Transactional(readOnly=true)
    public PageData<PostView> posts(int page, String q, String type, String status, String moderation, String custody, Long category, CustomUserDetails user) {
        access.require(user,MANAGER); String keyword=query(q);
        var data=posts.findAll((root,cq,cb)-> {
            List<Predicate> p=new ArrayList<>();
            p.add(cb.isNull(root.get("deletedAt")));
            p.add(cb.or(cb.gt(cb.locate(cb.lower(root.get("title")),keyword),0),cb.gt(cb.locate(cb.lower(root.get("description")),keyword),0)));
            if(!"ALL".equals(type)) p.add(cb.equal(root.get("postType"),type));
            if(!"ALL".equals(status)) p.add(cb.equal(root.get("status"),status));
            if(!"ALL".equals(moderation)) p.add(cb.equal(root.get("moderationStatus"),moderation));
            if(!"ALL".equals(custody)) p.add(cb.equal(root.get("custodyMode"),custody));
            if(category!=null) p.add(cb.equal(root.get("category").get("categoryId"),category));
            return cb.and(p.toArray(Predicate[]::new));
        },page(page));
        return result(data,data.stream().map(p->postView(p,false)).toList());
    }
    @Transactional(readOnly=true)
    public PostView detail(long id, CustomUserDetails user) {
        access.require(user,MANAGER); return postView(post(id),true);
    }
    @Transactional
    public PostView moderate(long id, Moderation request, CustomUserDetails user) {
        Account actor=access.require(user,MANAGER);
        Post p=posts.findForClaimUpdate(id).orElseThrow(()->bad("Không tìm thấy bài đăng."));
        if(p.getDeletedAt()!=null) throw bad("Bài đăng đã bị xóa.");
        if(!p.getUpdatedAt().toInstant().equals(request.expectedUpdatedAt().toInstant()))
            throw new AppException(ErrorCode.CLAIM_STALE,"Bài đăng đã thay đổi. Tải lại trước khi thao tác.");
        if(!Set.of("VISIBLE","HIDDEN").contains(p.getModerationStatus()) || p.getModerationStatus().equals(request.status()))
            throw bad("Chỉ ẩn bài đang hiển thị hoặc gỡ ẩn bài đang bị ẩn.");
        p.moderate(request.status(),request.reason().strip(),actor); posts.flush();
        return postView(p,true);
    }
    private Post post(long id) {
        return posts.findById(id).filter(p->p.getDeletedAt()==null).orElseThrow(()->bad("Không tìm thấy bài đăng."));
    }
    private PostView postView(Post p, boolean detail) {
        return new PostView(p.getId(),p.getTitle(),detail?p.getDescription():null,p.getPostType(),p.getStatus(),
            p.getModerationStatus(),p.getCustodyMode(),p.getCategory().getName(),person(p.getCreatedBy()),
            detail?p.getCreatedBy().getEmail():null,p.getLocationText(),p.getCreatedAt(),p.getUpdatedAt(),
            detail?p.getModerationNote():null,person(p.getModeratedBy()),p.getModeratedAt(),
            p.getImages().stream().limit(detail?5:1).map(i->storage.readUrl(i.getStorageKey(),i.getImageUrl())).toList(),
            detail?receipts.findByPostId(p.getId()).map(this::receiptView).orElse(null):null);
    }
    public ReceiptView receiptView(ItemReceipt r) {
        return new ReceiptView(r.getId(),r.getReceiptCode(),r.getPostId(),r.getTitle(),r.getStatus(),r.getConditionAtReceipt(),
            r.getStorageLocation()==null?null:r.getStorageLocation().getCode()+" · "+r.getStorageLocation().getName(),
            r.getStorageLocation()==null?null:r.getStorageLocation().getId(),person(r.getReceivedBy()),r.getReceivedAt(),r.getFinderName(),r.getFinderContact());
    }
    @Transactional(readOnly=true)
    public PageData<ReceiptView> stored(int page,String q,Long locationId,CustomUserDetails user) {
        access.require(user,APPROVER,MANAGER); String keyword=query(q);
        Set<Long> ids=locationId==null?Set.of():descendants(locationId,locations.findAll());
        var data=receipts.findAll((root,cq,cb)-> {
            var p=new ArrayList<Predicate>(); p.add(cb.equal(root.get("status"),"IN_STORAGE"));
            p.add(cb.equal(root.get("reviewStatus"),"ACCEPTED"));
            p.add(cb.or(cb.gt(cb.locate(cb.lower(root.get("title")),keyword),0),cb.gt(cb.locate(cb.lower(root.get("receiptCode")),keyword),0)));
            if(locationId!=null) p.add(root.get("storageLocation").get("id").in(ids));
            return cb.and(p.toArray(Predicate[]::new));
        },page(page));
        return result(data,data.stream().map(this::receiptView).toList());
    }
    @Transactional(readOnly=true)
    public List<LocationView> locations(CustomUserDetails user) {
        access.require(user,APPROVER,MANAGER);
        return locations.findAll(Sort.by("code")).stream().map(this::locationView).toList();
    }
    private LocationView locationView(StorageLocation l) { return new LocationView(l.getId(),l.getCode(),l.getName(),l.getParentId(),l.isActive()); }
    @Transactional
    public LocationView saveLocation(Long id,LocationInput request,CustomUserDetails user) {
        access.require(user,MANAGER);
        List<StorageLocation> all=locations.lockAll();
        StorageLocation current=id==null?null:all.stream().filter(l->l.getId().equals(id)).findFirst().orElseThrow(()->bad("Không tìm thấy vị trí kho."));
        String code=request.code().strip().toUpperCase(Locale.ROOT);
        if(locations.existsByCodeIgnoreCaseAndIdNot(code,id==null?-1L:id)) throw bad("Mã vị trí đã được sử dụng.");
        Set<Long> subtree=id==null?Set.of():descendants(id,all);
        if(request.parentId()!=null) {
            if(subtree.contains(request.parentId())) throw bad("Vị trí cha không được là chính nó hoặc vị trí con.");
            StorageLocation parent=all.stream().filter(l->l.getId().equals(request.parentId())).findFirst().orElseThrow(()->bad("Vị trí cha không tồn tại."));
            if(!parent.isActive()) throw bad("Vị trí cha đã ngừng sử dụng.");
        }
        if(!request.active() && id!=null) {
            if(receipts.existsByStorageLocation_IdInAndStatusIn(subtree,List.of("IN_STORAGE"))) throw bad("Vị trí hoặc vị trí con vẫn còn đồ trong kho.");
            if(all.stream().anyMatch(l->!l.getId().equals(id)&&subtree.contains(l.getId())&&l.isActive()))
                throw bad("Hãy ngừng sử dụng các vị trí con trước.");
        }
        if(current==null) current=StorageLocation.create(code,request.name().strip(),request.parentId());
        current.update(code,request.name().strip(),request.parentId(),request.active());
        return locationView(locations.saveAndFlush(current));
    }
    static Set<Long> descendants(Long id,List<StorageLocation> all) {
        Set<Long> ids=new HashSet<>(); ids.add(id);
        boolean changed; do { changed=false; for(var l:all) if(ids.contains(l.getParentId())&&ids.add(l.getId())) changed=true; } while(changed);
        return ids;
    }
}
