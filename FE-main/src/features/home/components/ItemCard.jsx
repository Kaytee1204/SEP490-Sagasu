import React from 'react';
import { MapPin } from 'lucide-react';

export default function ItemCard({ item }) {
  const statusBadge = {
    lost: { label: 'Cần tìm', cls: 'badge-lost' },
    found: { label: 'Nhặt được', cls: 'badge-found' },
    done: { label: 'Đã trao trả', cls: 'badge-done' },
  }[item.status];

  return (
    <div className="item-card animate-fadeInUp" id={`item-card-${item.id}`}>
      <div className="item-card-img-wrap">
        <img
          src={item.img}
          alt={item.title}
          className="item-card-img"
          loading="lazy"
          onError={e => { e.target.src = `https://picsum.photos/seed/${item.id}/400/300`; }}
        />
        <div className="item-card-badge">
          <span className={`badge ${statusBadge.cls}`}>{statusBadge.label}</span>
        </div>
        <div className="item-card-time">{item.time}</div>
      </div>
      <div className="item-card-body">
        <div className="item-card-category">{item.category}</div>
        <h3 className="item-card-title">{item.title}</h3>
        <p className="item-card-desc">{item.desc}</p>
        <div className="item-card-meta">
          <MapPin size={11} className="shrink-0" />
          <span>{item.location}</span>
        </div>
      </div>
    </div>
  );
}
