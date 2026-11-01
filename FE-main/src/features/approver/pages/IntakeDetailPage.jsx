import { useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowUpRight, BadgeCheck, Check, ClipboardCheck, LoaderCircle, MapPin, Pencil, Plus, RefreshCw, ShieldCheck } from 'lucide-react';
import { approverApi } from '../api/approverApi';
import { useReceipt } from '@/features/receipt/hooks/useIntakeReceipts';
import { receiptDate } from '@/features/receipt/receiptUtils';
import { ReceiptLoading, ReceiptError } from '@/features/receipt/components/ReceiptFeedback';
import ReceiptStatusBadge from '@/features/receipt/components/ReceiptStatusBadge';
import ReceiptEvidence from '@/features/receipt/components/ReceiptEvidence';
import IntakeConfirmDialog from '../components/IntakeConfirmDialog';

export default function IntakeDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const loaded = useReceipt(id, false, approverApi);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [published, setPublished] = useState(null);
  const pending = useRef(false);

  const submit = async () => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try {
      const result = await approverApi.submit(id, loaded.receipt.updatedAt);
      setPublished(result); setConfirm(false);
      navigate(location.pathname, { replace: true, state: null });
      loaded.reload();
    } catch (failure) { setError(failure.message || 'Chưa thể xác nhận. Vui lòng thử lại.'); }
    finally { pending.current = false; setBusy(false); }
  };

  if (loaded.loading && !published) return <ReceiptLoading />;
  if (loaded.error && !published) return <div className="mx-auto max-w-3xl space-y-4 p-6"><Link to="/approver/receipts" className="text-teal-700">← Phiếu tiếp nhận</Link><ReceiptError message={loaded.error} onRetry={loaded.reload} /></div>;
  const receipt = published || loaded.receipt;
  const draft = receipt.canProcess;
  const ready = draft && receipt.storageLocationId && receipt.storageLocationActive && receipt.categoryActive && receipt.images.some(image => !image.sensitive);
  const publicImages = receipt.images.filter(image => !image.sensitive);
  const notice = location.state?.receiptNotice;

  return <div className="mx-auto w-full max-w-6xl space-y-6 p-4 sm:p-6 lg:p-8">
    <Link to="/approver/receipts" className="inline-flex items-center gap-2 text-sm font-semibold text-slate-600 hover:text-teal-700"><ArrowLeft size={16} />Phiếu tiếp nhận</Link>
    {notice && draft && <div role="status" className="rounded-xl border border-teal-200 bg-teal-50 p-4 text-teal-900">{notice}</div>}
    {!draft && receipt.postId && <section role="status" className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-teal-200 bg-teal-50 p-5 sm:p-6">
      <div className="flex items-start gap-3"><span className="rounded-full bg-teal-700 p-2 text-white"><Check size={22} /></span><div><h2 className="font-bold text-teal-950">Đã tiếp nhận tài sản và đăng bài</h2><p className="mt-1 text-sm leading-6 text-teal-800">Bài đăng đã có xác nhận của nhà trường. Hồ sơ tiếp nhận được lưu để đối chiếu.</p></div></div>
      <Link to={`/chi-tiet/${receipt.postId}`} className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-3 font-semibold text-white hover:bg-teal-800">Xem bài đăng<ArrowUpRight size={17} /></Link>
    </section>}
    <header className="flex flex-col items-start justify-between gap-4 sm:flex-row">
      <div className="min-w-0 flex-1"><div className="mb-3 flex flex-wrap items-center gap-3"><span className="text-sm font-bold text-teal-700">{receipt.source === 'CHECKER' ? 'Hồ sơ tuần tra cũ' : receipt.source === 'EXISTING_POST' ? 'Từ bài có sẵn' : 'Tiếp nhận trực tiếp'} · Phiếu #{receipt.id}</span><ReceiptStatusBadge receipt={receipt} /></div><h1 className="break-words text-2xl font-bold text-slate-900 sm:text-3xl">{receipt.title}</h1><p className="mt-2 text-sm text-slate-600">{receipt.categoryName} · Người lập: {receipt.recordedBy || 'Chưa cập nhật tên'}</p></div>
      <div className="flex gap-2"><button type="button" disabled={busy || loaded.loading} onClick={() => { setPublished(null); loaded.reload(); }} aria-label="Tải lại phiếu" className="rounded-xl border border-slate-200 bg-white p-3 text-slate-600 disabled:opacity-50"><RefreshCw size={17} /></button>{draft ? <Link to={`/approver/receipts/${id}/edit`} className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:border-teal-400"><Pencil size={16} />Chỉnh sửa</Link> : <Link to="/approver/receipts/new" className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700"><Plus size={16} />Phiếu mới</Link>}</div>
    </header>
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="min-w-0 space-y-5">
        {draft && <section className="rounded-2xl border border-blue-200 bg-blue-50/60 p-5"><h2 className="flex items-center gap-2 font-semibold text-blue-950"><ClipboardCheck size={20} />Xem lại trước khi xác nhận</h2><p className="mt-2 text-sm leading-6 text-blue-900">Kiểm tra tài sản thực tế, ảnh và nơi lưu trữ. Khi xác nhận, hệ thống đăng bài tìm chủ sở hữu và ghi nhận nhà trường đã nhận tài sản.</p></section>}
        <ReceiptEvidence receipt={receipt} />
      </div>
      <aside className="space-y-5 lg:sticky lg:top-4">
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 className="flex items-center gap-2 font-semibold text-slate-900"><MapPin size={19} className="text-teal-700" />Nơi lưu trữ tài sản</h2><p className={`mt-3 break-words text-lg font-semibold ${receipt.storageLocationName ? 'text-slate-800' : 'text-amber-800'}`}>{receipt.storageLocationName || 'Chưa chọn nơi lưu trữ'}</p>
          {draft && !receipt.storageLocationActive && <p className="mt-2 text-sm leading-6 text-amber-800">Chọn một nơi lưu trữ đang hoạt động trước khi xác nhận.</p>}
          {!draft && <dl className="mt-4 space-y-3 border-t border-slate-100 pt-4 text-sm"><div><dt className="text-slate-500">Cán bộ tiếp nhận</dt><dd className="mt-1 font-semibold text-slate-800">{receipt.receivedBy || receipt.recordedBy}</dd></div><div><dt className="text-slate-500">Thời điểm tiếp nhận</dt><dd className="mt-1 font-semibold text-slate-800">{receiptDate(receipt.receivedAt)}</dd></div></dl>}
        </section>
        {draft && <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><h2 className="font-semibold text-slate-900">Nội dung sẽ được đăng</h2><div className="mt-4 space-y-3 text-sm leading-6 text-slate-600"><p>Tên, danh mục, mô tả, nơi và thời gian nhặt được; {publicImages.length} ảnh công khai.</p><p>Người đăng: <strong className="text-slate-800">{receipt.canProcess ? 'Cán bộ xác nhận tiếp nhận' : receipt.recordedBy}</strong></p><p className="flex items-center gap-2 font-semibold text-blue-700"><BadgeCheck size={18} />Nhà trường đã tiếp nhận</p><p>Nơi lưu trữ, cán bộ và thời điểm tiếp nhận sẽ hiển thị trên bài.</p><p className="flex gap-2 border-t border-slate-100 pt-3"><ShieldCheck size={18} className="mt-1 shrink-0 text-teal-700" />Đặc điểm nhận dạng riêng tư, ghi chú và ảnh nội bộ chỉ lưu trong hồ sơ.</p></div></section>}
        <section className="rounded-2xl border border-slate-200 bg-slate-50 p-5 text-sm text-slate-600"><p className="font-semibold">Mã hồ sơ</p><p className="mt-2 break-all font-mono text-xs">{receipt.receiptCode}</p><p className="mt-4">Lập lúc: {receiptDate(receipt.recordedAt)}</p><p className="mt-2">Cập nhật: {receiptDate(receipt.updatedAt)}</p></section>
      </aside>
    </div>
    {draft && <div className="sticky bottom-0 z-10 flex flex-wrap items-center justify-between gap-4 rounded-t-2xl border-t border-slate-200 bg-white/95 p-4 shadow-lg backdrop-blur-sm sm:px-6"><p className="max-w-md text-sm leading-6 text-slate-600">{ready ? 'Chỉ xác nhận khi bạn đã nhận và kiểm tra tài sản thực tế.' : 'Hoàn thiện nơi lưu trữ, danh mục và ảnh công khai để tiếp nhận.'}</p><div className="flex flex-wrap gap-3"><Link to={`/approver/receipts/${id}/edit`} className="rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700">Chỉnh sửa phiếu</Link><button type="button" disabled={!ready || busy} onClick={() => { setError(''); setConfirm(true); }} className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-3 text-sm font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-50">{busy ? <LoaderCircle size={18} className="animate-spin" /> : <ClipboardCheck size={18} />}Xác nhận tiếp nhận & đăng bài</button></div></div>}
    {confirm && <IntakeConfirmDialog receipt={receipt} busy={busy} error={error} onClose={() => setConfirm(false)} onConfirm={submit} />}
  </div>;
}
