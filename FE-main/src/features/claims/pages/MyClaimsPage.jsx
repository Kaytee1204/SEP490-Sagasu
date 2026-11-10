import '@/features/workspace/Workspace.css';
import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import { getPostDetail } from '@/features/items/api/postApi';
import { getGroupedClaims, getOfficeClaims, getClaimsOnPost } from '../api/claimApi';
import { CLAIM_STATUS, claimDate, validClaimId, claimDetailPath, evidenceUrl } from '../claimUtils';
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
  const { isApprover, user } = useAuth();
  const [post, setPost] = useState(null);
  useEffect(() => {
    if (!postId) return;
    let active = true;
    getPostDetail(postId).then(value => { if (active) setPost(value); }).catch(() => {});
    return () => { active = false; };
  }, [postId]);
  const [searchParams, setSearchParams] = useSearchParams();
  const storageKey = `sagasu_claims_state_${user?.id}_${postId || 'my'}`;

  const readSavedState = () => {
    try {
      const raw = sessionStorage.getItem(storageKey);
      if (!raw) return null;
      return JSON.parse(raw);
    } catch {
      return null;
    }
  };

  const saved = readSavedState();
  const [view, setView] = useState(() => {
    const urlView = searchParams.get('view');
    if (urlView === 'sent' || (urlView === 'office' && isApprover) || urlView === 'received') return urlView;
    if (saved?.view && (saved.view !== 'office' || isApprover)) return saved.view;
    return initialView;
  });
  const [page, setPage] = useState(() => {
    const urlPage = searchParams.get('claimPage');
    if (urlPage && /^[1-9]\d*$/.test(urlPage)) return Number(urlPage);
    return Number.isInteger(saved?.page) && saved.page >= 1 ? saved.page : 1;
  });
  const [status, setStatus] = useState(() => {
    const urlStatus = searchParams.get('claimStatus');
    if (urlStatus && (urlStatus === 'ALL' || CLAIM_STATUS[urlStatus])) return urlStatus;
    return saved?.status && (saved.status === 'ALL' || CLAIM_STATUS[saved.status]) ? saved.status : 'ALL';
  });
  const [attempt, setAttempt] = useState(0);
  const [snapshot, setSnapshot] = useState({ key: '', result: null, error: '' });

  const updateClaimState = (nextView, nextPage, nextStatus) => {
    setView(nextView);
    setPage(nextPage);
    setStatus(nextStatus);
    try {
      sessionStorage.setItem(storageKey, JSON.stringify({ view: nextView, page: nextPage, status: nextStatus }));
    } catch { /* ignore storage errors */ }
    const updated = new URLSearchParams(searchParams);
    if (!postId) {
      if (nextView === 'received') updated.delete('view');
      else updated.set('view', nextView);
    }
    if (nextPage <= 1) updated.delete('claimPage');
    else updated.set('claimPage', String(nextPage));
    if (nextStatus === 'ALL') updated.delete('claimStatus');
    else updated.set('claimStatus', nextStatus);
    setSearchParams(updated, { replace: true });
  };
  const incoming = Boolean(postId) || view !== 'sent';
  const grouped = !postId && view !== 'office';
  const key = `${postId}:${view}:${page}:${status}:${attempt}`;
  useEffect(() => {
    let cancelled = false;
    const request = postId ? getClaimsOnPost(postId, page, status)
      : view === 'office' ? getOfficeClaims(page, status) : getGroupedClaims(page, status, !incoming);
    request.then(result => { if (!cancelled) setSnapshot({ key, result, error: '' }); })
      .catch(error => { if (!cancelled) setSnapshot({ key, result: null, error: error.message }); });
    return () => { cancelled = true; };
  }, [postId, incoming, view, page, status, key]);
  const loading = snapshot.key !== key;
  const Wrapper = embedded ? 'section' : 'main';
  const title = 'Yêu cầu nhận đồ';
  return <Wrapper className={`${embedded ? '' : 'container'} claims-page ${embedded ? 'claims-embedded' : ''}`} aria-label={title}>

    {!embedded && <header className="claims-heading"><div><h1>{title}</h1>
      <p className="claims-muted">{view === 'office' ? 'Xét duyệt yêu cầu nhận đồ đã được phòng ban tiếp nhận, xác minh.' : incoming ? 'Đọc căn cứ để xác định đúng chủ sở hữu. Yêu cầu chờ bạn duyệt được ưu tiên.' : 'Theo dõi những yêu cầu nhận đồ bạn đã gửi.'}</p></div>
      <div className="claims-filters"><Link className="btn btn-ghost" to={postId ? `/chi-tiet/${postId}` : '/tim-kiem'}>{postId ? 'Về bài đăng' : 'Tìm đồ thất lạc'}</Link></div></header>}
    {post && <Link className="workflow-post-context" to={`/chi-tiet/${post.id}`}>
      {post.images?.[0]?.url && <img src={post.images[0].url} alt="" />}
      <span><strong>{post.title}</strong><small>{post.locationText} · Xem bài đăng</small></span>
    </Link>}
    {!postId && <div className="claims-filters" role="tablist" aria-label="Loại danh sách claim">
      {[['received', 'Người khác yêu cầu'], ['sent', 'Tôi yêu cầu'], ...(isApprover ? [['office', 'Claim gửi phòng ban']] : [])].map(([value, label]) => <button type="button" key={value}
        role="tab" aria-selected={view === value} className={`chip ${view === value ? 'active' : ''}`}
        onClick={() => updateClaimState(value, 1, 'ALL')}>{label}</button>)}
    </div>}
    <div className="claims-filters" aria-label="Lọc trạng thái claim">
      {Object.entries({ ALL: 'Tất cả', ...CLAIM_STATUS }).map(([value, label]) => <button key={value} type="button"
        className={`chip ${status === value ? 'active' : ''}`} aria-pressed={status === value}
        onClick={() => updateClaimState(view, 1, value)}>{label}</button>)}
    </div>
    {loading ? <p role="status">Đang tải claim…</p> : snapshot.error ? <div className="claim-alert" role="alert"><p>{snapshot.error}</p>
      <button className="btn btn-ghost" type="button" onClick={() => setAttempt(n => n + 1)}>Thử lại</button></div> : <>
      <p className="claims-muted" role="status">{snapshot.result.totalElements} {grouped ? 'bài đăng' : 'hồ sơ'}</p>
      {snapshot.result.content.length ? (grouped
        ? snapshot.result.content.map(group => <ClaimPostGroup key={`${group.postId}:${status}`} group={group} incoming={incoming} status={status} />)
        : <ClaimRows claims={snapshot.result.content} showPost={!postId} incoming={incoming} collapseHistory={Boolean(postId) && status === 'ALL'} />) : <div className="claim-panel"><p>{status !== 'ALL' ? 'Không có hồ sơ ở trạng thái này.'
        : view === 'office' ? 'Chưa có claim gửi cho phòng ban.' : incoming ? 'Chưa có claim trên các bài đăng của bạn.' : 'Bạn chưa gửi claim nào.'}</p>
        {page > 1 && <button className="btn btn-ghost" onClick={() => updateClaimState(view, 1, status)}>Về trang đầu</button>}</div>}
      {snapshot.result.totalPages > 1 && <Pagination currentPage={page} totalPages={snapshot.result.totalPages} onPageChange={nextPage => updateClaimState(view, nextPage, status)} />}
    </>}
  </Wrapper>;
}

