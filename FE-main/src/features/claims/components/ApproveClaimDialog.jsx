import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { reviewClaim } from '../api/claimApi';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';
import './RejectClaimDialog.css';

export default function ApproveClaimDialog({ claim, onClose, onApproved }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const titleId = useId();
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState('');
  const [discard, setDiscard] = useState(false);

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
    if (sending.current) return;
    if (reason.trim()) setDiscard(true);
    else onClose();
  }

  function handleFormSubmit(event) {
    event.preventDefault();
    if (sending.current || !reason.trim()) return;
    setError('');
    setConfirmOpen(true);
  }

  async function executeSubmit() {
    if (sending.current || !reason.trim()) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      await reviewClaim(claim.id, { decision: 'APPROVED', reason: reason.trim(), expectedVersion: claim.version });
    } catch (problem) {
      setError(problem.message || 'Chưa thể chấp thuận yêu cầu. Vui lòng thử lại.');
      setConfirmOpen(false);
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    setConfirmOpen(false);
    onApproved();
  }

  return <dialog ref={dialog} className="claim-reject-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Chấp thuận yêu cầu nhận đồ?</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={handleFormSubmit}>
      <p>Chấp thuận <strong>{claim.claimantName}</strong> nhận <strong>{claim.postTitle}</strong>.</p>
      <p>Chỉ chấp thuận sau khi đã đối chiếu căn cứ và bằng chứng. Hai bên có thể chat để trao đổi việc nhận đồ.</p>
      <p className="claim-notice">Nếu còn hồ sơ đang chờ, hệ thống sẽ thông báo cho những người đó và tạm dừng giao đồ trong ít nhất 24 giờ để họ có thể gửi tranh chấp.</p>
      <label className="claim-reject-detail">Căn cứ chấp thuận *
        <textarea required maxLength={2000} value={reason} disabled={busy} placeholder="Ví dụ: Mô tả và bằng chứng khớp với đặc điểm của món đồ…"
          onChange={event => { setReason(event.target.value); setDiscard(false); }} /></label>
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {discard ? <div className="claim-reject-discard" role="alert"><p>Bỏ căn cứ đang nhập và quay lại hồ sơ?</p>
        <div className="claim-actions"><button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ nội dung và đóng</button></div></div>
        : <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại hồ sơ</button>
          <button type="submit" className="btn btn-accent" disabled={busy || !reason.trim()}>{busy ? 'Đang lưu…' : 'Chấp thuận nhận đồ'}</button></footer>}
    </form>
    <ConfirmActionPopup
      open={confirmOpen}
      variant="success"
      title="Bạn có chắc chắn chấp thuận?"
      description={`Xác nhận chấp thuận cho ${claim.claimantName} nhận lại "${claim.postTitle}". Hai bên sẽ có thể trao đổi để bàn giao đồ.`}
      confirmLabel="Đồng ý chấp thuận"
      cancelLabel="Quay lại"
      busy={busy}
      onConfirm={executeSubmit}
      onClose={() => setConfirmOpen(false)}
    />
  </dialog>;
}
