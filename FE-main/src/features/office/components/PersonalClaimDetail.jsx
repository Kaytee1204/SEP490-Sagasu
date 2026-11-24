import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, ArrowLeft } from 'lucide-react';
import Badge from './Badge';
import OfficeReturnPanel from './OfficeReturnPanel';
import OfficeClaimTimeline from './OfficeClaimTimeline';
import { date } from '../officeUtils';
import ReportButton from '../../reports/components/ReportButton';
import ClaimAppealPanel from '@/features/claims/components/ClaimAppealPanel';
import CancelClaimDialog from '@/features/claims/components/CancelClaimDialog';

export default function PersonalClaimDetail({ claim, workflow, onChanged }) {
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const ended = ['COMPLETED','REJECTED','CANCELLED','CLOSED'].includes(claim.status);
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
        {claim.canCancel && <button type="button" className="of-button of-danger" onClick={() => setWithdrawOpen(true)}>Rút yêu cầu</button>}
      </div>
    </header>
    <OfficeReturnPanel claim={claim} state={workflow} mine onChanged={onChanged}/>
    <ClaimAppealPanel key={claim.id} claim={claim} />
    {withdrawOpen && claim.canCancel && <CancelClaimDialog claim={claim} onClose={() => setWithdrawOpen(false)}
      onCancelled={() => { setWithdrawOpen(false); onChanged(); }} />}
    <section className="of-panel"><h2>Thông tin yêu cầu</h2><dl className="of-facts"><div><dt>Người yêu cầu nhận đồ</dt><dd>{claim.claimantName}</dd></div><div><dt>Email liên hệ</dt><dd>{claim.claimantEmail || 'Chưa cung cấp'}</dd></div><div><dt>Điện thoại</dt><dd>{claim.claimantPhone || 'Chưa cung cấp'}</dd></div>{claim.channel === 'COUNTER' && <div><dt>Nhân viên tạo yêu cầu</dt><dd>{claim.createdBy}</dd></div>}</dl></section>
    <section className="of-panel"><h2>Căn cứ sở hữu đã gửi</h2><p className="of-prose">{claim.description}</p><dl className="of-question-answers">{(claim.answers || []).map(a=><div key={a.id}><dt>{a.question}</dt><dd className="of-prose">{a.answer}</dd></div>)}</dl>{claim.evidenceNote && <p className="of-callout of-prose">{claim.evidenceNote}</p>}<div className="of-gallery">{(claim.evidence || []).map(e=><a key={e.id} href={e.url} target="_blank" rel="noreferrer"><img src={e.url} alt={e.originalName || 'Bằng chứng sở hữu'}/></a>)}</div></section>
    {ended && claim.appointment && <details className="of-panel op-history"><summary>Lịch hẹn trước đây</summary><dl className="of-facts"><div><dt>Thời gian hẹn</dt><dd>{date(claim.appointment.scheduledAt)}</dd></div><div><dt>Địa điểm nhận đồ</dt><dd>{claim.appointment.location}</dd></div></dl><p>Lịch hẹn thuộc yêu cầu đã kết thúc.</p></details>}
    <OfficeClaimTimeline claim={claim} workflow={workflow} mine />
  </div>;
}
