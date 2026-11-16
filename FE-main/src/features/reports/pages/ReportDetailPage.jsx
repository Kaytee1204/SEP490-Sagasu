import '@/styles/Workspace.css';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { getReport } from '../api/reportApi';
import { reportTypes, reportStatuses as statuses } from '../constants/reportConstants';
import { formatReportDate as date, reportTargetType as typeOf } from '../utils/reportUtils';
import '../styles/Reports.css';

export default function ReportDetailPage() {
  const {id}=useParams(),{user}=useAuth();return <ReportDetail key={`${user.id}:${id}`} id={id}/>;
}
function ReportDetail({id}) {
  const [report,setReport]=useState(null),[error,setError]=useState(''),[image,setImage]=useState(null);
  useEffect(()=>{let cancelled=false;if(!/^[1-9]\d*$/.test(id)){setError('Mã báo cáo không hợp lệ.');return;}getReport(id).then(data=>{if(!cancelled)setReport(data);}).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[id]);
  const evidence=(report?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh bằng chứng'}));
  return <main className="container reports-page"><Link className="btn btn-ghost" to="/bao-cao-cua-toi">Báo cáo của tôi</Link><h1>{report?.targetLabel || "Chi tiết báo cáo"}</h1>
    {error&&<p role="alert" className="report-error">{error}</p>}{!report&&!error&&<p role="status">Đang tải…</p>}
    {report&&<><section className="report-card"><div className="report-card-meta"><span className="report-kind-badge">{reportTypes[typeOf(report)]}</span><span className={`report-state report-state-${report.status}`}>{statuses[report.status]}</span></div>
    <dl className="report-detail-fields"><div><dt>Lý do báo cáo</dt><dd>{report.reasonLabel || 'Chưa phân loại (báo cáo cũ)'}</dd></div>
      {report.description&&<div><dt>Mô tả chi tiết</dt><dd className="report-description">{report.description}</dd></div>}
      <div><dt>Thời gian gửi</dt><dd>{date(report.createdAt)}</dd></div>
      {report.resolutionNote&&<div><dt>Kết luận xử lý</dt><dd className="report-description">{report.resolutionNote}</dd></div>}
    </dl>
    <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} onClick={()=>setImage(i)} aria-label="Xem ảnh bằng chứng"><img src={item.url} alt={item.name}/></button>)}</div>
    </section>
    <details className="report-card"><summary>Lịch sử xử lý</summary>{report.activities.map((item,i)=><p key={i}>{date(item.at)} · {statuses[item.status]}{item.note&&` · ${item.note}`}</p>)}</details></>}
    {image!==null&&<ClaimEvidencePreview images={evidence} initialIndex={image} onClose={()=>setImage(null)}/>}
  </main>;
}
