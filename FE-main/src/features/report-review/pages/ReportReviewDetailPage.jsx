import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import ReportDetail from '../components/ReportDetail';
import '../ReportReview.css';
export default function ReportReviewDetailPage(){const {id}=useParams(),navigate=useNavigate(),[params]=useSearchParams();const suffix=params.toString()?`?${params}`:'';return <main className="rr"><header className="rr-header"><div><h1>Xử lý báo cáo #{id}</h1><p>Đối chiếu bằng chứng, trao đổi với các bên và ghi nhận kết quả.</p></div></header><ReportDetail key={id} id={id} onBack={()=>navigate(`/admin/reports${suffix}`)} onRelated={value=>navigate(`/admin/reports/${value}${suffix}`)}/></main>;}
