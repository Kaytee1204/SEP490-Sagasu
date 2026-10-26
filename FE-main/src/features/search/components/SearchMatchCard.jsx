import React from 'react';
import { Link } from 'react-router-dom';

export default function SearchMatchCard({ item, isTopMatch = false }) {
  const isFound = item.status === 'found';
  return (
    <div
      className="card animate-fadeInUp"
      style={{
        display: 'flex', flexDirection: 'column',
        border: '1px solid var(--border)',
        overflow: 'hidden', position: 'relative'
      }}
    >
      <div style={{ position: 'relative', paddingTop: '65%' }}>
        <img
          src={item.img}
          alt={item.title}
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
        />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.4) 0%, transparent 40%)' }} />
      </div>

      <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', flex: 1 }}>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
          <span className={`badge ${isFound ? 'badge-found' : 'badge-lost'}`}>
            {isFound ? 'Nhặt được' : 'Cần tìm'}
          </span>
          <span className="badge badge-accent">{item.category || 'Ví & Giấy tờ'}</span>
        </div>

        <h3 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '12px', color: 'var(--text-primary)', lineHeight: 1.3 }}>
          {item.title}
        </h3>

        <div style={{ marginTop: 'auto' }}>
          <Link to={`/chi-tiet/${item.id}`} className="btn btn-ghost btn-full">
            Xem chi tiết
          </Link>
        </div>
      </div>
    </div>
  );
}
