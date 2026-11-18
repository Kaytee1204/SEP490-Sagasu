import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, Package } from 'lucide-react';
import { date } from '../officeUtils';

export default function PersonalClaimCards({ claims }) {
  return <div className="op-claim-list">{claims.map(claim=><article className="op-claim-card" key={claim.id}>
    <div className="op-item-icon"><Package size={24} aria-hidden="true"/></div>
    <div className="op-claim-content"><div className="op-claim-title"><Link to={`/van-phong/claims/${claim.id}`}>{claim.postTitle}</Link><span className="of-progress-badge">{({PENDING:'Chờ văn phòng xem xét',NEEDS_INFO:'Cần bổ sung thông tin',APPROVED:'Đã duyệt',REJECTED:'Đã từ chối',CANCELLED:'Đã hủy',COMPLETED:'Đã nhận đồ',CLOSED:'Đã kết thúc'})[claim.status] || 'Đang xử lý'}</span></div>
      <p className="op-meta">Yêu cầu #{claim.id} · {claim.channel==='COUNTER'?'Lập tại quầy':'Gửi trực tuyến'}</p>
      <p className="op-next-step">{{APPOINTMENT_REVIEW:'Lịch hẹn đã qua. Vui lòng liên hệ văn phòng để hẹn lại.',EMAIL_UNSENT:'Văn phòng đang chuẩn bị lời mời nhận đồ.',REPLY_EXPIRED:'Đã hết hạn phản hồi lịch hẹn.',NEEDS_INFO:'Văn phòng cần bạn bổ sung thông tin.',AWAITING_REPLY:'Bạn có lịch hẹn cần phản hồi.',REVIEW:'Văn phòng đang xem xét yêu cầu của bạn.',COMPLETED:'Việc nhận lại đồ đã hoàn tất.',REJECTED:'Mở yêu cầu để xem kết quả và lý do.'}[claim.stage] || 'Xem tiến trình và thông tin mới nhất từ văn phòng.'}</p>
      <p className="op-date"><CalendarDays size={15} aria-hidden="true"/>Gửi lúc {date(claim.createdAt)}</p>
    </div>
    <Link className={`of-button ${['AWAITING_REPLY','NEEDS_INFO'].includes(claim.stage)?'of-primary':''}`} to={`/van-phong/claims/${claim.id}`}>{claim.stage==='AWAITING_REPLY'?'Phản hồi lịch hẹn':claim.stage==='NEEDS_INFO'?'Bổ sung thông tin':'Xem yêu cầu'}<ArrowRight size={16} aria-hidden="true"/></Link>
  </article>)}</div>;
}
