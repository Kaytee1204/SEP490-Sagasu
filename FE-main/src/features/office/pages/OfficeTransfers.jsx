import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowRight,
  ArrowUpDown,
  Calendar,
  CalendarDays,
  Filter,
  Inbox,
  Package,
  Plus,
  RotateCcw,
  Search,
  SlidersHorizontal,
} from "lucide-react";
import { workflowApi } from "../api/workflowApi";
import useLoad from "../hooks/useLoad";
import { date } from "../officeUtils";
import { TIME_RANGE_OPTIONS, matchesTimeFilter } from "@/features/claims/utils/claimUtils";
import LoadState from "../components/LoadState";
import Empty from "../components/Empty";
import SearchBox from "../components/SearchBox";
import Select from "../components/Select";
import Pager from "../components/Pager";
import TransferStage from "../components/TransferStage";
import { transferStage } from "../transferStage";
import { useAuth } from "@/context/AuthContext";
import PersonalOfficeHeader from "../components/PersonalOfficeHeader";
import "../Office.css";
import "../PersonalOffice.css";
import "../StaffWorkspace.css";

const statuses = [
  ["PENDING", "Đang chờ tiếp nhận"],
  ["UNSCHEDULED", "Cần hẹn lịch"],
  ["AWAITING_REPLY", "Chờ phản hồi lịch"],
  ["CONFIRMED", "Đã đồng ý lịch"],
  ["RECEIVED", "Đã nhận đồ"],
  ["REJECTED", "Đã từ chối"],
  ["CANCELLED", "Đã hủy yêu cầu"],
  ["ALL", "Tất cả"],
];

const STAFF_TRANSFER_TABS = [
  { value: "ALL", label: "Tất cả" },
  { value: "PENDING", label: "Đang chờ", tone: "warning" },
  { value: "UNSCHEDULED", label: "Cần hẹn lịch", tone: "warning" },
  { value: "AWAITING_REPLY", label: "Chờ phản hồi" },
  { value: "CONFIRMED", label: "Đã đồng ý lịch" },
  { value: "RECEIVED", label: "Đã nhận đồ" },
  { value: "REJECTED", label: "Đã từ chối" },
];

function progress(t) {
  if (t.status !== "PENDING") return statuses.find(([s]) => s === t.status)?.[1];
  if (!t.appointment || ["DECLINED", "EXPIRED"].includes(t.appointment.responseStatus)) {
    return "Cần hẹn lịch";
  }
  return t.appointment.responseStatus === "CONFIRMED"
    ? "Đã đồng ý lịch"
    : t.appointment.responseStatus === "UNSENT"
      ? "Chưa gửi được lịch"
      : "Chờ phản hồi lịch";
}

