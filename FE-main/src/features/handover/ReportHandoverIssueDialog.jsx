import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { uploadHandover } from './handoverApi';
import ReportReasonPicker from '@/features/reports/ReportReasonPicker';
import useReportReasons from '@/features/reports/useReportReasons';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_ACCEPT, IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';

export default function ReportHandoverIssueDialog({ claim, handover, onClose, onReported }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const request = useRef(null);
  const titleId = useId();
  const { options, error: catalogError, reload } = useReportReasons('CLAIM', claim.id, true);
  const [reasonCode, setReasonCode] = useState('');
  const [reason, setReason] = useState('');
  const [files, setFiles] = useState([]);
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
    if (reasonCode || reason.trim() || files.length) setDiscard(true);
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
    if (busy || sending.current || !reason.trim() || !options?.reasons.some(item => item.code === reasonCode)) return;
    sending.current = true;
    setBusy(true);
    setError('');
    request.current ||= { reasonCode, reason: reason.trim(), expectedVersion: handover.version, clientRequestId: crypto.randomUUID() };
    let result;
    try {
      result = await uploadHandover(claim.id, request.current, files, '/issues');
    } catch (problem) {
      setError(problem.message || 'Chưa thể gửi báo cáo sự cố. Vui lòng thử lại.');
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    onReported(result);
  }

  return <dialog ref={dialog} className="handover-record-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Báo sự cố bàn giao</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={submit}>
      <div className="handover-record-context"><strong>{claim.postTitle}</strong><p>Người giao: {claim.finderName}</p></div>
      <p>Báo cáo sẽ được chuyển đến Manager và khóa hoàn tất bàn giao. Hai bên vẫn có thể chat. Nội dung đã gửi không thể sửa hoặc rút lại.</p>
      <ReportReasonPicker options={options} error={catalogError} reload={reload} value={reasonCode} disabled={busy}
        onChange={code => { setReasonCode(code); request.current = null; setDiscard(false); setError(''); }} />
      <label className="handover-record-field">Mô tả chi tiết sự cố *
        <textarea required maxLength={4800} value={reason} disabled={busy} placeholder="Mô tả điều đã xảy ra, ví dụ: chưa nhận được đồ hoặc nhận sai món đồ…"
          onChange={event => { setReason(event.target.value); request.current = null; setDiscard(false); }} /></label>
      <label className="handover-record-field">Ảnh bằng chứng (tùy chọn)
        <span className="claims-muted">{IMAGE_FORMATS} · Tối đa 5 ảnh, 5 MB mỗi ảnh · Đã chọn {files.length}/5</span>
        <input type="file" accept={IMAGE_ACCEPT} multiple disabled={busy} onChange={pickImages} /></label>
      <ImageProcessingStatus active={processing} />
      {files.length > 0 && <ul className="handover-record-files">{files.map((file, index) => <li key={`${file.name}:${index}`}>
        <span>{file.name}</span><button type="button" disabled={busy} aria-label={`Bỏ ảnh ${file.name}`} onClick={() => {
          setFiles(current => current.filter((_, position) => position !== index)); request.current = null;
        }}>Bỏ ảnh</button></li>)}</ul>}
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {discard ? <div className="handover-record-discard" role="alert"><p>Bỏ lý do và ảnh đang chọn để đóng?</p>
        <footer><button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
          <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ nội dung và đóng</button></footer></div>
        : <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại</button>
          <button type="submit" className="btn btn-accent" disabled={busy || !reason.trim() || !options?.reasons.some(item => item.code === reasonCode)}>{processing ? 'Đang xử lý ảnh…' : submitting ? 'Đang gửi…' : 'Gửi báo cáo sự cố'}</button></footer>}
    </form>
  </dialog>;
}
