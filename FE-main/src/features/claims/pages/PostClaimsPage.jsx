import '@/styles/Workspace.css';
import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, ArrowUpRight, Clock3, Inbox, LayoutList, MapPin, Tag, TriangleAlert, UsersRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { getPostDetail } from '@/features/items/api/postApi';
import { getGroupedClaims } from '../api/claimApi';
import {
  CLAIM_STATUS,
  HISTORY_STATUSES,
  TIME_RANGE_OPTIONS,
  claimDate,
  claimDetailPath,
  claimDisplayStatus,
  claimShortDate,
  claimShortTime,
  matchesClaimStatus,
  matchesTimeFilter,
  rejectedAfterDispute,
  validClaimId,
} from '../utils/claimUtils';
import { ClaimPostThumbnail } from '../components/ClaimListShared';
import ClaimTimeFilter from '../components/ClaimTimeFilter';
import '../styles/Claims.css';
import './MyClaimsPage.css';
import './PostClaimsPage.css';

const POST_STATUS_LABELS = {
  OPEN: 'Đang mở',
  RESERVED: 'Đang giữ để trao trả',
  RETURNED: 'Đã trao trả',
  CLOSED: 'Đã đóng',
};

const VALID_TIME_RANGES = new Set(TIME_RANGE_OPTIONS.map(o => o.value));

function initialsOf(name) {
  const parts = String(name || 'U').trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
  return String(name || 'U').slice(0, 2).toUpperCase();
}

export default function PostClaimsPage() {
  const { id } = useParams();
  const { user } = useAuth();
  if (!validClaimId(id)) {
    return (
      <main className="container claims-page">
        <p role="alert">Mã bài đăng không hợp lệ.</p>
      </main>
    );
  }
  return <PostClaimsContent key={`${user?.id}:${id}`} postId={id} />;
}

