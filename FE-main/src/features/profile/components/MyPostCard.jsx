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
      <p><strong>{MODERATION[post.moderationStatus]}</strong> · {HANDLING[post.status]}</p>
      <p>Đăng ngày {new Date(post.createdAt).toLocaleString('vi-VN', {timeZone:'Asia/Ho_Chi_Minh'})}</p>
      {post.editBlockedReason && <p className="my-post-note">Không thể sửa: {post.editBlockedReason}.</p>}
      {post.deleteBlockedReason && <p className="my-post-note">Không thể xóa: {post.deleteBlockedReason}.</p>}
    </div>
    <div className="my-post-actions">
      {post.postType === 'FOUND' && <Link className="btn btn-ghost" to={`/bai-dang/${post.id}/claims`} state={fromState}>Claim nhận đồ</Link>}
      {post.canEdit && <Link className="btn btn-ghost" to={`/sua-tin/${post.id}`} state={fromState}>Sửa bài</Link>}
      {post.canDelete && <button type="button" className="btn btn-ghost" onClick={() => onDelete(post)}>Xóa bài</button>}
    </div>
  </article>;
}
