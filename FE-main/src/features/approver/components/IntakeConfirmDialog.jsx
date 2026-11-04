import { useEffect, useRef, useState } from 'react';
import { BadgeCheck, LoaderCircle } from 'lucide-react';
import Modal from '@/components/common/Modal';
import { ReceiptError } from '@/features/receipt/components/ReceiptFeedback';

export default function IntakeConfirmDialog({ receipt, busy, error, onClose, onConfirm }) {
  const [acknowledged, setAcknowledged] = useState(false);
  const content = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const dialog = content.current.closest('[role="dialog"]');
    dialog.setAttribute('aria-label', 'Xác nhận tiếp nhận tài sản');
    const controls = () => [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled)')];
    controls()[0]?.focus();
    const trap = (event) => {
      if (event.key !== 'Tab') return;
      const items = controls();
      if (event.shiftKey && document.activeElement === items[0]) { event.preventDefault(); items.at(-1)?.focus(); }
      else if (!event.shiftKey && document.activeElement === items.at(-1)) { event.preventDefault(); items[0]?.focus(); }
    };
    dialog.addEventListener('keydown', trap);
    return () => { dialog.removeEventListener('keydown', trap); previous?.focus(); };
  }, []);

  return <Modal isOpen title="Xác nhận tiếp nhận tài sản" maxWidth="lg" onClose={() => { if (!busy) onClose(); }}>
    <div ref={content} className="space-y-5" aria-busy={busy}>
      <div className="rounded-xl border border-slate-200 bg-slate-50 p-4"><p className="break-words font-semibold text-slate-900">{receipt.title}</p><p className="mt-2 text-sm text-slate-600">Lưu tại: <strong>{receipt.storageLocationName}</strong></p></div>
      <p className="text-sm leading-6 text-slate-600">Bài đăng sẽ được công khai với xác nhận của văn phòng. Nội dung phiếu được khóa sau khi tiếp nhận để bảo toàn hồ sơ.</p>
      <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-teal-200 bg-teal-50 p-4 text-sm font-medium leading-6 text-teal-950"><input type="checkbox" checked={acknowledged} disabled={busy} onChange={(event) => setAcknowledged(event.target.checked)} className="mt-1 h-4 w-4 shrink-0 accent-teal-700" />Tôi đã nhận tài sản thực tế, kiểm tra thông tin và xác nhận đúng nơi lưu trữ.</label>
      {error && <ReceiptError message={error} />}
      <div className="flex flex-wrap justify-end gap-3"><button type="button" disabled={busy} onClick={onClose} className="rounded-xl border border-slate-200 px-4 py-3 text-sm font-semibold disabled:opacity-50">Kiểm tra lại</button><button type="button" disabled={busy || !acknowledged} onClick={onConfirm} className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-3 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50">{busy ? <LoaderCircle size={18} className="animate-spin" /> : <BadgeCheck size={18} />}{busy ? 'Đang tiếp nhận & đăng bài…' : 'Xác nhận & đăng bài'}</button></div>
    </div>
  </Modal>;
}
