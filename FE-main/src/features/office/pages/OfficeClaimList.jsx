import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { ArrowRight, ArrowUpDown, Calendar, Filter, Inbox, Plus, RotateCcw, Search, SlidersHorizontal } from "lucide-react";
import { officeApi } from "../api/officeApi";
import { date, labels, progressLabels } from "../officeUtils";
import { TIME_RANGE_OPTIONS, claimShortDate } from "@/features/claims/utils/claimUtils";
import { ClaimPostThumbnail } from "@/features/claims/components/ClaimListShared";
import "@/features/claims/pages/MyClaimsPage.css";
import useLoad from "../hooks/useLoad";
import Badge from "../components/Badge";
import LoadState from "../components/LoadState";
import Empty from "../components/Empty";
import SearchBox from "../components/SearchBox";
import Select from "../components/Select";
import Pager from "../components/Pager";
import ClaimProgress from "../components/ClaimProgress";
import PersonalOfficeHeader from "../components/PersonalOfficeHeader";
import PersonalClaimCards from "../components/PersonalClaimCards";
import "../Office.css";
import "../PersonalOffice.css";
import "../StaffWorkspace.css";

const STAFF_CLAIM_TABS = [
  { value: "ALL", label: "Tất cả" },
  { value: "PENDING", label: "Chờ xử lý", tone: "warning" },
  { value: "NEEDS_INFO", label: "Chờ bổ sung", tone: "warning" },
  { value: "APPROVED", label: "Đã duyệt" },
  { value: "COMPLETED", label: "Hoàn tất" },
  { value: "REJECTED", label: "Từ chối" },
];

const DEFAULT_FILTERS = {
  page: 1,
  q: "",
  status: "ALL",
  stage: "ALL",
  channel: "ALL",
  timeRange: "ALL",
  sort: "NEWEST",
};

