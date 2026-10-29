import React from 'react';

export default function ProfileStatCard({ stat }) {
  const Icon = stat.icon;
  return (
    <div
      style={{
        background: 'var(--bg-surface)',
        border: '1px solid #e2e8f0',
        borderRadius: '10px',
        padding: '16px 20px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '14px',
        boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
      }}
    >
      <div>
        <div
          style={{
            fontSize: '0.78rem',
            fontWeight: 600,
            color: 'var(--text-secondary)',
            marginBottom: '4px',
          }}
        >
          {stat.label}
        </div>
        <div
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: '1.5rem',
            fontWeight: 700,
            color: 'var(--text-primary)',
            lineHeight: 1.15,
          }}
        >
          {stat.value}
        </div>
        {stat.sub && (
          <div
            style={{
              fontSize: '0.74rem',
              color: 'var(--text-muted)',
              marginTop: '4px',
            }}
          >
            {stat.sub}
          </div>
        )}
      </div>

      <div
        style={{
          width: 42,
          height: 42,
          borderRadius: '8px',
          background: 'rgba(30, 107, 107, 0.08)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--accent)',
          flexShrink: 0,
        }}
      >
        <Icon size={19} strokeWidth={1.8} />
      </div>
    </div>
  );
}