export default function OfficeTransfers({ mine = false }) {
  const { user } = useAuth();
  const officer = !mine && user?.role === "APPROVER";
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();

  const defaultStatus = mine ? "ALL" : "PENDING";
  const [filters, setFilters] = useState({
    page: Math.max(1, Number(params.get("page")) || 1),
    status: params.get("status") || defaultStatus,
    q: params.get("q") || "",
    claimFilter: "ALL",
    timeRange: "ALL",
    sort: "NEWEST",
    mine,
  });

  const apiParams = useMemo(
    () => ({
      page: filters.page,
      status: filters.status,
      q: filters.q,
      mine,
    }),
    [filters.page, filters.status, filters.q, mine],
  );

  const state = useLoad(() => workflowApi.transfers(apiParams), JSON.stringify(apiParams));
  const [tabCounts, setTabCounts] = useState({});

  useEffect(() => {
    if (mine) return;
    let active = true;
    Promise.allSettled(
      STAFF_TRANSFER_TABS.map((tab) =>
        workflowApi.transfers({ page: 1, status: tab.value, q: filters.q, mine: false }),
      ),
    ).then((settled) => {
      if (!active) return;
      const next = {};
      STAFF_TRANSFER_TABS.forEach((tab, idx) => {
        const res = settled[idx];
        next[tab.value] = res?.status === "fulfilled" ? Number(res.value?.totalElements ?? 0) : 0;
      });
      setTabCounts(next);
    });
    return () => {
      active = false;
    };
  }, [mine, filters.q, state.data?.totalElements]);

  const base = location.pathname;
  const setSelected = (id, openSchedule = false) =>
    navigate(`${base}/${id}${location.search}`, { state: { openSchedule } });

  const change = (key, value) => {
    const next = { ...filters, [key]: value, page: key === "page" ? value : 1 };
    setFilters(next);
    if (["page", "status", "q"].includes(key)) {
      setParams({ page: next.page, status: next.status, q: next.q }, { replace: true });
    }
  };

  const [showExtraFilters, setShowExtraFilters] = useState(false);
  const extraActiveCount = filters.claimFilter !== "ALL" ? 1 : 0;
  const isExtraOpen = showExtraFilters || extraActiveCount > 0;

  const resetFilters = () => {
    setShowExtraFilters(false);
    setFilters({
      page: 1,
      status: defaultStatus,
      q: "",
      claimFilter: "ALL",
      timeRange: "ALL",
      sort: "NEWEST",
      mine,
    });
    setParams({}, { replace: true });
  };

  const hasActiveFilter =
    Boolean(filters.q?.trim?.()) ||
    filters.status !== defaultStatus ||
    (!mine && (filters.claimFilter !== "ALL" || filters.timeRange !== "ALL" || filters.sort !== "NEWEST"));

  const displayedContent = useMemo(() => {
    const raw = state.data?.content || [];
    if (mine) return raw;
    const filtered = raw.filter((t) => {
      if (filters.claimFilter === "HAS_CLAIMS" && Number(t.activeClaims || 0) <= 0) return false;
      if (filters.claimFilter === "NO_CLAIMS" && Number(t.activeClaims || 0) > 0) return false;
      if (filters.timeRange !== "ALL" && !matchesTimeFilter(t.requestedAt, filters.timeRange, "", "")) {
        return false;
      }
      return true;
    });
    const sorted = [...filtered];
    sorted.sort((a, b) => {
      const diff = new Date(a.requestedAt || 0).getTime() - new Date(b.requestedAt || 0).getTime();
      return filters.sort === "OLDEST" ? diff : -diff;
    });
    return sorted;
  }, [state.data?.content, mine, filters.claimFilter, filters.timeRange, filters.sort]);

  if (mine) {
    return (
      <div className="office-page office-personal">
        <PersonalOfficeHeader active="transfers" />
        <section className="of-panel">
          <div className="of-toolbar">
            <SearchBox
              value={filters.q}
              onChange={(q) => change("q", q)}
              placeholder="Tìm theo tên đồ vật…"
            />
            <Select
              label="Trạng thái tiếp nhận"
              value={filters.status}
              onChange={(s) => change("status", s)}
              options={statuses}
            />
          </div>
          <LoadState state={state}>
            {state.data && (
              <>
                {!displayedContent.length ? (
                  <Empty
                    text={
                      hasActiveFilter
                        ? "Không tìm thấy yêu cầu phù hợp"
                        : "Bạn chưa gửi yêu cầu tiếp nhận"
                    }
                    description={
                      hasActiveFilter
                        ? "Thử thay đổi từ khóa hoặc bỏ bộ lọc để xem lại danh sách."
                        : "Nếu đang giữ đồ nhặt được, mở bài đăng của bạn để nhờ văn phòng tiếp nhận và hỗ trợ trao trả."
                    }
                  >
                    {hasActiveFilter ? (
                      <button type="button" className="of-button" onClick={resetFilters}>
                        Xóa bộ lọc
                      </button>
                    ) : (
                      <Link className="of-button" to="/">
                        Về trang chủ
                      </Link>
                    )}
                  </Empty>
                ) : (
                  <div className="of-transfer-grid of-transfer-list">
                    {displayedContent.map((t) => (
                      <article className="of-transfer-card" key={t.id}>
                        {t.images[0] ? (
                          <img className="of-transfer-image" src={t.images[0]} alt={t.title} />
                        ) : (
                          <div className="of-transfer-image of-transfer-placeholder">
                            <Package size={28} />
                          </div>
                        )}
                        <div className="of-transfer-body">
                          <span
                            className={`of-transfer-status ${
                              t.status === "RECEIVED"
                                ? "is-complete"
                                : t.status === "CANCELLED" || t.status === "REJECTED"
                                  ? "is-cancelled"
                                  : ""
                            }`}
                          >
                            {progress(t)}
                          </span>
                          <h2>{t.title}</h2>
                          <p className="of-muted">
                            {t.finder} · {date(t.requestedAt)}
                          </p>
                          {t.status === "PENDING" &&
                            t.appointment &&
                            !["DECLINED", "EXPIRED"].includes(t.appointment.responseStatus) && (
                              <p>
                                <strong>{date(t.appointment.scheduledAt)}</strong>
                                <br />
                                {t.appointment.location}
                              </p>
                            )}
                          {t.activeClaims > 0 && <p>{t.activeClaims} yêu cầu nhận lại đồ</p>}
                          {t.reason && (
                            <p className="of-prose of-reason-preview">
                              <strong>Lý do:</strong> {t.reason}
                            </p>
                          )}
                        </div>
                        <div className="of-transfer-row-action">
                          <button
                            type="button"
                            className={`of-button ${
                              t.status === "PENDING" && t.appointment?.responseStatus === "PENDING"
                                ? "of-primary"
                                : ""
                            }`}
                            onClick={() => setSelected(t.id)}
                          >
                            {t.status === "PENDING" && t.appointment?.responseStatus === "PENDING"
                              ? "Phản hồi lịch hẹn"
                              : "Xem yêu cầu"}{" "}
                            <ArrowRight size={16} />
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>
                )}
                <Pager data={state.data} onChange={(page) => change("page", page)} />
              </>
            )}
          </LoadState>
        </section>
      </div>
    );
  }

  return (
    <div className="sw-page">
      <header className="sw-heading">
        <h1>{officer ? "Tiếp nhận tài sản từ bài đăng" : "Theo dõi chuyển giao"}</h1>
        {officer && <p>Xử lý yêu cầu chuyển giao, lập lịch hẹn và tiếp nhận tại quầy đối với tài sản đã có bài đăng.</p>}
      </header>

      {/* Card 1: Filter Controls */}
      <section className="sw-controls" aria-label="Bộ lọc yêu cầu tiếp nhận đồ">
        <div className="sw-controls-top">
          <label className="sw-search">
            <Search size={17} aria-hidden="true" />
            <input
              type="search"
              maxLength={200}
              value={filters.q}
              onChange={(e) => change("q", e.target.value)}
              placeholder="Tìm theo tên đồ vật hoặc người giao…"
              aria-label="Tìm kiếm yêu cầu tiếp nhận"
            />
          </label>

          {officer && (
            <button
              type="button"
              className="sw-primary-btn"
              onClick={() => navigate("/approver/transfers/new")}
            >
              <Plus size={16} aria-hidden="true" />
              Tiếp nhận tại quầy
            </button>
          )}
        </div>

        <div className="sw-filter-row">
          <div className="sw-status-group">
            <span className="sw-filter-label">Trạng thái</span>
            <div className="sw-status-pills" role="group" aria-label="Lọc theo trạng thái tiếp nhận">
              {STAFF_TRANSFER_TABS.map((tab) => {
                const active = filters.status === tab.value;
                const count = tabCounts[tab.value];
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
              <label htmlFor="sw-transfer-time">
                <Calendar size={13} aria-hidden="true" />
                Thời gian
              </label>
              <select
                id="sw-transfer-time"
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
              <label htmlFor="sw-transfer-sort">
                <ArrowUpDown size={13} aria-hidden="true" />
                Sắp xếp
              </label>
              <select
                id="sw-transfer-sort"
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
              <label htmlFor="sw-transfer-claims">
                <Filter size={13} aria-hidden="true" />
                Yêu cầu nhận đi kèm
              </label>
              <select
                id="sw-transfer-claims"
                value={filters.claimFilter}
                onChange={(e) => change("claimFilter", e.target.value)}
              >
                <option value="ALL">Tất cả</option>
                <option value="HAS_CLAIMS">Có người đang claim</option>
                <option value="NO_CLAIMS">Chưa có claim</option>
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
                  {hasActiveFilter
                    ? "Không tìm thấy yêu cầu phù hợp"
                    : "Chưa có yêu cầu tiếp nhận nào"}
                </h3>
                <p>
                  {hasActiveFilter
                    ? "Thử thay đổi từ khóa hoặc đặt lại bộ lọc để xem toàn bộ danh sách."
                    : "Khi người nhặt đồ gửi yêu cầu chuyển giao về văn phòng, hồ sơ sẽ hiển thị tại đây."}
                </p>
              </div>
            ) : (
              <>
                <div className="sw-results-bar">
                  <h2>Danh sách yêu cầu chuyển giao về văn phòng</h2>
                  <span className="sw-results-count">
                    {displayedContent.length} / {state.data.totalElements} yêu cầu
                  </span>
                </div>

                <div className="sw-table-card">
                  <div className="sw-table-head sw-grid-transfers" aria-hidden="true">
                    <span>Đồ vật & Người giao</span>
                    <span>Tiến trình tiếp nhận</span>
                    <span>Lịch hẹn & Kết quả</span>
                    <span style={{ textAlign: "right" }}>Thao tác</span>
                  </div>

                  <div className="sw-table-body">
                    {displayedContent.map((transfer) => {
                      const stage = transferStage(transfer);
                      const appointment = transfer.appointment;
                      const canSchedule =
                        officer && transfer.requestedBy !== user?.id && stage === "UNSCHEDULED";
                      const active = transfer.status === "PENDING";

                      return (
                        <article key={transfer.id} className="sw-table-row sw-grid-transfers">
                          <div className="sw-item-cell">
                            <div className="sw-thumb">
                              {transfer.images?.[0] ? (
                                <img src={transfer.images[0]} alt="" loading="lazy" />
                              ) : (
                                <Package size={22} aria-hidden="true" />
                              )}
                            </div>
                            <div className="sw-item-text">
                              <button
                                type="button"
                                onClick={() => setSelected(transfer.id, canSchedule)}
                                className="sw-item-title"
                                style={{
                                  background: "none",
                                  border: 0,
                                  padding: 0,
                                  textAlign: "left",
                                  cursor: "pointer",
                                }}
                              >
                                {transfer.title}
                              </button>
                              <div className="sw-item-sub">
                                <span className="sw-code-tag">#{transfer.id}</span>
                                <span>{transfer.finder || "Chưa ghi nhận"}</span>
                                <span>· {date(transfer.requestedAt)}</span>
                              </div>
                            </div>
                          </div>

                          <div className="sw-meta-cell">
                            <TransferStage stage={stage} />
                            {active && transfer.activeClaims > 0 && (
                              <small>{transfer.activeClaims} yêu cầu nhận lại đi kèm</small>
                            )}
                          </div>

                          <div className="sw-meta-cell">
                            {active && appointment ? (
                              <>
                                <small style={{ display: "inline-flex", alignItems: "center", gap: 5 }}>
                                  <CalendarDays size={13} aria-hidden="true" />
                                  {stage === "UNSCHEDULED" ? "Lịch cũ" : "Lịch mang đồ đến"}
                                </small>
                                <strong>{date(appointment.scheduledAt)}</strong>
                                {appointment.location && <small>{appointment.location}</small>}
                              </>
                            ) : active ? (
                              <small>Chưa xếp lịch hẹn</small>
                            ) : (
                              <>
                                {transfer.processedAt && (
                                  <small>Xử lý lúc {date(transfer.processedAt)}</small>
                                )}
                                {transfer.reason && <small>{transfer.reason}</small>}
                                {transfer.receiptId && (
                                  <strong>Hồ sơ tiếp nhận #{transfer.receiptId}</strong>
                                )}
                              </>
                            )}
                          </div>

                          <div className="sw-action-cell">
                            <button
                              type="button"
                              className={`sw-action-pill ${canSchedule ? "is-solid" : "is-default"}`}
                              onClick={() => setSelected(transfer.id, canSchedule)}
                            >
                              {canSchedule ? "Sắp xếp lịch" : "Xem & xử lý"}
                              <ArrowRight size={14} aria-hidden="true" />
                            </button>
                          </div>
                        </article>
                      );
                    })}
                  </div>
                </div>
              </>
            )}

            <Pager data={state.data} onChange={(page) => change("page", page)} />
          </>
        )}
      </LoadState>
    </div>
  );
}
