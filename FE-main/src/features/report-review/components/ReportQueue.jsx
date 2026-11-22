import { useEffect, useId, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ArrowLeft, ArrowRight, Inbox, Search, SlidersHorizontal, X,
} from "lucide-react";
import ReportGroup from "./ReportGroup";
import { ErrorBox } from "./Shared";
import { reportTargets } from "../format";
import useReviewResource from "../hooks/useReviewResource";

const defaults = { page: 1, target: "ALL", q: "", queue: "READY", assignee: "ALL" };
const queues = {
  READY: "Cần xử lý",
  WAITING: "Đang chờ",
  HISTORY: "Đã kết thúc",
};
const assignees = {
  ALL: "Phụ trách · Tất cả",
  UNASSIGNED: "Chưa nhận",
  MINE: "Tôi phụ trách",
};
const violationTargets = { ALL: "Tất cả đối tượng", POST: reportTargets.POST, CLAIM: reportTargets.CLAIM };
const isReportReference = value => /^(?:#\s*)?[0-9]+$/.test(value.trim());

export default function ReportQueue({ kind, reportPath }) {
  const [params, setParams] = useSearchParams();
  const advancedId = useId();
  const queue = params.get("queue");
  const storedAssignee = params.get("assignee") ?? queue;
  const filters = {
    kind,
    page: Math.max(1, Math.min(100000, Number.parseInt(params.get("page"), 10) || 1)),
    target: kind === "GENERAL" && Object.hasOwn(violationTargets, params.get("target")) ? params.get("target") : defaults.target,
    queue: Object.hasOwn(queues, queue) ? queue
      : ["RESOLVED", "REJECTED"].includes(params.get("status")) ? "HISTORY" : defaults.queue,
    assignee: Object.hasOwn(assignees, storedAssignee) ? storedAssignee : defaults.assignee,
    q: params.get("q") ?? "",
  };
  const [showAdvanced, setShowAdvanced] = useState(filters.target !== defaults.target);
  const [draft, setDraft] = useState(null);
  const [reload, setReload] = useState(0);
  const search = draft?.base === filters.q ? draft.value : filters.q;
  const lookupById = isReportReference(filters.q);
  const isFiltered = Boolean(filters.q || search || filters.assignee !== defaults.assignee || filters.target !== defaults.target);
  useEffect(() => {
    if (search === filters.q) return;
    const timer = setTimeout(() => {
      setDraft(null);
      setParams(old => {
        const next = new URLSearchParams(old);
        if (search.trim()) next.set("q", search.trim());
        else next.delete("q");
        next.delete("page");
        return next;
      }, { replace: true });
    }, 350);
    return () => clearTimeout(timer);
  }, [search, filters.q, setParams]);
  useEffect(() => {
    const refresh = () => setReload(value => value + 1);
    window.addEventListener("focus", refresh);
    window.addEventListener("report-review-changed", refresh);
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") refresh();
    }, 60_000);
    return () => {
      window.removeEventListener("focus", refresh);
      window.removeEventListener("report-review-changed", refresh);
      window.clearInterval(timer);
    };
  }, []);
  const { data, error } = useReviewResource("queue", filters, reload);
  useEffect(() => {
    if (data && filters.page > Math.max(1, data.totalPages)) {
      setParams(old => {
        const next = new URLSearchParams(old);
        if (data.totalPages > 1) next.set("page", String(data.totalPages));
        else next.delete("page");
        return next;
      }, { replace: true });
    }
  }, [data, filters.page, setParams]);
  const loading = !data && !error;

  function updateFilters(changes) {
    setParams(old => {
      const next = new URLSearchParams(old);
      next.delete("status");
      for (const [key, value] of Object.entries({ queue: filters.queue, assignee: filters.assignee, page: defaults.page, ...changes })) {
        if (value === defaults[key]) next.delete(key);
        else next.set(key, value);
      }
      return next;
    }, { replace: true });
  }

  function selectQueue(value) {
    const clearReference = lookupById || isReportReference(search);
    if (clearReference) setDraft(null);
    updateFilters(clearReference ? { queue: value, q: "" } : { queue: value });
  }

  function resetFilters() {
    setDraft(null);
    updateFilters({ assignee: defaults.assignee, target: defaults.target, q: "" });
  }

  return <section className="rr-queue-panel" aria-label="Hồ sơ báo cáo" aria-busy={loading}>
      <div className="rr-queue-toolbar">
        <div className="rr-queue-views" role="group" aria-label="Hàng đợi xử lý">
          {Object.entries(queues).map(([value, label]) => (
            <button type="button" key={value} aria-pressed={!lookupById && filters.queue === value} onClick={() => selectQueue(value)}>
              {label}{!lookupById && filters.queue === value && data?.totalElements > 0 &&
                <span className="rr-tab-count">{data.totalElements}</span>}
            </button>
          ))}
        </div>
        <div className="rr-queue-filters">
          <div className="rr-queue-search">
            <Search size={17} aria-hidden="true" />
            <input type="search" aria-label="Tìm báo cáo" value={search} maxLength={200}
              onChange={event => setDraft({ base: filters.q, value: event.target.value })}
              placeholder="Tìm theo tên bài, mã hoặc lý do" aria-describedby={lookupById ? `${advancedId}-search` : undefined} />
          </div>
          {!lookupById && <select aria-label="Phụ trách" value={filters.assignee}
            onChange={event => updateFilters({ assignee: event.target.value })}>
            {Object.entries(assignees).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>}
          {kind === "GENERAL" && !lookupById && <button type="button" className="rr-more-filters"
            aria-controls={advancedId} aria-expanded={showAdvanced} onClick={() => setShowAdvanced(value => !value)}>
            <SlidersHorizontal size={15} aria-hidden="true" /> Lọc thêm{filters.target !== defaults.target && " (1)"}
          </button>}
          {isFiltered && <button type="button" className="rr-clear-filters" onClick={resetFilters}>
            <X size={15} aria-hidden="true" /> Xóa lọc
          </button>}
        </div>
        {kind === "GENERAL" && <div id={advancedId} className="rr-extra-filters" hidden={!showAdvanced || lookupById}>
          <label>
            Đối tượng báo cáo
            <select value={filters.target}
              onChange={event => updateFilters({ target: event.target.value })}>
              {Object.entries(violationTargets).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
        </div>}
        {lookupById && <p id={`${advancedId}-search`} className="rr-search-scope">
          Đang tìm mã báo cáo trong mọi trạng thái của trang này.
        </p>}
      </div>
      <ErrorBox>{error}</ErrorBox>
      {loading && <p className="rr-queue-state" role="status">Đang tải hồ sơ…</p>}
      {data && !data.content.length && <div className="rr-queue-state">
        <Inbox size={25} aria-hidden="true" />
        <strong>{lookupById ? "Không tìm thấy mã báo cáo trong trang này"
          : isFiltered ? "Không có hồ sơ phù hợp"
          : filters.queue === "READY" ? "Hiện chưa có hồ sơ cần xử lý" : "Hàng đợi đang trống"}</strong>
        {isFiltered && <button type="button" onClick={resetFilters}>Xóa bộ lọc để xem lại</button>}
        {!isFiltered && filters.queue === "READY" && <button type="button" onClick={() => selectQueue("WAITING")}>Xem hồ sơ đang chờ</button>}
      </div>}
      {data?.content.length > 0 && <div className="rr-queue-results">
        <table className="rr-queue-table" aria-label="Danh sách hồ sơ báo cáo" role="table">
          <thead role="rowgroup"><tr role="row">
            <th scope="col" role="columnheader">Hồ sơ</th>
            <th scope="col" role="columnheader">Lý do / ưu tiên</th>
            <th scope="col" role="columnheader">Trạng thái</th>
            <th scope="col" role="columnheader">Phụ trách</th>
            <th scope="col" role="columnheader"><span className="sr-only">Mở hồ sơ</span></th>
          </tr></thead>
          {data.content.map(report => <ReportGroup key={`${kind}:${report.report_id}`} report={report}
            filters={filters} reportPath={reportPath} revision={reload} timeZone={data.workCalendar?.zone} />)}
        </table>
      </div>}
      {data?.totalPages > 1 && <nav className="rr-queue-pagination" aria-label="Trang hồ sơ">
        <span>Trang {data.page} / {data.totalPages}</span>
        <button type="button" disabled={data.page <= 1} onClick={() => updateFilters({ page: data.page - 1 })}>
          <ArrowLeft size={15} aria-hidden="true" /> Trước
        </button>
        <button type="button" disabled={data.page >= data.totalPages} onClick={() => updateFilters({ page: data.page + 1 })}>
          Sau <ArrowRight size={15} aria-hidden="true" />
        </button>
      </nav>}
  </section>;
}
