import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import { getMyClaims, getReceivedClaims, getOfficeClaims, getClaimsOnPost } from '../api/claimApi';
import { CLAIM_STATUS, claimDate, validClaimId, claimDetailPath } from '../claimUtils';
import '../Claims.css';

export default function MyClaimsPage({ embedded = false }) {
  const { user, isApprover } = useAuth();
  const [params] = useSearchParams();
  const initialView = params.get('view') === 'sent' ? 'sent' : params.get('view') === 'office' && isApprover ? 'office' : 'received';
  return <ClaimList key={`${user?.id}:${initialView}`} initialView={initialView} embedded={embedded} />;
}
export function PostClaimsPage() {
  const { id } = useParams();
  const { user } = useAuth();
  if (!validClaimId(id)) return <main className="container claims-page"><p role="alert">Mã bài đăng không hợp lệ.</p></main>;
  return <ClaimList key={`${user?.id}:${id}`} postId={id} />;
}
function ClaimList({ postId, embedded = false, initialView = 'received' }) {
  const { isApprover } = useAuth();
  const [view, setView] = useState(initialView);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState('ALL');
  const [attempt, setAttempt] = useState(0);
  const [snapshot, setSnapshot] = useState({ key: '', result: null, error: '' });
  const incoming = Boolean(postId) || view !== 'sent';
  const key = `${postId}:${view}:${page}:${status}:${attempt}`;
  useEffect(() => {
    let cancelled = false;
    const request = postId ? getClaimsOnPost(postId, page, status)
      : view === 'office' ? getOfficeClaims(page, status) : incoming ? getReceivedClaims(page, status) : getMyClaims(page, status);
    request.then(result => { if (!cancelled) setSnapshot({ key, result, error: '' }); })
      .catch(error => { if (!cancelled) setSnapshot({ key, result: null, error: error.message }); });
    return () => { cancelled = true; };
  }, [postId, incoming, view, page, status, key]);
  const loading = snapshot.key !== key;
  const Wrapper = embedded ? 'section' : 'main';
  const title = postId ? `Claim nhận đồ trên bài #${postId}` : 'Claim của tôi';
  return <Wrapper className={`${embedded ? '' : 'container'} claims-page ${embedded ? 'claims-embedded' : ''}`} aria-label={title}>
    <header className="claims-heading"><div>{embedded ? <h2>{title}</h2> : <h1>{title}</h1>}
      <p className="claims-muted">{view === 'office' ? 'Xét duyệt yêu cầu nhận đồ đã được phòng ban tiếp nhận, xác minh.' : incoming ? 'Xem claim trên các bài của bạn và mở hồ sơ để xét duyệt.' : 'Theo dõi những yêu cầu nhận đồ bạn đã gửi.'}</p></div>
      <Link className="btn btn-ghost" to={postId ? `/chi-tiet/${postId}` : '/tim-kiem'}>{postId ? 'Về bài đăng' : 'Tìm đồ thất lạc'}</Link></header>
    {!postId && <div className="claims-filters" role="tablist" aria-label="Loại danh sách claim">
      {[['received', 'Claim P2P trên bài của tôi'], ['sent', 'Tôi đã gửi'], ...(isApprover ? [['office', 'Claim gửi phòng ban']] : [])].map(([value, label]) => <button type="button" key={value}
        role="tab" aria-selected={view === value} className={`chip ${view === value ? 'active' : ''}`}
        onClick={() => { setView(value); setPage(1); setStatus('ALL'); }}>{label}</button>)}
    </div>}
    <div className="claims-filters" aria-label="Lọc trạng thái claim">
      {Object.entries({ ALL: 'Tất cả', ...CLAIM_STATUS }).map(([value, label]) => <button key={value} type="button"
        className={`chip ${status === value ? 'active' : ''}`} aria-pressed={status === value}
        onClick={() => { setStatus(value); setPage(1); }}>{label}</button>)}
    </div>
    {loading ? <p role="status">Đang tải claim…</p> : snapshot.error ? <div className="claim-alert" role="alert"><p>{snapshot.error}</p>
      <button className="btn btn-ghost" type="button" onClick={() => setAttempt(n => n + 1)}>Thử lại</button></div> : <>
      <p className="claims-muted" role="status">{snapshot.result.totalElements} hồ sơ</p>
      {snapshot.result.content.length ? snapshot.result.content.map(claim => <Link className="claim-card" key={claim.id} to={claimDetailPath(claim.postId, claim.id)}>
        <span className={`claim-status claim-status-${claim.status}`}>{CLAIM_STATUS[claim.status]}</span>
        <span className="claims-muted"> · {claim.claimType === 'OFFICE' ? 'Phòng ban tiếp nhận' : 'P2P'}</span>
        <h2>{claim.postTitle}</h2><p>Hồ sơ #{claim.id} · {incoming ? `Người claim: ${claim.claimantName}` : claim.claimType === 'OFFICE' ? 'Xét duyệt bởi phòng ban tiếp nhận' : `Người nhặt: ${claim.finderName}`}</p>
        <p className="claims-muted">Gửi lúc {claimDate(claim.createdAt)}</p><span>{incoming && claim.status === 'PENDING' ? 'Mở hồ sơ để xét duyệt →' : 'Xem hồ sơ →'}</span>
      </Link>) : <div className="claim-panel"><p>{status !== 'ALL' ? 'Không có hồ sơ ở trạng thái này.'
        : view === 'office' ? 'Chưa có claim gửi cho phòng ban.' : incoming ? 'Chưa có claim trên các bài đăng của bạn.' : 'Bạn chưa gửi claim nào.'}</p>
        {page > 1 && <button className="btn btn-ghost" onClick={() => setPage(1)}>Về trang đầu</button>}</div>}
      {snapshot.result.totalPages > 1 && <Pagination currentPage={page} totalPages={snapshot.result.totalPages} onPageChange={setPage} />}
    </>}
  </Wrapper>;
}
