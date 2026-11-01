import { BadgeCheck } from 'lucide-react';

export default function OfficeReceiptBadge({ receipt }) {
  if (!receipt) return null;
  return <span className="inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-semibold leading-5 text-blue-800" title="Tài sản đã được cán bộ nhà trường xác nhận tiếp nhận thực tế">
    <BadgeCheck size={16} className="shrink-0 text-blue-600" aria-hidden="true" />Nhà trường đã tiếp nhận
  </span>;
}
