import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, Package } from 'lucide-react';
import ClaimProgress from './ClaimProgress';
import { date } from '../officeUtils';

export default function PersonalClaimCards({ claims }) {
  return <div className="op-claim-list">{claims.map(claim=><article className="op-claim-card" key={claim.id}>
    <div className="op-item-icon"><Package size={24} aria-hidden="true"/></div>
    <div className="op-claim-content"><div className="op-claim-title"><Link to={`/van-phong/claims/${claim.id}`}>{claim.postTitle}</Link><ClaimProgress stage={claim.stage} scheduledAt={claim.collectionAt}/></div>
      <p className="op-meta">Yêu cầu #{claim.id} · {claim.channel==='COUNTER'?`Lập tại quầy bởi ${claim.createdBy}`:'Gửi trực tuyến'}</p>
      <p className="op-date"><CalendarDays size={15} aria-hidden="true"/>Gửi lúc {date(claim.createdAt)}</p>
    </div>
    <Link className={`of-button ${['AWAITING_REPLY','NEEDS_INFO'].includes(claim.stage)?'of-primary':''}`} to={`/van-phong/claims/${claim.id}`}>{claim.stage==='AWAITING_REPLY'?'Phản hồi lịch hẹn':claim.stage==='NEEDS_INFO'?'Bổ sung thông tin':'Xem yêu cầu'}<ArrowRight size={16} aria-hidden="true"/></Link>
  </article>)}</div>;
}
