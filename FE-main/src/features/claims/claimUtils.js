export const CLAIM_STATUS = { PENDING: 'Chờ xét duyệt', APPROVED: 'Đã chấp thuận', REJECTED: 'Bị từ chối', CANCELLED: 'Đã hủy', COMPLETED:'Đã nhận đồ',CLOSED:'Đã đóng' };
export const CLAIM_ACTION = { SUBMIT: 'Gửi claim', APPROVE: 'Chấp thuận', REJECT: 'Từ chối', CANCEL: 'Hủy claim',HANDOVER:'Người nhặt ghi nhận đã giao',CONFIRM_RECEIPT:'Người nhận xác nhận đã nhận đồ',AUTO_CLOSE:'Hệ thống đóng do bài đã trao trả',REPORT_ISSUE:'Báo sự cố bàn giao' };
export const claimDate = value => new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
export const validClaimId = value => /^[1-9]\d*$/.test(String(value || ''));
export const claimDetailPath = (postId, claimId) => `/bai-dang/${encodeURIComponent(postId)}/claims/${encodeURIComponent(claimId)}`;
export function postClaimType(post) {
  if (post.postType !== 'FOUND') return null;
  if (post.custodyMode === 'P2P' && !post.officeReceipt) return 'P2P';
  if (post.custodyMode === 'OFFICE' && post.officeReceipt?.status === 'IN_STORAGE'
      && post.officeReceipt.receivedAt) return 'OFFICE';
  return null;
}
export const canSubmitPostClaim = post => Boolean(postClaimType(post))
  && post.status === 'OPEN' && post.moderationStatus === 'VISIBLE' && !post.isOwner && !post.canEditOwnPost;
export function evidenceUrl(value) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : null; }
  catch { return null; }
}
