import ReceiptMetadata from '@/features/receipt/components/ReceiptMetadata';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, RefreshCw, MapPin, CheckCircle2 } from 'lucide-react';
import { useReceipt } from '@/features/checker/hooks/useCheckerReceipts';
import { receiptDate } from '@/features/checker/receiptUtils';
import ReceiptStatusBadge from '@/features/checker/components/ReceiptStatusBadge';
import { ReceiptError, ReceiptLoading } from '@/features/checker/components/ReceiptFeedback';
import ReceiptEvidence, { ReceiptText } from '@/features/receipt/components/ReceiptEvidence';
import { approverApi } from '../api/approverApi';
import ReviewDecisionPanel from '../components/ReviewDecisionPanel';

export default function ReviewReceiptPage() {
  const { id } = useParams();
  const loaded = useReceipt(id, false, approverApi);
  const [notice, setNotice] = useState('');
  if (loaded.loading) return <ReceiptLoading />;
  if (loaded.error) return <div className="mx-auto max-w-3xl space-y-4 p-6"><Link to="/approver/receipts" className="text-base text-teal-700">← Hàng đợi tiếp nhận</Link><ReceiptError message={loaded.error} onRetry={loaded.reload} /></div>;
  const receipt = loaded.receipt;
  const reviewable = receipt.source === 'CHECKER' && receipt.status === 'SUBMITTED' && receipt.reviewStatus === 'PENDING';
  return <div className="mx-auto w-full max-w-6xl space-y-6 p-4 sm:p-6 lg:p-8">
    <Link to="/approver/receipts" className="inline-flex items-center gap-2 text-base font-medium text-slate-600 hover:text-teal-700"><ArrowLeft size={16} />Hàng đợi tiếp nhận</Link>
    {notice && <div role="status" className="flex gap-3 rounded-xl border border-teal-200 bg-teal-50 p-4 text-base text-teal-800"><CheckCircle2 size={19} className="shrink-0" />{notice}</div>}
    <div className="flex items-start justify-between gap-4"><div className="min-w-0"><div className="mb-3 flex flex-wrap items-center gap-3"><p className="text-sm font-bold tracking-wide text-teal-700">Hồ sơ tiếp nhận · #{receipt.id}</p><ReceiptStatusBadge receipt={receipt} /></div><h1 className="break-words text-2xl font-bold text-slate-900 sm:text-3xl">{receipt.title}</h1><ReceiptMetadata receipt={receipt} /></div><button type="button" onClick={() => { setNotice(''); loaded.reload(); }} aria-label="Tải lại phiếu" className="rounded-xl border border-slate-200 bg-white p-3 text-slate-600"><RefreshCw size={17} /></button></div>
    {receipt.source === 'WALK_IN' && <p className="rounded-xl border border-sky-200 bg-sky-50 p-4 text-base leading-6 text-sky-800">Đây là phiếu mang đến trực tiếp. Các quyết định xét duyệt trên màn hình này chỉ áp dụng cho hồ sơ do nhân viên tuần tra gửi.</p>}
    {receipt.reviewStatus === 'NEEDS_INFO' && <p className="rounded-xl border border-orange-200 bg-orange-50 p-4 text-base leading-6 text-orange-900">Đang chờ nhân viên tuần tra bổ sung và gửi lại. Phiếu sẽ trở về hàng đợi chờ xử lý sau khi được gửi lại.</p>}
    <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
      <ReceiptEvidence receipt={receipt} />
      <aside className="min-w-0 space-y-5">
        <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 className="flex items-center gap-2 font-semibold text-slate-800"><MapPin size={18} className="text-teal-600" />Vị trí lưu giữ</h2><p className={`text-base leading-6 ${receipt.storageLocationName ? 'text-slate-700' : 'text-amber-700'}`}>{receipt.storageLocationName || 'Chưa cập nhật vị trí'}</p>{receipt.storageLocationId && !receipt.storageLocationActive && <p className="text-sm text-amber-700">Vị trí này đã ngừng sử dụng.</p>}{!receipt.storageLocationId && <p className="text-sm leading-5 text-slate-600">Có thể yêu cầu nhân viên tuần tra bổ sung vị trí nếu cần đối chiếu đồ thực tế.</p>}</section>
        {receipt.reviewedAt && <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"><h2 className="font-semibold text-slate-800">{receipt.reviewStatus === 'PENDING' ? 'Phản hồi lần trước' : 'Kết quả xét duyệt gần nhất'}</h2><p className="text-sm text-slate-600">{receipt.reviewedBy} · {receiptDate(receipt.reviewedAt)}</p><ReceiptText title="Nội dung phản hồi">{receipt.reviewNote}</ReceiptText></section>}
        {reviewable && <ReviewDecisionPanel key={receipt.updatedAt} receipt={receipt} onReviewed={() => { setNotice('Đã lưu quyết định và cập nhật kết quả cho nhân viên tuần tra.'); loaded.reload(); }} />}
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5"><p className="mb-2 text-sm font-semibold text-slate-600">Mã hồ sơ</p><p className="break-all font-mono text-sm leading-5 text-slate-600">{receipt.receiptCode}</p><p className="mt-4 text-sm leading-5 text-slate-600">Cập nhật lần cuối<br />{receiptDate(receipt.updatedAt)}</p></div>
      </aside>
    </div>
  </div>;
}
