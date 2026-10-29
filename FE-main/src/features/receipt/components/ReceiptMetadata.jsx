import { receiptDate } from '@/features/checker/receiptUtils';

export default function ReceiptMetadata({ receipt }) {
  const fields = [
    ['Danh mục', receipt.categoryName],
    ['Người lập', receipt.recordedBy || 'Chưa ghi nhận'],
    ['Nguồn tiếp nhận', receipt.source === 'CHECKER' ? 'Nhặt khi tuần tra' : 'Mang đến trực tiếp'],
    [receipt.submittedAt ? 'Gửi lúc' : 'Lập lúc', receiptDate(receipt.submittedAt || receipt.recordedAt)],
  ];
  return <dl className="mt-5 grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-2">
    {fields.map(([label, value]) => <div key={label} className="min-w-0">
      <dt className="text-sm font-medium text-slate-600">{label}</dt>
      <dd className="mt-1 break-words text-base font-semibold leading-6 text-slate-900">{value}</dd>
    </div>)}
  </dl>;
}
