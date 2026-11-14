package com.example.base.service.chat;

import com.example.base.entity.*;
import com.example.base.exception.*;
import com.example.base.repository.*;
import com.example.base.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class ChatAccessPolicy {
    private final AccountRepository accounts;
    private final ItemReceiptRepository receipts;

    public Account actor(CustomUserDetails user) {
        if (user == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        Account account = accounts.findById(user.getId()).orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
        if (!active(account)) throw new AppException(ErrorCode.FORBIDDEN);
        return account;
    }
    public boolean active(Account account) {
        if (account.getRole() == null || account.getStatus() != AccountStatus.ACTIVE) return false;
        var details = new CustomUserDetails(account);
        return details.isAccountNonLocked() && details.isAccountNonExpired();
    }
    public boolean eligible(Claim claim) {
        Post post = claim.getPost();
        return !claim.isOffice() && "APPROVED".equals(claim.getStatus()) && "FOUND".equals(post.getPostType())
                && "P2P".equals(post.getCustodyMode()) && "VISIBLE".equals(post.getModerationStatus())
                && "RESERVED".equals(post.getStatus()) && post.getDeletedAt() == null
                && !Objects.equals(post.getAccount().getId(), claim.getClaimant().getId())
                && !receipts.existsByPostId(post.getId());
    }
    public String readOnlyReason(ChatConversation conversation) {
        Claim claim = conversation.getLatestClaim();
        if (!Objects.equals(claim.getPost().getId(), conversation.getPost().getId())
                || !Objects.equals(claim.getClaimant().getId(), conversation.getClaimant().getId())
                || !eligible(claim)) return "Yêu cầu nhận đồ hoặc bài đăng không còn cho phép gửi tin. Bạn vẫn có thể xem lịch sử.";
        if (!active(conversation.getClaimant()) || !active(conversation.getPost().getAccount()))
            return "Một tài khoản hiện không thể tham gia trò chuyện. Lịch sử vẫn được giữ.";
        return null;
    }
}
