import React from 'react';
import { Image as ImageIcon, MapPin, Clock } from 'lucide-react';

export default function ItemMatchBreakdown({ matchData }) {
  if (!matchData) return null;

  return (
    <div style={{
      background: 'linear-gradient(135deg, rgba(16,185,129,0.05) 0%, rgba(16,185,129,0.1) 100%)',
      border: '1px solid rgba(16,185,129,0.2)',
      borderRadius: 'var(--radius-2xl)', padding: '24px'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
        <div style={{
          width: 48, height: 48, borderRadius: '50%', background: 'var(--status-found)',
          color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontFamily: 'var(--font-display)', fontSize: '1.125rem', fontWeight: 800,
          boxShadow: '0 4px 12px rgba(16,185,129,0.3)'
        }}>
          {matchData.score}%
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: '1.0625rem', color: 'var(--text-primary)' }}>
            Độ tương đồng cao
          </div>
          <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Hệ thống AI đánh giá vật phẩm này có khả năng cao là của bạn.
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: 120, fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <ImageIcon size={14} /> Ngoại hình
          </div>
          <div style={{ flex: 1, height: 6, background: 'rgba(16,185,129,0.1)', borderRadius: 999, overflow: 'hidden' }}>
            <div style={{ width: `${matchData.visual}%`, height: '100%', background: 'var(--status-found)', borderRadius: 999 }} />
          </div>
          <div style={{ width: 40, textAlign: 'right', fontWeight: 700, fontSize: '0.875rem', color: 'var(--status-found)' }}>
            {matchData.visual}%
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: 120, fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <MapPin size={14} /> Địa điểm
          </div>
          <div style={{ flex: 1, height: 6, background: 'rgba(16,185,129,0.1)', borderRadius: 999, overflow: 'hidden' }}>
            <div style={{ width: `${matchData.location}%`, height: '100%', background: 'var(--status-found)', borderRadius: 999 }} />
          </div>
          <div style={{ width: 40, textAlign: 'right', fontWeight: 700, fontSize: '0.875rem', color: 'var(--status-found)' }}>
            {matchData.location}%
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div style={{ width: 120, fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Clock size={14} /> Thời gian
          </div>
          <div style={{ flex: 1, height: 6, background: 'rgba(16,185,129,0.1)', borderRadius: 999, overflow: 'hidden' }}>
            <div style={{ width: `${matchData.time}%`, height: '100%', background: 'var(--status-found)', borderRadius: 999 }} />
          </div>
          <div style={{ width: 40, textAlign: 'right', fontWeight: 700, fontSize: '0.875rem', color: 'var(--status-found)' }}>
            {matchData.time}%
          </div>
        </div>
      </div>
    </div>
  );
}
