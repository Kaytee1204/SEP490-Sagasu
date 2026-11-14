import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, ClipboardCheck, XCircle } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { getPostDetail } from '@/features/items/api/postApi';
import { getClaim, getClaimOnPost, reviewClaim, cancelClaim } from '../api/claimApi';
import { evidenceUrl, validClaimId, claimDetailPath, postClaimsPath } from '../claimUtils';
import ClaimEvidencePreview from '../components/ClaimEvidencePreview';
import { ClaimActivityTimeline, ClaimDetailSummary, ClaimParticipantPanel, ClaimStatusPanel, ClaimVerificationPanel } from '../components/ClaimDetailPanels';
import RejectClaimDialog from '../components/RejectClaimDialog';
import CancelClaimDialog from '../components/CancelClaimDialog';
import ApproveClaimDialog from '../components/ApproveClaimDialog';
import ReportButton from '@/features/reports/ReportButton';
import HandoverPanel from '@/features/handover/HandoverPanel';
import { useChat } from '@/features/chat/ChatProvider';
import '../Claims.css';
import './ClaimDetailPage.css';

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
  const {revision}=useChat();
  const { user } = useAuth();
  const location = useLocation();
  const [claim, setClaim] = useState(null);
  const [post, setPost] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState(location.state?.submitted ? 'Đã gửi hồ sơ thành công.' : '');
  const [decision, setDecision] = useState('APPROVED');
  const [reason, setReason] = useState('');
  const [cancelReason, setCancelReason] = useState('');
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [previewImageId, setPreviewImageId] = useState(null);
  const [focusRevision,setFocusRevision]=useState(0);
  useEffect(()=>{
    const refetch=()=>{if(document.visibilityState==='visible')setFocusRevision(value=>value+1);};
    window.addEventListener('focus',refetch);document.addEventListener('visibilitychange',refetch);
    return()=>{window.removeEventListener('focus',refetch);document.removeEventListener('visibilitychange',refetch);};
  },[]);
  const previewImages = (claim?.evidence || []).map(image => ({
    id: image.id, url: evidenceUrl(image.url), name: image.originalName || 'Ảnh bằng chứng',
  })).filter(image => image.url);
  const inFlight = useRef(false);
  useEffect(() => {
    let cancelled = false;
    getPostDetail(postId).then(data => { if (!cancelled) setPost(data); }).catch(() => {});
    return () => { cancelled = true; };
  }, [postId]);
  useEffect(() => {
    let cancelled = false;
    getClaimOnPost(postId, id).then(data => { if (!cancelled) { setClaim(data); setError(''); } })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [postId, id,revision,focusRevision]);
  useEffect(() => {
    if (claim && location.hash === '#handover') document.getElementById('handover')?.scrollIntoView({ block: 'start' });
  }, [claim?.id, location.hash]);
  async function act(action, reviewDecision = decision) {
    if (inFlight.current) return;
    if (action === 'review' && !reason.trim()) { setError('Vui lòng nhập lý do xét duyệt.'); return; }
    inFlight.current = true; setBusy(true); setError(''); setNotice('');
    try {
      if (action === 'review') await reviewClaim(id, { decision: reviewDecision, reason: reason.trim(), expectedVersion: claim.version });
      else await cancelClaim(id, { reason: cancelReason.trim() || null, expectedVersion: claim.version });
      setNotice(action === 'review' ? 'Đã lưu kết quả xét duyệt.' : 'Đã hủy claim. Hồ sơ và bằng chứng vẫn được lưu.');
      setClaim(await getClaimOnPost(postId, id)); setReason(''); setCancelReason(''); setConfirmCancel(false);
    } catch (err) { setError(err.message); }
    finally { inFlight.current = false; setBusy(false); }
  }
  const returnTo = location.state?.claimListReturn;
  const listPath = typeof returnTo === 'string' && (returnTo === '/claim-cua-toi' || returnTo.startsWith('/claim-cua-toi?'))
    ? returnTo : claim?.claimType === 'OFFICE' ? `/claim-cua-toi?view=${String(user?.id) === String(claim.claimantId) ? 'sent' : 'office'}`
      : postClaimsPath(postId, 'ALL', String(user?.id) === String(claim?.claimantId) ? 'sent' : 'received');
  const showHandover = claim?.claimType === 'P2P' && ['APPROVED', 'COMPLETED'].includes(claim.status);
  return <main className="container claims-page claim-detail-page">
    <nav className="claim-detail-navigation" aria-label="Điều hướng yêu cầu nhận đồ">
      <Link to={listPath}><ArrowLeft size={17} aria-hidden="true" />Về danh sách yêu cầu</Link><span>Chi tiết yêu cầu nhận đồ</span>
    </nav>
    {error && <div role="alert" className="claim-alert">{error}</div>}
    {notice && <div role="status" className="claim-notice">{notice}</div>}
    {!claim ? <section className="claim-panel claim-detail-loading" role="status"><ClipboardCheck size={28} aria-hidden="true" /><h1>{error ? 'Chưa mở được yêu cầu' : 'Đang tải yêu cầu nhận đồ…'}</h1>
      {error && <Link className="btn btn-ghost" to={`/chi-tiet/${postId}`}>Xem bài đăng</Link>}</section> : <>
      <ClaimDetailSummary claim={claim} post={post} />
      <div className="claim-detail-layout">
        <div className="claim-detail-main">
          {showHandover ? <HandoverPanel claim={claim} onChanged={()=>getClaimOnPost(postId,id).then(setClaim).catch(e=>setError(e.message))}/> : (!claim.canReview || claim.status !== 'PENDING') && <ClaimStatusPanel claim={claim} />}
          {claim.claimType === 'OFFICE' && <p className="claim-detail-office-note">Món đồ do văn phòng tiếp nhận lưu giữ. Hồ sơ và việc nhận đồ được xử lý qua bên tiếp nhận.</p>}
          <ClaimVerificationPanel claim={claim} onPreviewImage={setPreviewImageId} />
        </div>
        <aside className="claim-detail-sidebar" aria-label="Thông tin và xử lý yêu cầu">
      {claim.canReview && <form className="claim-panel claim-form claim-detail-review" onSubmit={event => { event.preventDefault(); act('review', event.nativeEvent.submitter?.value || decision); }}>
        <h2><ClipboardCheck size={20} aria-hidden="true" />Xét duyệt yêu cầu</h2>
        <p>Đối chiếu căn cứ và ảnh bằng chứng để xác định đúng người nhận.</p>
        {claim.claimType !== 'P2P' && <label>Kết quả<select disabled={busy} value={decision} onChange={event => setDecision(event.target.value)}>
          <option value="APPROVED">Chấp thuận</option><option value="REJECTED">Từ chối — căn cứ chưa phù hợp hoặc chưa đủ</option></select></label>}
        {claim.claimType !== 'P2P' && <label>Lý do xét duyệt *<textarea required disabled={busy} maxLength={2000} value={reason} onChange={event => setReason(event.target.value)} /></label>}
        {claim.claimType === 'P2P' ? <div className="claim-actions">
          <button type="button" onClick={() => setApproveOpen(true)} className="btn btn-accent" disabled={busy}><CheckCircle2 size={18} aria-hidden="true" />{busy ? 'Đang lưu…' : 'Chấp thuận nhận đồ'}</button>
          <button type="button" className="btn btn-ghost claim-detail-reject" disabled={busy} onClick={() => setRejectOpen(true)}><XCircle size={18} aria-hidden="true" />Từ chối yêu cầu</button>
        </div> : <button type="submit" className="btn btn-accent" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu kết quả xét duyệt'}</button>}
      </form>}
      <ClaimParticipantPanel claim={claim} />
      <ClaimActivityTimeline activities={claim.activities} />
      <div className="claim-detail-secondary-actions">
      {claim.canCancel && claim.claimType === 'P2P' && <button className="btn btn-ghost claim-detail-cancel" type="button" disabled={busy} onClick={() => setCancelOpen(true)}><XCircle size={17} aria-hidden="true" />Hủy yêu cầu nhận đồ</button>}
      {claim.canCancel && claim.claimType !== 'P2P' && <section className="claim-detail-office-cancel"><h2>Hủy yêu cầu nhận đồ</h2><p>Chỉ hủy được trước khi bàn giao. Lịch sử và bằng chứng của hồ sơ vẫn được giữ lại.</p>
        {!confirmCancel ? <button className="btn btn-ghost" type="button" disabled={busy} onClick={() => setConfirmCancel(true)}>Hủy yêu cầu nhận đồ</button>
          : <div className="claim-form"><p>Bạn xác nhận hủy claim này?</p><label>Lý do hủy (không bắt buộc)<textarea disabled={busy} maxLength={2000} value={cancelReason} onChange={event => setCancelReason(event.target.value)} /></label>
            <div className="claim-actions"><button type="button" className="btn btn-primary" disabled={busy} onClick={() => act('cancel')}>Xác nhận hủy</button>
              <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => setConfirmCancel(false)}>Giữ claim</button></div></div>}
      </section>}
      <ReportButton type="CLAIM" id={claim.id} targetLabel={claim.postTitle} label="Báo cáo yêu cầu này" />
      </div>
        </aside>
      </div>
    </>}
    {rejectOpen && claim?.canReview && claim.claimType === 'P2P' && <RejectClaimDialog claim={claim}
      onClose={() => setRejectOpen(false)} onRejected={() => {
        setRejectOpen(false); setReason(''); setError(''); setNotice('Đã từ chối yêu cầu nhận đồ.');
        setClaim(current => ({ ...current, status: 'REJECTED', canReview: false }));
        getClaimOnPost(postId, id).then(setClaim).catch(problem => setError(problem.message));
      }} />}
    {cancelOpen && claim?.canCancel && claim.claimType === 'P2P' && <CancelClaimDialog claim={claim}
      onClose={() => setCancelOpen(false)} onCancelled={() => {
        setCancelOpen(false); setError(''); setNotice('Đã hủy yêu cầu nhận đồ. Hồ sơ và bằng chứng vẫn được lưu.');
        setClaim(current => ({ ...current, status: 'CANCELLED', canCancel: false, canOpenChat: false }));
        getClaimOnPost(postId, id).then(setClaim).catch(problem => setError(problem.message));
      }} />}
    {approveOpen && claim?.canReview && claim.claimType === 'P2P' && <ApproveClaimDialog claim={claim}
      onClose={() => setApproveOpen(false)} onApproved={() => {
        setApproveOpen(false); setError(''); setNotice('Đã chấp thuận yêu cầu. Hai bên có thể trao đổi để nhận đồ.');
        setClaim(current => ({ ...current, status: 'APPROVED', canReview: false }));
        getClaimOnPost(postId, id).then(setClaim).catch(problem => setError(problem.message));
      }} />}
    {previewImageId !== null && <ClaimEvidencePreview images={previewImages}
      initialIndex={previewImages.findIndex(image => image.id === previewImageId)} onClose={() => setPreviewImageId(null)} />}
  </main>;
}