function ClaimPostThumbnail({ url, title }) {
  const [failed, setFailed] = useState(false);
  const source = evidenceUrl(url);
  return <div className="claim-post-thumbnail">
    {source && !failed ? <img src={source} alt={`Ảnh bài đăng: ${title}`} loading="lazy" onError={() => setFailed(true)} />
      : <span>{failed ? 'Không tải được ảnh' : 'Chưa có ảnh'}</span>}
  </div>;
}

function ClaimRow({ claim, incoming, showPost = false }) {
  return <Link className="claim-group-row" to={claimDetailPath(claim.postId, claim.id)}>
    {showPost && <div className="workflow-post-context"><ClaimPostThumbnail url={claim.postThumbnailUrl} title={claim.postTitle} /><strong>{claim.postTitle}</strong></div>}
    <div className="claim-group-row-heading"><strong>{incoming ? claim.claimantName : claim.claimType === 'OFFICE' ? 'Phòng ban tiếp nhận' : `Người nhặt: ${claim.finderName}`}</strong>
      <span className={`claim-status claim-status-${claim.status}`}>{claim.status === 'PENDING' && claim.claimType === 'P2P' ? (incoming ? 'Chờ bạn xét duyệt' : 'Chờ người nhặt phản hồi') : CLAIM_STATUS[claim.status]}</span></div>
    {claim.descriptionPreview && <p className="workflow-preview">{claim.descriptionPreview}</p>}
    {claim.closeReason === 'ITEM_RETURNED' && <p className="claims-muted">Đã đóng vì bài được trao trả.</p>}
    <p className="claims-muted">Gửi lúc {claimDate(claim.createdAt)}</p>
    <span>{incoming && claim.status === 'PENDING' ? 'Xem căn cứ & xét duyệt →' : 'Xem hồ sơ →'}</span>
  </Link>;
}

