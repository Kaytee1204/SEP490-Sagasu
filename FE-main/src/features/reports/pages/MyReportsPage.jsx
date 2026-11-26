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
const statusFilters = { ACTION: 'Cần tôi xử lý', IN_PROGRESS: 'Đang xử lý', FINISHED: 'Đã kết thúc' };
const emptyByStatus = {
  ACTION: 'Hiện chưa có báo cáo cần bạn xử lý',
  IN_PROGRESS: 'Hiện chưa có báo cáo đang xử lý',
  FINISHED: 'Hiện chưa có báo cáo đã kết thúc',
};
const targetIcons = {
  POST: FileText, USER: UserRound, CLAIM: ClipboardCheck, MESSAGE: MessageSquare,
  HANDOVER_ISSUE: Handshake, CLAIM_APPEAL: Scale,
};
const violationTypes = { POST: 'Vi phạm · Bài đăng', CLAIM: 'Vi phạm · Yêu cầu nhận đồ',
  USER: 'Vi phạm · Tài khoản', MESSAGE: 'Vi phạm · Tin nhắn' };
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
  const status = Object.hasOwn(statusFilters, params.get('status')) ? params.get('status') : 'ACTION';
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
                onClick={() => setFilters({ status: value === 'ACTION' ? null : value })}>
                {label}
                {value !== 'FINISHED' && data?.tabCounts?.[value] > 0 &&
                  <span className="report-tab-count">{data.tabCounts[value]}</span>}
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
        <section className="report-list-section" aria-label={statusFilters[status]}>
          {items.map(report => <ReportRow key={report.report_id} report={report} returnTo={returnTo} />)}
        </section>
        <p className="reports-count" role="status">{data.totalElements} báo cáo</p>
        <Pagination currentPage={page} totalPages={data.totalPages}
          onPageChange={next => setFilters({ page: next === 1 ? null : String(next) })} />
      </>}
      {data && data.totalElements === 0 && (
        <div className="report-card reports-empty">
          <h2>{type === 'ALL' ? emptyByStatus[status] : 'Chưa có báo cáo phù hợp'}</h2>
          {type !== 'ALL' ? <button type="button" className="btn btn-ghost" onClick={() => setFilters({ type: null })}>
            Xóa lọc theo loại
          </button> : status === 'ACTION' && <button type="button" className="btn btn-ghost"
            onClick={() => setFilters({ status: 'IN_PROGRESS' })}>Xem báo cáo đang xử lý</button>}
        </div>
      )}
    </main>
  );
}

function ReportRow({ report, returnTo }) {
  const Icon = targetIcons[report.report_type] || Flag;
  const title = reportTitles[report.report_type] || 'Báo cáo';
  const subject = reportSubject(report);
  const type = violationTypes[report.report_type] || reportTypes[report.report_type] || title;
  const needsAction = report.needs_reply || report.needs_reopen;
  const isFinished = finished(report.status);
  const displayStatus = report.withdrawn ? 'WITHDRAWN' : report.status;
  const state = report.needs_reply ? 'Cần bạn phản hồi'
    : report.needs_reopen ? 'Cần xét lại người nhận' : appealSteps[report.appeal_step] || reportStatuses[displayStatus];
  const action = report.needs_reply ? 'Gửi thông tin bổ sung'
    : report.needs_reopen ? 'Xem việc cần làm' : report.appeal_step ? 'Xem bước tiếp theo' : isFinished ? 'Xem kết quả' : 'Xem tiến trình';
  const hash = report.needs_reply ? '#phan-hoi' : report.appeal_step ? '#buoc-tiep-theo' : isFinished ? '#ket-qua' : '';
  return (
    <article className={`report-entry${needsAction ? ' report-entry-action' : ''}`}>
      <div className="report-entry-content">
        <span className="report-entry-icon">
          <Icon size={22} aria-hidden="true" />
          {report.post_thumbnail_url && <img key={report.post_thumbnail_url} src={report.post_thumbnail_url}
            alt="" loading="lazy" onError={event => { event.currentTarget.hidden = true; }} />}
        </span>
        <div>
          <span className="report-entry-type">{type}</span>
          <h3><Link to={reportDetailPath(report.report_id) + hash} state={{ returnTo }}>{subject.value || title}</Link></h3>
          <div className="report-entry-meta">
            <time dateTime={report.created_at}><Clock3 size={13} aria-hidden="true" /> Gửi lúc {formatReportDate(report.created_at)}</time>
          </div>
        </div>
      </div>
      <p className="report-entry-reason">{report.report_reason || 'Chưa phân loại'}</p>
      <div className="report-entry-actions">
        <span className={`report-state report-state-${needsAction ? 'ACTION' : report.appeal_step ? 'IN_REVIEW' : displayStatus}`}>{state}</span>
        <Link className={`btn ${needsAction ? 'btn-accent' : 'btn-ghost'}`}
          to={reportDetailPath(report.report_id) + hash} state={{ returnTo }}
          aria-label={`${action} báo cáo #${report.report_id}`}>
          {action}<ArrowRight size={15} aria-hidden="true" />
        </Link>
      </div>
    </article>
  );
}
