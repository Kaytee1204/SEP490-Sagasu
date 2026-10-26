import React from 'react';
import { MapPin, Clock } from 'lucide-react';

export default function ItemInfoSection({ item }) {
  return (
    <div>
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
        <span className="badge badge-accent">{item.category}</span>
        <span className="badge" style={{ background: 'white', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}>
          ID: #{item.id}
        </span>
      </div>
      <h1 className="text-display-md" style={{ marginBottom: '20px' }}>{item.title}</h1>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', paddingBottom: '24px', borderBottom: '1px solid var(--border)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', color: 'var(--text-secondary)' }}>
          <MapPin size={16} style={{ marginTop: 2, flexShrink: 0, color: 'var(--text-muted)' }} />
          <div>
            <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Địa điểm</div>
            <div style={{ fontSize: '0.9375rem' }}>{item.location}</div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px', color: 'var(--text-secondary)' }}>
          <Clock size={16} style={{ marginTop: 2, flexShrink: 0, color: 'var(--text-muted)' }} />
          <div>
            <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Thời gian</div>
            <div style={{ fontSize: '0.9375rem' }}>{item.time} ngày {item.date}</div>
          </div>
        </div>
      </div>
    </div>
  );
}
