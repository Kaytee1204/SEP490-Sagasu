import { Link } from 'react-router-dom';
import { Package, ArrowLeft } from 'lucide-react';
import Badge from './Badge';
import OfficeReturnPanel from './OfficeReturnPanel';
import SupplementForm from './SupplementForm';
import SupplementHistory from './SupplementHistory';
import { date } from '../officeUtils';
import ReportButton from '../../reports/components/ReportButton';

export default function PersonalClaimDetail({ claim, onChanged }) {
  const ended = ['COMPLETED','REJECTED','CANCELLED','CLOSED'].includes(claim.status);
  const activities = claim.activities || [];
  return <div className="op-detail-content">
    <Link className="op-back" to="/van-phong/claims"><ArrowLeft size={16}/> Nhận đồ tại văn phòng</Link>
    <header className="op-item-heading">
      <span className="op-item-icon"><Package size={26} aria-hidden="true" /></span>
      <div className="op-item-title">
        <p className="op-meta">Yêu cầu #{claim.id} · {claim.channel === 'COUNTER' ? 'Lập tại quầy' : 'Gửi trực tuyến'}</p>
        <h1>{claim.postTitle}</h1>
        <p className="op-meta">Gửi lúc {date(claim.createdAt)}</p>
      </div>
      <div className="op-item-actions">
        <Badge value={claim.status} />
        <ReportButton type="CLAIM" id={claim.id} label="Phản ánh về yêu cầu nhận đồ" />
      </div>
    </header>
    <OfficeReturnPanel claim={claim} mine onChanged={onChanged}/>
    {claim.ownRequest && claim.status === 'NEEDS_INFO' && <SupplementForm claim={claim} onDone={onChanged}/>}
    <section className="of-panel"><h2>Thông tin yêu cầu</h2><dl className="of-facts"><div><dt>Người yêu cầu nhận đồ</dt><dd>{claim.claimantName}</dd></div><div><dt>Email liên hệ</dt><dd>{claim.claimantEmail || 'Chưa cung cấp'}</dd></div><div><dt>Điện thoại</dt><dd>{claim.claimantPhone || 'Chưa cung cấp'}</dd></div>{claim.channel === 'COUNTER' && <div><dt>Nhân viên tạo yêu cầu</dt><dd>{claim.createdBy}</dd></div>}</dl></section>
    <section className="of-panel"><h2>Căn cứ sở hữu đã gửi</h2><p className="of-prose">{claim.description}</p><dl className="of-question-answers">{(claim.answers || []).map(a=><div key={a.id}><dt>{a.question}</dt><dd className="of-prose">{a.answer}</dd></div>)}</dl>{claim.evidenceNote && <p className="of-callout of-prose">{claim.evidenceNote}</p>}<div className="of-gallery">{(claim.evidence || []).map(e=><a key={e.id} href={e.url} target="_blank" rel="noreferrer"><img src={e.url} alt={e.originalName || 'Bằng chứng sở hữu'}/></a>)}</div></section>
    <details className="of-panel op-history"><summary>Thông tin bổ sung</summary><SupplementHistory claim={claim}/></details>
    {ended && claim.appointment && <details className="of-panel op-history"><summary>Lịch hẹn trước đây</summary><p>{date(claim.appointment.scheduledAt)} · {claim.appointment.location}</p><p>Lịch hẹn thuộc yêu cầu đã kết thúc.</p></details>}
    <details className="of-panel op-history"><summary>Lịch sử xử lý · {activities.length} cập nhật</summary><ol className="of-timeline">{activities.map(a=><li key={a.id}><strong>{({SUBMIT:'Đã gửi yêu cầu',APPROVE:'Đã duyệt và hẹn nhận đồ',REJECT:'Đã từ chối',REQUEST_INFO:'Yêu cầu bổ sung thông tin',RESUBMIT:'Đã bổ sung thông tin',OFFICE_CANCEL:'Đã hủy lịch và thu hồi duyệt',HANDOVER:'Đã giao đồ',CONFIRM_RECEIPT:'Hoàn tất trao trả',AUTO_CLOSE:'Đã đóng yêu cầu',INVITE_CONFIRM:'Đã đồng ý lịch hẹn',INVITE_DECLINE:'Đã từ chối lịch hẹn',INVITE_EXPIRE:'Hết hạn phản hồi',RESCHEDULE:'Đã đổi lịch',TRANSFER_OFFICE:'Văn phòng tiếp nhận xử lý',CANCEL:'Đã hủy yêu cầu'})[a.action] || 'Cập nhật yêu cầu'}</strong><p className="of-muted">{a.actorName || 'Hệ thống'} · {date(a.createdAt)}</p>{a.reason && <p className="of-prose">{a.reason}</p>}</li>)}</ol></details>
  </div>;
}
