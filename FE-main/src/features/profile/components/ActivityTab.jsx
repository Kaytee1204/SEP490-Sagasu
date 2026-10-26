import React from 'react';

export default function ActivityTab({ activityLog }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
      {activityLog.map((item, i) => {
        const Icon = item.icon;
        return (
          <div
            key={i}
            style={{
              display: 'grid', gridTemplateColumns: '36px 1fr auto',
              gap: '14px', alignItems: 'flex-start',
              padding: '16px 0',
              borderBottom: i < activityLog.length - 1 ? '1px solid var(--border)' : 'none',
            }}
          >
            <div style={{
              width: 36, height: 36, borderRadius: '50%',
              background: 'rgba(30,107,107,0.08)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: item.color, flexShrink: 0,
            }}>
              <Icon size={16} strokeWidth={1.8} />
            </div>
            <div>
              <div style={{ fontWeight: 600, fontSize: '0.875rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
                {item.label}
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>{item.detail}</div>
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', flexShrink: 0, paddingTop: '2px' }}>
              {item.time}
            </div>
          </div>
        );
      })}
    </div>
  );
}
