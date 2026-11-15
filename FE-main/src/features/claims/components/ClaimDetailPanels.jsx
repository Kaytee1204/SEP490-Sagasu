import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowUpRight, BadgeCheck, ChevronDown, CircleCheck, CircleX, Clock3, FileCheck2, History, Images, ListChecks, MapPin, Package, UserRound } from 'lucide-react';
import { CLAIM_ACTION, CLAIM_STATUS, claimDate, evidenceUrl } from '../claimUtils';
import ClaimAnswerSummary from './ClaimAnswerSummary';
import OpenChatButton from '@/features/chat/components/OpenChatButton';

function PostCover({ url, title }) {
  const [failed, setFailed] = useState(false);
  return url && !failed
    ? <img src={url} alt={title} onError={() => setFailed(true)} />
    : <span className="claim-detail-cover-placeholder" role="img" aria-label="Ảnh món đồ chưa sẵn sàng"><Package size={32} aria-hidden="true" /></span>;
}

export function ClaimDetailSummary({ claim, post }) {
  const cover = evidenceUrl(post?.images?.[0]?.url);
  const StatusIcon = claim.status === 'PENDING' ? Clock3 : ['APPROVED', 'COMPLETED'].includes(claim.status) ? BadgeCheck : CircleX;
  return <section className="claim-detail-hero" aria-label="Món đồ được yêu cầu nhận">
    <div className="claim-detail-cover"><PostCover key={cover || claim.postId} url={cover} title={claim.postTitle} /></div>
    <div className="claim-detail-hero-info">
      <div className="claim-detail-hero-meta"><span>Yêu cầu nhận đồ #{claim.id}</span>
        <span className={`claim-status claim-status-${claim.status}`}><StatusIcon size={15} aria-hidden="true" />{CLAIM_STATUS[claim.status] || claim.status}</span></div>
      <h1>{claim.postTitle}</h1>
      {post?.categoryName && <p className="claim-detail-category">{post.categoryName}</p>}
      {post?.locationText && <p className="claim-detail-location"><MapPin size={15} aria-hidden="true" /><span>{post.locationText}</span></p>}
    </div>
    <div className="claim-detail-hero-actions">
      {claim.canOpenChat && <OpenChatButton postId={claim.postId} label="Nhắn tin trao đổi" className="btn claim-detail-chat" />}
      <Link className="btn btn-ghost" to={`/chi-tiet/${claim.postId}`}>Xem bài đăng<ArrowUpRight size={17} aria-hidden="true" /></Link>
    </div>
  </section>;
}

export function ClaimVerificationPanel({ claim, onPreviewImage }) {
  const evidence = claim.evidence || [];
  const answers = claim.answers || [];
  return <section className="claim-panel claim-detail-verification" aria-labelledby="claim-verification-title">
    <header className="claim-detail-section-heading"><span className="claim-detail-section-icon"><FileCheck2 size={20} aria-hidden="true" /></span>
      <div><h2 id="claim-verification-title">Căn cứ xác minh</h2><p>Thông tin người yêu cầu đã gửi để đối chiếu.</p></div></header>
    <div className="claim-detail-evidence-copy"><h3>Căn cứ nhận đồ</h3><p className="claim-prose">{claim.description}</p></div>
    {claim.evidenceNote && <div className="claim-detail-evidence-copy"><h3>Ghi chú bằng chứng</h3><p className="claim-prose">{claim.evidenceNote}</p></div>}
    <div className="claim-detail-evidence-heading"><h3><Images size={17} aria-hidden="true" />Ảnh bằng chứng</h3><span>{evidence.length} ảnh</span></div>
    {evidence.length ? <div className="claim-evidence">{evidence.map(image => {
      const url = evidenceUrl(image.url);
      const name = image.originalName || 'Ảnh bằng chứng';
      return <figure key={image.id}>{url ? <button type="button" className="claim-evidence-thumbnail" aria-label={`Xem ảnh ${name}`}
        onClick={() => onPreviewImage(image.id)}><img src={url} alt={name} /></button> : <p>Ảnh tạm thời chưa sẵn sàng.</p>}
        <figcaption title={name}>{name}</figcaption></figure>;
    })}</div> : <p className="claim-detail-no-evidence"><Images size={20} aria-hidden="true" />Hồ sơ không đính kèm ảnh.</p>}
    {answers.length > 0 && <details className="claim-detail-questions" open={claim.status === 'PENDING'}>
      <summary><ListChecks size={19} aria-hidden="true" /><span>Câu trả lời xác minh</span><small>{answers.length} câu</small><ChevronDown size={17} className="claim-detail-chevron" aria-hidden="true" /></summary>
      <ClaimAnswerSummary answers={answers} embedded />
    </details>}
  </section>;
}

