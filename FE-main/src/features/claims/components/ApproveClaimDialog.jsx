import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { reviewClaim } from '../api/claimApi';
import './RejectClaimDialog.css';

export default function ApproveClaimDialog({ claim, onClose, onApproved }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const titleId = useId();
  const [note, setNote] = useState('');
  const [evidenceVerified, setEvidenceVerified] = useState(false);
  const needsBasis = claim.priorWrongRecipientConclusion != null;
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
    if (note.trim()) setDiscard(true);
    else onClose();
  }

  async function submit(event) {
    event.preventDefault();
    if (sending.current || !evidenceVerified || (needsBasis && !note.trim())) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      await reviewClaim(claim.id, {
        decision: 'APPROVED',
        reason: note.trim() || null,
        expectedVersion: claim.version,
        evidenceVerified,
      });
    } catch (problem) {
      setError(problem.message || 'Chưa thể chấp thuận yêu cầu. Vui lòng thử lại.');
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    onApproved();
  }

  return <dialog ref={dialog} className="claim-reject-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Chấp thuận yêu cầu nhận đồ?</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={submit}>
      <p>Chấp thuận <strong>{claim.claimantName}</strong> nhận <strong>{claim.postTitle}</strong>.</p>
      <p>Nếu đây là hồ sơ hợp lệ duy nhất, hai bên có thể nhắn tin và hẹn giao đồ ngay.</p>
      <p className="claim-notice">Nếu còn hồ sơ đang chờ, hệ thống sẽ thông báo cho những người đó và mở hạn gửi tranh chấp 24 giờ. Chat chỉ xem và bàn giao tạm dừng trong thời gian này. Có thể mở sớm khi hồ sơ cạnh tranh cuối cùng được rút hoặc được quản lý xác minh là sai, và mọi tranh chấp đã kết thúc.</p>
      <label className="claim-reject-option">
        <input type="checkbox" required checked={evidenceVerified} disabled={busy}
          onChange={event => { setEvidenceVerified(event.target.checked); setDiscard(false); }} />
        Tôi đã đối chiếu thông tin trong hồ sơ với món đồ và xác nhận đây là người nhận phù hợp.
      </label>
      {needsBasis && <p className="claim-notice"><strong>Kết luận duyệt nhầm trước đó</strong><br />{claim.priorWrongRecipientConclusion}</p>}
      <label className="claim-reject-detail">{needsBasis ? "Căn cứ mới để chọn lại người này *" : "Ghi chú (tùy chọn)"}
        <textarea required={needsBasis} maxLength={2000} value={note} disabled={busy} placeholder="Ví dụ: Mô tả đúng vết xước ở góc trái…"
          onChange={event => { setNote(event.target.value); setDiscard(false); }} />
      </label>
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {discard ? <div className="claim-reject-discard" role="alert"><p>Bỏ ghi chú đang nhập và quay lại hồ sơ?</p>
        <div className="claim-actions"><button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ nội dung và đóng</button></div></div>
        : <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại hồ sơ</button>
          <button type="submit" className="btn btn-accent" disabled={busy || !evidenceVerified || (needsBasis && !note.trim())}>{busy ? 'Đang lưu…' : 'Chấp thuận nhận đồ'}</button></footer>}
    </form>
  </dialog>;
}
