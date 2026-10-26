import React from 'react';

export default function ProfileStatCard({ stat, index }) {
  const Icon = stat.icon;
  return (
    <div
      className="animate-fadeInUp"
      style={{
        animationDelay: `${index * 0.07}s`,
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-xl)',
        padding: '22px 24px',
        transition: 'all 250ms cubic-bezier(0.34,1.56,0.64,1)',
        cursor: 'default',
      }}
      onMouseEnter={e => {
        e.currentTarget.style.transform = 'translateY(-3px)';
        e.currentTarget.style.boxShadow = 'var(--shadow-md)';
        e.currentTarget.style.borderColor = 'rgba(30,107,107,0.25)';
      }}
      onMouseLeave={e => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.boxShadow = 'none';
        e.currentTarget.style.borderColor = 'var(--border)';
      }}
    >
      <div style={{
        width: 40, height: 40, borderRadius: 'var(--radius-md)',
        background: 'rgba(30,107,107,0.08)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        color: 'var(--accent)', marginBottom: '16px',
      }}>
        <Icon size={18} strokeWidth={1.8} />
      </div>
      <div style={{
        fontFamily: 'var(--font-display)', fontSize: '1.875rem',
        fontWeight: 800, letterSpacing: '-0.03em', color: 'var(--text-primary)',
        lineHeight: 1,
      }}>
        {stat.value}
      </div>
      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>{stat.sub}</div>
      <div style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--text-secondary)', marginTop: '6px' }}>
        {stat.label}
      </div>
    </div>
  );
}
