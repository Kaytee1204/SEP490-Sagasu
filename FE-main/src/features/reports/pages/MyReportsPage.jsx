import '@/styles/Workspace.css';
import { useEffect, useState } from 'react';
import { UserRound, ChevronDown, ArrowUpRight, Clock3, Flag, MessageSquare, FileText, ClipboardCheck, Handshake } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import { getMyReports } from '../api/reportApi';
import { reportTypes, reportStatuses as statuses } from '../constants/reportConstants';
import { formatReportDate as date, reportTargetType as typeOf } from '../utils/reportUtils';
import '../styles/Reports.css';

export default function MyReportsPage() {
  const {user}=useAuth();return <MyReports key={user.id}/>;
}
function MyReports() {
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState('ALL');
  const [type, setType] = useState('ALL');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    setResult(null);
    setError('');
    getMyReports(page, status, type).then(data => {
      if (!cancelled) setResult(data);
    }).catch(problem => {
      if (!cancelled) setError(problem.message);
    });
    return () => { cancelled = true; };
  }, [page, status, type]);

  const groups = new Map();
  for (const report of result?.content || []) {
    const key = report.targetAccount?.id ? 'account-' + report.targetAccount.id : 'report-' + report.id;
    if (!groups.has(key)) groups.set(key, { account: report.targetAccount, reports: [] });
    groups.get(key).reports.push(report);
  }
  return <main className="container reports-page reports-dashboard">
    <div className="reports-heading"><div><h1>Báo cáo của tôi</h1><p>Theo dõi báo cáo và sự cố bàn giao bạn đã gửi.</p></div>
    </div>
    <div className="reports-toolbar">
      <div className="reports-navigation">
        <label className="reports-type-filter">Loại báo cáo
          <select value={type} onChange={event => { setType(event.target.value); setPage(1); }}>
            {Object.entries({ALL:'Tất cả loại', ...reportTypes}).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
        <Link className="btn btn-ghost" to="/bao-cao-trao-doi"><MessageSquare size={16}/> Trao đổi & kết quả xử lý<ArrowUpRight size={15}/></Link>
      </div>
      <div className="reports-status-row">
      <span className="reports-filter-label">Trạng thái</span>
      <div className="claims-filters" role="group" aria-label="Trạng thái báo cáo">
        {Object.entries({ALL:'Tất cả', ...statuses}).map(([value, label]) =>
          <button type="button" key={value} className={`chip ${status === value ? 'active' : ''}`} aria-pressed={status === value}
            onClick={() => { setStatus(value); setPage(1); }}>{label}</button>)}
      </div>
      </div>
    </div>
    {error && <p role="alert" className="report-error">{error}</p>}
    {!result && !error && <p role="status">Đang tải báo cáo…</p>}
    {result && <>
      <p className="reports-count" role="status">{result.totalElements} báo cáo{result.totalPages > 1 && ` · ${groups.size} nhóm trên trang này`}</p>
      <div className="report-account-list">
        {[...groups.entries()].map(([key, group]) => <ReportAccountGroup key={key} group={group} />)}
      </div>
      {!result.content.length && <div className="report-card reports-empty"><h2>Chưa có báo cáo phù hợp</h2>
        <p>Thử chọn loại báo cáo hoặc trạng thái khác.</p>
        {(type !== 'ALL' || status !== 'ALL') && <button type="button" className="btn btn-ghost"
          onClick={() => { setType('ALL'); setStatus('ALL'); setPage(1); }}>Xóa bộ lọc</button>}</div>}
      <Pagination currentPage={page} totalPages={result.totalPages} onPageChange={setPage} />
    </>}
  </main>;
}

const targetIcons = { POST: FileText, USER: UserRound, CLAIM: ClipboardCheck, MESSAGE: MessageSquare, HANDOVER_ISSUE: Handshake };
function ReportAccountGroup({ group }) {
  const [expanded, setExpanded] = useState(false);
  const pending = group.reports.filter(report => ['OPEN', 'IN_REVIEW'].includes(report.status)).length;
  return <section className="report-account-group">
    <header className="report-account-heading">
      <span className="report-account-avatar"><UserRound size={23}/></span>
      <div className="report-account-info">
        <h2>{group.account?.name || 'Không còn thông tin tài khoản'}</h2>
        {pending > 0 && <span className="report-state report-state-OPEN report-group-pending">{pending} báo cáo chờ xử lý</span>}
        <p>{group.reports.length} báo cáo</p>
      </div>
      <button type="button" className="btn btn-ghost" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>
        {expanded ? 'Thu gọn báo cáo' : 'Xem báo cáo'}<ChevronDown size={16} className={expanded ? 'report-chevron-open' : ''}/>
      </button>
    </header>
    {expanded && <div className="report-account-items">{group.reports.map(report => {
      const type = typeOf(report), Icon = targetIcons[type] || Flag;
      return <article className="report-entry" key={report.id}>
        <span className="report-entry-icon"><Icon size={20}/></span>
        <div className="report-entry-content">
          <div className="report-entry-meta"><span className="report-kind-badge">{reportTypes[type] || 'Báo cáo'}</span>
            <span className={'report-state report-state-' + report.status}>{statuses[report.status] || report.status}</span><span>#{report.id}</span></div>
          <h3>{type === 'USER' ? 'Báo cáo tài khoản ' + (report.targetLabel || group.account?.name || '') : report.targetLabel || 'Nội dung #' + report.targetId}</h3>
          <p><strong>Lý do:</strong> {report.reasonLabel || 'Chưa phân loại (báo cáo cũ)'}</p>
          {report.description && <p className="report-entry-description">{report.description}</p>}
          <time dateTime={report.createdAt}><Clock3 size={14}/> Gửi lúc {date(report.createdAt)}</time>
        </div>
        <Link className="btn btn-ghost report-view-button" to={'/bao-cao/' + report.id}>Xem báo cáo<ArrowUpRight size={16}/></Link>
      </article>;
    })}</div>}
  </section>;
}
