import { useEffect, useId, useRef, useState } from 'react';
import { Flag, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { createReport } from './reportApi';
import ReportReasonPicker from './ReportReasonPicker';
import useReportReasons, { reportTitles, reportTypes } from './useReportReasons';
import './Reports.css';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
import ImageUploadButton from '@/components/common/ImageUploadButton';

export default function ReportButton({ type, id, label = 'Báo cáo', targetLabel }) {
  const [open, setOpen] = useState(false);
  const [sent, setSent] = useState(null);
  return <>
    <button type="button" className="report-trigger" onClick={() => setOpen(true)} title={label} aria-label={label}>
      <Flag size={15} /><span>{label}</span>
    </button>
    {sent && <Link className="claims-muted" role="status" to={`/bao-cao/${sent}`}>Đã gửi báo cáo — Theo dõi xử lý</Link>}
    {open && <ReportForm key={`${type}:${id}`} type={type} id={id} targetLabel={targetLabel}
      close={() => setOpen(false)} done={result => { setSent(result.id); setOpen(false); }} />}
  </>;
}

function ReportForm({ type, id, targetLabel, close, done }) {
  const dialog = useRef(null);
  const title = useId();
  const sending = useRef(false);
  const retry = useRef(null);
  const { options, error: catalogError, reload } = useReportReasons(type, id);
  const [reasonCode, setReasonCode] = useState('');
  const [description, setDescription] = useState('');
  const [files, setFiles] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setBusy] = useState(false);
  const { prepare, processing } = useImageUploads();
  const busy = submitting || processing;
  const [discard, setDiscard] = useState(false);
  const selected = options?.reasons.find(reason => reason.code === reasonCode);
  const canSend = selected && (!selected.requiresDescription || description.trim());

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

  function changed() {
    retry.current = null;
    setDiscard(false);
    setError('');
  }

  function requestClose() {
    if (sending.current || processing) return;
    if (reasonCode || description.trim() || files.length) setDiscard(true);
    else close();
  }

  async function pickImages(event) {
    const picked = [...event.target.files];
    event.target.value = '';
    if (busy || !picked.length) return;
    if (files.length + picked.length > 5) {
      setError('Chọn tối đa 5 ảnh.');
      return;
    }
    setError('');
    setDiscard(false);
    try {
      const prepared = await prepare(picked);
      if (prepared) { setFiles(current => [...current, ...prepared]); retry.current = null; }
    } catch (problem) { setError(problem.message); }
  }

  async function send(event) {
    event.preventDefault();
    if (busy || sending.current || !canSend) return;
    sending.current = true;
    setBusy(true);
    setError('');
    retry.current ||= {
      targetType: type, targetId: Number(id), reasonCode,
      description: description.trim(), clientRequestId: crypto.randomUUID(),
    };
    try {
      done(await createReport(retry.current, files));
    } catch (problem) {
      setError(problem.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return <dialog ref={dialog} className="report-dialog" aria-labelledby={title}
    onCancel={event => { event.preventDefault(); requestClose(); }}>
    <header><h2 id={title}>{reportTitles[options?.group || type]}</h2>
      <button disabled={busy} type="button" aria-label="Đóng" onClick={requestClose}><X size={20} /></button></header>
    <form onSubmit={send}>
      <div className="report-form-content">
      <div className="report-target-context"><span>{reportTypes[options?.group || type]}</span>
        <strong>{targetLabel || `${reportTypes[type]} #${id}`}</strong></div>
      <ReportReasonPicker options={options} error={catalogError} reload={reload} value={reasonCode}
        disabled={busy} onChange={code => { setReasonCode(code); changed(); }} />
      <label>{selected?.requiresDescription ? 'Mô tả chi tiết *' : 'Mô tả thêm (không bắt buộc)'}
        <textarea required={!!selected?.requiresDescription} maxLength={4800} value={description} disabled={busy}
          placeholder="Bổ sung thông tin giúp làm rõ báo cáo…"
          onChange={event => { setDescription(event.target.value); changed(); }} /></label>
      <div className="image-upload-field"><span>Ảnh bằng chứng (tùy chọn)</span>
        <span className="claims-muted">{IMAGE_FORMATS} · Tối đa 5 ảnh, 5 MB mỗi ảnh · Đã chọn {files.length}/5</span>
        <ImageUploadButton label="Thêm ảnh bằng chứng" disabled={busy||files.length>=5} onChange={pickImages} /></div>
      <ImageProcessingStatus active={processing} />
      {files.length > 0 && <ul className="report-selected-files">{files.map((file, index) => <li key={`${file.name}:${index}`}>
        <span>{file.name}</span><button type="button" disabled={busy} aria-label={`Bỏ ảnh ${file.name}`} onClick={() => {
          setFiles(current => current.filter((_, position) => position !== index)); changed();
        }}>Bỏ ảnh</button></li>)}</ul>}
      {error && <p className="report-error" role="alert">{error}</p>}
      </div>
      {discard ? <div className="report-discard" role="alert"><p>Bỏ nội dung báo cáo đang nhập?</p><footer>
        <button type="button" className="btn btn-primary" onClick={() => setDiscard(false)}>Tiếp tục nhập</button>
        <button type="button" className="btn btn-ghost" onClick={close}>Bỏ nội dung và đóng</button>
      </footer></div> : <footer>
        <button className="btn btn-ghost" type="button" disabled={busy} onClick={requestClose}>Hủy</button>
        <button type="submit" className="btn btn-accent" disabled={busy || !canSend}>{processing ? 'Đang xử lý ảnh…' : submitting ? 'Đang gửi…' : 'Gửi báo cáo'}</button>
      </footer>}
    </form>
  </dialog>;
}
