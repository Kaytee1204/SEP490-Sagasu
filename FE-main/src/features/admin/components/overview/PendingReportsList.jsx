import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, CheckCircle2 } from 'lucide-react';

export default function PendingReportsList({ pendingReports }) {
  const navigate = useNavigate();

  return (
    <div style={{
      background: 'white', borderRadius: '10px',
      border: '1px solid rgba(0,0,0,0.07)',
      boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
      overflow: 'hidden'
    }}>
      {/* Header box */}
      <div style={{
        padding: '14px 18px', borderBottom: '1px solid rgba(0,0,0,0.06)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#dc2626' }} />
          <h2 style={{ fontSize: '0.9rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
            Khiếu nại chờ xử lý ({pendingReports.length})
          </h2>
        </div>
        <button
          onClick={() => navigate('/admin/reports')}
          style={{
            background: 'none', border: 'none', color: 'var(--teal-700)',
            fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer',
            display: 'flex', alignItems: 'center', gap: '2px'
          }}
        >
          Xem tất cả <ChevronRight size={13} />
        </button>
      </div>

      {/* List items */}
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {pendingReports.map((report, idx) => (
          <div
            key={report.id}
            onClick={() => navigate(`/admin/reports?id=${report.id}`)}
            style={{
              padding: '14px 18px',
              borderBottom: idx < pendingReports.length - 1 ? '1px solid rgba(0,0,0,0.05)' : 'none',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '14px',
              cursor: 'pointer', transition: 'background 120ms'
            }}
            onMouseEnter={e => { e.currentTarget.style.background = '#f8fafc'; }}
            onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px' }}>
                <span style={{
                  fontSize: '0.65rem', fontWeight: 700, textTransform: 'uppercase',
                  padding: '1px 5px', borderRadius: '3px',
                  background: '#fef2f2', color: '#dc2626'
                }}>
                  {report.typeLabel}
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
                  {report.id}
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>
                  · {report.time}
                </span>
              </div>

              <div style={{
                fontSize: '0.825rem', fontWeight: 600, color: 'var(--text-primary)',
                lineHeight: 1.4, margin: '2px 0 4px',
                whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
              }}>
                "{report.reason}"
              </div>

              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Bị tố cáo: <strong style={{ color: '#be123c' }}>{report.reportedUser}</strong> ({report.reportedUserPhone})
              </div>
            </div>

            <div style={{
              padding: '5px 8px', borderRadius: '5px',
              background: 'rgba(30,107,107,0.06)', color: 'var(--teal-700)',
              fontSize: '0.725rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '2px', flexShrink: 0
            }}>
              <span>Chi tiết</span>
              <ChevronRight size={13} />
            </div>
          </div>
        ))}

        {pendingReports.length === 0 && (
          <div style={{ padding: '32px 20px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.825rem' }}>
            <CheckCircle2 size={20} style={{ color: '#10b981', margin: '0 auto 6px' }} />
            Tất cả khiếu nại đã được giải quyết
          </div>
        )}
      </div>
    </div>
  );
}
