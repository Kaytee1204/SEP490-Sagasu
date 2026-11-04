import { useEffect,useState } from 'react';
import { Link,useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Pagination from '@/components/common/Pagination';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { getMyReports,getReport } from './reportApi';
import './Reports.css';
const statuses={OPEN:'Chờ xử lý',IN_REVIEW:'Đang xem xét',RESOLVED:'Đã xử lý',REJECTED:'Bị từ chối'};
const targets={POST:'Bài đăng',CLAIM:'Hồ sơ claim',MESSAGE:'Tin nhắn',USER:'Tài khoản'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});
export default function MyReportsPage() {
  const {user}=useAuth();return <MyReports key={user.id}/>;
}
function MyReports() {
  const [page,setPage]=useState(1),[status,setStatus]=useState('ALL'),[result,setResult]=useState(null),[error,setError]=useState('');
  useEffect(()=>{let cancelled=false;setResult(null);setError('');getMyReports(page,status).then(data=>{if(!cancelled)setResult(data);}).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[page,status]);
  return <main className="container reports-page"><h1>Báo cáo của tôi</h1><p>Theo dõi báo cáo và sự cố bàn giao bạn đã gửi.</p>
    <div className="claims-filters">{Object.entries({ALL:'Tất cả',...statuses}).map(([value,label])=><button key={value} className={`chip ${status===value?'active':''}`} onClick={()=>{setStatus(value);setPage(1);}}>{label}</button>)}</div>
    {error&&<p role="alert" className="report-error">{error}</p>}{!result&&!error&&<p role="status">Đang tải báo cáo…</p>}
    {result&&<><p>{result.totalElements} báo cáo</p>{result.content.map(report=><Link className="report-card" key={report.id} to={`/bao-cao/${report.id}`}><h2>Báo cáo #{report.id} · {statuses[report.status]}</h2><p>{report.kind==='HANDOVER_ISSUE'?'Sự cố bàn giao':targets[report.targetType]} #{report.targetId}</p><p>{report.description.length>240?report.description.slice(0,240)+'…':report.description}</p><p>{date(report.createdAt)}</p></Link>)}
    {!result.content.length&&<p className="report-card">Chưa có báo cáo ở trạng thái này.</p>}<Pagination currentPage={page} totalPages={result.totalPages} onPageChange={setPage}/></>}
  </main>;
}
export function ReportDetailPage() {
  const {id}=useParams(),{user}=useAuth();return <ReportDetail key={`${user.id}:${id}`} id={id}/>;
}
function ReportDetail({id}) {
  const [report,setReport]=useState(null),[error,setError]=useState(''),[image,setImage]=useState(null);
  useEffect(()=>{let cancelled=false;if(!/^[1-9]\d*$/.test(id)){setError('Mã báo cáo không hợp lệ.');return;}getReport(id).then(data=>{if(!cancelled)setReport(data);}).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[id]);
  const evidence=(report?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh bằng chứng'}));
  return <main className="container reports-page"><Link className="btn btn-ghost" to="/bao-cao-cua-toi">Báo cáo của tôi</Link><h1>Báo cáo #{id}</h1>
    {error&&<p role="alert" className="report-error">{error}</p>}{!report&&!error&&<p role="status">Đang tải…</p>}
    {report&&<><section className="report-card"><h2>{statuses[report.status]}</h2><p>{report.kind==='HANDOVER_ISSUE'?'Sự cố bàn giao':targets[report.targetType]} #{report.targetId}</p><p>{report.description}</p><p>Gửi lúc {date(report.createdAt)}</p>
    <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} onClick={()=>setImage(i)} aria-label="Xem ảnh bằng chứng"><img src={item.url} alt={item.name}/></button>)}</div>
    {report.resolutionNote&&<><h2>Kết luận xử lý</h2><p>{report.resolutionNote}</p></>}</section>
    <section className="report-card"><h2>Lịch sử xử lý</h2>{report.activities.map((item,i)=><p key={i}>{date(item.at)} · {statuses[item.status]}{item.note&&` · ${item.note}`}</p>)}</section></>}
    {image!==null&&<ClaimEvidencePreview images={evidence} initialIndex={image} onClose={()=>setImage(null)}/>}
  </main>;
}
