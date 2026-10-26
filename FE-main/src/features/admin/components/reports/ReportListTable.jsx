import React from 'react';

export default function ReportListTable({ reports, filteredReports, currentFilter, onFilterChange, onSelectReport }) {
  const pendingCount = reports.filter(r => r.status === 'pending').length;
  const resolvedCount = reports.filter(r => r.status === 'resolved').length;

  return (
    <div>
      {/* Toolbar: Fast Filter Tabs */}
      <div style={{
        padding: '12px 24px', background: '#fafbfc', borderBottom: '1px solid rgba(0,0,0,0.06)',
        display: 'flex', gap: '6px', flexWrap: 'wrap'
      }}>
        {[
          { key: 'pending', label: 'Chờ xử lý', count: pendingCount },
          { key: 'fraud_claim', label: 'Nghi vấn mạo nhận', count: reports.filter(r => r.reportType === 'fraud_claim').length },
          { key: 'extortion', label: 'Đòi tiền chuộc', count: reports.filter(r => r.reportType === 'extortion').length },
          { key: 'dispute', label: 'Tranh chấp', count: reports.filter(r => r.reportType === 'dispute').length },
          { key: 'resolved', label: 'Đã giải quyết', count: resolvedCount },
          { key: 'all', label: 'Tất cả ca', count: reports.length },
        ].map(f => (
          <button
            key={f.key}
            type="button"
            onClick={() => onFilterChange(f.key)}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: '6px',
              padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 600,
              border: currentFilter === f.key ? '1px solid var(--teal-600)' : '1px solid transparent',
              background: currentFilter === f.key ? 'white' : 'transparent',
              color: currentFilter === f.key ? 'var(--teal-700)' : 'var(--text-secondary)',
              cursor: 'pointer', transition: 'all 120ms'
            }}
          >
            <span>{f.label}</span>
            <span style={{
              fontSize: '0.7rem', padding: '1px 6px', borderRadius: '10px',
              background: currentFilter === f.key ? 'var(--teal-50)' : 'rgba(0,0,0,0.04)',
              color: currentFilter === f.key ? 'var(--teal-700)' : 'var(--text-muted)'
            }}>
              {f.count}
            </span>
          </button>
        ))}
      </div>

      {/* Summary Table */}
      <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <table style={{ width: '100%', minWidth: '820px', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
          <thead>
            <tr style={{ background: '#f8fafc', borderBottom: '1px solid rgba(0,0,0,0.06)', color: 'var(--text-secondary)' }}>
              <th style={{ padding: '12px 24px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap' }}>Mã ca &amp; Loại vi phạm</th>
              <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap' }}>Đối tượng tố cáo</th>
              <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap' }}>Tóm tắt lý do</th>
              <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap' }}>Vật phẩm</th>
              <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', whiteSpace: 'nowrap' }}>Thời gian</th>
              <th style={{ padding: '12px 24px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', textAlign: 'right', whiteSpace: 'nowrap', width: '140px' }}>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {filteredReports.map(report => (
              <tr
                key={report.id}
                onClick={() => onSelectReport(report)}
                style={{
                  borderBottom: '1px solid rgba(0,0,0,0.05)',
                  background: report.status === 'pending' ? '#fffdfd' : 'white',
                  cursor: 'pointer',
                  transition: 'background 120ms'
                }}
                onMouseEnter={e => {
                  e.currentTarget.style.background = report.status === 'pending' ? '#fff5f5' : '#f8fafc';
                }}
                onMouseLeave={e => {
                  e.currentTarget.style.background = report.status === 'pending' ? '#fffdfd' : 'white';
                }}
              >
                <td style={{ padding: '14px 24px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={{ fontSize: '0.75rem', fontWeight: 800, color: 'var(--teal-700)', fontFamily: 'var(--font-mono)' }}>
                      {report.id}
                    </span>
                    <span style={{
                      fontSize: '0.675rem', fontWeight: 700, padding: '2px 7px', borderRadius: '4px',
                      background: report.severity === 'critical' ? '#fef2f2' : report.severity === 'high' ? '#fff7ed' : '#fefce8',
                      color: report.severity === 'critical' ? '#dc2626' : report.severity === 'high' ? '#ea580c' : '#ca8a04',
                      border: `1px solid ${report.severity === 'critical' ? '#fecaca' : report.severity === 'high' ? '#fed7aa' : '#fef08a'}`
                    }}>
                      {report.severity === 'critical' ? 'Khẩn cấp' : report.severity === 'high' ? 'Cao' : 'Thường'}
                    </span>
                  </div>
                  <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-primary)', marginTop: '2px' }}>
                    {report.typeLabel}
                  </div>
                </td>

                <td style={{ padding: '14px 18px' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    Người gửi: <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{report.reporter}</span>
                  </div>
                  <div style={{ fontSize: '0.775rem', color: '#be123c', fontWeight: 600, marginTop: '2px' }}>
                    Bị tố cáo: {report.reportedUser}
                  </div>
                </td>

                <td style={{ padding: '14px 18px', maxWidth: '280px' }}>
                  <div style={{
                    fontSize: '0.8rem', color: 'var(--text-primary)',
                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'
                  }}>
                    "{report.reason}"
                  </div>
                  <div style={{
                    fontSize: '0.725rem', color: 'var(--text-muted)',
                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', marginTop: '2px'
                  }}>
                    {report.evidence}
                  </div>
                </td>

                <td style={{ padding: '14px 18px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <img
                      src={report.itemImg}
                      alt={report.itemName}
                      style={{ width: '28px', height: '28px', borderRadius: '4px', objectFit: 'cover' }}
                    />
                    <div style={{ fontSize: '0.8rem', fontWeight: 500, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '140px' }}>
                      {report.itemName}
                    </div>
                  </div>
                </td>

                <td style={{ padding: '14px 18px', fontSize: '0.775rem', color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                  {report.time}
                </td>

                <td style={{ padding: '14px 24px', textAlign: 'right', whiteSpace: 'nowrap', width: '140px' }}>
                  <span style={{
                    display: 'inline-flex', alignItems: 'center', gap: '6px',
                    fontSize: '0.75rem', fontWeight: 600, padding: '4px 10px', borderRadius: '6px',
                    whiteSpace: 'nowrap',
                    background: report.status === 'resolved' ? '#ecfdf5' : report.status === 'dismissed' ? '#f1f5f9' : '#fff1f2',
                    color: report.status === 'resolved' ? '#059669' : report.status === 'dismissed' ? '#64748b' : '#e11d48',
                    border: `1px solid ${report.status === 'resolved' ? '#a7f3d0' : report.status === 'dismissed' ? '#e2e8f0' : '#fecdd3'}`
                  }}>
                    <span style={{
                      width: 6, height: 6, borderRadius: '50%', flexShrink: 0,
                      background: report.status === 'resolved' ? '#10b981' : report.status === 'dismissed' ? '#94a3b8' : '#e11d48'
                    }} />
                    {report.status === 'resolved' ? 'Đã giải quyết' : report.status === 'dismissed' ? 'Đã bác bỏ' : 'Chờ xử lý'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {filteredReports.length === 0 && (
        <div style={{ textAlign: 'center', padding: '48px', color: 'var(--text-muted)' }}>
          Không có báo cáo vi phạm nào trong danh mục này.
        </div>
      )}
    </div>
  );
}
