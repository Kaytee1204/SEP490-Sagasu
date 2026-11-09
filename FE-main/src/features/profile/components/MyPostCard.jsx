import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';

const MODERATION = {VISIBLE:'Đang hiển thị', PENDING:'Chờ duyệt', REJECTED:'Bị từ chối', HIDDEN:'Bị ẩn'};
const HANDLING = {OPEN:'Đang mở', RESERVED:'Đang xử lý nhận đồ', RETURNED:'Đã trao trả', CLOSED:'Đã đóng'};

export default function MyPostCard({ post, onDelete }) {
  const [imageFailed, setImageFailed] = useState(false);
  const location = useLocation();
  const fromState = { from: `${location.pathname}${location.search}`, fromLabel: 'Tin đăng của tôi' };
  return <article className="my-post-card">
    <div className="my-post-thumbnail">
      {post.thumbnailUrl && !imageFailed ? <img src={post.thumbnailUrl} alt="" onError={() => setImageFailed(true)} />
        : <span>Chưa có ảnh</span>}
    </div>
    <div className="my-post-body">
      <span className={`badge ${post.postType === 'LOST' ? 'badge-lost' : 'badge-found'}`}>
        {post.postType === 'LOST' ? 'Mất đồ' : 'Nhặt được'}
      </span>
      <h2><Link className="my-post-detail-link" to={`/chi-tiet/${post.id}`} state={fromState}>{post.title}</Link></h2>
      <p>{post.categoryName} · {post.locationText}</p>
      <p><strong>{post.moderationStatus === 'VISIBLE' ? HANDLING[post.status] : MODERATION[post.moderationStatus]}</strong></p>
      <p>Đăng ngày {new Date(post.createdAt).toLocaleDateString('vi-VN', {timeZone:'Asia/Ho_Chi_Minh'})}</p>
    </div>
    <div className="my-post-actions">
      {post.postType === 'FOUND' && <Link className="btn btn-ghost" to={`/bai-dang/${post.id}/claims${post.pendingClaimCount > 0 ? '?claimStatus=PENDING' : ''}`} state={fromState}>{post.pendingClaimCount > 0 ? `${post.pendingClaimCount} yêu cầu chờ duyệt` : 'Xem yêu cầu nhận đồ'}</Link>}
      <details className="workflow-menu"><summary aria-label="Thao tác bài đăng">⋯</summary><div>
      {post.canEdit && <Link className="btn btn-ghost" to={`/sua-tin/${post.id}`} state={fromState}>Sửa bài</Link>}
      {post.canDelete && <button type="button" className="btn btn-ghost" onClick={() => onDelete(post)}>Ẩn bài đăng</button>}
      {post.editBlockedReason && <p className="my-post-note">Sửa bài: {post.editBlockedReason}</p>}
      {post.deleteBlockedReason && <p className="my-post-note">Ẩn bài: {post.deleteBlockedReason}</p>}
      </div></details>
    </div>
  </article>;
}
