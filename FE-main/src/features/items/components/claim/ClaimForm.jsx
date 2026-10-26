import React from 'react';
import { Lock, FileText, Image as ImageIcon, Camera, X, ArrowRight } from 'lucide-react';

export default function ClaimForm({ item, formData, onChange, onUpload, onSubmit }) {
  return (
    <div className="card animate-fadeInUp" style={{ padding: '40px', boxShadow: 'var(--shadow-lg)' }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: '16px',
        background: 'rgba(99,102,241,0.05)', padding: '20px',
        borderRadius: 'var(--radius-lg)', marginBottom: '32px',
        border: '1px solid rgba(99,102,241,0.2)'
      }}>
        <Lock size={24} style={{ color: 'var(--status-done)', flexShrink: 0 }} />
        <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
          <strong style={{ color: 'var(--text-primary)', display: 'block', marginBottom: '4px' }}>Bảo mật thông tin</strong>
          Thông tin xác minh của bạn chỉ được gửi đến <strong>{item.finder}</strong> (người nhặt). Hệ thống sẽ theo dõi quá trình để đảm bảo minh bạch.
        </div>
      </div>

      <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
        {/* 1. Secret Detail */}
        <div className="input-group">
          <label className="input-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <FileText size={16} /> Đặc điểm nhận dạng bí mật <span style={{ color: 'var(--status-lost)' }}>*</span>
          </label>
          <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
            Hãy mô tả một chi tiết mà chỉ chủ nhân thực sự mới biết (VD: Số tiền chính xác trong ví, thẻ ngân hàng tên gì, đồ vật bị xước ở đâu...).
          </div>
          <textarea
            className="input"
            rows={4}
            placeholder="Nhập đặc điểm bí mật..."
            value={formData.secretDetail}
            onChange={e => onChange({ ...formData, secretDetail: e.target.value })}
            required
          />
        </div>

        {/* 2. Proof Image */}
        <div>
          <label className="input-label" style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <ImageIcon size={16} /> Ảnh bằng chứng (Tuỳ chọn)
          </label>
          <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
            Ảnh chụp chung với đồ vật, ảnh hoá đơn mua hàng, hoặc ảnh đồ vật trước khi bị mất.
          </div>
          <div
            onClick={() => document.getElementById('proof-upload').click()}
            style={{
              border: '2px dashed var(--border-strong)', borderRadius: 'var(--radius-lg)',
              padding: formData.proofImage ? '12px' : '32px 20px', textAlign: 'center', cursor: 'pointer',
              background: 'var(--bg-canvas)', transition: 'all 200ms'
            }}
          >
            {formData.proofImage ? (
              <div style={{ position: 'relative', height: 160 }}>
                <img
                  src={formData.proofImage}
                  alt="Proof"
                  style={{ width: '100%', height: '100%', objectFit: 'contain', borderRadius: 'var(--radius-md)' }}
                />
                <button
                  type="button"
                  onClick={(e) => { e.stopPropagation(); onChange({ ...formData, proofImage: null }); }}
                  style={{
                    position: 'absolute', top: 8, right: 8, width: 32, height: 32,
                    borderRadius: '50%', background: 'rgba(0,0,0,0.6)', color: 'white',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', border: 'none', cursor: 'pointer'
                  }}
                >
                  <X size={16} />
                </button>
              </div>
            ) : (
              <>
                <Camera size={24} style={{ color: 'var(--text-muted)', margin: '0 auto 12px' }} />
                <div style={{ fontSize: '0.9375rem', fontWeight: 600, color: 'var(--text-primary)' }}>Tải ảnh lên</div>
              </>
            )}
            <input
              id="proof-upload"
              type="file"
              onChange={onUpload}
              accept="image/*"
              style={{ display: 'none' }}
            />
          </div>
        </div>

        {/* 3. Contact Info */}
        <div className="input-group">
          <label className="input-label">Thông tin liên lạc</label>
          <input
            type="text"
            className="input"
            value={formData.contactInfo}
            readOnly
            style={{ background: 'var(--bg-canvas)', color: 'var(--text-secondary)' }}
          />
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '8px' }}>
            Sử dụng SĐT mặc định trong hồ sơ cá nhân.
          </div>
        </div>

        <div className="input-group">
          <label className="input-label">Thời gian / Địa điểm gặp mong muốn</label>
          <input
            type="text"
            className="input"
            placeholder="VD: Chiều nay sau 17h tại thư viện..."
            value={formData.meetingPref}
            onChange={e => onChange({ ...formData, meetingPref: e.target.value })}
          />
        </div>

        <div style={{ paddingTop: '24px', borderTop: '1px solid var(--border)', display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" className="btn btn-primary btn-lg" style={{ background: 'var(--accent)' }}>
            Gửi yêu cầu xác minh <ArrowRight size={16} />
          </button>
        </div>
      </form>
    </div>
  );
}
