import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  ArrowUpDown,
  Calendar,
  Inbox,
  MapPin,
  Pencil,
  Plus,
  RotateCcw,
  Search,
} from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { officeApi } from "../api/officeApi";
import { date } from "../officeUtils";
import { TIME_RANGE_OPTIONS, matchesTimeFilter } from "@/features/claims/utils/claimUtils";
import useLoad from "../hooks/useLoad";
import LocationDialog from "./LocationDialog";
import Badge from "./Badge";
import Notice from "./Notice";
import LoadState from "./LoadState";
import Empty from "./Empty";
import Pager from "./Pager";
import "../Office.css";
import "../StaffWorkspace.css";

const DEFAULT_FILTERS = { page: 1, q: "", locationId: "", timeRange: "ALL", sort: "NEWEST" };

export default function StorageWorkspace({ locationsOnly = false }) {
  const { isManager } = useAuth();
  const tab = locationsOnly ? "locations" : "items";

  const [filters, setFilters] = useState(() => {
    try {
      const saved = JSON.parse(sessionStorage.getItem("office-storage-filters"));
      return saved ? { ...DEFAULT_FILTERS, ...saved } : DEFAULT_FILTERS;
    } catch {
      return DEFAULT_FILTERS;
    }
  });

  useEffect(() => {
    try {
      sessionStorage.setItem("office-storage-filters", JSON.stringify(filters));
    } catch {
      /* Storage is optional. */
    }
  }, [filters]);

  const apiParams = useMemo(
    () => ({ page: filters.page, q: filters.q, locationId: filters.locationId }),
    [filters.page, filters.q, filters.locationId],
  );

  const items = useLoad(
    () => (locationsOnly ? Promise.resolve(null) : officeApi.stored(apiParams)),
    JSON.stringify(apiParams) + locationsOnly,
  );
  const locations = useLoad(officeApi.locations, "locations");
  const [shelfCounts, setShelfCounts] = useState({});
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState("");

  const activeLocations = useMemo(
    () => (locations.data || []).filter((l) => l.active),
    [locations.data],
  );

  useEffect(() => {
    if (locationsOnly || !activeLocations.length) return;
    let active = true;
    const queries = [
      { key: "", locationId: "" },
      ...activeLocations.slice(0, 8).map((loc) => ({ key: String(loc.id), locationId: String(loc.id) })),
    ];
    Promise.allSettled(
      queries.map(({ locationId }) => officeApi.stored({ page: 1, q: filters.q, locationId })),
    ).then((settled) => {
      if (!active) return;
      const next = {};
      queries.forEach(({ key }, idx) => {
        const res = settled[idx];
        next[key] = res?.status === "fulfilled" ? Number(res.value?.totalElements ?? 0) : 0;
      });
      setShelfCounts(next);
    });
    return () => {
      active = false;
    };
  }, [locationsOnly, activeLocations, filters.q, items.data?.totalElements]);

  const [showExtraFilters, setShowExtraFilters] = useState(false);
  const change = (key, value) => setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  const resetFilters = () => {
    setFilters(DEFAULT_FILTERS);
    setShowExtraFilters(false);
  };

  const hasActiveFilter =
    Boolean(filters.q?.trim?.()) ||
    Boolean(filters.locationId) ||
    filters.timeRange !== "ALL" ||
    filters.sort !== "NEWEST";

  const displayedItems = useMemo(() => {
    const raw = items.data?.content || [];
    const byTime =
      filters.timeRange === "ALL"
        ? raw
        : raw.filter((item) => matchesTimeFilter(item.receivedAt, filters.timeRange, "", ""));
    const sorted = [...byTime];
    sorted.sort((a, b) => {
      if (filters.sort === "CODE") {
        return String(a.code || "").localeCompare(String(b.code || ""), "vi");
      }
      const diff = new Date(a.receivedAt || 0).getTime() - new Date(b.receivedAt || 0).getTime();
      return filters.sort === "OLDEST" ? diff : -diff;
    });
    return sorted;
  }, [items.data?.content, filters.timeRange, filters.sort]);

  return (
    <div className="sw-page">
      <header className="sw-heading">
        <h1>{locationsOnly ? "Vị trí lưu trữ kho" : "Tài sản lưu kho"}</h1>
        {!locationsOnly && <p>Tra cứu tài sản đang lưu kho theo vị trí lưu trữ.</p>}
      </header>

      <Notice>{message}</Notice>

      {tab === "items" ? (
        <>
          {/* Card 1: Filter Controls */}
          <section className="sw-controls" aria-label="Bộ lọc kho lưu trữ">
            <div className="sw-controls-top">
              <label className="sw-search">
                <Search size={17} aria-hidden="true" />
                <input
                  type="search"
                  maxLength={200}
                  value={filters.q}
                  onChange={(e) => change("q", e.target.value)}
                  placeholder="Tìm tên đồ vật hoặc mã tiếp nhận…"
                  aria-label="Tìm đồ đang lưu kho"
                />
              </label>

              {isManager && (
                <div className="sw-segmented" role="navigation" aria-label="Quản lý kho">
                  <Link className={!locationsOnly ? "is-active" : ""} to="/admin/storage">
                    Đồ đang lưu giữ
                  </Link>
                  <Link className={locationsOnly ? "is-active" : ""} to="/admin/storage/locations">
                    Vị trí lưu trữ
                  </Link>
                </div>
              )}
            </div>

            <div className="sw-filter-row">
              <div className="sw-status-group">
                <span className="sw-filter-label">Vị trí ngăn tủ</span>
                <div className="sw-status-pills" role="group" aria-label="Lọc nhanh theo ngăn tủ">
                  <button
                    type="button"
                    aria-pressed={filters.locationId === ""}
                    onClick={() => change("locationId", "")}
                    className={filters.locationId === "" ? "is-active" : ""}
                  >
                    Tất cả{typeof shelfCounts[""] === "number" ? ` (${shelfCounts[""]})` : ""}
                  </button>
                  {activeLocations.slice(0, 8).map((loc) => {
                    const key = String(loc.id);
                    const active = String(filters.locationId) === key;
                    const count = shelfCounts[key];
                    return (
                      <button
                        key={loc.id}
                        type="button"
                        aria-pressed={active}
                        onClick={() => change("locationId", key)}
                        className={active ? "is-active" : ""}
                      >
                        {loc.code}
                        {typeof count === "number" ? ` (${count})` : ""}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="sw-dropdowns">
                <div className="sw-field">
                  <label htmlFor="sw-storage-time">
                    <Calendar size={13} aria-hidden="true" />
                    Thời gian
                  </label>
                  <select
                    id="sw-storage-time"
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
                  <label htmlFor="sw-storage-sort">
                    <ArrowUpDown size={13} aria-hidden="true" />
                    Sắp xếp
                  </label>
                  <select
                    id="sw-storage-sort"
                    value={filters.sort}
                    onChange={(e) => change("sort", e.target.value)}
                  >
                    <option value="NEWEST">Mới tiếp nhận</option>
                    <option value="OLDEST">Lưu kho lâu nhất</option>
                    <option value="CODE">Theo mã tiếp nhận</option>
                  </select>
                </div>

                <button
                  type="button"
                  className={`sw-extra-toggle-btn ${showExtraFilters ? "is-active" : ""}`}
                  onClick={() => setShowExtraFilters((v) => !v)}
                  title="Chọn vị trí cụ thể"
                  aria-expanded={showExtraFilters}
                >
                  <MapPin size={13} aria-hidden="true" />
                  Vị trí tủ
                </button>

                {hasActiveFilter && (
                  <button type="button" className="sw-reset-btn" onClick={resetFilters}>
                    <RotateCcw size={13} aria-hidden="true" />
                    Đặt lại
                  </button>
                )}
              </div>
            </div>

            {showExtraFilters && (
              <div className="sw-extra-filter-bar" role="group" aria-label="Bộ lọc vị trí kho">
                <div className="sw-field">
                  <label htmlFor="sw-storage-loc">
                    <MapPin size={13} aria-hidden="true" />
                    Tất cả ngăn tủ trong kho
                  </label>
                  <select
                    id="sw-storage-loc"
                    value={filters.locationId}
                    onChange={(e) => change("locationId", e.target.value)}
                  >
                    <option value="">Tất cả vị trí</option>
                    {(locations.data || []).map((l) => (
                      <option key={l.id} value={String(l.id)}>
                        {l.code} · {l.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            )}
          </section>

          {/* Card 2: Stored Items Table */}
          <LoadState state={items}>
            {items.data && (
              <>
                {!displayedItems.length ? (
                  <div className="sw-empty-card">
                    <Inbox size={32} strokeWidth={1.6} aria-hidden="true" />
                    <h3>Không có đồ đang lưu kho phù hợp</h3>
                    <p>
                      {hasActiveFilter
                        ? "Thử đổi từ khóa hoặc đặt lại bộ lọc để xem toàn bộ kho."
                        : "Đồ vật sau khi xác nhận tiếp nhận sẽ hiển thị tại đây."}
                    </p>
                  </div>
                ) : (
                  <>
                    <div className="sw-results-bar">
                      <h2>Tài sản đang bảo quản tại văn phòng</h2>
                      <span className="sw-results-count">
                        {displayedItems.length} / {items.data.totalElements} món đồ
                      </span>
                    </div>

                    <div className="sw-table-card">
                      <div className="sw-table-head sw-grid-storage" aria-hidden="true">
                        <span>Đồ vật & Mã tiếp nhận</span>
                        <span>Vị trí lưu kho</span>
                        <span>Thông tin tiếp nhận</span>
                        <span style={{ textAlign: "right" }}>Thao tác</span>
                      </div>

                      <div className="sw-table-body">
                        {displayedItems.map((item) => (
                          <article key={item.id} className="sw-table-row sw-grid-storage">
                            <div className="sw-item-cell">
                              <div className="sw-item-text">
                                <Link
                                  className="sw-item-title"
                                  to={
                                    isManager
                                      ? `/admin/items/${item.postId}`
                                      : `/approver/receipts/${item.id}`
                                  }
                                >
                                  {item.title}
                                </Link>
                                <div className="sw-item-sub">
                                  {item.code && <span className="sw-code-tag">{item.code}</span>}
                                  {item.condition && <span>{item.condition}</span>}
                                </div>
                              </div>
                            </div>

                            <div>
                              <span
                                className={`sw-shelf-tag ${!item.location ? "is-missing" : ""}`}
                                title={item.location || "Chưa bố trí vị trí"}
                              >
                                <MapPin size={13} aria-hidden="true" />
                                <span>{item.location || "Chưa bố trí vị trí"}</span>
                              </span>
                            </div>

                            <div className="sw-meta-cell">
                              <strong>{item.receivedBy || "Văn phòng"}</strong>
                              <small>{date(item.receivedAt)}</small>
                            </div>

                            <div className="sw-action-cell">
                              {isManager ? (
                                <Link
                                  className="sw-action-pill is-default"
                                  to={`/admin/items/${item.postId}`}
                                >
                                  Xem bài đăng
                                  <ArrowRight size={14} aria-hidden="true" />
                                </Link>
                              ) : (
                                <>
                                  <Link
                                    className="sw-action-pill is-default"
                                    to={`/approver/receipts/${item.id}`}
                                  >
                                    Hồ sơ
                                  </Link>
                                  <Link
                                    className="sw-action-pill is-solid"
                                    to={`/approver/office-claims/new?postId=${item.postId}`}
                                  >
                                    Lập yêu cầu
                                    <ArrowRight size={14} aria-hidden="true" />
                                  </Link>
                                </>
                              )}
                            </div>
                          </article>
                        ))}
                      </div>
                    </div>
                  </>
                )}

                <Pager
                  data={items.data}
                  onChange={(page) => setFilters((f) => ({ ...f, page }))}
                />
              </>
            )}
          </LoadState>
        </>
      ) : (
        <>
          <section className="sw-controls">
            <div className="sw-controls-top">
              {isManager && (
                <div className="sw-segmented" role="navigation" aria-label="Quản lý kho">
                  <Link className={!locationsOnly ? "is-active" : ""} to="/admin/storage">
                    Đồ đang lưu giữ
                  </Link>
                  <Link className={locationsOnly ? "is-active" : ""} to="/admin/storage/locations">
                    Vị trí lưu trữ
                  </Link>
                </div>
              )}
              {isManager && (
                <button
                  type="button"
                  className="sw-primary-btn"
                  onClick={() => setEditing({})}
                >
                  <Plus size={16} />
                  Thêm vị trí
                </button>
              )}
            </div>
          </section>

          <LoadState state={locations}>
            {locations.data && (
              <div className="of-panel">
                <p className="of-muted">
                  Sắp xếp theo tòa nhà, phòng, tủ hoặc ngăn. Chỉ ngừng sử dụng khi vị trí và các vị
                  trí con không còn đồ.
                </p>
                <div className="of-table-wrap">
                  <table className="of-table of-storage-location-table">
                    <thead>
                      <tr>
                        <th>Vị trí</th>
                        <th>Thuộc vị trí</th>
                        <th>Trạng thái</th>
                        <th>Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {locations.data.map((l) => (
                        <tr key={l.id}>
                          <td data-label="Vị trí">
                            <strong>{l.name}</strong>
                            <small>{l.code}</small>
                          </td>
                          <td data-label="Thuộc vị trí">
                            {locations.data.find((p) => p.id === l.parentId)?.name || "Vị trí gốc"}
                          </td>
                          <td data-label="Trạng thái">
                            <Badge value={l.active ? "Đang sử dụng" : "Ngừng sử dụng"} />
                          </td>
                          <td>
                            <button
                              type="button"
                              className="sw-action-pill is-default"
                              onClick={() => setEditing(l)}
                              aria-label={`Chỉnh sửa ${l.name}`}
                            >
                              <Pencil size={14} />
                              Sửa
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {!locations.data.length && <Empty text="Chưa có vị trí lưu trữ" />}
              </div>
            )}
          </LoadState>
        </>
      )}

      {editing && (
        <LocationDialog
          key={editing.id || "new"}
          initial={editing}
          locations={locations.data || []}
          onClose={() => setEditing(null)}
          onDone={() => {
            setEditing(null);
            setMessage("Đã lưu vị trí kho.");
            locations.reload();
            items.reload();
          }}
        />
      )}
    </div>
  );
}
