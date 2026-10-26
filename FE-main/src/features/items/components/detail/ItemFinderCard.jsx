import React from 'react';
import { CheckCircle } from 'lucide-react';

export default function ItemFinderCard({ finder }) {
  if (!finder) return null;

  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      padding: '16px', background: 'white', borderRadius: 'var(--radius-xl)',
      border: '1px solid var(--border)'
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <img
          src={finder.avatar}
          alt="Finder"
          style={{ width: 44, height: 44, borderRadius: '50%' }}
        />
        <div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '2px' }}>
            Người đăng tin
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontWeight: 600, fontSize: '0.9375rem' }}>{finder.name}</span>
            {finder.verified && <CheckCircle size={14} style={{ color: 'var(--status-found)' }} />}
          </div>
        </div>
      </div>
      <button type="button" className="btn btn-ghost btn-sm">Xem hồ sơ</button>
    </div>
  );
}
