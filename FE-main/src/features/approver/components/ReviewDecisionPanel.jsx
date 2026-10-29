import { useEffect, useRef, useState } from 'react';
import { CheckCircle2, MessageSquare, XCircle } from 'lucide-react';
import Modal from '@/components/common/Modal';
import { ReceiptError } from '@/features/checker/components/ReceiptFeedback';
import { approverApi } from '../api/approverApi';

const decisions = [
  { value: 'ACCEPTED', label: 'Chấp nhận', hint: 'Hồ sơ đủ thông tin để tiếp tục xử lý.', icon: CheckCircle2 },
  { value: 'NEEDS_INFO', label: 'Yêu cầu bổ sung', hint: 'Trả về nhân viên tuần tra để sửa và gửi lại.', icon: MessageSquare },
  { value: 'REJECTED', label: 'Từ chối', hint: 'Kết thúc xét duyệt phiếu này.', icon: XCircle },
];

function ReviewConfirmation({ decision, note, busy, error, onClose, onConfirm }) {
  const content = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const dialog = content.current.closest('[role="dialog"]');
    dialog.setAttribute('aria-label', 'Xác nhận kết quả xét duyệt');
    const buttons = () => [...dialog.querySelectorAll('button:not(:disabled)')];
    buttons()[0]?.focus();
    const trap = (event) => {
      if (event.key !== 'Tab') return;
      const items = buttons();
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1)?.focus(); }
      else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0]?.focus(); }
    };
    dialog.addEventListener('keydown', trap);
    return () => { dialog.removeEventListener('keydown', trap); previous?.focus(); };
  }, []);
  return <Modal isOpen title="Xác nhận kết quả xét duyệt" onClose={() => { if (!busy) onClose(); }}><div ref={content} className="space-y-5"><p className="text-base leading-6 text-slate-600">Quyết định: <strong className="text-slate-900">{decision.label}</strong>. Kết quả sẽ hiển thị cho nhân viên tuần tra ngay sau khi xác nhận.</p>{note.trim() && <p className="max-h-48 overflow-y-auto whitespace-pre-wrap break-words rounded-xl bg-slate-50 p-4 text-base leading-6 text-slate-700">{note.trim()}</p>}<p className="text-sm leading-5 text-slate-600">Kiểm tra kỹ quyết định và nội dung phản hồi. Kết quả đã lưu không thể sửa tại màn hình này.</p>{error && <ReceiptError message={error} />}<div className="flex flex-wrap justify-end gap-3"><button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2.5 text-base font-semibold disabled:opacity-50">Quay lại</button><button type="button" disabled={busy} onClick={onConfirm} className={`rounded-lg px-4 py-2.5 text-base font-semibold text-white disabled:opacity-50 ${decision.value === 'REJECTED' ? 'bg-rose-700' : 'bg-teal-700'}`}>{busy ? 'Đang lưu…' : 'Xác nhận quyết định'}</button></div></div></Modal>;
}

export default function ReviewDecisionPanel({ receipt, onReviewed }) {
  const [value, setValue] = useState('');
  const [note, setNote] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const noteInput = useRef(null);
  const pending = useRef(false);
  const selected = decisions.find((decision) => decision.value === value);
  const preview = () => {
    setError('');
    if (!selected) { setError('Chọn quyết định xét duyệt trước khi tiếp tục.'); return; }
    if (value !== 'ACCEPTED' && !note.trim()) { setError('Ghi rõ lý do hoặc thông tin nhân viên tuần tra cần bổ sung.'); noteInput.current?.focus(); return; }
    setConfirm(true);
  };
  const review = async () => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try { await approverApi.review(receipt.id, { decision: value, note: note.trim() || null, expectedUpdatedAt: receipt.updatedAt }); setConfirm(false); onReviewed(); }
    catch (failure) { setError(failure.message); }
    finally { pending.current = false; setBusy(false); }
  };
  return <section className="space-y-5 rounded-2xl border border-teal-200 bg-white p-5 shadow-sm">
    <div><h2 className="font-semibold text-slate-900">Quyết định xét duyệt</h2><p className="mt-1 text-sm leading-5 text-slate-600">Đối chiếu thông tin, ảnh và tình trạng đồ vật trước khi chọn.</p></div>
    <fieldset disabled={busy} className="space-y-2"><legend className="mb-2 text-sm font-semibold text-slate-600">Chọn một quyết định</legend>{decisions.map(({ value: choice, label, hint, icon: Icon }) => <label key={choice} className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition ${value === choice ? 'border-teal-500 bg-teal-50' : 'border-slate-200 hover:bg-slate-50'}`}><input type="radio" name="review-decision" value={choice} checked={value === choice} onChange={() => { setValue(choice); setError(''); }} className="mt-1 accent-teal-700" /><div className="min-w-0"><span className={`flex items-center gap-2 text-base font-semibold ${choice === 'REJECTED' ? 'text-rose-700' : 'text-slate-800'}`}><Icon size={16} />{label}</span><span className="mt-1 block text-sm leading-5 text-slate-600">{hint}</span></div></label>)}</fieldset>
    <div><label htmlFor="review-note" className="mb-2 block text-base font-semibold text-slate-700">Phản hồi cho nhân viên tuần tra {value && value !== 'ACCEPTED' && <span className="text-rose-600">*</span>}</label><textarea ref={noteInput} id="review-note" value={note} disabled={busy} onChange={(event) => { setNote(event.target.value); setError(''); }} maxLength={2000} rows={4} placeholder="Nêu rõ thông tin cần bổ sung hoặc lý do quyết định…" className="w-full rounded-xl border border-slate-200 px-3 py-2.5 text-base outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100" /><p className="mt-1 text-sm text-slate-600">Bắt buộc khi yêu cầu bổ sung hoặc từ chối.</p></div>
    {error && !confirm && <ReceiptError message={error} />}
    <button type="button" disabled={busy} onClick={preview} className="w-full rounded-xl bg-teal-700 px-4 py-3 text-base font-semibold text-white hover:bg-teal-800 disabled:opacity-50">Kiểm tra quyết định</button>
    {confirm && <ReviewConfirmation decision={selected} note={note} busy={busy} error={error} onClose={() => setConfirm(false)} onConfirm={review} />}
  </section>;
}
