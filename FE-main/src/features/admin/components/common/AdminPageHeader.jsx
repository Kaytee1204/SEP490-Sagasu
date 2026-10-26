import React from 'react';

export default function AdminPageHeader({ title, description, counts }) {
  return (
    <div style={{
      padding: '20px 24px', borderBottom: '1px solid rgba(0,0,0,0.06)',
      display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px'
    }}>
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <h2 style={{ fontFamily: 'var(--font-display)', fontSize: '1.2rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
            {title}
          </h2>
          {counts?.pending > 0 ? (
            <span style={{ fontSize: '0.75rem', fontWeight: 700, background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca', padding: '2px 8px', borderRadius: '12px' }}>
              {counts.pending} ca chờ xử lý
            </span>
          ) : (
            <span style={{ fontSize: '0.75rem', fontWeight: 600, background: '#ecfdf5', color: '#059669', padding: '2px 8px', borderRadius: '12px' }}>
              Tất cả đã giải quyết
            </span>
          )}
        </div>
        {description && (
          <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', margin: '4px 0 0' }}>
            {description}
          </p>
        )}
      </div>

      {counts && (
        <div style={{ display: 'flex', gap: '8px', fontSize: '0.775rem', color: 'var(--text-muted)' }}>
          <span style={{ fontWeight: 600, color: '#dc2626' }}>{counts.pending} Chờ xử lý</span>
          <span>·</span>
          <span style={{ fontWeight: 600, color: '#059669' }}>{counts.resolved} Đã xong</span>
          <span>·</span>
          <span style={{ fontWeight: 600, color: 'var(--text-muted)' }}>{counts.dismissed} Đã bác bỏ</span>
        </div>
      )}
    </div>
  );
}
