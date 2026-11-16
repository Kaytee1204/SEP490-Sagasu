import { Link } from 'react-router-dom';
import { ArrowLeft, Building2, PackageCheck, Send } from 'lucide-react';

export default function PersonalOfficeHeader({ active, backPath='/claim-cua-toi?view=sent' }) {
  return <>
    <Link className="op-back" to={backPath}><ArrowLeft size={16} aria-hidden="true"/> Yêu cầu nhận đồ</Link>
    <header className="op-heading"><div className="op-heading-icon"><Building2 size={24} aria-hidden="true"/></div><div><h1>{active==='claims'?'Nhận đồ tại văn phòng':'Gửi đồ đến văn phòng'}</h1><p>{active==='claims'?'Theo dõi yêu cầu, bổ sung thông tin và phản hồi lịch hẹn nhận lại đồ.':'Theo dõi lịch mang đồ đến và kết quả văn phòng tiếp nhận.'}</p></div></header>
    <nav className="op-navigation" aria-label="Yêu cầu của bạn tại văn phòng">
      <Link to="/van-phong/claims" state={{officeClaimReturn:backPath}} aria-current={active==='claims'?'page':undefined}><PackageCheck size={18} aria-hidden="true"/>Nhận lại đồ</Link>
      <Link to="/van-phong/chuyen-giao" aria-current={active==='transfers'?'page':undefined}><Send size={18} aria-hidden="true"/>Gửi đồ đến văn phòng</Link>
    </nav>
  </>;
}
