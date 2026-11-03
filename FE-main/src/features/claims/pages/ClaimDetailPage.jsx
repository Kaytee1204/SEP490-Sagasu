import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getClaim, getClaimOnPost, reviewClaim, cancelClaim } from '../api/claimApi';
import { CLAIM_STATUS, CLAIM_ACTION, claimDate, evidenceUrl, validClaimId, claimDetailPath } from '../claimUtils';
import ClaimEvidencePreview from '../components/ClaimEvidencePreview';
import ClaimAnswerSummary from '../components/ClaimAnswerSummary';
import OpenChatButton from '@/features/chat/components/OpenChatButton';
import '../Claims.css';

export default function ClaimDetailPage() {
  const { postId, claimId } = useParams();
  const { user } = useAuth();
  if (!validClaimId(postId) || !validClaimId(claimId)) return <main className="container claims-page"><p role="alert">Mã bài đăng hoặc claim không hợp lệ.</p></main>;
  return <ClaimDetail key={`${user?.id}:${postId}:${claimId}`} postId={postId} id={claimId} />;
}

export function LegacyClaimRedirect() {
  const { id } = useParams();
  const { user } = useAuth();
  if (!validClaimId(id)) return <main className="container claims-page"><p role="alert">Mã claim không hợp lệ.</p></main>;
  return <LegacyClaimLookup key={`${user?.id}:${id}`} id={id} />;
}
function LegacyClaimLookup({ id }) {
  const location = useLocation();
  const [result, setResult] = useState(null);
  useEffect(() => {
    let cancelled = false;
    getClaim(id).then(claim => { if (!cancelled) setResult({ claim }); })
      .catch(error => { if (!cancelled) setResult({ error: error.message }); });
    return () => { cancelled = true; };
  }, [id]);
  if (result?.claim) return <Navigate to={claimDetailPath(result.claim.postId, result.claim.id)} replace state={location.state} />;
  return <main className="container claims-page">
    {result?.error ? <p role="alert">{result.error}</p> : <p role="status">Đang mở hồ sơ claim…</p>}
    <Link to="/claim-cua-toi">Về danh sách claim</Link>
  </main>;
}

