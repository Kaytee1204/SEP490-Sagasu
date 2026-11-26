import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, Package } from 'lucide-react';
import { date } from '../officeUtils';
import { counterConfirmationPath } from '../officeRoutes';

const statuses = {
  PENDING: 'Chờ văn phòng xem xét', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã hủy', COMPLETED: 'Đã nhận đồ', CLOSED: 'Đã kết thúc',
};
const guidance = {
  APPOINTMENT_REVIEW: 'Lịch hẹn đã qua. Vui lòng liên hệ văn phòng để hẹn lại.',
  EMAIL_UNSENT: 'Văn phòng đang chuẩn bị lời mời nhận đồ.',
  REPLY_EXPIRED: 'Đã hết hạn phản hồi lịch hẹn.',
  AWAITING_REPLY: 'Quý vị có lịch hẹn cần phản hồi.',
  REVIEW: 'Văn phòng đang xem xét yêu cầu của Quý vị.',
  AWAITING_RECEIPT: 'Vui lòng đối chiếu tài sản và xác nhận biên bản ngay tại quầy.',
  COUNTER_CONFIRMATION_EXPIRED: 'Đã hết hạn xác nhận. Vui lòng đề nghị nhân viên tại quầy mở lại biên bản.',
  COMPLETED: 'Việc nhận lại đồ đã hoàn tất.',
  REJECTED: 'Mở yêu cầu để xem kết quả và lý do.',
};

export default function PersonalClaimCards({ claims }) {
  return <div className="op-claim-list">{claims.map(claim => {
    const confirming = claim.stage === 'AWAITING_RECEIPT';
    const appointmentReply = claim.stage === 'AWAITING_REPLY';
    const path = confirming ? counterConfirmationPath(claim.id) : `/van-phong/claims/${claim.id}`;
    const label = confirming ? 'Chờ xác nhận tại quầy' : statuses[claim.status] || 'Đang xử lý';
    return <article className="op-claim-card" key={claim.id}>
    <div className="op-item-icon"><Package size={24} aria-hidden="true"/></div>
    <div className="op-claim-content"><div className="op-claim-title"><Link to={path}>{claim.postTitle}</Link><span className="of-progress-badge">{label}</span></div>
      <p className="op-meta">Yêu cầu #{claim.id} · {claim.channel==='COUNTER'?'Lập tại quầy':'Gửi trực tuyến'}</p>
      <p className="op-next-step">{guidance[claim.stage] || 'Xem tiến trình và thông tin mới nhất từ văn phòng.'}</p>
      <p className="op-date"><CalendarDays size={15} aria-hidden="true"/>Gửi lúc {date(claim.createdAt)}</p>
    </div>
    <Link className={`of-button ${confirming || appointmentReply ? 'of-primary' : ''}`} to={path}>
      {confirming ? 'Xác nhận tại quầy' : appointmentReply ? 'Phản hồi lịch hẹn' : 'Xem yêu cầu'}<ArrowRight size={16} aria-hidden="true"/>
    </Link>
  </article>;
  })}</div>;
}
