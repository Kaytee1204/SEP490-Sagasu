import { useEffect, useId, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { getHandover, uploadHandover } from '../api/handoverApi';
import { reportDescriptionHint } from "@/features/reports/utils/reportDescriptionHints";
import ReportReasonPicker from '@/features/reports/components/ReportReasonPicker';
import useDeadlineCountdown from '@/hooks/useDeadlineCountdown';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
import ImageUploadButton from '@/components/common/ImageUploadButton';
import HandoverIssueDeadline from './HandoverIssueDeadline';
import { handoverIssueDeadline, incidentPhaseLabels } from '../utils/issueForm';
import '../styles/Handover.css';

export default function ReportHandoverIssueDialog({ claim, handover, onClose, onReported }) {
  const dialog = useRef(null);
  const sending = useRef(false);
  const request = useRef(null);
  const titleId = useId();
  const [current, setCurrent] = useState(handover);
  const [reasonCode, setReasonCode] = useState('');
  const [reason, setReason] = useState('');
  const [files, setFiles] = useState([]);
  const [submitting, setBusy] = useState(false);
  const { prepare, processing } = useImageUploads();
  const busy = submitting || processing;
  const [error, setError] = useState('');
  const reasons = current.issueReasons ?? [];
  const validReason = reasons.some(item => item.code === reasonCode);
  const [reasonNotice, setReasonNotice] = useState('');
  const deadline = handoverIssueDeadline(current);
  const timeLeft = useDeadlineCountdown(deadline, { showDays: true });
  const canReport = current.canReportIssue && (!deadline || Boolean(timeLeft));

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
    if (!sending.current && !processing) onClose();
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
    try {
      const prepared = await prepare(selected);
      if (prepared) { setFiles(current => [...current, ...prepared]); request.current = null; }
    } catch (problem) { setError(problem.message); }
  }

  async function submit(event) {
    event.preventDefault();
    if (busy || sending.current || !canReport || !reason.trim() || !validReason) return;
    sending.current = true;
    setBusy(true);
    setError('');
    request.current ||= { reasonCode, reason: reason.trim(), messageId: null,
      expectedVersion: current.version, expectedPhase: current.issuePhase, clientRequestId: crypto.randomUUID() };
    let result;
    try {
      result = await uploadHandover(claim.id, request.current, files, '/issues');
    } catch (problem) {
      setError(problem.message || 'Chưa thể gửi báo cáo sự cố. Vui lòng thử lại.');
      try {
        const latest = await getHandover(claim.id);
        setCurrent(latest);
        if (latest.version !== current.version || latest.status !== current.status) {
          // Keep the request ID on a retry so a lost success response cannot duplicate a submission.
          if (latest.canReportIssue) {
            request.current.expectedVersion = latest.version;
            request.current.expectedPhase = latest.issuePhase;
          }
        }
        if (latest.canReportIssue && !latest.issueReasons?.some(item => item.code === reasonCode)) {
          setReasonCode('');
          setReasonNotice('Chọn một lý do trong danh sách mới.');
          request.current = null;
        }
      } catch {
        // The submission error already explains why the user stayed on the form.
      }
      return;
    } finally {
      sending.current = false;
      setBusy(false);
    }
    onReported(result);
  }

  return <dialog ref={dialog} className="handover-record-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); close(); }}>
    <header><h2 id={titleId}>Báo sự cố trao trả</h2>
      <button type="button" aria-label="Đóng" disabled={busy} onClick={close}><X size={20} /></button></header>
    <form onSubmit={submit}>
      {incidentPhaseLabels[current.issuePhase] && <p className="claims-muted">{incidentPhaseLabels[current.issuePhase]}</p>}
      <HandoverIssueDeadline deadline={deadline} timeLeft={timeLeft} />
      <ReportReasonPicker options={{ reasons }} value={reasonCode} disabled={busy || !canReport}
        onChange={code => { setReasonCode(code); request.current = null; setError(''); setReasonNotice(''); }} />
      {reasonNotice && <p role="status" className="claims-muted">{reasonNotice}</p>}
      <label className="handover-record-field">Mô tả chi tiết sự cố *
        <small>{reportDescriptionHint(reasonCode)}</small>
        <textarea required maxLength={4800} value={reason} disabled={busy}
          onChange={event => { setReason(event.target.value); request.current = null; }} /></label>
      <div className="handover-record-field"><span>Ảnh bằng chứng (tùy chọn)</span>
        <span className="claims-muted">{IMAGE_FORMATS} · Tối đa 5 ảnh, 5 MB mỗi ảnh · Đã chọn {files.length}/5</span>
        <ImageUploadButton label="Thêm ảnh bằng chứng" disabled={busy||files.length>=5} onChange={pickImages} /></div>
      <ImageProcessingStatus active={processing} />
      {files.length > 0 && <ul className="handover-record-files">{files.map((file, index) => <li key={`${file.name}:${index}`}>
        <span>{file.name}</span><button type="button" disabled={busy} aria-label={`Bỏ ảnh ${file.name}`} onClick={() => {
          setFiles(current => current.filter((_, position) => position !== index)); request.current = null;
        }}>Bỏ ảnh</button></li>)}</ul>}
      {error && <p role="alert" className="claim-alert">{error}</p>}
      {current.canReportIssue && <p className="claims-muted">{current.issuePhase === 'BEFORE'
        ? 'Sau khi bạn gửi báo cáo, người nhặt sẽ tạm thời không thể bấm “Ghi nhận đã giao đồ” trong lúc quản lý xem xét.'
        : current.issuePhase === 'DURING'
          ? 'Hệ thống sẽ tạm dừng xác nhận đã nhận đồ trong lúc quản lý xem xét.'
          : 'Quản lý sẽ xem xét sự cố dựa trên hồ sơ trao trả và bằng chứng bạn cung cấp.'}</p>}
      <footer><button type="button" className="btn btn-ghost" disabled={busy} onClick={close}>Quay lại</button>
        <button type="submit" className="btn btn-accent" disabled={busy || !reason.trim() || !validReason || !canReport}>{processing ? 'Đang xử lý ảnh…' : submitting ? 'Đang gửi…' : 'Gửi báo cáo sự cố'}</button></footer>
    </form>
  </dialog>;
}
