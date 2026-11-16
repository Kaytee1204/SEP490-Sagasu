import '@/styles/Workspace.css';
import { useEffect, useState } from 'react';
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

  return <main className="container reports-page">
    <div className="reports-heading"><div><h1>Báo cáo của tôi</h1><p>Theo dõi báo cáo và sự cố bàn giao bạn đã gửi.</p></div>
      {/* Keep the existing participant exchange flow. */}
      <Link className="btn btn-ghost" to="/bao-cao-trao-doi">Thông tin cần bổ sung & kết quả xử lý →</Link></div>
    <div className="reports-toolbar">
      <div className="claims-filters" role="group" aria-label="Trạng thái báo cáo">
        {Object.entries({ALL:'Tất cả', ...statuses}).map(([value, label]) =>
          <button type="button" key={value} className={`chip ${status === value ? 'active' : ''}`} aria-pressed={status === value}
            onClick={() => { setStatus(value); setPage(1); }}>{label}</button>)}
      </div>
      <label className="reports-type-filter">Loại báo cáo
        <select value={type} onChange={event => { setType(event.target.value); setPage(1); }}>
          {Object.entries({ALL:'Tất cả loại', ...reportTypes}).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
    </div>
    {error && <p role="alert" className="report-error">{error}</p>}
    {!result && !error && <p role="status">Đang tải báo cáo…</p>}
    {result && <>
      <p className="reports-count" role="status">{result.totalElements} báo cáo</p>
      {result.content.map(report => <Link className="report-card report-list-card" key={report.id} to={`/bao-cao/${report.id}`}>
        <div className="report-card-meta"><span className="report-kind-badge">{reportTypes[typeOf(report)]}</span>
          <span className={`report-state report-state-${report.status}`}>{statuses[report.status]}</span></div>
        <h2>{report.targetLabel || `Báo cáo #${report.id}`}</h2>
        <p className="report-reason-label"><strong>Lý do:</strong> {report.reasonLabel || 'Chưa phân loại (báo cáo cũ)'}</p>
        {report.description && <p className="report-excerpt">{report.description.length > 240 ? report.description.slice(0,240) + '…' : report.description}</p>}
        <div className="report-card-bottom"><time dateTime={report.createdAt}>Gửi lúc {date(report.createdAt)}</time><span>Xem chi tiết →</span></div>
      </Link>)}
      {!result.content.length && <div className="report-card reports-empty"><h2>Chưa có báo cáo phù hợp</h2>
        <p>Thử chọn loại báo cáo hoặc trạng thái khác.</p>
        {(type !== 'ALL' || status !== 'ALL') && <button type="button" className="btn btn-ghost"
          onClick={() => { setType('ALL'); setStatus('ALL'); setPage(1); }}>Xóa bộ lọc</button>}</div>}
      <Pagination currentPage={page} totalPages={result.totalPages} onPageChange={setPage} />
    </>}
  </main>;
}
