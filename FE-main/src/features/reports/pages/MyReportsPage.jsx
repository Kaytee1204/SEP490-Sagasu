import '@/styles/Workspace.css';
import { useEffect } from 'react';
import {
  ArrowRight, ClipboardCheck, Clock3, FileText, Flag, Handshake, Inbox, MessageSquare, Scale, Send, UserRound,
} from 'lucide-react';
import { Link, useLocation, useOutletContext, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import useReportResource from '../hooks/useReportResource';
import { getReportWorkspace } from '../api/reportApi';
import { reportStatuses, reportTitles } from '../constants/reportConstants';
import { appealSteps, formatReportDate, reportDetailPath, reportsPath, reportSubject } from '../utils/reportUtils';
import '../styles/Reports.css';

const reportTypes = {
  ALL: 'Tất cả loại',
  VIOLATION: 'Vi phạm',
  HANDOVER_ISSUE: 'Sự cố bàn giao',
  CLAIM_APPEAL: 'Tranh chấp',
};
const statusFilters = { ALL: 'Tất cả', ACTION: 'Cần tôi xử lý', IN_PROGRESS: 'Đang xử lý', FINISHED: 'Đã kết thúc' };
const targetIcons = {
  POST: FileText, USER: UserRound, CLAIM: ClipboardCheck, MESSAGE: MessageSquare,
  HANDOVER_ISSUE: Handshake, CLAIM_APPEAL: Scale,
};
const finished = status => status === 'RESOLVED' || status === 'REJECTED';

export default function MyReportsPage() {
  const { user } = useAuth();
  return <MyReports key={user.id} />;
}

function MyReports() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const { reportActions } = useOutletContext();
  const mine = params.get('tab') !== 'related';
  const status = Object.hasOwn(statusFilters, params.get('status')) ? params.get('status') : 'ALL';
  const type = Object.hasOwn(reportTypes, params.get('type')) ? params.get('type') : 'ALL';
  const requestedPage = Number(params.get('page'));
  const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 && requestedPage <= 2147483647 ? requestedPage : 1;
  const { data, loading, error, reload } = useReportResource(
    `${mine}:${status}:${type}:${page}`,
    () => getReportWorkspace({ page, mine, status, type }),
  );

  useEffect(() => {
    if (data && page > Math.max(1, data.totalPages)) {
      setParams(previous => {
        const next = new URLSearchParams(previous);
        const lastPage = Math.max(1, data.totalPages);
        if (lastPage > 1) next.set('page', String(lastPage));
        else next.delete('page');
        return next;
      }, { replace: true });
    }
  }, [data, page, setParams]);

  function setFilters(changes) {
    setParams(previous => {
      const next = new URLSearchParams(previous);
      next.delete('page');
      for (const [key, value] of Object.entries(changes)) {
        if (value) next.set(key, value);
        else next.delete(key);
      }
      return next;
    });
  }

  const items = data?.content ?? [];
  const pending = items.filter(report => report.needs_reply || report.needs_reopen);
  const others = items.filter(report => !report.needs_reply && !report.needs_reopen);
  const filtered = status !== 'ALL' || type !== 'ALL';
  const returnTo = location.pathname + location.search;
  return (
    <main className="container reports-page reports-dashboard">
      <header className="reports-heading">
        <h1>Báo cáo của tôi</h1>
        <p>Theo dõi báo cáo đã gửi và những vụ việc cần bạn phản hồi.</p>
      </header>
      <div className="reports-toolbar">
        <nav className="report-scope" aria-label="Phạm vi báo cáo">
          <Link to={reportsPath} aria-current={mine ? 'page' : undefined}>
            <Send size={17} aria-hidden="true" /> Tôi đã gửi
            {reportActions.mine > 0 && <span className="report-action-count" aria-label={`${reportActions.mine} cần xử lý`}>{reportActions.mine}</span>}
          </Link>
          <Link to="?tab=related" aria-current={!mine ? 'page' : undefined}>
            <Inbox size={17} aria-hidden="true" /> Liên quan đến tôi
            {reportActions.related > 0 && <span className="report-action-count" aria-label={`${reportActions.related} cần xử lý`}>{reportActions.related}</span>}
          </Link>
        </nav>
        <div className="reports-filters">
          <div className="report-status-filters" role="group" aria-label="Trạng thái báo cáo">
            {Object.entries(statusFilters).map(([value, label]) => (
              <button type="button" key={value} aria-pressed={status === value}
                onClick={() => setFilters({ status: value === 'ALL' ? null : value })}>
                {label}
              </button>
            ))}
          </div>
          <label className="reports-type-filter">
            <span>Loại báo cáo</span>
            <select value={type} onChange={event => setFilters({ type: event.target.value === 'ALL' ? null : event.target.value })}>
              {Object.entries(reportTypes).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
        </div>
      </div>
      {loading && !data && <p className="reports-count" role="status">Đang tải báo cáo…</p>}
      {error && <div className="report-card">
        <p className="report-error" role="alert">{error}</p>
        <button className="btn btn-ghost" type="button" onClick={reload}>Thử tải lại</button>
      </div>}
      {data && items.length > 0 && <>
        {pending.length > 0 && <section className="report-list-section" aria-labelledby="reports-action-title">
          <h2 id="reports-action-title">Cần bạn xử lý</h2>
          {pending.map(report => <ReportRow key={report.report_id} report={report} returnTo={returnTo} />)}
        </section>}
        {others.length > 0 && <section className="report-list-section" aria-labelledby="reports-list-title">
          <h2 id="reports-list-title">{pending.length ? 'Các báo cáo khác' : 'Danh sách báo cáo'}</h2>
          {others.map(report => <ReportRow key={report.report_id} report={report} returnTo={returnTo} />)}
        </section>}
        <p className="reports-count" role="status">{data.totalElements} báo cáo</p>
        <Pagination currentPage={page} totalPages={data.totalPages}
          onPageChange={next => setFilters({ page: next === 1 ? null : String(next) })} />
      </>}
      {data && data.totalElements === 0 && (
        <div className="report-card reports-empty">
          <h2>{filtered ? 'Chưa có báo cáo phù hợp' : mine ? 'Bạn chưa gửi báo cáo nào' : 'Chưa có báo cáo liên quan đến bạn'}</h2>
          {filtered && <button type="button" className="btn btn-ghost" onClick={() => setFilters({ status: null, type: null })}>
            Xóa bộ lọc
          </button>}
        </div>
      )}
    </main>
  );
}

function ReportRow({ report, returnTo }) {
  const Icon = targetIcons[report.report_type] || Flag;
  const title = reportTitles[report.report_type] || 'Báo cáo';
  const subject = reportSubject(report);
  const needsAction = report.needs_reply || report.needs_reopen;
  const isFinished = finished(report.status);
  const state = report.needs_reply ? 'Cần bạn phản hồi'
    : report.needs_reopen ? 'Cần xét lại người nhận' : appealSteps[report.appeal_step] || reportStatuses[report.status];
  const action = report.needs_reply ? 'Gửi thông tin bổ sung'
    : report.needs_reopen ? 'Xem việc cần làm' : report.appeal_step ? 'Xem bước tiếp theo' : isFinished ? 'Xem kết quả' : 'Xem tiến trình';
  const hash = report.needs_reply ? '#phan-hoi' : report.appeal_step ? '#buoc-tiep-theo' : isFinished ? '#ket-qua' : '';
  return (
    <article className={`report-entry${needsAction ? ' report-entry-action' : ''}`}>
      <span className="report-entry-icon"><Icon size={19} aria-hidden="true" /></span>
      <div className="report-entry-content">
        <h3>{title}</h3>
        {subject.value && <p>{subject.label} <span className="report-meta-dot">·</span> <strong>{subject.value}</strong></p>}
        <div className="report-entry-meta">
          <span>Báo cáo #{report.report_id}</span>
          <time dateTime={report.created_at}><Clock3 size={13} aria-hidden="true" /> Gửi lúc {formatReportDate(report.created_at)}</time>
        </div>
      </div>
      <div className="report-entry-actions">
        <span className={`report-state report-state-${needsAction ? 'ACTION' : report.appeal_step ? 'IN_REVIEW' : report.status}`}>{state}</span>
        <Link className={`btn ${needsAction ? 'btn-accent' : 'btn-ghost'}`}
          to={reportDetailPath(report.report_id) + hash} state={{ returnTo }}
          aria-label={`${action} báo cáo #${report.report_id}`}>
          {action}<ArrowRight size={15} aria-hidden="true" />
        </Link>
      </div>
    </article>
  );
}
