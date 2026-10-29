export const RECEIPT_FILTERS = [
  ['ALL', 'Tất cả'], ['DRAFT', 'Bản nháp'], ['SUBMITTED', 'Chờ duyệt'],
  ['NEEDS_INFO', 'Cần bổ sung'], ['ACCEPTED', 'Đã duyệt'], ['REJECTED', 'Từ chối'],
];

export function receiptState(receipt) {
  if (receipt.status === 'DRAFT') return { label: 'Bản nháp', tone: 'bg-slate-100 text-slate-600 ring-slate-200', dot: 'bg-slate-400' };
  if (receipt.reviewStatus === 'REJECTED' || receipt.status === 'REJECTED') return { label: 'Từ chối', tone: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500' };
  if (receipt.reviewStatus === 'NEEDS_INFO') return { label: 'Yêu cầu bổ sung', tone: 'bg-orange-50 text-orange-800 ring-orange-200', dot: 'bg-orange-500' };
  if (receipt.status === 'RETURNED') return { label: 'Đã trả đồ', tone: 'bg-sky-50 text-sky-700 ring-sky-200', dot: 'bg-sky-500' };
  if (receipt.status === 'IN_STORAGE') return { label: 'Đã vào kho', tone: 'bg-teal-50 text-teal-700 ring-teal-200', dot: 'bg-teal-500' };
  if (receipt.reviewStatus === 'ACCEPTED') return { label: 'Đã duyệt', tone: 'bg-teal-50 text-teal-700 ring-teal-200', dot: 'bg-teal-500' };
  return { label: 'Chờ duyệt', tone: 'bg-amber-50 text-amber-800 ring-amber-200', dot: 'bg-amber-500' };
}

export const canEditReceipt = (receipt) => (receipt.status === 'DRAFT' && receipt.reviewStatus === 'PENDING') || (receipt.status === 'SUBMITTED' && receipt.reviewStatus === 'NEEDS_INFO');
export const receiptDate = (value) => value ? new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
}).format(new Date(value)) : '—';

// datetime-local is explicitly shown as Vietnam time, independent of browser timezone.
export function vietnamInput(value) {
  if (!value) return '';
  return new Date(new Date(value).getTime() + 7 * 3600000).toISOString().slice(0, 16);
}
