import '@/features/workspace/Workspace.css';
import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { ArrowUpRight, Building2, ChevronDown, Clock3, Filter, History, Inbox, Send, UserRound, UsersRound, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import { getGroupedClaims, getOfficeClaims } from '../api/claimApi';
import { CLAIM_STATUS, claimDate, validClaimId, claimDetailPath, evidenceUrl } from '../claimUtils';
import '../Claims.css';
import './MyClaimsPage.css';

const HISTORY_STATUSES = ['COMPLETED', 'REJECTED', 'CANCELLED', 'CLOSED'];

export default function MyClaimsPage({ embedded = false }) {
  const { user, isApprover } = useAuth();
  const [params] = useSearchParams();
  const initialView = params.get('view') === 'sent' ? 'sent' : params.get('view') === 'office' && isApprover ? 'office' : 'received';
  return <ClaimList key={`${user?.id}:${initialView}:${params.get("postId") || ""}`} initialView={initialView} embedded={embedded} />;
}
export function PostClaimsPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  if (!validClaimId(id)) return <main className="container claims-page"><p role="alert">Mã bài đăng không hợp lệ.</p></main>;
  const target = new URLSearchParams({ view: 'received', postId: id, claimStatus: params.get('claimStatus') || 'ALL' });
  return <Navigate replace to={`/claim-cua-toi?${target}`} />;
}
function ClaimList({ embedded = false, initialView = 'received' }) {
  const { isApprover } = useAuth();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const postId = validClaimId(searchParams.get('postId')) ? searchParams.get('postId') : null;
  const [expandedGroups, setExpandedGroups] = useState(() => new Set((searchParams.get('expanded') || '').split(',').filter(Boolean)));
  const [view, setView] = useState(() => {
    const urlView = searchParams.get('view');
    if (urlView === 'sent' || (urlView === 'office' && isApprover) || urlView === 'received') return urlView;
    return initialView;
  });
  const [page, setPage] = useState(() => {
    const urlPage = searchParams.get('claimPage');
    if (urlPage && /^[1-9]\d*$/.test(urlPage)) return Number(urlPage);
    return 1;
  });
  const [status, setStatus] = useState(() => {
    const urlStatus = searchParams.get('claimStatus');
    if (urlStatus && (urlStatus === 'ALL' || urlStatus === 'HISTORY' || CLAIM_STATUS[urlStatus])) return urlStatus;
    return 'ALL';
  });
  const [attempt, setAttempt] = useState(0);
  const [snapshot, setSnapshot] = useState({ key: '', result: null, error: '' });

  const updateClaimState = (nextView, nextPage, nextStatus) => {
    setView(nextView);
    setPage(nextPage);
    setStatus(nextStatus);
    const updated = new URLSearchParams(searchParams);
    if (nextView !== view) { updated.delete('postId'); updated.delete('expanded'); }
    if (nextView === 'received') updated.delete('view');
    else updated.set('view', nextView);
    if (nextPage <= 1) updated.delete('claimPage');
    else updated.set('claimPage', String(nextPage));
    if (nextStatus === 'ALL') updated.delete('claimStatus');
    else updated.set('claimStatus', nextStatus);
    setSearchParams(updated, { replace: true });
  };
  const incoming = view !== 'sent';
  const grouped = view !== 'office';
  const key = `${postId}:${view}:${page}:${status}:${attempt}`;
  useEffect(() => {
    let cancelled = false;
    const request = view === 'office' ? getOfficeClaims(page, status) : getGroupedClaims(page, status, !incoming, postId);
    request.then(result => { if (!cancelled) setSnapshot({ key, result, error: '' }); })
      .catch(error => { if (!cancelled) setSnapshot({ key, result: null, error: error.message }); });
    return () => { cancelled = true; };
  }, [postId, incoming, view, page, status, key]);
  const loading = snapshot.key !== key;
  const historySelected = status === 'HISTORY' || HISTORY_STATUSES.includes(status);
  const activeFilter = historySelected ? 'HISTORY' : status;
  const Wrapper = embedded ? 'section' : 'main';
  const title = 'Yêu cầu nhận đồ';
  const allPostsParams = new URLSearchParams(searchParams);
  ['postId', 'expanded', 'claimPage'].forEach(key => allPostsParams.delete(key));
  const allPostsPath = `/claim-cua-toi${allPostsParams.size ? `?${allPostsParams}` : ''}`;
  const views = [
    ['received', 'Người khác yêu cầu', UsersRound],
    ['sent', 'Tôi yêu cầu', Send],
    ...(isApprover ? [['office', 'Yêu cầu gửi phòng ban', Building2]] : []),
  ];
  return <Wrapper className={`${embedded ? '' : 'container'} claims-page claim-list-page ${embedded ? 'claims-embedded' : ''}`} aria-label={title}>

    {!embedded && <header className="claim-list-heading"><h1>{title}</h1>
      <p>{view === 'office' ? 'Đối chiếu và xử lý yêu cầu nhận đồ gửi cho phòng ban.' : incoming ? 'Xem yêu cầu theo từng món đồ và chọn hồ sơ cần xử lý.' : 'Theo dõi kết quả xét duyệt và việc nhận lại đồ của bạn.'}</p></header>}
    {postId && <div className="claim-list-context"><span><Filter size={16} aria-hidden="true" />Đang lọc theo bài đăng</span>
      <Link to={`/chi-tiet/${postId}`}>Xem bài đăng<ArrowUpRight size={15} aria-hidden="true" /></Link>
      <Link className="claim-list-clear-context" to={allPostsPath}><X size={15} aria-hidden="true" />Bỏ lọc bài đăng</Link></div>}
    <section className="claim-list-controls" aria-label="Chọn danh sách và trạng thái yêu cầu">
      <div className="claim-list-navigation">
        <div className="claim-list-tabs" role="group" aria-label="Loại yêu cầu nhận đồ">
          {views.map(([value, label, Icon]) => <button type="button" key={value} aria-pressed={view === value}
            className={view === value ? 'active' : ''} onClick={() => updateClaimState(value, 1, 'ALL')}><Icon size={18} aria-hidden="true" />{label}</button>)}
        </div>
        <Link className="claim-list-office-link" to="/van-phong/claims" state={{ officeClaimReturn: location.pathname + location.search }}><Building2 size={17} aria-hidden="true" />Nhận đồ tại văn phòng<ArrowUpRight size={15} aria-hidden="true" /></Link>
      </div>
      <div className="claim-list-filter-row">
      <div className="claim-list-status-group"><span className="claim-list-filter-label">Trạng thái</span><div className="claim-list-status-filters" role="group" aria-label="Lọc trạng thái yêu cầu">
      {Object.entries({ ALL: 'Tất cả', PENDING: incoming ? 'Chờ bạn duyệt' : 'Chờ phản hồi', APPROVED: 'Đang nhận đồ', HISTORY: 'Lịch sử' }).map(([value, label]) => <button key={value} type="button"
        className={activeFilter === value ? 'active' : ''} aria-pressed={activeFilter === value}
        onClick={() => updateClaimState(view, 1, value)}>{label}</button>)}
      </div></div>
    {historySelected && <div className="claim-list-history-filter">
      <label htmlFor="claim-history-status">Trạng thái trong lịch sử</label>
      <select id="claim-history-status" value={status} onChange={event => updateClaimState(view, 1, event.target.value)}>
        <option value="HISTORY">Tất cả lịch sử</option>
        {HISTORY_STATUSES.map(value => <option key={value} value={value}>{CLAIM_STATUS[value]}</option>)}
      </select>
    </div>}
      </div>
    </section>
    {loading ? <div className="claim-list-loading" role="status"><Inbox size={24} aria-hidden="true" />Đang tải yêu cầu nhận đồ…</div> : snapshot.error ? <div className="claim-alert" role="alert"><p>{snapshot.error}</p>
      <button className="btn btn-ghost" type="button" onClick={() => setAttempt(n => n + 1)}>Thử lại</button></div> : <>
      <header className="claim-list-results-heading"><h2>{grouped ? incoming ? 'Yêu cầu theo món đồ' : 'Những món đồ bạn đã yêu cầu' : 'Hồ sơ gửi phòng ban'}</h2>
        <span role="status">{snapshot.result.totalElements} {grouped ? 'món đồ' : 'hồ sơ'}</span></header>
      {snapshot.result.content.length ? <div className="claim-list-results">{(grouped
        ? snapshot.result.content.map(group => <ClaimPostGroup key={`${group.postId}:${status}`} group={group} incoming={incoming} status={status}
          expanded={expandedGroups.has(String(group.postId)) || (postId === String(group.postId) && !searchParams.has('expanded')) || (!incoming && !searchParams.has('expanded'))}
          onToggle={value => {
            const next = new Set(snapshot.result.content.filter(g => expandedGroups.has(String(g.postId)) || (!searchParams.has('expanded') && (!incoming || postId === String(g.postId)))).map(g => String(g.postId)));
            if (value) next.add(String(group.postId)); else next.delete(String(group.postId));
            setExpandedGroups(next);
            const updated = new URLSearchParams(searchParams); updated.set('expanded', [...next].join(','));
            setSearchParams(updated, { replace: true });
          }} />)
        : <ClaimRows claims={snapshot.result.content} showPost={!postId} incoming={incoming} collapseHistory={Boolean(postId) && status === 'ALL'} />)}</div> : <div className="claim-list-empty"><Inbox size={32} aria-hidden="true" /><h3>{status !== 'ALL' ? 'Không có yêu cầu phù hợp'
        : view === 'office' ? 'Chưa có yêu cầu gửi cho phòng ban' : incoming ? 'Chưa có yêu cầu nhận đồ' : 'Bạn chưa gửi yêu cầu nhận đồ'}</h3>
        <p>{status !== 'ALL' ? 'Thử chọn trạng thái khác để xem các hồ sơ còn lại.' : incoming ? 'Yêu cầu của người khác sẽ được nhóm theo món đồ bạn đăng.' : 'Các yêu cầu bạn gửi sẽ được theo dõi tại đây.'}</p>
        {page > 1 ? <button type="button" className="btn btn-ghost" onClick={() => updateClaimState(view, 1, status)}>Về trang đầu</button>
          : status !== 'ALL' && <button type="button" className="btn btn-ghost" onClick={() => updateClaimState(view, 1, 'ALL')}>Xem tất cả trạng thái</button>}</div>}
      {snapshot.result.totalPages > 1 && <Pagination className="claim-list-pagination" currentPage={page} totalPages={snapshot.result.totalPages} onPageChange={nextPage => updateClaimState(view, nextPage, status)} />}
    </>}
  </Wrapper>;
}

