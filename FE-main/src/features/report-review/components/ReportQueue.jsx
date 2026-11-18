import ReportGroup from "./ReportGroup";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Search, RefreshCw, SlidersHorizontal } from "lucide-react";
import { ErrorBox, Empty } from "./Shared";
import { labels, reportTargets } from "../format";
import useReviewResource from "../hooks/useReviewResource";
const defaults = { page: 1, status: "ALL", kind: "ALL", target: "ALL", q: "", queue: "ALL" };
const queues = { ALL: "Tất cả", UNASSIGNED: "Chưa nhận xử lý", MINE: "Tôi phụ trách", WAITING: "Chờ phản hồi", REPLIED: "Có phản hồi cần xem" };

export default function ReportQueue({ onOpen }) {
  const [params, setParams] = useSearchParams();
  const filters = {
    ...defaults,
    ...Object.fromEntries(Object.keys(defaults).filter(key => params.has(key)).map(key => [key, params.get(key)])),
    page: Math.max(1, Math.min(100000, Number.parseInt(params.get("page"), 10) || 1)),
  };
  const [search, setSearch] = useState(filters.q);
  const [reload, setReload] = useState(0);
  const [showFilters, setShowFilters] = useState(false);
  const activeFilters = ["status", "kind", "target"].filter(key => filters[key] !== defaults[key]).length;
  useEffect(() => {
    if (search === (params.get("q") ?? "")) return;
    const timer = setTimeout(() => setParams(old => {
      const next = new URLSearchParams(old);
      if (search) next.set("q", search);
      else next.delete("q");
      next.delete("page");
      return next;
    }, { replace: true }), 350);
    return () => clearTimeout(timer);
  }, [search, params, setParams]);
  const { data, error } = useReviewResource("queue", filters, reload);
  const filter = (key, value) => setParams(old => {
    const next = new URLSearchParams(old);
    if (key !== "page") next.delete("page");
    if (value === defaults[key]) next.delete(key);
    else next.set(key, value);
    return next;
  }, { replace: true });
  return (
    <section className="rr-card rr-queue">
      <nav className="rr-tabs" aria-label="Việc cần xử lý">
        {Object.entries(queues).map(([value,label]) => <button key={value} aria-pressed={filters.queue===value} className={filters.queue===value ? "active" : ""} onClick={() => filter("queue",value)}>{label}</button>)}
      </nav>
      <label className="rr-mobile-queue">
        Việc cần xử lý
        <select value={filters.queue} onChange={event => filter("queue", event.target.value)}>
          {Object.entries(queues).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <div className={`rr-filters${showFilters ? " rr-filters-open" : ""}`}>
        <label className="rr-search">
          Tìm báo cáo
          <div>
            <Search size={18} />
            <input
              value={search}
              maxLength={200}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Tên bài, mã báo cáo, người gửi hoặc lý do…"
            />
          </div>
        </label>
        <button className="rr-mobile-filter-toggle" aria-expanded={showFilters} onClick={() => setShowFilters(!showFilters)}>
          <SlidersHorizontal size={16} aria-hidden="true" /> Bộ lọc {activeFilters > 0 && `(${activeFilters})`}
        </button>
        <label className="rr-extra-filter">
          Trạng thái
          <select
            value={filters.status}
            onChange={(e) => filter("status", e.target.value)}
          >
            <option value="ALL">Tất cả trạng thái</option>
            {["OPEN", "IN_REVIEW", "RESOLVED", "REJECTED"].map((s) => (
              <option key={s} value={s}>
                {labels[s]}
              </option>
            ))}
          </select>
        </label>
        <label className="rr-extra-filter">
          Loại báo cáo
          <select
            value={filters.kind}
            onChange={(e) => filter("kind", e.target.value)}
          >
            <option value="ALL">Tất cả loại</option>
            <option value="GENERAL">Báo cáo vi phạm</option>
            <option value="HANDOVER_ISSUE">Vướng mắc trao trả</option>
          </select>
        </label>
        <label className="rr-extra-filter">
          Mục được báo cáo
          <select
            value={filters.target}
            onChange={(e) => filter("target", e.target.value)}
          >
            {Object.entries({ ALL: "Tất cả", ...reportTargets }).map(([v, t]) => (
              <option key={v} value={v}>
                {t}
              </option>
            ))}
          </select>
        </label>
      </div>
      <ErrorBox>{error}</ErrorBox>
      {error && (
        <button onClick={() => setReload((x) => x + 1)}>Thử lại</button>
      )}
      {!data && !error && <Empty>Đang tải báo cáo…</Empty>}
      {data && (
        <>
          <div className="rr-row rr-list-meta">
            <span role="status"><strong>{data.totalReports ?? data.totalElements} báo cáo</strong> · {data.totalElements} mục</span>
            <button onClick={() => setReload((x) => x + 1)}>
              <RefreshCw size={15} /> Làm mới
            </button>
          </div>
          {!data.content.length ? (
            <Empty>Chưa có báo cáo phù hợp. Thử đổi bộ lọc hoặc từ khóa.</Empty>
          ) : (
            <div className="rr-group-list">
              {data.content.map((report) => (
                <ReportGroup key={report.report_id} report={report} filters={filters} onOpen={onOpen} />
              ))}
            </div>
          )}
          <footer className="rr-pagination">
            <button
              disabled={data.page <= 1}
              onClick={() => filter("page", data.page - 1)}
            >
              Trước
            </button>
            <span>
              Trang {data.page}/{data.totalPages}
            </span>
            <button
              disabled={data.page >= data.totalPages}
              onClick={() => filter("page", data.page + 1)}
            >
              Sau
            </button>
          </footer>
        </>
      )}
    </section>
  );
}