function PostClaimsContent({ postId }) {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  const [status, setStatus] = useState(() => {
    const urlStatus = searchParams.get('claimStatus');
    if (urlStatus && (urlStatus === 'ALL' || urlStatus === 'HISTORY' || CLAIM_STATUS[urlStatus])) return urlStatus;
    return 'ALL';
  });
  const [timeRange, setTimeRange] = useState(() => {
    const urlRange = searchParams.get('timeRange');
    return urlRange && VALID_TIME_RANGES.has(urlRange) ? urlRange : 'ALL';
  });
  const [fromDate, setFromDate] = useState(() => searchParams.get('fromDate') || '');
  const [toDate, setToDate] = useState(() => searchParams.get('toDate') || '');
  const [sortOrder, setSortOrder] = useState(() => (searchParams.get('sort') === 'OLDEST' ? 'OLDEST' : 'NEWEST'));
  const [attempt, setAttempt] = useState(0);
  const [post, setPost] = useState(null);
  const [snapshot, setSnapshot] = useState({ key: '', group: null, error: '' });

  const fromList = location.state?.fromClaimsList;
  const revokedClaimId = location.state?.revokedClaimId;
  const backPath = typeof fromList === 'string' && (fromList === '/claim-cua-toi' || fromList.startsWith('/claim-cua-toi?'))
    ? fromList
    : '/claim-cua-toi';

  useEffect(() => {
    let cancelled = false;
    getPostDetail(postId)
      .then(data => { if (!cancelled) setPost(data); })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [postId]);

  const key = `${postId}:${attempt}`;
  useEffect(() => {
    let cancelled = false;
    getGroupedClaims(1, 'ALL', false, postId, 100)
      .then(result => {
        if (!cancelled) {
          const matched = (result?.content || []).find(g => String(g.postId) === String(postId)) || null;
          setSnapshot({ key, group: matched, error: '' });
        }
      })
      .catch(error => {
        if (!cancelled) setSnapshot({ key, group: null, error: error.message || 'Không tải được danh sách yêu cầu.' });
      });
    return () => { cancelled = true; };
  }, [postId, key]);

  const syncParams = next => {
    const nextStatus = next.status ?? status;
    const nextRange = next.timeRange ?? timeRange;
    const nextFrom = next.fromDate !== undefined ? next.fromDate : fromDate;
    const nextTo = next.toDate !== undefined ? next.toDate : toDate;
    const nextSort = next.sortOrder ?? sortOrder;

    setStatus(nextStatus);
    setTimeRange(nextRange);
    setFromDate(nextFrom);
    setToDate(nextTo);
    setSortOrder(nextSort);

    const updated = new URLSearchParams(searchParams);
    if (nextStatus !== status) updated.delete(`reviewPage_${postId}`);
    if (nextStatus === 'ALL') updated.delete('claimStatus');
    else updated.set('claimStatus', nextStatus);

    if (nextRange === 'ALL') {
      updated.delete('timeRange');
      updated.delete('fromDate');
      updated.delete('toDate');
    } else {
      updated.set('timeRange', nextRange);
      if (nextRange === 'CUSTOM' && nextFrom) updated.set('fromDate', nextFrom);
      else updated.delete('fromDate');
      if (nextRange === 'CUSTOM' && nextTo) updated.set('toDate', nextTo);
      else updated.delete('toDate');
    }

    if (nextSort === 'NEWEST') updated.delete('sort');
    else updated.set('sort', nextSort);

    updated.delete('mode');

    setSearchParams(updated, { replace: true });
  };

  const loading = snapshot.key !== key;
  const group = snapshot.group;

  const { allClaims, visibleClaims, pendingCount, approvedCount, latestClaim, statusCounts } = useMemo(() => {
    const raw = group?.claims || [];
    const byTimeAll = timeRange === 'ALL'
      ? raw
      : raw.filter(c => matchesTimeFilter(c.createdAt, timeRange, fromDate, toDate));
    const counts = Object.fromEntries(['ALL', 'PENDING', 'APPROVED', 'HISTORY']
      .map(value => [value, byTimeAll.filter(c => matchesClaimStatus(c, value, true)).length]));
    const byStatus = byTimeAll.filter(c => matchesClaimStatus(c, status, true));
    const sorted = [...byStatus].sort((a, b) => {
      const diff = (Date.parse(b.createdAt) - Date.parse(a.createdAt)) || (b.id - a.id);
      return sortOrder === 'OLDEST' ? -diff : diff;
    });
    return {
      allClaims: raw,
      visibleClaims: sorted,
      pendingCount: raw.filter(c => matchesClaimStatus(c, 'PENDING', true)).length,
      approvedCount: raw.filter(c => c.status === 'APPROVED').length,
      latestClaim: raw[0] || null,
      statusCounts: counts,
    };
  }, [group, status, timeRange, fromDate, toDate, sortOrder]);

  const historySelected = status === 'HISTORY' || HISTORY_STATUSES.includes(status);
  const activeFilter = historySelected ? 'HISTORY' : status;
  const hasAnyFilter = status !== 'ALL' || timeRange !== 'ALL' || sortOrder !== 'NEWEST';
  const onlyArchived = status === 'ALL' && statusCounts.ALL === 0 && statusCounts.HISTORY > 0;

  const displayTitle = group?.postTitle || post?.title || 'Chi tiết bài đăng';
  const displayThumb = group?.postThumbnailUrl || post?.images?.[0]?.url || post?.images?.[0]?.imageUrl || null;
  const postStatusLabel = post?.status ? (POST_STATUS_LABELS[post.status] || post.status) : null;

  return (
    <main className="container claims-page claim-list-page post-claims-page" aria-label={`Yêu cầu nhận đồ cho ${displayTitle}`}>
      <nav className="post-claims-nav" aria-label="Điều hướng yêu cầu nhận đồ">
        <Link className="post-claims-back" to={backPath}>
          <ArrowLeft size={17} aria-hidden="true" />
          Về danh sách yêu cầu nhận đồ
        </Link>
        <Link className="post-claims-origin-link" to={`/chi-tiet/${postId}`}>
          Xem bài đăng gốc
          <ArrowUpRight size={16} aria-hidden="true" />
        </Link>
      </nav>

      <section className="post-claims-hero" aria-label="Thông tin món đồ">
        <ClaimPostThumbnail url={displayThumb} title={displayTitle} />
        <div className="post-claims-hero-body">
          <div className="post-claims-hero-top">
            <span className="post-claims-id-tag">Mã bài #{postId}</span>
            {postStatusLabel && <span className={`post-claims-post-status status-${post?.status}`}>{postStatusLabel}</span>}
          </div>
          <h1>{displayTitle}</h1>
          <div className="post-claims-hero-meta">
            {post?.categoryName && (
              <span><Tag size={14} aria-hidden="true" />{post.categoryName}</span>
            )}
            {post?.locationText && (
              <span><MapPin size={14} aria-hidden="true" />{post.locationText}</span>
            )}
            {latestClaim?.createdAt && (
              <span><Clock3 size={14} aria-hidden="true" />Mới nhất: {claimDate(latestClaim.createdAt)}</span>
            )}
          </div>
        </div>
        {group && (
          <div className="post-claims-stats" aria-label="Tổng quan hồ sơ nhận đồ">
            <div className="post-claims-stat">
              <strong>{group.claimantCount}</strong>
              <span><UsersRound size={13} aria-hidden="true" />Người gửi</span>
            </div>
            <div className={`post-claims-stat ${pendingCount > 0 ? 'is-highlight' : ''}`}>
              <strong>{pendingCount}</strong>
              <span>Chờ duyệt</span>
            </div>
            {approvedCount > 0 && (
              <div className="post-claims-stat is-approved">
                <strong>{approvedCount}</strong>
                <span>Đang nhận đồ</span>
              </div>
            )}
            <div className="post-claims-stat">
              <strong>{allClaims.length}</strong>
              <span>Tổng hồ sơ</span>
            </div>
          </div>
        )}
      </section>

      {validClaimId(revokedClaimId) && <p className="claim-notice" role="status">
        Đã thu hồi chấp thuận. Hồ sơ #{revokedClaimId} đã về chờ xét duyệt.
      </p>}
      <section className="claim-list-controls post-claims-controls" aria-label="Lọc và chế độ hiển thị hồ sơ">
        <div className="post-claims-toolbar-top">
          <div className="claim-list-tabs" role="group" aria-label="Danh sách yêu cầu">
            <span className="active" style={{ display: 'inline-flex', alignItems: 'center', gap: 7, padding: '8px 14px', fontWeight: 700 }}>
              <LayoutList size={17} aria-hidden="true" />
              Danh sách người yêu cầu
            </span>
          </div>
          <span className="post-claims-count-pill" role="status">
            {visibleClaims.length} hồ sơ phù hợp
          </span>
        </div>

        <div className="claim-list-filter-row">
          <div className="claim-list-status-group">
            <span className="claim-list-filter-label">Trạng thái</span>
            <div className="claim-list-status-filters" role="group" aria-label="Lọc trạng thái yêu cầu">
              {[
                { value: 'ALL', label: `Tất cả (${statusCounts.ALL})`, warning: false },
                { value: 'PENDING', label: `Chờ xét duyệt (${statusCounts.PENDING})`, warning: statusCounts.PENDING > 0 },
                { value: 'APPROVED', label: `Đang nhận đồ (${statusCounts.APPROVED})`, warning: false },
                { value: 'HISTORY', label: `Lịch sử (${statusCounts.HISTORY})`, warning: false },
              ].map(btn => (
                <button
                  key={btn.value}
                  type="button"
                  className={`${activeFilter === btn.value ? 'active' : ''} ${btn.warning ? 'is-warning' : ''}`.trim()}
                  aria-pressed={activeFilter === btn.value}
                  onClick={() => syncParams({ status: btn.value })}
                >
                  {btn.label}
                </button>
              ))}
            </div>
          </div>

          {historySelected && (
            <div className="claim-list-history-filter">
              <label htmlFor="post-claim-history-status">Trạng thái trong lịch sử</label>
              <select
                id="post-claim-history-status"
                value={status}
                onChange={event => syncParams({ status: event.target.value })}
              >
                <option value="HISTORY">Tất cả lịch sử</option>
                {HISTORY_STATUSES.map(value => (
                  <option key={value} value={value}>{CLAIM_STATUS[value]}</option>
                ))}
              </select>
            </div>
          )}

          <ClaimTimeFilter
            timeRange={timeRange}
            fromDate={fromDate}
            toDate={toDate}
            sortOrder={sortOrder}
            onTimeRangeChange={value => syncParams({ timeRange: value, fromDate: value === 'CUSTOM' ? fromDate : '', toDate: value === 'CUSTOM' ? toDate : '' })}
            onFromDateChange={value => syncParams({ timeRange: 'CUSTOM', fromDate: value })}
            onToDateChange={value => syncParams({ timeRange: 'CUSTOM', toDate: value })}
            onSortOrderChange={value => syncParams({ sortOrder: value })}
            onReset={() => syncParams({ timeRange: 'ALL', fromDate: '', toDate: '', sortOrder: 'NEWEST' })}
          />
        </div>
      </section>

      {loading ? (
        <div className="claim-list-loading" role="status">
          <Inbox size={24} aria-hidden="true" />
          Đang tải danh sách yêu cầu nhận đồ…
        </div>
      ) : snapshot.error ? (
        <div className="claim-alert" role="alert">
          <p>{snapshot.error}</p>
          <button className="btn btn-ghost" type="button" onClick={() => setAttempt(n => n + 1)}>Thử lại</button>
        </div>
      ) : !visibleClaims.length ? (
        <div className="claim-list-empty">
          <Inbox size={32} aria-hidden="true" />
          <h3>{onlyArchived ? 'Các yêu cầu đã được chuyển vào lịch sử' : hasAnyFilter ? 'Không có hồ sơ phù hợp với bộ lọc' : 'Chưa có yêu cầu nhận đồ nào cho bài đăng này'}</h3>
          {onlyArchived ? (
            <button type="button" className="btn btn-ghost" onClick={() => syncParams({ status: 'HISTORY' })}>Xem lịch sử</button>
          ) : <>
            <p>{hasAnyFilter ? 'Thử thay đổi khoảng thời gian hoặc trạng thái để xem các hồ sơ còn lại.' : 'Khi có người gửi yêu cầu nhận lại món đồ này, hồ sơ sẽ hiển thị tại đây.'}</p>
            {hasAnyFilter ? (
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => syncParams({ status: 'ALL', timeRange: 'ALL', fromDate: '', toDate: '', sortOrder: 'NEWEST' })}
              >
                Xóa tất cả bộ lọc
              </button>
            ) : (
              <Link className="btn btn-ghost" to={backPath}>Quay lại danh sách yêu cầu</Link>
            )}
          </>}
        </div>
      ) : (
        <div className="claim-modern-table-card">
          <div className="claim-modern-table-head post-claims-table-grid" role="row">
            <span className="col-left">Người yêu cầu</span>
            <span className="col-center">Ngày gửi</span>
            <span className="col-center">Trạng thái</span>
            <span className="col-center">Thao tác</span>
          </div>

          <div className="claim-modern-table-body" role="rowgroup">
            {visibleClaims.map(claim => (
              <Link
                key={claim.id}
                className="claim-modern-row post-claims-table-grid"
                to={claimDetailPath(postId, claim.id)}
                state={{ claimListReturn: location.pathname + location.search }}
              >
                <div className="claim-modern-item-cell col-left">
                  <span className="claim-modern-avatar-circle" aria-hidden="true">
                    {initialsOf(claim.claimantName)}
                  </span>
                  <div className="claim-modern-item-info">
                    <strong>{claim.claimantName || 'Thành viên'}</strong>
                    {claim.disputeOutcome === 'APPEAL_KEEP' &&
                      <small className="post-claims-dispute-warning">
                        <TriangleAlert size={14} aria-hidden="true" /> Tranh chấp đã bị bác
                      </small>}
                  </div>
                </div>

                <div className="claim-modern-date-cell col-center" data-label="Ngày gửi">
                  <span>{claimShortDate(claim.createdAt)}</span>
                  <small>{claimShortTime(claim.createdAt)}</small>
                </div>

                <div className="claim-modern-status-cell col-center" data-label="Trạng thái">
                  <span className={`claim-status-pill status-${claimDisplayStatus(claim)}`}>
                    <span className="claim-status-dot" aria-hidden="true" />
                    {rejectedAfterDispute(claim) ? 'Bị bác sau tranh chấp' : (CLAIM_STATUS[claimDisplayStatus(claim)] || claim.status)}
                  </span>
                </div>

                <div className="claim-modern-action-cell col-center">
                  <span className="claim-modern-action-pill">
                    {matchesClaimStatus(claim, 'PENDING', true) && post?.status === 'OPEN' ? 'Xem & duyệt' : 'Xem hồ sơ'}
                    <ArrowRight size={15} aria-hidden="true" />
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </div>
      )}
    </main>
  );
}
