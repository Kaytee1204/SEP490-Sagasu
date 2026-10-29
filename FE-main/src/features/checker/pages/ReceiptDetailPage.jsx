import ReceiptMetadata from '@/features/receipt/components/ReceiptMetadata';
import { useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Pencil, Send, RefreshCw, FileText } from 'lucide-react';
import { checkerApi } from '../api/checkerApi';
import { useReceipt } from '../hooks/useCheckerReceipts';
import { canEditReceipt, receiptDate } from '../receiptUtils';
import ReceiptStatusBadge from '../components/ReceiptStatusBadge';
import ReceiptEvidence from '@/features/receipt/components/ReceiptEvidence';
import { ReceiptError, ReceiptLoading } from '../components/ReceiptFeedback';
import ReceiptConfirmDialog from '../components/ReceiptConfirmDialog';
import ReceiptLocationCard from '../components/ReceiptLocationCard';

export default function ReceiptDetailPage() {
  const { id } = useParams();
  const loaded = useReceipt(id);
  const location = useLocation();
  const navigate = useNavigate();
  const [notice, setNotice] = useState(location.state?.receiptNotice || '');
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(false);
  const submit = async () => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try {
      await checkerApi.submit(id, loaded.receipt.updatedAt);
      setConfirm(false); setNotice('Đã gửi phiếu. Bạn có thể theo dõi kết quả xét duyệt tại đây.');
      navigate(location.pathname, { replace: true, state: null });
      loaded.reload();
    } catch (failure) { setError(failure.message); }
    finally { pending.current = false; setBusy(false); }
  };

  if (loaded.loading) return <ReceiptLoading />;
  if (loaded.error) return <div className="mx-auto max-w-3xl space-y-4 p-6"><Link to="/checker/receipts" className="text-base text-teal-700">← Phiếu của tôi</Link><ReceiptError message={loaded.error} onRetry={loaded.reload} /></div>;
  const receipt = loaded.receipt;
  const draft = canEditReceipt(receipt);
  return <div className="mx-auto w-full max-w-6xl space-y-6 p-4 sm:p-6 lg:p-8">
    <Link to="/checker/receipts" className="inline-flex items-center gap-2 text-base font-medium text-slate-600 hover:text-teal-700"><ArrowLeft size={16} />Phiếu của tôi</Link>
    {notice && <div role="status" className="flex items-start gap-3 rounded-xl border border-teal-200 bg-teal-50 p-4 text-base text-teal-800"><CheckCircle2 size={19} className="shrink-0" /><p className="flex-1">{notice}</p><button type="button" onClick={() => { setNotice(''); navigate(location.pathname, { replace: true, state: null }); }} aria-label="Đóng thông báo">×</button></div>}
    <div className="flex flex-wrap items-start justify-between gap-4"><div className="min-w-0 flex-1"><div className="mb-3 flex flex-wrap items-center gap-3"><p className="text-sm font-bold tracking-wide text-teal-700">Hồ sơ tiếp nhận · #{receipt.id}</p><ReceiptStatusBadge receipt={receipt} /></div><h1 className="break-words text-2xl font-bold text-slate-900 sm:text-3xl">{receipt.title}</h1><ReceiptMetadata receipt={receipt} /></div><div className="flex flex-wrap gap-2"><button type="button" onClick={() => { setNotice(''); loaded.reload(); }} aria-label="Tải lại phiếu" className="rounded-xl border border-slate-200 bg-white p-3 text-slate-600 hover:text-teal-700"><RefreshCw size={17} /></button>{draft && <><Link to={`/checker/receipts/${id}/edit`} className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-base font-semibold text-slate-600 hover:border-teal-300"><Pencil size={16} />Chỉnh sửa</Link><button type="button" onClick={() => { setError(''); setConfirm(true); }} className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-base font-semibold text-white hover:bg-teal-800"><Send size={16} />{receipt.reviewStatus === 'NEEDS_INFO' ? 'Gửi duyệt lại' : 'Gửi duyệt'}</button></>}</div></div>
    {receipt.reviewStatus === 'NEEDS_INFO' ? <div className="rounded-xl border border-orange-200 bg-orange-50 p-4 text-base leading-6 text-orange-900"><p className="font-semibold">Người xét duyệt yêu cầu bổ sung</p><p className="mt-2 whitespace-pre-wrap break-words">{receipt.reviewNote}</p><p className="mt-2 text-sm">Chỉnh sửa hồ sơ, lưu thay đổi rồi gửi duyệt lại để tiếp tục xử lý.</p></div> : draft && <div className="flex gap-3 rounded-xl border border-slate-200 bg-slate-100/60 p-4 text-base leading-6 text-slate-600"><FileText size={19} className="mt-0.5 shrink-0 text-teal-600" /><p>Phiếu đang là <strong>bản nháp</strong>. Kiểm tra thông tin và ảnh trước khi gửi cho người xét duyệt.</p></div>}
    <ReceiptLocationCard key={receipt.updatedAt} receipt={receipt} onSaved={() => { setNotice('Đã cập nhật vị trí lưu giữ.'); loaded.reload(); }} />
    <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
      <ReceiptEvidence receipt={receipt} />
      <aside className="space-y-5"><section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 className="mb-5 font-semibold text-slate-800">Tiến trình hồ sơ</h2><ol className="space-y-6 border-l border-slate-200 pl-5"><li className="relative"><span className="absolute -left-[25px] top-1 h-2 w-2 rounded-full bg-teal-600 ring-4 ring-white" /><p className="text-base font-semibold text-slate-700">Đã lập phiếu</p><p className="mt-1 text-sm text-slate-600">{receiptDate(receipt.recordedAt)}</p></li><li className="relative"><span className={`absolute -left-[25px] top-1 h-2 w-2 rounded-full ring-4 ring-white ${receipt.submittedAt ? 'bg-teal-600' : 'bg-slate-200'}`} /><p className={`text-base font-semibold ${receipt.submittedAt ? 'text-slate-700' : 'text-slate-600'}`}>{receipt.submittedAt ? 'Lần gửi xét duyệt gần nhất' : 'Chưa gửi xét duyệt'}</p>{receipt.submittedAt && <p className="mt-1 text-sm text-slate-600">{receiptDate(receipt.submittedAt)}</p>}</li><li className="relative"><span className={`absolute -left-[25px] top-1 h-2 w-2 rounded-full ring-4 ring-white ${receipt.reviewedAt ? 'bg-teal-600' : 'bg-slate-200'}`} /><p className={`text-base font-semibold ${receipt.reviewedAt ? 'text-slate-700' : 'text-slate-600'}`}>{receipt.reviewedAt ? 'Phản hồi xét duyệt gần nhất' : 'Kết quả xét duyệt'}</p>{receipt.reviewedAt && <p className="mt-1 text-sm text-slate-600">{receiptDate(receipt.reviewedAt)}</p>}</li></ol>
          {(receipt.reviewNote || receipt.reviewedBy || receipt.reviewStatus !== 'PENDING') && <div className="mt-5 space-y-2 rounded-xl bg-slate-50 p-4"><ReceiptStatusBadge receipt={receipt} />{receipt.reviewedBy && <p className="text-sm text-slate-600">Người xét duyệt: {receipt.reviewedBy}</p>}{receipt.reviewNote && <p className="whitespace-pre-wrap break-words text-base leading-6 text-slate-700">{receipt.reviewNote}</p>}</div>}
        </section><div className="rounded-2xl border border-slate-200 bg-slate-50 p-5"><p className="mb-2 text-sm font-semibold text-slate-600">Mã hồ sơ</p><p className="break-all font-mono text-sm leading-5 text-slate-600">{receipt.receiptCode}</p><p className="mt-4 text-sm leading-5 text-slate-600">Cập nhật lần cuối<br />{receiptDate(receipt.updatedAt)}</p></div></aside>
    </div>
    {confirm && <ReceiptConfirmDialog busy={busy} error={error} onClose={() => setConfirm(false)} onConfirm={submit} />}
  </div>;
}
