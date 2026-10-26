import React from 'react';
import { ArrowLeft, Phone, Ban, RotateCcw } from 'lucide-react';

export default function ReportDetailView({ 
  report, 
  onClose, 
  resolutionReason, 
  setResolutionReason, 
  onAction, 
  onReopen 
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {/* Detail Navigation Toolbar */}
      <div style={{
        padding: '12px 24px', background: '#fafbfc', borderBottom: '1px solid rgba(0,0,0,0.06)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px'
      }}>
        <button
          type="button"
          onClick={onClose}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '6px',
            padding: '6px 12px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.1)',
            background: 'white', color: 'var(--text-secondary)', fontSize: '0.8rem', fontWeight: 600,
            cursor: 'pointer', transition: 'all 120ms'
          }}
        >
          <ArrowLeft size={14} />
          <span>Quay lại danh sách khiếu nại</span>
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ fontSize: '0.775rem', fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--teal-700)', background: 'rgba(30,107,107,0.08)', padding: '2px 8px', borderRadius: '4px' }}>
            {report.id}
          </span>

          <span style={{
            fontSize: '0.725rem', fontWeight: 700, padding: '2px 8px', borderRadius: '4px',
            background: report.severity === 'critical' ? '#fef2f2' : report.severity === 'high' ? '#fff7ed' : '#fefce8',
            color: report.severity === 'critical' ? '#dc2626' : report.severity === 'high' ? '#ea580c' : '#ca8a04',
            border: `1px solid ${report.severity === 'critical' ? '#fecaca' : report.severity === 'high' ? '#fed7aa' : '#fef08a'}`
          }}>
            {report.severity === 'critical' ? 'Khẩn cấp' : report.severity === 'high' ? 'Mức độ cao' : 'Mức độ thường'}
          </span>

          <span style={{
            fontSize: '0.725rem', fontWeight: 600, padding: '3px 9px', borderRadius: '4px',
            whiteSpace: 'nowrap',
            background: report.status === 'resolved' ? '#ecfdf5' : report.status === 'dismissed' ? '#f1f5f9' : '#fff1f2',
            color: report.status === 'resolved' ? '#059669' : report.status === 'dismissed' ? '#64748b' : '#e11d48',
            border: `1px solid ${report.status === 'resolved' ? '#a7f3d0' : report.status === 'dismissed' ? '#e2e8f0' : '#fecdd3'}`
          }}>
            {report.status === 'resolved' ? 'Đã giải quyết' : report.status === 'dismissed' ? 'Đã bác bỏ' : 'Chờ xử lý'}
          </span>
        </div>
      </div>

      {/* Dossier Body */}
      <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
        {/* 2-Column Comparative Dossier */}
        <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1.8fr', gap: '24px', alignItems: 'start' }}>
          {/* CỘT TRÁI: Vật phẩm & 2 bên đối soát */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Vật phẩm liên quan */}
            <div style={{ background: '#f8fafc', borderRadius: '8px', padding: '14px 16px', border: '1px solid rgba(0,0,0,0.06)' }}>
              <div style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '8px' }}>
                Vật phẩm liên quan
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <img
                  src={report.itemImg}
                  alt={report.itemName}
                  style={{ width: '48px', height: '48px', borderRadius: '8px', objectFit: 'cover', border: '1px solid rgba(0,0,0,0.08)' }}
                />
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
                    {report.itemName}
                  </div>
                  <div style={{ fontSize: '0.725rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', marginTop: '2px' }}>
                    Mã bài đăng: {report.itemId}
                  </div>
                </div>
              </div>
            </div>

            {/* 2 Bên đối soát */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: '10px' }}>
              {/* Người tố cáo */}
              <div style={{ padding: '12px 14px', background: '#f8fafc', borderRadius: '8px', border: '1px solid rgba(0,0,0,0.06)' }}>
                <div style={{ fontSize: '0.675rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 700 }}>
                  Bên gửi tố cáo
                </div>
                <div style={{ fontWeight: 600, fontSize: '0.875rem', marginTop: '3px', color: 'var(--text-primary)' }}>
                  {report.reporter}
                </div>
                <div style={{ fontSize: '0.725rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                  Thời điểm gửi: {report.time}
                </div>
              </div>

              {/* Bên bị tố cáo */}
              <div style={{ padding: '12px 14px', background: '#fff1f2', borderRadius: '8px', border: '1px solid #fecdd3' }}>
                <div style={{ fontSize: '0.675rem', color: '#be123c', textTransform: 'uppercase', fontWeight: 700 }}>
                  Bên bị tố cáo (Tài khoản nghi vấn)
                </div>
                <div style={{ fontWeight: 700, fontSize: '0.875rem', marginTop: '3px', color: '#9f1239' }}>
                  {report.reportedUser}
                </div>
                <div style={{ fontSize: '0.775rem', color: '#be123c', display: 'flex', alignItems: 'center', gap: '5px', marginTop: '3px', fontWeight: 600 }}>
                  <Phone size={12} /> {report.reportedUserPhone}
                </div>
              </div>
            </div>
          </div>

          {/* CỘT PHẢI: Nội dung tố cáo & Bằng chứng */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* Nội dung tố cáo */}
            <div style={{ background: '#ffffff', borderRadius: '8px', padding: '16px', border: '1px solid rgba(0,0,0,0.08)' }}>
              <div style={{ fontSize: '0.725rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', marginBottom: '8px' }}>
                Loại vi phạm: <span style={{ color: 'var(--teal-700)' }}>{report.typeLabel}</span>
              </div>
              <div style={{ background: '#f8fafc', padding: '12px 14px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.04)', fontSize: '0.875rem', color: 'var(--text-primary)', lineHeight: 1.55 }}>
                "{report.reason}"
              </div>
            </div>

            {/* Bằng chứng xác minh */}
            <div style={{ background: '#ffffff', borderRadius: '8px', padding: '16px', border: '1px solid rgba(0,0,0,0.08)' }}>
              <div style={{ fontSize: '0.725rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', marginBottom: '6px' }}>
                Bằng chứng cung cấp:
              </div>
              <div style={{ fontSize: '0.825rem', color: 'var(--text-secondary)', lineHeight: 1.5, background: '#fafbfc', padding: '10px 12px', borderRadius: '6px' }}>
                {report.evidence}
              </div>
            </div>
          </div>
        </div>

        {/* ── KHU VỰC QUẢN TRỊ VIÊN XỬ LÝ ── */}
        <div style={{
          background: '#f8fafc', borderRadius: '10px',
          padding: '20px 22px', border: '1px solid rgba(0,0,0,0.08)',
          display: 'flex', flexDirection: 'column', gap: '14px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ fontWeight: 700, fontSize: '0.925rem', color: 'var(--text-primary)' }}>
              Quyết định &amp; Kết luận xử lý của Quản trị viên
            </div>
            {report.status !== 'pending' && (
              <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#059669', background: '#ecfdf5', padding: '2px 8px', borderRadius: '4px' }}>
                ✓ Đã có quyết định
              </span>
            )}
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>
              Lý do &amp; Căn cứ xử lý <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <textarea
              rows={3}
              placeholder="Nhập nhận định, căn cứ xử lý vi phạm hoặc lý do bác bỏ khiếu nại..."
              value={resolutionReason}
              onChange={(e) => setResolutionReason(e.target.value)}
              disabled={report.status !== 'pending'}
              style={{
                width: '100%', padding: '10px 12px', borderRadius: '8px',
                border: '1px solid rgba(0,0,0,0.12)', fontSize: '0.85rem',
                fontFamily: 'var(--font-body)', outline: 'none',
                background: report.status !== 'pending' ? '#f1f5f9' : 'white',
                lineHeight: 1.5, resize: 'vertical'
              }}
            />
          </div>

          {report.status === 'pending' ? (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '10px', flexWrap: 'wrap', paddingTop: '4px' }}>
              <button
                type="button"
                onClick={() => onAction('DISMISS_REPORT')}
                style={{
                  padding: '8px 16px', borderRadius: '6px', background: 'white',
                  border: '1px solid rgba(0,0,0,0.15)', color: 'var(--text-secondary)',
                  fontSize: '0.825rem', fontWeight: 600, cursor: 'pointer'
                }}
              >
                Bác bỏ khiếu nại
              </button>

              <button
                type="button"
                onClick={() => onAction('RESOLVE_REPORT')}
                style={{
                  padding: '8px 16px', borderRadius: '6px', background: 'var(--teal-600)',
                  border: 'none', color: 'white', fontSize: '0.825rem', fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                Đã giải quyết / Cảnh cáo
              </button>

              <button
                type="button"
                onClick={() => onAction('BAN_AND_RESOLVE')}
                style={{
                  padding: '8px 16px', borderRadius: '6px', background: '#dc2626',
                  border: 'none', color: 'white', fontSize: '0.825rem', fontWeight: 600,
                  cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: '6px',
                  boxShadow: '0 1px 3px rgba(220, 38, 38, 0.2)'
                }}
              >
                <Ban size={14} />
                <span>Khóa tài khoản vi phạm</span>
              </button>
            </div>
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', paddingTop: '6px' }}>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                Hồ sơ này đã kết luận. Bạn có thể mở lại ca nếu cần phúc khảo.
              </div>
              <button
                type="button"
                onClick={() => onReopen(report.id)}
                style={{
                  display: 'inline-flex', alignItems: 'center', gap: '5px',
                  padding: '6px 12px', borderRadius: '6px', background: 'white',
                  border: '1px solid rgba(0,0,0,0.15)', color: 'var(--teal-700)',
                  fontSize: '0.775rem', fontWeight: 600, cursor: 'pointer'
                }}
              >
                <RotateCcw size={13} />
                <span>Mở lại ca để xử lý lại</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
