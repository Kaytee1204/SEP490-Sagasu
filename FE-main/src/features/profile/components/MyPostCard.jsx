import { postClaimsPath } from '@/features/claims/claimUtils';
import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowUpRight, CalendarDays, Check, ChevronDown, ClipboardList, Clock3, Eye, EyeOff, LockKeyhole, MapPin, Package, Pencil, CircleX } from 'lucide-react';

const MODERATION = {
  VISIBLE: { label: 'Đang hiển thị', icon: Eye },
  PENDING: { label: 'Chờ duyệt', icon: Clock3 },
  REJECTED: { label: 'Bị từ chối', icon: CircleX },
  HIDDEN: { label: 'Bị ẩn', icon: EyeOff },
};
const HANDLING = { RESERVED: 'Đang xử lý nhận đồ', RETURNED: 'Đã trao trả', CLOSED: 'Đã đóng' };

export default function MyPostCard({ post, onDelete, onEdit }) {
  const [imageFailed, setImageFailed] = useState(false);
  const location = useLocation();
  const fromState = { from: `${location.pathname}${location.search}`, fromLabel: 'Tin đăng của tôi' };
  const isLost = post.postType === 'LOST';
  const hasPendingClaims = !isLost && post.pendingClaimCount > 0;
  const moderation = MODERATION[post.moderationStatus] || { label: post.moderationStatus, icon: Clock3 };
  const StatusIcon = moderation.icon;
  const handling = post.status === 'OPEN' ? (isLost ? 'Đang tìm đồ' : 'Chờ chủ sở hữu') : HANDLING[post.status] || post.status;
  const hasBlockedActions = (!post.canEdit && post.editBlockedReason) || (!post.canDelete && post.deleteBlockedReason);

  return <article className={`my-post-card${hasPendingClaims ? ' has-pending-claims' : ''}`}>
    <div className="my-post-card-main">
      <div className="my-post-thumbnail">
        {post.thumbnailUrl && !imageFailed ? <img src={post.thumbnailUrl} alt="" loading="lazy" onError={() => setImageFailed(true)} />
          : <span><Package size={25} aria-hidden="true" /><small>Chưa có ảnh</small></span>}
      </div>
      <div className="my-post-body">
        <div className="my-post-labels">
          <span className={`badge ${isLost ? 'badge-lost' : 'badge-found'}`}>{isLost ? 'Mất đồ' : 'Nhặt được'}</span>
          <span className="my-post-category">{post.categoryName}</span>
        </div>
        <h2><Link className="my-post-detail-link" to={`/chi-tiet/${post.id}`} state={fromState}>{post.title}</Link></h2>
        <div className="my-post-meta">
          <span><MapPin size={14} aria-hidden="true" />{post.locationText || 'Chưa cập nhật địa điểm'}</span>
          <span><CalendarDays size={14} aria-hidden="true" />Đăng {new Date(post.createdAt).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</span>
        </div>
      </div>
      <div className="my-post-state" aria-label="Trạng thái bài đăng">
        <span className="my-post-moderation" data-status={post.moderationStatus}><StatusIcon size={14} aria-hidden="true" />{moderation.label}</span>
        {post.moderationStatus === 'VISIBLE' && <span className="my-post-handling" data-status={post.status}>
          {post.status === 'RETURNED' && <Check size={14} aria-hidden="true" />}{handling}
        </span>}
      </div>
    </div>
    <footer className="my-post-card-footer">
      {!isLost && <Link className={`my-post-claims-link${hasPendingClaims ? ' needs-review' : ''}`}
        to={postClaimsPath(post.id, hasPendingClaims ? 'PENDING' : 'ALL')} state={fromState}>
        <ClipboardList size={16} aria-hidden="true" />
        {hasPendingClaims ? 'Yêu cầu chờ bạn duyệt' : 'Xem yêu cầu nhận đồ'}
        {hasPendingClaims && <span>{post.pendingClaimCount}</span>}
      </Link>}
      <div className="my-post-card-actions">
        <Link className="my-post-view-button" to={`/chi-tiet/${post.id}`} state={fromState}>Xem bài<ArrowUpRight size={15} aria-hidden="true" /></Link>
        <button type="button" className="my-post-edit-button" disabled={!post.canEdit} onClick={() => onEdit(post)}>
          <Pencil size={15} aria-hidden="true" />Sửa bài
        </button>
        <button type="button" className="my-post-hide-button" disabled={!post.canDelete} onClick={() => onDelete(post)}>
          <EyeOff size={15} aria-hidden="true" />Ẩn bài
        </button>
      </div>
    </footer>
    {hasBlockedActions && <details className="my-post-restrictions">
      <summary><LockKeyhole size={13} aria-hidden="true" /><span>Vì sao không thể sửa hoặc ẩn bài?</span><ChevronDown size={14} aria-hidden="true" /></summary>
      <ul>
        {!post.canEdit && post.editBlockedReason && <li><strong>Sửa bài:</strong> {post.editBlockedReason}</li>}
        {!post.canDelete && post.deleteBlockedReason && <li><strong>Ẩn bài:</strong> {post.deleteBlockedReason}</li>}
      </ul>
    </details>}
  </article>;
}
