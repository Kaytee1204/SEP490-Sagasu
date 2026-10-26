import React from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Clock } from 'lucide-react';

export default function SmartMatchCard({ item }) {
  return (
    <div
      className="card animate-fadeInUp"
      style={{
        display: 'flex', flexDirection: 'column',
        border: '1px solid rgba(16,185,129,0.3)',
        overflow: 'hidden', position: 'relative'
      }}
    >
      <div style={{
        position: 'absolute', top: 12, right: 12, zIndex: 10,
        background: item.matchScore >= 80 ? 'var(--status-found)' : 'var(--status-warning)',
        color: 'white', fontWeight: 800, fontSize: '0.9375rem',
        padding: '6px 12px', borderRadius: 'var(--radius-md)',
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
        display: 'flex', alignItems: 'center', gap: '4px'
      }}>
        {item.matchScore}%
        <span style={{ fontSize: '0.65rem', fontWeight: 600, textTransform: 'uppercase', opacity: 0.9 }}>Match</span>
      </div>

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
          <span className="badge badge-found">Nhặt được</span>
          <span className="badge badge-accent">Ví & Giấy tờ</span>
        </div>

        <h3 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '12px', color: 'var(--text-primary)', lineHeight: 1.3 }}>
          {item.title}
        </h3>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
            <MapPin size={14} style={{ color: 'var(--text-muted)' }} />
            {item.location}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
            <Clock size={14} style={{ color: 'var(--text-muted)' }} />
            {item.time} — {item.date}
          </div>
        </div>

        <div style={{
          background: 'var(--bg-canvas)', borderRadius: 'var(--radius-md)',
          padding: '12px', marginBottom: '20px', border: '1px solid var(--border)'
        }}>
          <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px', textTransform: 'uppercase' }}>
            Chi tiết AI Match
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px' }}>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Hình ảnh</div>
              <div style={{ fontSize: '0.875rem', fontWeight: 700, color: 'var(--status-found)' }}>{item.matchBreakdown.visual}%</div>
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Địa điểm</div>
              <div style={{ fontSize: '0.875rem', fontWeight: 700, color: 'var(--status-warning)' }}>{item.matchBreakdown.location}%</div>
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Thời gian</div>
              <div style={{ fontSize: '0.875rem', fontWeight: 700, color: 'var(--status-found)' }}>{item.matchBreakdown.time}%</div>
            </div>
          </div>
        </div>

        <div style={{ marginTop: 'auto' }}>
          <Link to={`/chi-tiet/${item.id}`} className="btn btn-primary btn-full">
            Xem chi tiết & Xác minh
          </Link>
        </div>
      </div>
    </div>
  );
}
