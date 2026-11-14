import { BadgeCheck } from 'lucide-react';

export default function OfficeReceiptBadge({ receipt, compact = false }) {
  if (!receipt) return null;
  return <span className={compact
    ? 'inline-flex size-7 items-center justify-center rounded-full border border-blue-200 bg-white/95 text-blue-700 shadow-sm'
    : 'inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-semibold leading-5 text-blue-800'}
    title={compact ? 'Văn phòng đã tiếp nhận' : 'Tài sản đã được cán bộ văn phòng xác nhận tiếp nhận thực tế'}>
    <BadgeCheck size={16} className="shrink-0 text-blue-600" aria-hidden="true" />
    <span className={compact ? 'sr-only' : undefined}>Văn phòng đã tiếp nhận</span>
  </span>;
}
