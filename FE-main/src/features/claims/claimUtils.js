export const CLAIM_STATUS = { PENDING: 'Chờ xét duyệt', APPROVED: 'Đã chấp thuận', REJECTED: 'Bị từ chối', CANCELLED: 'Đã hủy' };
export const CLAIM_ACTION = { SUBMIT: 'Gửi claim', APPROVE: 'Chấp thuận', REJECT: 'Từ chối', CANCEL: 'Hủy claim' };
export const claimDate = value => new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
export const validClaimId = value => /^[1-9]\d*$/.test(String(value || ''));
export const claimDetailPath = (postId, claimId) => `/bai-dang/${encodeURIComponent(postId)}/claims/${encodeURIComponent(claimId)}`;
export function evidenceUrl(value) {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : null; }
  catch { return null; }
}
