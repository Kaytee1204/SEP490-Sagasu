import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { MapPin, Clock, UserRound } from 'lucide-react';

const STATUS = { OPEN: 'Đang xử lý', RESERVED: 'Đang xác minh nhận đồ', RETURNED: 'Đã trao trả', CLOSED: 'Đã đóng' };

function Thumbnail({ url, title }) {
  const [failed, setFailed] = useState(false);
  let safeUrl;
  try { const parsed = new URL(url); if (['https:', 'http:'].includes(parsed.protocol)) safeUrl = parsed.href; } catch { /* No valid photo URL. */ }
  if (failed || !safeUrl) return <div className="post-list-no-image" role="img" aria-label={`Chưa có ảnh: ${title}`}>Ảnh chưa khả dụng</div>;
  return <img src={safeUrl} alt={title} loading="lazy" onError={() => setFailed(true)} />;
}

export default function SearchMatchCard({ item }) {
  const location = useLocation();
  const fromState = { from: `${location.pathname}${location.search}`, fromLabel: 'Danh sách tìm kiếm' };
  const found = item.postType === 'FOUND';
  const eventTime = new Date(item.eventAt).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
  });
  return <article className="card post-list-card">
    <div className="post-list-image"><Thumbnail key={item.thumbnailUrl || 'empty'} url={item.thumbnailUrl} title={item.title} /></div>
    <div className="post-list-card-body">
      <div className="post-list-badges">
        <span className={`badge ${found ? 'badge-found' : 'badge-lost'}`}>{found ? 'Nhặt được' : 'Cần tìm'}</span>
        <span className="post-list-category" title={item.categoryName}>{item.categoryName}</span>
      </div>
      <h2 title={item.title}><Link to={`/chi-tiet/${item.id}`} state={fromState}>{item.title}</Link></h2>
      <p className="post-list-location"><MapPin size={16} aria-hidden="true" /><span title={item.locationText}>{item.locationText || "Chưa có địa điểm"}</span></p>
      <p><Clock size={16} aria-hidden="true" />{eventTime}</p>
      <p className="post-list-author"><UserRound size={16} aria-hidden="true" /><span title={item.authorName}>Người đăng: <strong>{item.authorName || "Chưa có thông tin"}</strong></span></p>
      <p className="post-list-handling">{STATUS[item.status] || item.status}</p>
      <Link to={`/chi-tiet/${item.id}`} state={fromState} className="btn btn-ghost btn-full">Xem chi tiết</Link>
    </div>
  </article>;
}
