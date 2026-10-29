import { useEffect, useRef } from 'react';
import { Send } from 'lucide-react';
import Modal from '@/components/common/Modal';
import { ReceiptError } from './ReceiptFeedback';

export default function ReceiptConfirmDialog({ busy, error, onClose, onConfirm }) {
  const content = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    const dialog = content.current.closest('[role="dialog"]');
    dialog.setAttribute('aria-label', 'Gửi phiếu để xét duyệt');
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
  return <Modal isOpen title="Gửi phiếu để xét duyệt" onClose={() => { if (!busy) onClose(); }}>
    <div ref={content} className="space-y-5">
      <p className="text-base leading-6 text-slate-600">Hãy chắc chắn thông tin và ảnh đã đầy đủ. Sau khi gửi, phiếu chuyển sang <strong>Chờ duyệt</strong>; nội dung chỉ được sửa nếu Người xét duyệt yêu cầu bổ sung. Bạn vẫn có thể cập nhật vị trí lưu giữ trước khi bàn giao kho.</p>
      {error && <ReceiptError message={error} />}
      <div className="flex justify-end gap-3">
        <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-slate-200 px-4 py-2.5 text-base font-semibold disabled:opacity-50">Kiểm tra lại</button>
        <button type="button" disabled={busy} onClick={onConfirm} className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2.5 text-base font-semibold text-white hover:bg-teal-800 disabled:opacity-50"><Send size={16} />{busy ? 'Đang gửi…' : 'Xác nhận gửi'}</button>
      </div>
    </div>
  </Modal>;
}
