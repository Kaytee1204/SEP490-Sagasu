export const CLAIM_STATUS = {
  PENDING: 'Chờ xét duyệt',
  APPROVED: 'Đã chấp thuận',
  REJECTED: 'Bị từ chối',
  CANCELLED: 'Đã hủy',
  COMPLETED: 'Đã nhận đồ',
  CLOSED: 'Đã đóng',
};

export const P2P_CLAIM_STEPS = ['Gửi yêu cầu', 'Xét duyệt', 'Ghi nhận giao đồ', 'Xác nhận nhận đồ'];

export const HISTORY_STATUSES = ['COMPLETED', 'REJECTED', 'CANCELLED', 'CLOSED'];
export const CLAIM_LIST_STATUSES = ['PENDING', 'APPROVED', 'HISTORY'];

export function claimListStatus(value) {
  if (HISTORY_STATUSES.includes(value)) return 'HISTORY';
  return CLAIM_LIST_STATUSES.includes(value) ? value : 'PENDING';
}

export const rejectedAfterDispute = claim => claim.disputeOutcome === 'APPEAL_REJECT_CLAIM';
export const disputeDismissed = claim =>
  ['PENDING', 'REJECTED'].includes(claim.status) && claim.disputeOutcome === 'APPEAL_KEEP';
export const claimDisplayStatus = claim =>
  rejectedAfterDispute(claim) ? 'REJECTED' : disputeDismissed(claim) ? 'CLOSED' : claim.status;

export function matchesClaimStatus(claim, status) {
  const displayStatus = claimDisplayStatus(claim);
  if (status === 'HISTORY') return HISTORY_STATUSES.includes(displayStatus);
  return displayStatus === status;
}

export const CLAIM_ACTION = {
  SUBMIT: 'Gửi claim',
  APPROVE: 'Chấp thuận',
  REVOKE_APPROVAL: 'Thu hồi chấp thuận',
  REJECT: 'Từ chối',
  CANCEL: 'Hủy claim',
  HANDOVER: 'Người nhặt ghi nhận đã giao',
  CONFIRM_RECEIPT: 'Người nhận xác nhận đã nhận đồ',
  AUTO_CLOSE: 'Hệ thống đóng do bài đã trao trả',
  REPORT_ISSUE: 'Báo sự cố bàn giao',
};

export const TIME_RANGE_OPTIONS = [
  { value: 'ALL', label: 'Tất cả thời gian' },
  { value: 'TODAY', label: 'Hôm nay' },
  { value: '7D', label: '7 ngày qua' },
  { value: '30D', label: '30 ngày qua' },
  { value: 'CUSTOM', label: 'Chọn khoảng ngày' },
];

export const claimDate = value => new Date(value).toLocaleString('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export const claimShortDate = value => new Date(value).toLocaleDateString('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

export const claimShortTime = value => new Date(value).toLocaleTimeString('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  hour: '2-digit',
  minute: '2-digit',
});

function vnDateKey(value) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(value));
  const map = Object.fromEntries(parts.map(p => [p.type, p.value]));
  return `${map.year}-${map.month}-${map.day}`;
}

export function matchesTimeFilter(isoDate, timeRange = 'ALL', fromDate = '', toDate = '') {
  if (!timeRange || timeRange === 'ALL') return true;
  if (!isoDate) return false;
  const targetMs = Date.parse(isoDate);
  if (Number.isNaN(targetMs)) return false;

  const now = Date.now();
  if (timeRange === 'TODAY') {
    return vnDateKey(targetMs) === vnDateKey(now);
  }
  if (timeRange === '7D') {
    return targetMs >= now - 7 * 24 * 60 * 60 * 1000;
  }
  if (timeRange === '30D') {
    return targetMs >= now - 30 * 24 * 60 * 60 * 1000;
  }
  if (timeRange === 'CUSTOM') {
    const key = vnDateKey(targetMs);
    if (fromDate && key < fromDate) return false;
    if (toDate && key > toDate) return false;
    return true;
  }
  return true;
}

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
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

export function postClaimsPath(postId, status, view = 'received') {
  const listStatus = claimListStatus(status);
  const params = new URLSearchParams();
  if (view === 'sent') {
    params.set('view', 'sent');
    params.set('postId', String(postId));
  }
  if (listStatus !== 'PENDING' || status === 'PENDING') params.set('claimStatus', listStatus);
  const base = view === 'sent' ? '/claim-cua-toi' : `/bai-dang/${encodeURIComponent(postId)}/claims`;
  return params.size ? `${base}?${params}` : base;
}