function ClaimRows({ claims, incoming, collapseHistory = true, latestOnly = false, showPost = false }) {
  const sorted = [...claims].sort((a, b) => (Date.parse(b.createdAt) - Date.parse(a.createdAt)) || b.id - a.id);
  const active = latestOnly ? sorted.slice(0, 1) : sorted.filter(c => !['CANCELLED', 'REJECTED', 'CLOSED'].includes(c.status))
    .sort((a, b) => Number(b.status === 'PENDING') - Number(a.status === 'PENDING'));
  const activeIds = new Set(active.map(c => c.id));
  const history = sorted.filter(c => !activeIds.has(c.id));
  if (!collapseHistory) return <div>{sorted.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}</div>;
  return <div>{active.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
    {history.length > 0 && <details className="claim-group-history"><summary>Lịch sử yêu cầu · {history.length} hồ sơ</summary>
      {history.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
    </details>}
  </div>;
}

function ClaimPostGroup({ group, incoming, status }) {
  const [expanded, setExpanded] = useState(!incoming);
  const visible = status === 'ALL' ? group.claims : group.claims.filter(claim => claim.status === status);
  const listId = `claim-post-group-${group.postId}`;
  return <article className="claim-panel claim-post-group">
    <button type="button" className="claim-group-toggle" aria-expanded={expanded} aria-controls={listId} onClick={() => setExpanded(value => !value)}>
      <ClaimPostThumbnail url={group.postThumbnailUrl} title={group.postTitle} />
      <span className="claim-group-heading"><strong>{group.postTitle}</strong>
        {incoming && group.pendingCount > 0 && <span className="claim-status claim-status-PENDING">{group.pendingCount} yêu cầu chờ duyệt</span>}
        <span className="claims-muted">{incoming ? `${group.claimantCount} người yêu cầu · ` : ''}{group.claims.length} hồ sơ</span>
        <span>{expanded ? 'Thu gọn ↑' : 'Xem các yêu cầu ↓'}</span>
      </span>
    </button>
    {expanded && <div id={listId} className="claim-group-expanded">
      <Link className="claim-group-post-link" to={`/chi-tiet/${group.postId}`}>Xem bài đăng →</Link>
      <ClaimRows claims={visible} incoming={incoming} collapseHistory={status === 'ALL'} latestOnly={!incoming} />
    </div>}
  </article>;
}