export function ClaimParticipantPanel({ claim }) {
  return <section className="claim-panel claim-detail-participants" aria-labelledby="claim-participants-title">
    <h2 id="claim-participants-title">Thông tin yêu cầu</h2>
    <dl>
      <div><dt><UserRound size={18} aria-hidden="true" />Người yêu cầu</dt><dd>{claim.claimantName || 'Thành viên'}</dd></div>
      <div><dt><Package size={18} aria-hidden="true" />{claim.claimType === 'OFFICE' ? 'Bên tiếp nhận' : 'Người nhặt'}</dt><dd>{claim.claimType === 'OFFICE' ? 'Văn phòng tiếp nhận' : claim.finderName || 'Thành viên'}</dd></div>
      <div><dt><Clock3 size={18} aria-hidden="true" />Ngày gửi yêu cầu</dt><dd><time dateTime={claim.createdAt}>{claimDate(claim.createdAt)}</time></dd></div>
      {claim.storageLocationName && <div><dt><MapPin size={18} aria-hidden="true" />Nơi lưu giữ</dt><dd>{claim.storageLocationName}</dd></div>}
    </dl>
  </section>;
}

function ActivityEntry({ event }) {
  const label = event.action === 'SUBMIT' ? 'Đã gửi yêu cầu' : event.action === 'CANCEL' ? 'Đã hủy yêu cầu' : CLAIM_ACTION[event.action] || event.action;
  return <li data-action={event.action}>
    <strong>{label}</strong><span>{event.actorName}</span><time dateTime={event.createdAt}>{claimDate(event.createdAt)}</time>
    {event.reason && (event.reason.length > 180
      ? <details className="claim-detail-activity-note"><summary>Xem lý do</summary><p>{event.reason}</p></details>
      : <p>{event.reason}</p>)}
  </li>;
}

export function ClaimActivityTimeline({ activities = [] }) {
  const recent = activities.slice(-3).reverse();
  const earlier = activities.slice(0, -3).reverse();
  return <section className="claim-panel claim-detail-history" aria-labelledby="claim-history-title">
    <h2 id="claim-history-title"><History size={18} aria-hidden="true" />Lịch sử xử lý</h2>
    {recent.length ? <ol className="claim-detail-timeline">{recent.map(event => <ActivityEntry key={event.id} event={event} />)}</ol> : <p className="claims-muted">Chưa có hoạt động xử lý.</p>}
    {earlier.length > 0 && <details className="claim-detail-earlier-activity"><summary>Xem {earlier.length} hoạt động trước<ChevronDown size={16} aria-hidden="true" /></summary>
      <ol className="claim-detail-timeline">{earlier.map(event => <ActivityEntry key={event.id} event={event} />)}</ol></details>}
  </section>;
}

export function ClaimStatusPanel({ claim }) {
  const copy = {
    PENDING: [claim.canReview ? 'Yêu cầu đang chờ bạn xét duyệt' : 'Đang chờ xét duyệt', claim.canReview ? 'Đối chiếu các căn cứ bên dưới trước khi chấp thuận người nhận.' : 'Người xét duyệt sẽ kiểm tra căn cứ của bạn và gửi thông báo khi có kết quả.'],
    APPROVED: ['Yêu cầu đã được chấp thuận', 'Theo dõi hướng dẫn của bên tiếp nhận để nhận lại món đồ.'],
    REJECTED: ['Yêu cầu chưa được chấp thuận', 'Xem lý do trong lịch sử xử lý để biết căn cứ cần làm rõ.'],
    CANCELLED: ['Yêu cầu đã được hủy', 'Nội dung, bằng chứng và lịch sử xử lý vẫn được lưu trong hồ sơ này.'],
    CLOSED: ['Yêu cầu đã kết thúc', claim.closeReason === 'ITEM_RETURNED' ? 'Món đồ đã được trao trả theo một yêu cầu khác.' : 'Hồ sơ đã đóng và được giữ lại để đối chiếu.'],
    COMPLETED: ['Đã hoàn tất nhận đồ', 'Quá trình nhận đồ của yêu cầu này đã hoàn tất.'],
  };
  const [title, description] = copy[claim.status] || [CLAIM_STATUS[claim.status] || claim.status, 'Theo dõi kết quả xử lý trong hồ sơ này.'];
  const StatusIcon = claim.status === 'PENDING' ? Clock3 : ['APPROVED', 'COMPLETED'].includes(claim.status) ? CircleCheck : CircleX;
  return <section className={`claim-panel claim-detail-state claim-detail-state-${claim.status}`} aria-label="Trạng thái xử lý">
    <StatusIcon size={24} aria-hidden="true" /><div><h2>{title}</h2><p>{description}</p>
      {claim.canRetry && <Link className="btn btn-primary" to={`/xac-minh/${claim.postId}?retryOf=${claim.id}`}>Gửi yêu cầu mới<ArrowUpRight size={16} aria-hidden="true" /></Link>}
    </div>
  </section>;
}
