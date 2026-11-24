import '@/styles/Workspace.css';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useOutletContext, useSearchParams } from 'react-router-dom';
import { ArrowUpRight, Building2, Filter, Inbox, Send, UsersRound, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import { getGroupedClaims } from '../api/claimApi';
import {
  CLAIM_STATUS,
  CLAIM_LIST_STATUSES,
  TIME_RANGE_OPTIONS,
  claimDetailPath,
  claimDisplayStatus,
  claimListStatus,
  claimShortDate,
  disputeDismissed,
  matchesClaimStatus,
  matchesTimeFilter,
  postClaimsPath,
  rejectedAfterDispute,
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
    return <Navigate replace to={postClaimsPath(legacyPostId, params.get('claimStatus'))} />;
  }
  return <ClaimList key={`${user?.id}:${initialView}:${params.get('postId') || ''}`} initialView={initialView} embedded={embedded} />;
}

function summarizeClaimGroup(group, incoming) {
  // The API returns claims for each post newest first.
  const latestClaim = group.claims[0];
  const activeClaim = group.claims.find(claim => matchesClaimStatus(claim, 'APPROVED'))
    || group.claims.find(claim => matchesClaimStatus(claim, 'PENDING'));
  const primaryClaim = activeClaim || latestClaim;
  return {
    ...group,
    primaryClaim,
    listStatus: activeClaim ? claimDisplayStatus(activeClaim) : 'HISTORY',
    displayDate: (incoming ? latestClaim : primaryClaim).createdAt,
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
  const status = claimListStatus(searchParams.get('claimStatus'));
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

    if (nextStatus === 'PENDING') updated.delete('claimStatus');
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
  // Fetch each post's claims together so the list can determine its current stage.
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

    const timeScopedGroups = rawGroups
      .filter(group => group.claims.length > 0)
      .map(group => summarizeClaimGroup(group, incoming))
      .filter(group => matchesTimeFilter(group.displayDate, timeRange, fromDate, toDate));

    const counts = Object.fromEntries(CLAIM_LIST_STATUSES.map(value => [
      value, timeScopedGroups.filter(group => group.listStatus === value).length,
    ]));

    const matchingGroups = timeScopedGroups.filter(group => group.listStatus === status);
    matchingGroups.sort((a, b) => {
      const descending = Date.parse(b.displayDate) - Date.parse(a.displayDate) || b.postId - a.postId;
      return sortOrder === 'OLDEST' ? -descending : descending;
    });

    const totalElements = matchingGroups.length;
    const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
    const safePage = Math.min(page, totalPages);
    const start = (safePage - 1) * PAGE_SIZE;

    return {
      statusCounts: counts,
      processed: {
        items: matchingGroups.slice(start, start + PAGE_SIZE),
        totalElements,
        totalPages,
        currentPage: safePage,
      },
    };
  }, [snapshot.result, status, timeRange, fromDate, toDate, sortOrder, page, incoming]);

  const Wrapper = embedded ? 'section' : 'main';
  const title = 'Danh sách yêu cầu nhận đồ';
  const allPostsParams = new URLSearchParams(searchParams);
  ['postId', 'expanded', 'claimPage'].forEach(paramKey => allPostsParams.delete(paramKey));
  const allPostsPath = `/claim-cua-toi${allPostsParams.size ? `?${allPostsParams}` : ''}`;
  const views = [
    ['received', 'Người khác yêu cầu', UsersRound],
    ['sent', 'Tôi yêu cầu', Send],
  ];
  const hasAnyFilter = timeRange !== 'ALL' || sortOrder !== 'NEWEST';
  const onlyArchived = status === 'PENDING' && statusCounts.PENDING === 0
    && statusCounts.APPROVED === 0 && statusCounts.HISTORY > 0;
  const hasClaims = snapshot.result?.content?.length > 0;

  const statusLabels = {
    PENDING: incoming ? 'Chờ xét duyệt' : 'Chờ phản hồi',
    APPROVED: 'Đang nhận đồ',
    HISTORY: 'Lịch sử',
  };
  const isReceiving = status === 'APPROVED';
  const summaryLabel = isReceiving
    ? (incoming ? 'Người được chấp thuận' : 'Người giao đồ')
    : status === 'HISTORY' ? 'Hồ sơ đã kết thúc' : 'Yêu cầu đang chờ';

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
            {views.map(([value, label, Icon]) => (
              <button
                type="button"
                key={value}
                aria-pressed={view === value}
                className={view === value ? 'active' : ''}
                onClick={() => syncParams({ view: value, page: 1, status: 'PENDING' })}
              >
                <Icon size={18} aria-hidden="true" />
                <span>{label}</span>
              </button>
            ))}
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
              {CLAIM_LIST_STATUSES.map(value => (
                <button
                  key={value}
                  type="button"
                  className={`${status === value ? 'active' : ''} ${value === 'PENDING' && statusCounts.PENDING > 0 ? 'is-warning' : ''}`.trim()}
                  aria-pressed={status === value}
                  onClick={() => syncParams({ page: 1, status: value })}
                >
                  {statusLabels[value]} ({statusCounts[value]})
                </button>
              ))}
            </div>
          </div>

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
                <span className="col-center">{summaryLabel}</span>
                <span className="col-center">{incoming ? 'Ngày gần nhất' : 'Ngày gửi'}</span>
                <span className="col-center">Trạng thái</span>
                <span className="col-center">Thao tác</span>
              </div>

              <div className="claim-modern-table-body" role="rowgroup">
                {processed.items.map(group => {
                  const { listStatus, primaryClaim } = group;
                  const archived = listStatus === 'HISTORY';
                  const statusCode = archived
                    ? (incoming ? 'CLOSED' : claimDisplayStatus(primaryClaim))
                    : listStatus;
                  let statusLabel = statusLabels[listStatus];
                  if (archived && incoming) statusLabel = 'Đã kết thúc';
                  if (archived && !incoming) {
                    statusLabel = CLAIM_STATUS[statusCode] || primaryClaim.status;
                    if (rejectedAfterDispute(primaryClaim)) statusLabel = 'Bị bác sau tranh chấp';
                    if (disputeDismissed(primaryClaim)) statusLabel = 'Tranh chấp đã bị bác';
                  }
                  const targetUrl = incoming
                    ? postClaimsPath(group.postId, listStatus)
                    : claimDetailPath(group.postId, primaryClaim.id);
                  const navState = incoming
                    ? { fromClaimsList: location.pathname + location.search }
                    : { claimListReturn: location.pathname + location.search };
                  const summary = isReceiving
                    ? (incoming ? primaryClaim.claimantName : primaryClaim.finderName) || 'Thành viên'
                    : `${group.claims.filter(claim => matchesClaimStatus(claim, listStatus)).length} yêu cầu`;

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
                          {!incoming && !isReceiving && <span>Người nhặt: {primaryClaim.finderName || 'Thành viên'}</span>}
                        </div>
                      </div>

                      <div className="claim-modern-count-cell col-center" data-label={summaryLabel}>
                        {summary}
                      </div>

                      <div className="claim-modern-date-cell col-center" data-label={incoming ? 'Ngày gần nhất' : 'Ngày gửi'}>
                        {group.displayDate ? claimShortDate(group.displayDate) : '—'}
                      </div>

                      <div className="claim-modern-status-cell col-center" data-label="Trạng thái">
                        <span className={`claim-status-pill status-${statusCode}`}>
                          <span className="claim-status-dot" aria-hidden="true" />
                          {statusLabel}
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
                {onlyArchived ? 'Các yêu cầu đã được chuyển vào lịch sử' : hasAnyFilter
                  ? 'Không có yêu cầu phù hợp với bộ lọc'
                  : hasClaims ? 'Chưa có yêu cầu trong mục này'
                  : incoming
                    ? 'Chưa có yêu cầu nhận đồ'
                    : 'Bạn chưa gửi yêu cầu nhận đồ'}
              </h3>
              {!onlyArchived && <p>
                {hasAnyFilter
                  ? 'Thử thay đổi khoảng thời gian hoặc trạng thái để xem các hồ sơ khác.'
                  : hasClaims ? 'Chọn mục trạng thái khác để xem các hồ sơ còn lại.'
                  : incoming
                    ? 'Khi có người gửi yêu cầu nhận lại món đồ bạn đăng, món đồ đó sẽ hiển thị tại đây.'
                    : 'Các yêu cầu bạn gửi sẽ được theo dõi tại đây.'}
              </p>}
              {onlyArchived ? (
                <button type="button" className="btn btn-ghost" onClick={() => syncParams({ page: 1, status: 'HISTORY' })}>
                  Xem lịch sử
                </button>
              ) : page > 1 ? (
                <button type="button" className="btn btn-ghost" onClick={() => syncParams({ page: 1 })}>
                  Về trang đầu
                </button>
              ) : hasAnyFilter && (
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => syncParams({ page: 1, timeRange: 'ALL', fromDate: '', toDate: '', sortOrder: 'NEWEST' })}
                >
                  Đặt lại thời gian và sắp xếp
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
