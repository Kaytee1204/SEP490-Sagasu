import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Scale } from 'lucide-react';
import ImageUploadButton from '@/components/common/ImageUploadButton';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
import useImageUploads from '@/hooks/useImageUploads';
import { getClaimAppeal, submitClaimAppeal } from '../api/claimApi';
import ReportProgress from '@/features/reports/components/ReportProgress';

export default function ClaimAppealPanel({ claim, focusRevision, notificationId }) {
  const [appeal, setAppeal] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    getClaimAppeal(claim.id).then(result => {
      if (active) {
        setAppeal(result);
        setLoadError('');
      }
    }).catch(problem => { if (active) setLoadError(problem.message); });
    return () => { active = false; };
  }, [claim.id, claim.version, focusRevision, notificationId, refresh]);

  const deadline = appeal?.deadline;
  useEffect(() => {
    const remaining = Date.parse(deadline) - Date.now();
    if (!Number.isFinite(remaining) || remaining <= 0) return;
    const timeout = window.setTimeout(() => setRefresh(value => value + 1), remaining + 100);
    return () => window.clearTimeout(timeout);
  }, [deadline]);

  const reportInProgress = ['OPEN', 'IN_REVIEW'].includes(appeal?.reportStatus);
  useEffect(() => {
    if (!reportInProgress) return;
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') setRefresh(value => value + 1);
    }, 30000);
    return () => window.clearInterval(interval);
  }, [reportInProgress]);

  if (!appeal && !loadError) return null;
  return <section className="claim-panel claim-appeal-panel" aria-labelledby="claim-appeal-title">
    <h2 id="claim-appeal-title"><Scale size={19} aria-hidden="true" />Kháng cáo kết quả xét duyệt</h2>
    {loadError && <div role="alert" className="claim-alert">
      <p>{loadError}</p>
      <button type="button" className="btn btn-ghost" onClick={() => setRefresh(value => value + 1)}>Tải lại trạng thái</button>
    </div>}
    {appeal?.reportId ? <>
      <ReportProgress status={appeal.reportStatus || 'OPEN'} appeal compact />
      <Link className="btn btn-ghost" to={`/bao-cao/${appeal.reportId}`}>Xem kháng cáo</Link>
    </> : appeal && <>
      <p>Một yêu cầu nhận đồ khác đã được chấp thuận. Hồ sơ của bạn vẫn đang chờ.</p>
      {appeal.canAppeal ? <>
        <p>Hạn gửi kháng cáo <strong>{new Date(deadline).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</strong></p>
        <AppealForm key={appeal.approvalId} claimId={claim.id} approvalId={appeal.approvalId}
          onSubmitted={reportId => {
            setAppeal(current => ({ ...current, canAppeal: false, reportId, reportStatus: 'OPEN' }));
            setRefresh(value => value + 1);
          }} />
      </> : <p>Đã hết thời hạn gửi kháng cáo cho lần chấp thuận này.</p>}
    </>}
  </section>;
}

function AppealForm({ claimId, approvalId, onSubmitted }) {
  const [reason, setReason] = useState('');
  const [files, setFiles] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const sending = useRef(false);
  const requestId = useRef(null);
  const { prepare, processing } = useImageUploads();
  const disabled = busy || processing;

  async function pickImages(event) {
    const selected = Array.from(event.target.files);
    event.target.value = '';
    if (selected.length + files.length > 5) {
      setError('Chọn tối đa 5 ảnh bằng chứng.');
      return;
    }
    setError('');
    try {
      const prepared = await prepare(selected);
      if (prepared) {
        requestId.current = null;
        setFiles(current => [...current, ...prepared]);
      }
    } catch (problem) {
      setError(problem.message);
    }
  }

  async function submit(event) {
    event.preventDefault();
    if (sending.current || disabled || !reason.trim()) return;
    sending.current = true;
    setBusy(true);
    setError('');
    requestId.current ||= crypto.randomUUID();
    try {
      const result = await submitClaimAppeal(claimId, {
        approvalId, reason: reason.trim(), clientRequestId: requestId.current,
      }, files);
      onSubmitted(result.reportId);
    } catch (problem) {
      setError(problem.message || 'Chưa gửi được kháng cáo. Vui lòng thử lại.');
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return <details className="claim-appeal-disclosure">
    <summary>Gửi kháng cáo</summary>
    <form className="claim-form" onSubmit={submit}>
      <label>Lý do và căn cứ sở hữu *
        <textarea required maxLength={4500} disabled={disabled} value={reason}
          onChange={event => { setReason(event.target.value); requestId.current = null; }}
          placeholder="Nêu đặc điểm hoặc bằng chứng cho thấy món đồ là của bạn." />
      </label>
      <div className="claim-appeal-images">
        <ImageUploadButton label="Thêm ảnh bằng chứng" disabled={disabled || files.length >= 5} onChange={pickImages} />
        <small>{files.length}/5 ảnh · Tối đa 5 MB mỗi ảnh</small>
      </div>
      <ImageProcessingStatus active={processing} />
      {files.length > 0 && <ul className="claim-appeal-file-list">{files.map((file, index) =>
        <li key={`${file.name}:${index}`}>
          <span>{file.name}</span>
          <button type="button" className="btn btn-ghost" disabled={disabled} aria-label={`Bỏ ảnh ${file.name}`}
            onClick={() => { setFiles(current => current.filter((_, item) => item !== index)); requestId.current = null; }}>Bỏ ảnh</button>
        </li>,
      )}</ul>}
      {error && <p className="claim-alert" role="alert">{error}</p>}
      <div className="claim-actions">
        <button type="submit" className="btn btn-accent" disabled={disabled || !reason.trim()}>{busy ? 'Đang gửi…' : 'Gửi cho quản lý'}</button>
      </div>
    </form>
  </details>;
}