function ClaimPostThumbnail({ url, title }) {
  const [failed, setFailed] = useState(false);
  const source = evidenceUrl(url);
  return <span className="claim-post-thumbnail">
    {source && !failed ? <img src={source} alt={`Ảnh bài đăng: ${title}`} loading="lazy" onError={() => setFailed(true)} />
      : <span>{failed ? 'Không tải được ảnh' : 'Chưa có ảnh'}</span>}
  </span>;
}

function ClaimRow({ claim, incoming, showPost = false }) {
  const location = useLocation();
  const actionLabel = incoming && claim.status === 'PENDING' ? 'Xem căn cứ & xét duyệt' : claim.status === 'APPROVED' ? 'Theo dõi nhận đồ' : 'Xem hồ sơ';
  return <Link className={`claim-group-row ${claim.status === 'PENDING' ? 'is-pending' : ''}`} to={claimDetailPath(claim.postId, claim.id)} state={{ claimListReturn: location.pathname + location.search }}>
    {showPost ? <ClaimPostThumbnail url={claim.postThumbnailUrl} title={claim.postTitle} /> : <span className="claim-row-avatar" aria-hidden="true"><UserRound size={21} /></span>}
    <div className="claim-row-content">
    {showPost && <strong className="claim-row-post-title">{claim.postTitle}</strong>}
    <div className="claim-group-row-heading"><strong>{incoming ? claim.claimantName : claim.claimType === 'OFFICE' ? 'Phòng ban tiếp nhận' : `Người nhặt: ${claim.finderName}`}</strong>
      <span className={`claim-status claim-status-${claim.status}`}>{claim.status === 'PENDING' && claim.claimType === 'P2P' ? (incoming ? 'Chờ bạn xét duyệt' : 'Chờ người nhặt phản hồi') : CLAIM_STATUS[claim.status]}</span></div>
    {claim.descriptionPreview && <p className="workflow-preview">{claim.descriptionPreview}</p>}
    {claim.closeReason === 'ITEM_RETURNED' && <p className="claims-muted">Đã đóng vì bài được trao trả.</p>}
    <p className="claim-row-date"><Clock3 size={14} aria-hidden="true" /><time dateTime={claim.createdAt}>{claimDate(claim.createdAt)}</time></p>
    </div>
    <span className="claim-row-action">{actionLabel}<ArrowUpRight size={16} aria-hidden="true" /></span>
  </Link>;
}

