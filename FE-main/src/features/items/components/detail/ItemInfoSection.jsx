import { MapPin, Clock } from 'lucide-react';

export default function ItemInfoSection({ item }) {
  const isLost = item.type === 'lost';

  return (
    <div className="post-detail-overview">
      <div className="post-detail-tags">
        <span className={`badge ${isLost ? 'badge-lost' : 'badge-found'}`}>
          {isLost ? 'Cần tìm đồ mất' : 'Đồ nhặt được'}
        </span>
        <span className="badge badge-accent">{item.category}</span>
        <span className="post-detail-id">#{item.id}</span>
      </div>
      <h1>{item.title}</h1>

      <dl className="post-detail-event">
        <div>
          <dt><MapPin size={17} aria-hidden="true" />{isLost ? 'Địa điểm mất đồ' : 'Địa điểm nhặt được'}</dt>
          <dd>{item.location || 'Chưa cập nhật địa điểm'}</dd>
        </div>
        <div>
          <dt><Clock size={17} aria-hidden="true" />{isLost ? 'Thời gian mất đồ' : 'Thời gian nhặt được'}</dt>
          <dd>{item.time} · {item.date}</dd>
        </div>
      </dl>
    </div>
  );
}
