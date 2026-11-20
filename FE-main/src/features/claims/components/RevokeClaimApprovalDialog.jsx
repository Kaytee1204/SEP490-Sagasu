import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { revokeClaimApproval } from '../api/claimApi';
import './RejectClaimDialog.css';

export default function RevokeClaimApprovalDialog({ claim, onClose, onRevoked }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const titleId = useId();
  const [reason, setReason] = useState('');
  const [custodyConfirmed, setCustodyConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
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

  async function submit(event) {
    event.preventDefault();
    if (sending.current || !reason.trim() || !custodyConfirmed) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      await revokeClaimApproval(claim.id, { reason: reason.trim(), expectedVersion: claim.version, custodyConfirmed });
    } catch (problem) {
      setError(problem.message || 'Chưa thể thu hồi. Hồ sơ có thể đã thay đổi hoặc đã ghi nhận giao đồ. Hãy đóng và tải lại hồ sơ.');
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    onRevoked();
  }

  return <dialog ref={dialog} className="claim-reject-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Thu hồi chấp thuận nhận đồ?</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={submit}>
      <p>Đưa yêu cầu của <strong>{claim.claimantName}</strong> cho <strong>{claim.postTitle}</strong> về chờ xét duyệt.</p>
      <p>Bạn có thể xét duyệt các yêu cầu khác. Người yêu cầu sẽ nhận được lý do; cuộc chat chuyển sang chỉ xem lịch sử.</p>
      <label className="claim-reject-option">
        <input type="checkbox" required checked={custodyConfirmed} disabled={busy}
          onChange={event => setCustodyConfirmed(event.target.checked)} />
        Tôi xác nhận vẫn đang giữ món đồ và chưa giao cho người nhận.
      </label>
      <label className="claim-reject-detail">Lý do thu hồi *
        <textarea required maxLength={2000} value={reason} disabled={busy} placeholder="Nêu lý do cần xét duyệt lại, không tiết lộ đặc điểm bí mật của món đồ…"
          onChange={event => { setReason(event.target.value); setDiscard(false); }} /></label>
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {discard ? <div className="claim-reject-discard" role="alert"><p>Bỏ lý do đang nhập và quay lại hồ sơ?</p>
        <div className="claim-actions"><button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ nội dung và đóng</button></div></div>
        : <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại hồ sơ</button>
          <button type="submit" className="btn btn-accent" disabled={busy || !reason.trim() || !custodyConfirmed}>{busy ? 'Đang lưu…' : 'Xác nhận thu hồi'}</button></footer>}
    </form>
  </dialog>;
}
