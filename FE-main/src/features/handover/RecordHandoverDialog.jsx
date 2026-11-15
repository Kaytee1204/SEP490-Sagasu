import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { uploadHandover } from './handoverApi';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
import ImageUploadButton from '@/components/common/ImageUploadButton';

export default function RecordHandoverDialog({ claim, onClose, onRecorded }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const request = useRef(null);
  const titleId = useId();
  const [note, setNote] = useState('');
  const [files, setFiles] = useState([]);
  const [confirmed, setConfirmed] = useState(false);
  const [submitting, setBusy] = useState(false);
  const { prepare, processing } = useImageUploads();
  const busy = submitting || processing;
  const [error, setError] = useState('');
  const [discard, setDiscard] = useState(false);

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

  function close() {
    if (sending.current || processing) return;
    if (note.trim() || files.length || confirmed) setDiscard(true);
    else onClose();
  }

  async function pickImages(event) {
    const selected = [...event.target.files];
    event.target.value = '';
    if (busy) return;
    if (files.length + selected.length > 5) {
      setError('Chọn tối đa 5 ảnh.');
      return;
    }
    setError('');
    setDiscard(false);
    try {
      const prepared = await prepare(selected);
      if (prepared) { setFiles(current => [...current, ...prepared]); request.current = null; }
    } catch (problem) { setError(problem.message); }
  }

  async function submit(event) {
    event.preventDefault();
    if (busy || sending.current || !confirmed) return;
    sending.current = true;
    setBusy(true);
    setError('');
    request.current ||= { note: note.trim() || null, expectedClaimVersion: claim.version, clientRequestId: crypto.randomUUID() };
    let result;
    try {
      result = await uploadHandover(claim.id, request.current, files);
    } catch (problem) {
      setError(problem.message || 'Chưa thể ghi nhận giao đồ. Vui lòng thử lại.');
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    onRecorded(result);
  }

  return <dialog ref={dialog} className="handover-record-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Ghi nhận đã giao đồ</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={submit}>
      <div className="handover-record-context"><strong>{claim.postTitle}</strong><p>Người nhận: {claim.claimantName}</p></div>
      <p>Sau khi ghi nhận, người nhận cần xác nhận đã nhận đúng đồ. Ghi nhận giao đồ không thể sửa hoặc rút lại.</p>
      <label className="handover-record-field">Ghi chú (tùy chọn)
        <textarea maxLength={2000} value={note} disabled={busy} placeholder="Ví dụ: Đã giao kèm các phụ kiện…"
          onChange={event => { setNote(event.target.value); request.current = null; setDiscard(false); }} /></label>
      <div className="handover-record-field"><span>Ảnh bàn giao (tùy chọn)</span>
        <span className="claims-muted">{IMAGE_FORMATS} · Tối đa 5 ảnh, 5 MB mỗi ảnh · Đã chọn {files.length}/5</span>
        <ImageUploadButton label="Thêm ảnh bàn giao" disabled={busy||files.length>=5} onChange={pickImages} /></div>
      <ImageProcessingStatus active={processing} />
      {files.length > 0 && <ul className="handover-record-files">{files.map((file, index) => <li key={`${file.name}:${index}`}>
        <span>{file.name}</span><button type="button" disabled={busy} aria-label={`Bỏ ảnh ${file.name}`} onClick={() => {
          setFiles(current => current.filter((_, position) => position !== index)); request.current = null;
        }}>Bỏ ảnh</button></li>)}</ul>}
      <label className="handover-record-confirm"><input type="checkbox" checked={confirmed} disabled={busy} required
        onChange={event => { setConfirmed(event.target.checked); setDiscard(false); }} />
        Tôi xác nhận đã giao đồ thực tế cho người nhận.</label>
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {discard ? <div className="handover-record-discard" role="alert"><p>Bỏ ghi chú và ảnh đang chọn để đóng?</p>
        <footer><button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ nội dung và đóng</button></footer></div>
        : <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại</button>
          <button type="submit" className="btn btn-accent" disabled={busy || !confirmed}>{processing ? 'Đang xử lý ảnh…' : submitting ? 'Đang ghi nhận…' : 'Xác nhận đã giao đồ'}</button></footer>}
    </form>
  </dialog>;
}
