import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { reviewClaim } from '../api/claimApi';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';
import './RejectClaimDialog.css';

const REASONS = ['Thông tin mô tả chưa khớp với món đồ', 'Bằng chứng chưa đủ để xác minh', 'Câu trả lời xác minh chưa phù hợp'];

export default function RejectClaimDialog({ claim, onClose, onRejected }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const titleId = useId();
  const [preset, setPreset] = useState('');
  const [detail, setDetail] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState('');
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const reason = [preset === 'OTHER' ? '' : preset, detail.trim()].filter(Boolean).join(' — ');

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
    if (preset || detail.trim()) setConfirmDiscard(true);
    else onClose();
  }

  function handleFormSubmit(event) {
    event.preventDefault();
    if (sending.current || !reason) return;
    setError('');
    setConfirmOpen(true);
  }

  async function executeSubmit() {
    if (sending.current || !reason) return;
    sending.current = true;
    setBusy(true);
    setError('');
    try {
      await reviewClaim(claim.id, { decision: 'REJECTED', reason, expectedVersion: claim.version });
    } catch (problem) {
      setError(problem.message || 'Chưa thể từ chối yêu cầu. Vui lòng thử lại.');
      setConfirmOpen(false);
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    setConfirmOpen(false);
    onRejected();
  }

  return <dialog ref={dialog} className="claim-reject-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Từ chối yêu cầu nhận đồ</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={handleFormSubmit}>
      <p>Yêu cầu của <strong>{claim.claimantName}</strong> cho <strong>{claim.postTitle}</strong>.</p>
      <p className="claims-muted">Lý do sẽ được gửi cho người yêu cầu. Họ có thể gửi hồ sơ mới với căn cứ phù hợp hơn.</p>
      <fieldset disabled={busy}><legend>Lý do từ chối *</legend>
        {[...REASONS, 'OTHER'].map(value => <label className="claim-reject-option" key={value}>
          <input type="radio" name="rejectReason" value={value} checked={preset === value}
            onChange={() => { setPreset(value); setConfirmDiscard(false); }} />
          {value === 'OTHER' ? 'Lý do khác' : value}
        </label>)}
      </fieldset>
      <label className="claim-reject-detail">{preset && preset !== 'OTHER' ? 'Bổ sung chi tiết (tùy chọn)' : 'Nhập lý do *'}
        <textarea autoComplete="off" required={!preset || preset === 'OTHER'} disabled={busy} maxLength={1800}
          value={detail} onChange={event => { setDetail(event.target.value); setConfirmDiscard(false); }}
          placeholder="Giải thích cụ thể để người yêu cầu hiểu lý do…" /></label>
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {confirmDiscard ? <div className="claim-reject-discard" role="alert">
        <p>Đóng và bỏ lý do đang nhập?</p><div className="claim-actions">
          <button type="button" className="btn btn-primary" onClick={() => setConfirmDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ nội dung và đóng</button>
        </div></div> : <footer>
        <button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại hồ sơ</button>
        <button type="submit" className="btn claim-reject-submit" disabled={busy || !reason}>
          {busy ? 'Đang gửi…' : 'Xác nhận từ chối'}</button>
      </footer>}
    </form>
    <ConfirmActionPopup
      open={confirmOpen}
      variant="danger"
      title="Bạn có chắc chắn từ chối?"
      description={`Yêu cầu nhận đồ của ${claim.claimantName} cho "${claim.postTitle}" sẽ bị từ chối và thông báo cho người yêu cầu.`}
      confirmLabel="Xác nhận từ chối"
      cancelLabel="Quay lại"
      busy={busy}
      onConfirm={executeSubmit}
      onClose={() => setConfirmOpen(false)}
    />
  </dialog>;
}
