import { Link } from 'react-router-dom';
import useLoad from '../../office/hooks/useLoad';
import { reviewApi } from '../api';
import { Badge, ErrorBox, Empty } from '../components/Shared';
import { date } from '../format';
import '../ReportReview.css';
export default function ReportResultsPage(){const state=useLoad(reviewApi.results,'results');return <main className="rr rr-public"><Link to="/bao-cao-cua-toi">← Báo cáo của tôi</Link><header className="rr-header"><div><h1>Kết quả xử lý báo cáo</h1><p>Kết luận chính thức của các phản ánh có liên quan đến bạn.</p></div><button onClick={state.reload} disabled={state.loading}>Làm mới</button></header><nav className="rr-tabs"><Link to="/bao-cao-trao-doi">Thông tin cần phản hồi</Link><Link to="/ket-qua-bao-cao" aria-current="page">Kết quả xử lý</Link></nav><ErrorBox>{state.error}</ErrorBox>{state.error&&<button onClick={state.reload}>Thử lại</button>}{state.loading?<Empty>Đang tải kết quả…</Empty>:state.data&&!state.data.length?<Empty>Chưa có kết quả xử lý liên quan đến bạn.</Empty>:null}<div className="rr-stack">{state.data?.map(r=><article className="rr-card" key={r.report_id}><div className="rr-row"><h2>Báo cáo #{r.report_id}</h2><Badge value={r.status}/></div><small>{date(r.resolved_at)}</small><p>{r.resolution_note}</p></article>)}</div></main>;}