export default function OfficeClaimList({ mine = false }) {
  const { postId } = useParams();
  const grouped = !mine && !postId;
  const filterKey = `office-claims-filters:${mine ? 'true' : postId || 'groups'}`;
  const location = useLocation();
  const returnTo = location.state?.officeClaimReturn;
  const backPath =
    typeof returnTo === "string" &&
    (returnTo === "/claim-cua-toi" || returnTo.startsWith("/claim-cua-toi?"))
      ? returnTo
      : "/claim-cua-toi?view=sent";

  const [filters, setFilters] = useState(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem(filterKey));
      return saved ? { ...DEFAULT_FILTERS, ...saved } : DEFAULT_FILTERS;
    } catch {
      return DEFAULT_FILTERS;
    }
  });

  useEffect(() => {
    sessionStorage.setItem(filterKey, JSON.stringify(filters));
  }, [filters, filterKey]);

  const since = useMemo(() => timeFilterStart(filters.timeRange), [filters.timeRange]);
  const apiParams = { page: filters.page, q: filters.q, status: filters.status };
  if (!mine) Object.assign(apiParams, {
    stage: filters.stage, channel: filters.channel, since,
    oldest: filters.sort === "OLDEST",
  });
  const loaded = useLoad(
    () => grouped ? officeApi.claimGroups(apiParams)
      : postId ? officeApi.claimsForPost(postId, apiParams) : officeApi.claims(apiParams, true),
    JSON.stringify(apiParams) + filterKey,
  );
  const tabCounts = grouped ? loaded.data?.counts : null;
  const state = grouped ? { ...loaded, data: loaded.data?.page } : loaded;

  const base = mine ? "/van-phong/claims" : "/approver/office-claims";
  const [showExtraFilters, setShowExtraFilters] = useState(false);
  const change = (key, value) => setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  const resetFilters = () => {
    setFilters(DEFAULT_FILTERS);
    setShowExtraFilters(false);
  };

  const extraActiveCount = (filters.stage !== "ALL" ? 1 : 0) + (filters.channel !== "ALL" ? 1 : 0);
  const isExtraOpen = showExtraFilters || extraActiveCount > 0;

  const hasActiveFilter =
    Boolean(filters.q?.trim?.()) ||
    filters.status !== "ALL" ||
    (!mine &&
      (filters.stage !== "ALL" ||
        filters.channel !== "ALL" ||
        filters.timeRange !== "ALL" ||
        filters.sort !== "NEWEST"));

  const displayedContent = state.data?.content ?? [];

  if (mine) {
    return (
      <div className="office-page office-personal">
        <PersonalOfficeHeader active="claims" backPath={backPath} />
        <div className="of-panel">
          <div className="of-toolbar">
            <SearchBox
              value={filters.q}
              onChange={(v) => change("q", v)}
              placeholder="Tìm đồ vật trong yêu cầu của bạn…"
            />
            <Select
              label="Trạng thái yêu cầu"
              value={filters.status}
              onChange={(v) => change("status", v)}
              options={[
                "ALL",
                "PENDING",
                "NEEDS_INFO",
                "APPROVED",
                "REJECTED",
                "CANCELLED",
                "COMPLETED",
                "CLOSED",
              ].map((v) => [v, v === "ALL" ? "Tất cả" : labels[v]])}
            />
          </div>
          <LoadState state={state}>
            {state.data && (
              <>
                {!displayedContent.length ? (
                  <Empty
                    text={
                      hasActiveFilter || filters.page > 1
                        ? "Không tìm thấy yêu cầu phù hợp"
                        : "Bạn chưa gửi yêu cầu nhận đồ"
                    }
                    description={
                      hasActiveFilter || filters.page > 1
                        ? "Thử thay đổi từ khóa hoặc bỏ bộ lọc để xem lại danh sách."
                        : "Khi tìm thấy đồ của mình trong bài đăng do văn phòng tiếp nhận, bạn có thể gửi yêu cầu nhận lại đồ."
                    }
                  >
                    {hasActiveFilter || filters.page > 1 ? (
                      <button type="button" className="of-button" onClick={resetFilters}>
                        Xóa bộ lọc
                      </button>
                    ) : (
                      <Link className="of-button of-primary" to="/tim-kiem">
                        Tìm đồ thất lạc
                      </Link>
                    )}
                  </Empty>
                ) : (
                  <PersonalClaimCards claims={displayedContent} />
                )}
                <Pager
                  data={state.data}
                  onChange={(page) => setFilters((f) => ({ ...f, page }))}
                />
              </>
            )}
          </LoadState>
        </div>
      </div>
    );
  }

  return (
    <div className="sw-page">
      <header className="sw-heading">
        {postId && <Link className="of-back" to={base}>← Danh sách bài đăng</Link>}
        <h1>{postId ? displayedContent[0]?.postTitle || `Yêu cầu của bài đăng #${postId}` : "Xét duyệt và trao trả tài sản"}</h1>
        <p>{postId ? `Bài đăng #${postId} · Yêu cầu nhận tài sản` : "Theo dõi và xử lý các yêu cầu nhận tài sản theo bài đăng."}</p>
      </header>

      {/* Card 1: Filter Controls */}
      <section className="sw-controls" aria-label="Bộ lọc yêu cầu nhận đồ">
        <div className="sw-controls-top">
          <label className="sw-search">
            <Search size={17} aria-hidden="true" />
            <input
              type="search"
              maxLength={200}
              value={filters.q}
              onChange={(e) => change("q", e.target.value)}
              placeholder="Tìm tên đồ vật, người yêu cầu hoặc mã hồ sơ…"
              aria-label="Tìm kiếm yêu cầu nhận đồ"
            />
          </label>

          <Link className="sw-primary-btn" to={`${base}/new`}>
            <Plus size={16} aria-hidden="true" />
            Lập yêu cầu tại quầy
          </Link>
        </div>

        <div className="sw-filter-row">
          <div className="sw-status-group">
            <span className="sw-filter-label">Trạng thái</span>
            <div className="sw-status-pills" role="group" aria-label="Lọc theo trạng thái">
              {STAFF_CLAIM_TABS.map((tab) => {
                const active = filters.status === tab.value;
                const count = tabCounts ? (tabCounts[tab.value] ?? 0) : undefined;
                const isWarn = tab.tone === "warning" && typeof count === "number" && count > 0;
                return (
                  <button
                    key={tab.value}
                    type="button"
                    aria-pressed={active}
                    onClick={() => change("status", tab.value)}
                    className={`${active ? "is-active" : ""} ${isWarn ? "is-warning" : ""}`.trim()}
                  >
                    {tab.label}
                    {typeof count === "number" ? ` (${count})` : ""}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="sw-dropdowns">
            <div className="sw-field">
              <label htmlFor="sw-claim-time">
                <Calendar size={13} aria-hidden="true" />
                Thời gian
              </label>
              <select
                id="sw-claim-time"
                value={filters.timeRange}
                onChange={(e) => change("timeRange", e.target.value)}
              >
                {TIME_RANGE_OPTIONS.filter((o) => o.value !== "CUSTOM").map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="sw-field">
              <label htmlFor="sw-claim-sort">
                <ArrowUpDown size={13} aria-hidden="true" />
                Sắp xếp
              </label>
              <select
                id="sw-claim-sort"
                value={filters.sort}
                onChange={(e) => change("sort", e.target.value)}
              >
                <option value="NEWEST">Mới nhất trước</option>
                <option value="OLDEST">Cũ nhất trước</option>
              </select>
            </div>

            <button
              type="button"
              className={`sw-extra-toggle-btn ${isExtraOpen ? "is-active" : ""}`}
              onClick={() => setShowExtraFilters((v) => !v)}
              title="Bộ lọc nâng cao"
              aria-expanded={isExtraOpen}
            >
              <SlidersHorizontal size={13} aria-hidden="true" />
              Lọc thêm {extraActiveCount > 0 ? `(${extraActiveCount})` : ""}
            </button>

            {hasActiveFilter && (
              <button type="button" className="sw-reset-btn" onClick={resetFilters}>
                <RotateCcw size={13} aria-hidden="true" />
                Đặt lại
              </button>
            )}
          </div>
        </div>

        {isExtraOpen && (
          <div className="sw-extra-filter-bar" role="group" aria-label="Bộ lọc nâng cao">
            <div className="sw-field">
              <label htmlFor="sw-claim-channel">
                <Filter size={13} aria-hidden="true" />
                Kênh gửi
              </label>
              <select
                id="sw-claim-channel"
                value={filters.channel}
                onChange={(e) => change("channel", e.target.value)}
              >
                <option value="ALL">Tất cả kênh</option>
                <option value="ONLINE">Trực tuyến</option>
                <option value="COUNTER">Tại quầy</option>
              </select>
            </div>

            <div className="sw-field">
              <label htmlFor="sw-claim-stage">
                <SlidersHorizontal size={13} aria-hidden="true" />
                Tiến trình
              </label>
              <select
                id="sw-claim-stage"
                value={filters.stage}
                onChange={(e) => change("stage", e.target.value)}
              >
                <option value="ALL">Tất cả tiến trình</option>
                {Object.entries(progressLabels).map(([val, lbl]) => (
                  <option key={val} value={val}>
                    {lbl}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
      </section>

      {/* Card 2: Table / Empty */}
      <LoadState state={state}>
        {state.data && (
          <>
            {!displayedContent.length ? (
              <div className="sw-empty-card">
                <Inbox size={32} strokeWidth={1.6} aria-hidden="true" />
                <h3>
                  {hasActiveFilter ? "Không tìm thấy yêu cầu phù hợp" : "Chưa có yêu cầu nhận đồ"}
                </h3>
                <p>
                  {hasActiveFilter
                    ? "Thử thay đổi bộ lọc hoặc đặt lại để xem toàn bộ hồ sơ."
                    : "Các yêu cầu nhận đồ gửi trực tuyến và hồ sơ lập tại quầy sẽ hiển thị tại đây."}
                </p>
              </div>
            ) : grouped ? (
              <ClaimGroups groups={displayedContent} base={base} />
            ) : (
              <>
                <div className="sw-results-bar">
                  <h2>Hồ sơ yêu cầu nhận đồ tại văn phòng</h2>
                  <span className="sw-results-count">
                    {displayedContent.length} / {state.data.totalElements} yêu cầu
                  </span>
                </div>

                <div className="sw-table-card">
                  <div className="sw-table-head sw-grid-claims" aria-hidden="true">
                    <span>Đồ vật & Mã hồ sơ</span>
                    <span>Người yêu cầu</span>
                    <span>Trạng thái & Tiến trình</span>
                    <span>Ngày gửi</span>
                    <span style={{ textAlign: "right" }}>Thao tác</span>
                  </div>

                  <div className="sw-table-body">
                    {displayedContent.map((c) => {
                      const canReview = c.canReview && c.status === "PENDING";
                      const reviewOnHold = !c.ownRequest && !c.canReview && ["PENDING", "NEEDS_INFO"].includes(c.status);
                      return (
                        <article key={c.id} className="sw-table-row sw-grid-claims">
                          <div className="sw-item-cell">
                            <div className="sw-item-text">
                              <Link className="sw-item-title" to={c.ownRequest ? `/van-phong/claims/${c.id}` : `${base}/${c.id}`}>
                                {c.postTitle}
                              </Link>
                              <div className="sw-item-sub">
                                <span className="sw-code-tag">#{c.id}</span>
                                <span>Bài đăng #{c.postId}</span>
                              </div>
                            </div>
                          </div>

                          <div className="sw-meta-cell">
                            <strong>{c.claimantName}</strong>
                            <div className="sw-item-sub">
                              <Badge value={c.channel} />
                              {c.channel === "COUNTER" && <span>· {c.createdBy}</span>}
                            </div>
                          </div>

                          <div>
                            <ClaimProgress stage={c.stage} scheduledAt={c.collectionAt} />
                            {reviewOnHold && <div className="sw-item-sub">Đã duyệt yêu cầu khác</div>}
                          </div>

                          <div className="sw-meta-cell">
                            <span>{date(c.createdAt)}</span>
                          </div>

                          <div className="sw-action-cell">
                            <Link
                              className={`sw-action-pill ${canReview ? "is-solid" : "is-default"}`}
                              to={c.ownRequest ? `/van-phong/claims/${c.id}` : `${base}/${c.id}`}
                            >
                              {canReview ? "Xét duyệt" : "Xem chi tiết"}
                              <ArrowRight size={14} aria-hidden="true" />
                            </Link>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </div>
              </>
            )}

            <Pager
              data={state.data}
              onChange={(page) => setFilters((f) => ({ ...f, page }))}
            />
          </>
        )}
      </LoadState>
    </div>
  );
}

function timeFilterStart(range) {
  const days = { "7D": 7, "30D": 30 }[range];
  if (days) return new Date(Date.now() - days * 86400000).toISOString();
  if (range === "TODAY") {
    const today = new Date().toLocaleDateString("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" });
    return `${today}T00:00:00+07:00`;
  }
  return null;
}

function ClaimGroups({ groups, base }) {
  return <div className="claim-modern-table-card">
    <div className="claim-modern-table-head" aria-hidden="true">
      <span className="col-left">Món đồ</span><span className="col-center">Số yêu cầu</span>
      <span className="col-center">Ngày gần nhất</span><span className="col-center">Trạng thái</span>
      <span className="col-center">Thao tác</span>
    </div>
    <div className="claim-modern-table-body">
      {groups.map(group => <Link key={group.postId} className="claim-modern-row" to={`${base}/post/${group.postId}`}>
        <div className="claim-modern-item-cell col-left">
          <ClaimPostThumbnail url={group.thumbnail} title={group.title} />
          <div className="claim-modern-item-info">
            <strong title={group.title}>{group.title}</strong>
            <span>{group.people} người yêu cầu{group.waiting > 0 ? ` · ${group.waiting} chờ xét duyệt` : ""}</span>
          </div>
        </div>
        <div className="claim-modern-count-cell col-center" data-label="Số yêu cầu">{group.claims} yêu cầu</div>
        <div className="claim-modern-date-cell col-center" data-label="Ngày gần nhất">{claimShortDate(group.latest)}</div>
        <div className="claim-modern-status-cell col-center" data-label="Trạng thái">
          <ClaimProgress stage={group.stage} />
        </div>
        <div className="claim-modern-action-cell col-center"><span className="claim-modern-action-pill">Xem yêu cầu</span></div>
      </Link>)}
    </div>
  </div>;
}
