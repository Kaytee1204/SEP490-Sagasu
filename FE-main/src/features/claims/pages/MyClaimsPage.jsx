import '@/styles/Workspace.css';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useOutletContext, useSearchParams } from 'react-router-dom';
import { ArrowUpRight, Building2, Filter, Inbox, Send, UsersRound, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import { getGroupedClaims } from '../api/claimApi';
import {
  CLAIM_STATUS,
  HISTORY_STATUSES,
  TIME_RANGE_OPTIONS,
  claimDetailPath,
  claimShortDate,
  matchesTimeFilter,
  postClaimsPath,
  validClaimId,
} from '../utils/claimUtils';
import { ClaimPostThumbnail } from '../components/ClaimListShared';
import ClaimTimeFilter from '../components/ClaimTimeFilter';
import '../styles/Claims.css';
import './MyClaimsPage.css';

const PAGE_SIZE = 8;
const VALID_TIME_RANGES = new Set(TIME_RANGE_OPTIONS.map(o => o.value));

export default function MyClaimsPage({ embedded = false }) {
  const { user, isApprover } = useAuth();
  const [params] = useSearchParams();
  const initialView = params.get('view') === 'sent' ? 'sent' : 'received';
  const legacyPostId = validClaimId(params.get('postId')) ? params.get('postId') : null;
  if (params.get('view') === 'office' && isApprover) return <Navigate replace to="/approver/office-claims" />;
  if (!embedded && initialView === 'received' && legacyPostId) {
    return <Navigate replace to={postClaimsPath(legacyPostId, params.get('claimStatus') || 'ALL', 'received')} />;
  }
  return <ClaimList key={`${user?.id}:${initialView}:${params.get('postId') || ''}`} initialView={initialView} embedded={embedded} />;
}

function resolveGroupStatus(visibleClaims, incoming) {
  const pendingCount = visibleClaims.filter(c => c.status === 'PENDING').length;
  const approvedCount = visibleClaims.filter(c => c.status === 'APPROVED').length;
  if (pendingCount > 0) {
    return {
      code: 'PENDING',
      label: incoming ? 'Cần bạn duyệt' : 'Chờ phản hồi',
      pendingCount,
      approvedCount,
    };
  }
  if (approvedCount > 0) {
    return {
      code: 'APPROVED',
      label: 'Đang nhận đồ',
      pendingCount,
      approvedCount,
    };
  }
  const latest = visibleClaims[0];
  if (!latest) return { code: 'CLOSED', label: 'Đã đóng', pendingCount: 0, approvedCount: 0 };
  return {
    code: latest.status,
    label: CLAIM_STATUS[latest.status] || latest.status,
    pendingCount,
    approvedCount,
  };
}

function ClaimList({ embedded = false, initialView = 'received' }) {
  const location = useLocation();
  const outletCtx = useOutletContext() || {};
  const claimCounts = outletCtx.claimCounts || { receivedPending: 0, sentPending: 0 };
  const [searchParams, setSearchParams] = useSearchParams();
  const postId = validClaimId(searchParams.get('postId')) ? searchParams.get('postId') : null;
  const autoSwitchedRef = useRef(false);

  const [view, setView] = useState(() => {
    const urlView = searchParams.get('view');
    if (urlView === 'sent' || urlView === 'received') return urlView;
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
  const [timeRange, setTimeRange] = useState(() => {
    const urlRange = searchParams.get('timeRange');
    return urlRange && VALID_TIME_RANGES.has(urlRange) ? urlRange : 'ALL';
  });
  const [fromDate, setFromDate] = useState(() => searchParams.get('fromDate') || '');
  const [toDate, setToDate] = useState(() => searchParams.get('toDate') || '');
  const [sortOrder, setSortOrder] = useState(() => (searchParams.get('sort') === 'OLDEST' ? 'OLDEST' : 'NEWEST'));
  const [attempt, setAttempt] = useState(0);
  const [snapshot, setSnapshot] = useState({ key: '', result: null, error: '' });

  const syncParams = next => {
    const nextView = next.view ?? view;
    const nextPage = next.page ?? page;
    const nextStatus = next.status ?? status;
    const nextRange = next.timeRange ?? timeRange;
    const nextFrom = next.fromDate !== undefined ? next.fromDate : fromDate;
    const nextTo = next.toDate !== undefined ? next.toDate : toDate;
    const nextSort = next.sortOrder ?? sortOrder;

    setView(nextView);
    setPage(nextPage);
    setStatus(nextStatus);
    setTimeRange(nextRange);
    setFromDate(nextFrom);
    setToDate(nextTo);
    setSortOrder(nextSort);

    const updated = new URLSearchParams(searchParams);
    if (nextView !== view) {
      updated.delete('postId');
      updated.delete('expanded');
    }
    if (nextView === 'received') updated.delete('view');
    else updated.set('view', nextView);

    if (nextPage <= 1) updated.delete('claimPage');
    else updated.set('claimPage', String(nextPage));

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

    setSearchParams(updated, { replace: true });
  };

  const incoming = view !== 'sent';
  // Fetch ALL grouped claims (up to 100) once per view/postId so live status counts & time filters are always exact.
  const key = `${postId}:${view}:${attempt}`;

  useEffect(() => {
    let cancelled = false;
    getGroupedClaims(1, 'ALL', !incoming, postId, 100)
      .then(result => { if (!cancelled) setSnapshot({ key, result, error: '' }); })
      .catch(error => { if (!cancelled) setSnapshot({ key, result: null, error: error.message }); });
    return () => { cancelled = true; };
  }, [postId, incoming, view, key]);

  const loading = snapshot.key !== key;

  // Đồng bộ với badge sidebar: nếu người dùng mở mặc định tab "Người khác yêu cầu" nhưng tab này trống
  // trong khi tab "Tôi yêu cầu" đang có yêu cầu chờ duyệt -> tự động chuyển sang "Tôi yêu cầu"
  useEffect(() => {
    if (autoSwitchedRef.current || embedded || postId) return;
    if (searchParams.get('view')) return;
    if (loading || view !== 'received') return;
    const rawCount = snapshot.result?.content?.length ?? 0;
    if (rawCount === 0 && claimCounts.sentPending > 0 && claimCounts.receivedPending === 0) {
      autoSwitchedRef.current = true;
      setView('sent');
    }
  }, [loading, view, snapshot.result, claimCounts.sentPending, claimCounts.receivedPending, embedded, postId, searchParams]);

  const { processed, statusCounts } = useMemo(() => {
    const rawGroups = snapshot.result?.content || [];

    // Apply time filter first so status pill counts reflect the selected time period accurately
    const timeScopedGroups = rawGroups
      .map(group => {
        const claimsInTime = timeRange === 'ALL'
          ? group.claims
          : group.claims.filter(c => matchesTimeFilter(c.createdAt, timeRange, fromDate, toDate));
        return { ...group, claimsInTime };
      })
      .filter(group => group.claimsInTime.length > 0);

    const counts = {
      ALL: timeScopedGroups.length,
      PENDING: timeScopedGroups.filter(g => g.claimsInTime.some(c => c.status === 'PENDING')).length,
      APPROVED: timeScopedGroups.filter(g => g.claimsInTime.some(c => c.status === 'APPROVED')).length,
      HISTORY: timeScopedGroups.filter(g => g.claimsInTime.some(c => HISTORY_STATUSES.includes(c.status))).length,
    };

    const enriched = timeScopedGroups
      .map(group => {
        const visibleClaims = status === 'ALL'
          ? group.claimsInTime
          : group.claimsInTime.filter(c => (status === 'HISTORY' ? HISTORY_STATUSES.includes(c.status) : c.status === status));
        const sortedClaims = [...visibleClaims].sort((a, b) => {
          const diff = Date.parse(b.createdAt) - Date.parse(a.createdAt);
          return sortOrder === 'OLDEST' ? -diff : diff;
        });
        const latestDate = sortedClaims[0]?.createdAt || null;
        return {
          ...group,
          visibleClaims: sortedClaims,
          latestDate,
          latestMs: latestDate ? Date.parse(latestDate) : 0,
          hasPending: sortedClaims.some(c => c.status === 'PENDING'),
        };
      })
      .filter(group => group.visibleClaims.length > 0);

    enriched.sort((a, b) => {
      if (sortOrder === 'OLDEST') return a.latestMs - b.latestMs || a.postId - b.postId;
      return (Number(b.hasPending) - Number(a.hasPending)) || (b.latestMs - a.latestMs) || (b.postId - a.postId);
    });

    const totalElements = enriched.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
    const safePage = Math.min(page, totalPages);
    const start = (safePage - 1) * PAGE_SIZE;

    return {
      statusCounts: counts,
      processed: {
        items: enriched.slice(start, start + PAGE_SIZE),
        totalElements,
        totalPages,
        currentPage: safePage,
      },
    };
  }, [snapshot.result, status, timeRange, fromDate, toDate, sortOrder, page]);

  const historySelected = status === 'HISTORY' || HISTORY_STATUSES.includes(status);
  const activeFilter = historySelected ? 'HISTORY' : status;
  const Wrapper = embedded ? 'section' : 'main';
  const title = 'Danh sách yêu cầu nhận đồ';
  const allPostsParams = new URLSearchParams(searchParams);
  ['postId', 'expanded', 'claimPage'].forEach(paramKey => allPostsParams.delete(paramKey));
  const allPostsPath = `/claim-cua-toi${allPostsParams.size ? `?${allPostsParams}` : ''}`;
  const views = [
    ['received', 'Người khác yêu cầu', UsersRound],
    ['sent', 'Tôi yêu cầu', Send],
  ];
  const hasAnyFilter = status !== 'ALL' || timeRange !== 'ALL' || sortOrder !== 'NEWEST';

  const statusButtons = [
    { value: 'ALL', label: `Tất cả (${statusCounts.ALL})`, tone: 'default' },
    {
      value: 'PENDING',
      label: `${incoming ? 'Cần bạn duyệt' : 'Chờ phản hồi'} (${statusCounts.PENDING})`,
      tone: statusCounts.PENDING > 0 ? 'warning' : 'default',
    },
    { value: 'APPROVED', label: `Đang nhận đồ (${statusCounts.APPROVED})`, tone: 'default' },
    { value: 'HISTORY', label: `Lịch sử (${statusCounts.HISTORY})`, tone: 'default' },
  ];

  return (
    <Wrapper className={`${embedded ? '' : 'container'} claims-page claim-list-page ${embedded ? 'claims-embedded' : ''}`} aria-label={title}>
      {!embedded && (
        <header className="claim-list-heading">
          <h1>{title}</h1>
          
        </header>
      )}

      {postId && (
        <div className="claim-list-context">
          <span><Filter size={16} aria-hidden="true" />Đang lọc theo bài đăng</span>
          <Link to={`/chi-tiet/${postId}`}>Xem bài đăng<ArrowUpRight size={15} aria-hidden="true" /></Link>
          <Link className="claim-list-clear-context" to={allPostsPath}><X size={15} aria-hidden="true" />Bỏ lọc bài đăng</Link>
        </div>
      )}

      <section className="claim-list-controls" aria-label="Bộ lọc danh sách yêu cầu nhận đồ">
        <div className="claim-list-navigation">
          <div className="claim-list-tabs" role="group" aria-label="Loại yêu cầu nhận đồ">
            {views.map(([value, label, Icon]) => {
              const tabPending = value === 'received' ? claimCounts.receivedPending : claimCounts.sentPending;
              return (
                <button
                  type="button"
                  key={value}
                  aria-pressed={view === value}
                  className={view === value ? 'active' : ''}
                  onClick={() => syncParams({ view: value, page: 1, status: 'ALL' })}
                >
                  <Icon size={18} aria-hidden="true" />
                  <span>{label}</span>
                  {tabPending > 0 && (
                    <span className="ml-1 inline-flex min-w-[20px] items-center justify-center rounded-full bg-amber-100 px-1.5 py-0.5 text-[11px] font-bold text-amber-800">
                      {tabPending > 99 ? '99+' : tabPending}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
          <Link
            className="claim-list-office-link"
            to="/van-phong/claims"
            state={{ officeClaimReturn: location.pathname + location.search }}
          >
            <Building2 size={17} aria-hidden="true" />
            Nhận đồ tại văn phòng
            <ArrowUpRight size={15} aria-hidden="true" />
          </Link>
        </div>

        <div className="claim-list-filter-row">
          <div className="claim-list-status-group">
            <span className="claim-list-filter-label">Trạng thái</span>
            <div className="claim-list-status-filters" role="group" aria-label="Lọc trạng thái yêu cầu">
              {statusButtons.map(btn => (
                <button
                  key={btn.value}
                  type="button"
                  className={`${activeFilter === btn.value ? 'active' : ''} ${btn.tone === 'warning' ? 'is-warning' : ''}`.trim()}
                  aria-pressed={activeFilter === btn.value}
                  onClick={() => syncParams({ page: 1, status: btn.value })}
                >
                  {btn.label}
                </button>
              ))}
            </div>
          </div>

          {historySelected && (
            <div className="claim-list-history-filter">
              <label htmlFor="claim-history-status">Trạng thái trong lịch sử</label>
              <select
                id="claim-history-status"
                value={status}
                onChange={event => syncParams({ page: 1, status: event.target.value })}
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
            onTimeRangeChange={value => syncParams({ page: 1, timeRange: value, fromDate: value === 'CUSTOM' ? fromDate : '', toDate: value === 'CUSTOM' ? toDate : '' })}
            onFromDateChange={value => syncParams({ page: 1, timeRange: 'CUSTOM', fromDate: value })}
            onToDateChange={value => syncParams({ page: 1, timeRange: 'CUSTOM', toDate: value })}
            onSortOrderChange={value => syncParams({ page: 1, sortOrder: value })}
            onReset={() => syncParams({ page: 1, timeRange: 'ALL', fromDate: '', toDate: '', sortOrder: 'NEWEST' })}
          />
        </div>
      </section>

      {loading ? (
        <div className="claim-list-loading" role="status">
          <Inbox size={24} aria-hidden="true" />
          Đang tải yêu cầu nhận đồ…
        </div>
      ) : snapshot.error ? (
        <div className="claim-alert" role="alert">
          <p>{snapshot.error}</p>
          <button className="btn btn-ghost" type="button" onClick={() => setAttempt(n => n + 1)}>Thử lại</button>
        </div>
      ) : (
        <>
          

          {processed.items.length ? (
            <div className="claim-modern-table-card">
              <div className="claim-modern-table-head" role="row">
                <span className="col-left">Món đồ</span>
                <span className="col-center">{incoming ? 'Số yêu cầu' : 'Hồ sơ'}</span>
                <span className="col-center">{incoming ? 'Ngày gần nhất' : 'Ngày gửi'}</span>
                <span className="col-center">Trạng thái</span>
                <span className="col-center">Thao tác</span>
              </div>

              <div className="claim-modern-table-body" role="rowgroup">
                {processed.items.map(group => {
                  const latestClaim = group.visibleClaims[0];
                  const statusInfo = resolveGroupStatus(group.visibleClaims, incoming);
                  const targetUrl = incoming
                    ? postClaimsPath(group.postId, status, 'received')
                    : claimDetailPath(group.postId, latestClaim.id);
                  const navState = incoming
                    ? { fromClaimsList: location.pathname + location.search }
                    : { claimListReturn: location.pathname + location.search };
                  const subText = incoming
                    ? `${group.claimantCount} người yêu cầu${statusInfo.pendingCount > 0 ? ` · ${statusInfo.pendingCount} chờ duyệt` : ''}`
                    : latestClaim?.claimType === 'OFFICE'
                      ? 'Văn phòng tiếp nhận'
                      : `Người nhặt: ${latestClaim?.finderName || 'Thành viên'}`;

                  return (
                    <Link
                      key={`${group.postId}:${status}`}
                      className="claim-modern-row"
                      to={targetUrl}
                      state={navState}
                    >
                      <div className="claim-modern-item-cell col-left">
                        <ClaimPostThumbnail url={group.postThumbnailUrl} title={group.postTitle} />
                        <div className="claim-modern-item-info">
                          <strong title={group.postTitle}>{group.postTitle}</strong>
                          <span>{subText}</span>
                        </div>
                      </div>

                      <div className="claim-modern-count-cell col-center" data-label={incoming ? 'Số yêu cầu' : 'Hồ sơ'}>
                        {group.visibleClaims.length} yêu cầu
                      </div>

                      <div className="claim-modern-date-cell col-center" data-label={incoming ? 'Ngày gần nhất' : 'Ngày gửi'}>
                        {group.latestDate ? claimShortDate(group.latestDate) : '—'}
                      </div>

                      <div className="claim-modern-status-cell col-center" data-label="Trạng thái">
                        <span className={`claim-status-pill status-${statusInfo.code}`}>
                          <span className="claim-status-dot" aria-hidden="true" />
                          {statusInfo.label}
                        </span>
                      </div>

                      <div className="claim-modern-action-cell col-center">
                        <span className="claim-modern-action-pill">
                          {incoming ? 'Xem yêu cầu' : 'Xem hồ sơ'}
                        </span>
                      </div>
                    </Link>
                  );
                })}
              </div>
            </div>
          ) : (
            <div className="claim-list-empty">
              <Inbox size={32} aria-hidden="true" />
              <h3>
                {hasAnyFilter
                  ? 'Không có yêu cầu phù hợp với bộ lọc'
                  : incoming
                    ? 'Chưa có yêu cầu nhận đồ'
                    : 'Bạn chưa gửi yêu cầu nhận đồ'}
              </h3>
              <p>
                {hasAnyFilter
                  ? 'Thử thay đổi khoảng thời gian hoặc trạng thái để xem các hồ sơ khác.'
                  : incoming
                    ? 'Khi có người gửi yêu cầu nhận lại món đồ bạn đăng, món đồ đó sẽ hiển thị tại đây.'
                    : 'Các yêu cầu bạn gửi sẽ được theo dõi tại đây.'}
              </p>
              {page > 1 ? (
                <button type="button" className="btn btn-ghost" onClick={() => syncParams({ page: 1 })}>
                  Về trang đầu
                </button>
              ) : hasAnyFilter && (
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => syncParams({ page: 1, status: 'ALL', timeRange: 'ALL', fromDate: '', toDate: '', sortOrder: 'NEWEST' })}
                >
                  Xóa tất cả bộ lọc
                </button>
              )}
            </div>
          )}

          {processed.totalPages > 1 && (
            <Pagination
              className="claim-list-pagination"
              currentPage={processed.currentPage}
              totalPages={processed.totalPages}
              onPageChange={nextPage => syncParams({ page: nextPage })}
            />
          )}
        </>
      )}
    </Wrapper>
  );
}
