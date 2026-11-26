import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { cancelClaim } from '../api/claimApi';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';
import './RejectClaimDialog.css';

export default function CancelClaimDialog({ claim, onClose, onCancelled }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const titleId = useId();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  function close() {
    if (!sending.current) onClose();
  }

  function handleFormSubmit(event) {
    event.preventDefault();
    if (sending.current) return;
    setError('');
    setConfirmOpen(true);
  }

  async function executeSubmit() {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      await cancelClaim(claim.id, { reason: reason.trim() || null, expectedVersion: claim.version });
    } catch (problem) {
      setError(problem.message || 'Chưa thể hủy yêu cầu. Vui lòng thử lại.');
      setConfirmOpen(false);
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    setConfirmOpen(false);
    onCancelled();
  }

  return <dialog ref={dialog} className="claim-reject-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Hủy yêu cầu nhận đồ?</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={handleFormSubmit}>
      <p>Bạn đang hủy yêu cầu nhận <strong>{claim.postTitle}</strong>.</p>
      <p>Hồ sơ và bằng chứng vẫn được lưu. Bạn có thể gửi yêu cầu mới nếu bài đăng còn cho phép nhận đồ.</p>
      {claim.status === "PENDING" && <p>Nếu bạn đang tranh chấp bằng hồ sơ này, rút yêu cầu cũng sẽ kết thúc tranh chấp của bạn.</p>}
      {claim.status === 'APPROVED' && <p className="claim-notice">Sau khi hủy, cuộc trò chuyện chỉ còn xem lịch sử. Bạn không thể tiếp tục bàn giao với hồ sơ này.</p>}
      <label className="claim-reject-detail">Lý do hủy (tùy chọn)
        <textarea maxLength={2000} value={reason} disabled={busy} placeholder="Ví dụ: Tôi đã tìm lại được đồ…"
          onChange={event => setReason(event.target.value)} /></label>
      {error && <p role="alert" className="claim-alert">{error}</p>}
      <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Giữ yêu cầu</button>
        <button type="submit" className="btn claim-reject-submit" disabled={busy}>{busy ? 'Đang hủy…' : 'Xác nhận hủy yêu cầu'}</button></footer>
    </form>
    <ConfirmActionPopup
      open={confirmOpen}
      variant="warning"
      title="Bạn có chắc chắn hủy yêu cầu?"
      description={`Yêu cầu nhận đồ "${claim.postTitle}" của bạn sẽ bị hủy. Hồ sơ và bằng chứng đã gửi vẫn được lưu trữ.`}
      confirmLabel="Xác nhận hủy"
      cancelLabel="Giữ yêu cầu"
      busy={busy}
      onConfirm={executeSubmit}
      onClose={() => setConfirmOpen(false)}
    />
  </dialog>;
}