function ClaimDetail({ postId, id }) {
  const { user } = useAuth();
  const location = useLocation();
  const [claim, setClaim] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(location.state?.submitted ? 'Đã gửi hồ sơ thành công.' : '');
  const [decision, setDecision] = useState('APPROVED');
  const [reason, setReason] = useState('');
  const [cancelReason, setCancelReason] = useState('');
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [busy, setBusy] = useState(false);
  const [previewImageId, setPreviewImageId] = useState(null);
  const previewImages = (claim?.evidence || []).map(image => ({
    id: image.id, url: evidenceUrl(image.url), name: image.originalName || 'Ảnh bằng chứng',
  })).filter(image => image.url);
  const inFlight = useRef(false);
  useEffect(() => {
    let cancelled = false;
    getClaimOnPost(postId, id).then(data => { if (!cancelled) { setClaim(data); setError(''); } })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [postId, id]);
  async function act(action) {
    if (inFlight.current) return;
    if (action === 'review' && !reason.trim()) { setError('Vui lòng nhập lý do xét duyệt.'); return; }
    inFlight.current = true; setBusy(true); setError(''); setNotice('');
    try {
      if (action === 'review') await reviewClaim(id, { decision, reason: reason.trim(), expectedVersion: claim.version });
      else await cancelClaim(id, { reason: cancelReason.trim() || null, expectedVersion: claim.version });
      setNotice(action === 'review' ? 'Đã lưu kết quả xét duyệt.' : 'Đã hủy claim. Hồ sơ và bằng chứng vẫn được lưu.');
      setClaim(await getClaimOnPost(postId, id)); setReason(''); setCancelReason(''); setConfirmCancel(false);
    } catch (err) { setError(err.message); }
    finally { inFlight.current = false; setBusy(false); }
  }
  return <main className="container claims-page">
    <header className="claims-heading"><h1>Hồ sơ claim #{id}</h1><div className="claim-actions">
      <Link className="btn btn-ghost" to={claim && String(user?.id) === String(claim.claimantId) ? '/claim-cua-toi?view=sent' : '/claim-cua-toi'}>Claim của tôi</Link>
      <Link className="btn btn-ghost" to={`/chi-tiet/${postId}`}>Xem bài đăng</Link></div></header>
    {error && <div role="alert" className="claim-alert">{error}</div>}
    {notice && <div role="status" className="claim-notice">{notice}</div>}
    {!claim ? <p role="status">{error ? 'Bạn có thể xem bài đăng hoặc trở về danh sách claim.' : 'Đang tải hồ sơ…'}</p> : <>
      <section className="claim-panel"><span className={`claim-status claim-status-${claim.status}`}>{CLAIM_STATUS[claim.status]}</span>
        {String(user?.id) !== String(claim.claimantId) && <p><Link to={`/bai-dang/${claim.postId}/claims`}>← Các claim trên bài này</Link></p>}
        <h2 className="claim-prose"><Link to={`/chi-tiet/${claim.postId}`}>{claim.postTitle}</Link></h2>
        <p>Người claim: <strong>{claim.claimantName}</strong> · Người nhặt: <strong>{claim.finderName}</strong></p>
        <p className="claims-muted">Gửi lúc {claimDate(claim.createdAt)}</p>
        {claim.status === 'APPROVED' && <p className="claim-notice">Người nhặt đã chấp thuận yêu cầu nhận đồ. Đây chưa phải xác nhận đã bàn giao hoặc đã nhận đồ.</p>}
        <h3>Căn cứ nhận đồ</h3><p className="claim-prose">{claim.description}</p>
        {claim.evidenceNote && <><h3>Ghi chú bằng chứng</h3><p className="claim-prose">{claim.evidenceNote}</p></>}
        <h3>Ảnh bằng chứng</h3><div className="claim-evidence">{claim.evidence.map(image => {
          const url = evidenceUrl(image.url);
          const name = image.originalName || 'Ảnh bằng chứng';
          return <div key={image.id}>{url ? <button type="button" className="claim-evidence-thumbnail" aria-label={`Xem ảnh ${name}`}
            onClick={() => setPreviewImageId(image.id)}><img src={url} alt={name} /></button> : <p>Ảnh tạm thời chưa sẵn sàng.</p>}
            <small>{image.originalName || 'Ảnh bằng chứng'}</small></div>;
        })}</div>{!claim.evidence.length && <p className="claims-muted">Hồ sơ không đính kèm ảnh.</p>}
      </section>
      {claim.canOpenChat && <OpenChatButton postId={claim.postId} />}
      <ClaimAnswerSummary answers={claim.answers} />
      {claim.canReview && <form className="claim-panel claim-form" onSubmit={event => { event.preventDefault(); act('review'); }}>
        <h2>Xét duyệt hồ sơ</h2><label>Kết quả<select disabled={busy} value={decision} onChange={event => setDecision(event.target.value)}>
          <option value="APPROVED">Chấp thuận</option><option value="REJECTED">Từ chối — căn cứ chưa phù hợp hoặc chưa đủ</option></select></label>
        <label>Lý do xét duyệt *<textarea required disabled={busy} maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>
        <button type="submit" className="btn btn-accent" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu kết quả xét duyệt'}</button>
        <Link to={`/bai-dang/${claim.postId}/claims`}>Xem các claim trên bài này</Link>
      </form>}
      {claim.canCancel && <section className="claim-panel"><h2>Hủy yêu cầu nhận đồ</h2><p>Chỉ hủy được trước khi bàn giao. Lịch sử và bằng chứng của hồ sơ vẫn được giữ lại.</p>
        {!confirmCancel ? <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => setConfirmCancel(true)}>Hủy claim</button>
          : <div className="claim-form"><p>Bạn xác nhận hủy claim này?</p><label>Lý do hủy (không bắt buộc)<textarea disabled={busy} maxLength={2000} value={cancelReason} onChange={event => setCancelReason(event.target.value)} /></label>
            <div className="claim-actions"><button type="button" className="btn btn-primary" disabled={busy} onClick={() => act('cancel')}>Xác nhận hủy</button>
              <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => setConfirmCancel(false)}>Giữ claim</button></div></div>}
      </section>}
      {claim.canRetry && <section className="claim-panel"><h2>Gửi hồ sơ mới</h2><p>Bạn có thể tạo claim mới. Hồ sơ này giữ nguyên trạng thái {claim.status === 'CANCELLED' ? 'đã hủy' : 'bị từ chối'} và lịch sử xử lý.</p>
        <Link className="btn btn-accent" to={`/xac-minh/${claim.postId}?retryOf=${claim.id}`}>Tạo claim mới</Link></section>}
      <section className="claim-panel"><h2>Lịch sử xử lý</h2><ol className="claim-timeline">{claim.activities.map(event => <li key={event.id}>
        <strong>{CLAIM_ACTION[event.action] || event.action}</strong> · {event.actorName}
        <p className="claims-muted">{claimDate(event.createdAt)}</p>{event.reason && <p className="claim-prose">{event.reason}</p>}
      </li>)}</ol></section>
    </>}
    {previewImageId !== null && <ClaimEvidencePreview images={previewImages}
      initialIndex={previewImages.findIndex(image => image.id === previewImageId)} onClose={() => setPreviewImageId(null)} />}
  </main>;
}
