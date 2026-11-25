import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { confirmHandover } from '../api/handoverApi';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';

export default function ConfirmReceiptDialog({ claim, handover, onClose, onConfirmed, onReportIssue }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const titleId = useId();
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const element = dialog.current;
    const focus = document.activeElement;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      if (focus?.isConnected) focus.focus();
    };
  }, []);

  function handleFormSubmit(event) {
    event.preventDefault();
    if (sending.current) return;
    setError('');
    setConfirmOpen(true);
  }

  async function executeConfirm() {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError('');
    let result;
    try {
      result = await confirmHandover(claim.id, handover.version);
    } catch (problem) {
      setError(problem.message || 'Chưa thể xác nhận nhận đồ. Vui lòng thử lại.');
      setConfirmOpen(false);
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    setConfirmOpen(false);
    onConfirmed(result);
  }

  return <dialog ref={dialog} className="handover-record-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!sending.current) onClose(); }}>
    <header inert={confirmOpen}><h2 id={titleId}>Xác nhận đã nhận đúng đồ</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={onClose}><X size={20} /></button></header>
    <form inert={confirmOpen} onSubmit={handleFormSubmit}>
      <div className="handover-record-context"><strong>{claim.postTitle}</strong><p>Người giao: {claim.finderName}</p></div>
      <p>Chỉ xác nhận khi bạn đã nhận và kiểm tra đúng món đồ của mình.</p>
      <ul className="handover-confirm-effects">
        <li>Hồ sơ nhận đồ hoàn tất; bài đăng chuyển sang đã trao trả.</li>
        <li>Cuộc trò chuyện chuyển sang chỉ đọc, vẫn giữ lịch sử.</li>
        <li>Bạn có thể báo sự cố phát hiện sau khi xác nhận trong vòng 7 ngày.</li>
      </ul>
      {onReportIssue && <div className="handover-record-context"><p>Chưa nhận được đồ hoặc có vấn đề?</p>
        <button type="button" className="btn btn-ghost" disabled={busy} onClick={onReportIssue}>Báo sự cố bàn giao</button></div>}
      {error && <p role="alert" className="claim-alert">{error}</p>}
      <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={onClose}>Chưa xác nhận</button>
        <button type="submit" className="btn btn-accent" disabled={busy}>{busy ? 'Đang xác nhận…' : 'Xác nhận đã nhận đúng đồ'}</button></footer>
    </form>
    <ConfirmActionPopup
      inline
      open={confirmOpen}
      variant="success"
      title="Xác nhận đã nhận đúng đồ?"
      description={`Bạn xác nhận đã nhận lại đúng "${claim.postTitle}". Hồ sơ nhận đồ sẽ hoàn tất và không thể hoàn tác.`}
      confirmLabel="Đã nhận đúng đồ"
      cancelLabel="Kiểm tra lại"
      busy={busy}
      onConfirm={executeConfirm}
      onClose={() => setConfirmOpen(false)}
    />
  </dialog>;
}
