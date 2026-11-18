import { useLocation, useNavigate, useParams } from 'react-router-dom';
import OfficeTransferContent from '../components/OfficeTransferContent';
import '../Office.css';
export default function OfficeTransferDetailPage({ mine=false }) {
  const {id}=useParams(),navigate=useNavigate(),location=useLocation();
  const base=mine?'/van-phong/chuyen-giao':location.pathname.startsWith('/admin/')?'/admin/transfers':'/approver/transfers';
  return <OfficeTransferContent key={id} id={id} mine={mine} openSchedule={location.state?.openSchedule === true} onClose={()=>navigate(base+(location.search||''))} onDone={()=>{}}/>;
}
