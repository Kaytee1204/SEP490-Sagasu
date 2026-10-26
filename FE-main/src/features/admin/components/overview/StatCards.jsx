import React from 'react';

export default function StatCards({ reports, items, pendingReports }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px' }}>
      {[
        { label: 'Tổng tin đăng', value: '1,842', change: '+12.5%', isUp: true },
        { label: 'Đã trao trả thành công', value: '1,420', change: '+18.2%', isUp: true },
        { label: 'Báo cáo vi phạm', value: `${reports.length}`, sub: `${pendingReports.length} chờ xử lý`, isWarning: pendingReports.length > 0 },
        { label: 'Tài khoản tạm khóa', value: '24', change: '+2 tuần này', isUp: false },
      ].map((stat, i) => (
        <div key={i} style={{
          background: 'white', borderRadius: '10px',
          padding: '16px 20px', border: '1px solid rgba(0,0,0,0.07)',
          boxShadow: '0 1px 2px rgba(0,0,0,0.02)'
        }}>
          <div style={{ fontSize: '0.775rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px' }}>
            {stat.label}
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
            <span style={{
              fontFamily: 'var(--font-display)', fontSize: '1.75rem', fontWeight: 700,
              color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums', lineHeight: 1
            }}>
              {stat.value}
            </span>
            {stat.change && (
              <span style={{
                fontSize: '0.7rem', fontWeight: 600,
                color: stat.isUp ? '#059669' : '#64748b',
                background: stat.isUp ? '#ecfdf5' : '#f1f5f9',
                padding: '2px 6px', borderRadius: '4px'
              }}>
                {stat.change}
              </span>
            )}
            {stat.sub && (
              <span style={{
                fontSize: '0.7rem', fontWeight: 600,
                color: stat.isWarning ? '#dc2626' : '#64748b',
                background: stat.isWarning ? '#fef2f2' : '#f1f5f9',
                padding: '2px 6px', borderRadius: '4px'
              }}>
                {stat.sub}
              </span>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
