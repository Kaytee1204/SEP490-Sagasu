import { useState } from 'react';
import { Image, MapPin, Clock, UserRound } from 'lucide-react';

export default function LivePostPreview({ formData, postType, authorName }) {
  const [selected, setSelected] = useState(null);
  const photos = formData.images || [];
  const photo = photos.includes(selected) ? selected : photos[0];
  return <section className="post-live-preview" aria-label="Bản xem trước bài đăng">
    <header><h2>Xem trước bài đăng</h2></header>
    <div className="post-live-photo">{photo ? <img src={photo} alt="Ảnh xem trước" /> : <div><Image size={28} /><span>Ảnh vật phẩm sẽ hiện tại đây</span></div>}</div>
    {photos.length > 1 && <div className="post-live-thumbs">{photos.map((src, index) => <button type="button" key={src} aria-label={`Xem ảnh ${index + 1}`} aria-pressed={src === photo} onClick={() => setSelected(src)}><img src={src} alt="" /></button>)}</div>}
    <div className="post-live-content">
      <div className="post-live-badges"><span className={`badge ${postType === 'lost' ? 'badge-lost' : 'badge-found'}`}>{postType === 'lost' ? 'Cần tìm' : 'Nhặt được'}</span><span>{formData.categoryName || 'Chưa chọn danh mục'}</span></div>
      <h3 title={formData.itemName}>{formData.itemName || 'Tên đồ vật của bạn'}</h3>
      <p><MapPin size={14} /><span title={formData.location}>{formData.location || 'Địa điểm mất / nhặt được'}</span></p>
      <p><Clock size={14} /><span>{formData.date ? formData.date.split('-').reverse().join('/') : 'Chưa chọn ngày'}{formData.time ? ` · ${formData.time}` : ''}</span></p>
      <p><UserRound size={14} /><span>Người đăng: {authorName || 'Bạn'}</span></p>
      <div className="post-live-description">{formData.description || 'Mô tả đồ vật sẽ xuất hiện ở đây.'}</div>
    </div>
  </section>;
}
