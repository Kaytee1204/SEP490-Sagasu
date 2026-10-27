import { useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Clock } from 'lucide-react';

const STATUS = { OPEN: 'Đang xử lý', RESERVED: 'Đang xác minh nhận đồ', RETURNED: 'Đã trao trả', CLOSED: 'Đã đóng' };

function Thumbnail({ url, title }) {
  const [failed, setFailed] = useState(false);
  let safeUrl;
  try { const parsed = new URL(url); if (['https:', 'http:'].includes(parsed.protocol)) safeUrl = parsed.href; } catch { /* No valid photo URL. */ }
  if (failed || !safeUrl) return <div className="post-list-no-image" role="img" aria-label={`Chưa có ảnh: ${title}`}>Ảnh chưa khả dụng</div>;
  return <img src={safeUrl} alt={title} loading="lazy" onError={() => setFailed(true)} />;
}

export default function SearchMatchCard({ item }) {
  const found = item.postType === 'FOUND';
  const eventTime = new Date(item.eventAt).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
  });
  return <article className="card post-list-card">
    <div className="post-list-image"><Thumbnail key={item.thumbnailUrl || 'empty'} url={item.thumbnailUrl} title={item.title} />
      <span className={`badge post-list-type ${found ? 'badge-found' : 'badge-lost'}`}>{found ? 'Nhặt được' : 'Cần tìm'}</span>
    </div>
    <div className="post-list-card-body">
      <span className="post-list-category">{item.categoryName}</span>
      <h2><Link to={`/chi-tiet/${item.id}`}>{item.title}</Link></h2>
      <p><MapPin size={16} aria-hidden="true" />{item.locationText}</p>
      <p><Clock size={16} aria-hidden="true" />{eventTime}</p>
      <p className="post-list-handling">{STATUS[item.status] || item.status}</p>
      <Link to={`/chi-tiet/${item.id}`} className="btn btn-ghost btn-full">Xem chi tiết</Link>
    </div>
  </article>;
}