function ClaimRows({ claims, incoming, collapseHistory = true, latestOnly = false, showPost = false }) {
  const sorted = [...claims].sort((a, b) => (Date.parse(b.createdAt) - Date.parse(a.createdAt)) || b.id - a.id);
  const active = latestOnly ? sorted.slice(0, 1) : sorted.filter(c => !HISTORY_STATUSES.includes(c.status))
    .sort((a, b) => Number(b.status === 'PENDING') - Number(a.status === 'PENDING'));
  const activeIds = new Set(active.map(c => c.id));
  const history = sorted.filter(c => !activeIds.has(c.id));
  if (!collapseHistory) return <div className="claim-row-list">{sorted.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}</div>;
  return <div className="claim-row-list">{active.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
    {history.length > 0 && <details className="claim-group-history"><summary><History size={17} aria-hidden="true" /><span>Lịch sử yêu cầu</span><small>{history.length} hồ sơ</small><ChevronDown size={17} aria-hidden="true" /></summary>
      <div className="claim-row-list">
      {history.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
      </div>
    </details>}
  </div>;
}

function ClaimPostGroup({ group, incoming, status, expanded, onToggle }) {
  const visible = status === 'ALL' ? group.claims : group.claims.filter(claim => status === 'HISTORY' ? HISTORY_STATUSES.includes(claim.status) : claim.status === status);
  const pendingCount = visible.filter(claim => claim.status === 'PENDING').length;
  const latestClaim = visible[0];
  const listId = `claim-post-group-${group.postId}`;
  return <article className={`claim-panel claim-post-group ${expanded ? 'is-expanded' : ''} ${incoming && pendingCount > 0 ? 'has-pending' : ''}`}>
    <button type="button" className="claim-group-toggle" aria-expanded={expanded} aria-controls={expanded ? listId : undefined} onClick={() => onToggle(!expanded)}>
      <ClaimPostThumbnail url={group.postThumbnailUrl} title={group.postTitle} />
      <span className="claim-group-heading"><strong title={group.postTitle}>{group.postTitle}</strong>
        {incoming && pendingCount > 0 && <span className="claim-status claim-status-PENDING">{pendingCount} yêu cầu chờ duyệt</span>}
        {!incoming && latestClaim && <span className={`claim-status claim-status-${latestClaim.status}`}>{latestClaim.status === 'PENDING' ? 'Chờ phản hồi' : CLAIM_STATUS[latestClaim.status]}</span>}
        <span className="claims-muted">{status === 'ALL' && incoming ? `${group.claimantCount} người yêu cầu · ` : ''}{visible.length} {status === 'ALL' ? 'hồ sơ' : 'hồ sơ phù hợp'}</span>
      </span>
      <span className="claim-group-action">{expanded ? 'Thu gọn yêu cầu' : 'Xem yêu cầu'}<ChevronDown size={18} aria-hidden="true" /></span>
    </button>
    {expanded && <div id={listId} className="claim-group-expanded">
      <header className="claim-group-content-heading"><h3>{incoming ? 'Yêu cầu nhận đồ trên bài này' : 'Yêu cầu của bạn'}</h3>
        <Link className="claim-group-post-link" to={`/chi-tiet/${group.postId}`}>Xem bài đăng<ArrowUpRight size={15} aria-hidden="true" /></Link></header>
      <ClaimRows claims={visible} incoming={incoming} collapseHistory={status === 'ALL'} latestOnly={!incoming} />
    </div>}
  </article>;
}
