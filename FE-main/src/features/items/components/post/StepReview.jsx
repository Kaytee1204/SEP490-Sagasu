import React from 'react';
import { ShieldCheck, MapPin, Clock } from 'lucide-react';

export default function StepReview({ formData, postType }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{
        background: 'var(--status-done-bg)', border: '1px solid rgba(99,102,241,0.2)',
        borderRadius: 'var(--radius-lg)', padding: '20px', display: 'flex', gap: '16px'
      }}>
        <ShieldCheck size={24} style={{ color: 'var(--status-done)', flexShrink: 0 }} />
        <div>
          <div style={{ fontWeight: 700, fontSize: '0.9375rem', color: 'var(--text-primary)', marginBottom: '4px' }}>
            Bảo mật thông tin
          </div>
          <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            Các thông tin nhạy cảm của vật phẩm sẽ được ẩn. Hệ thống AI chỉ dùng để so khớp ngầm. Người liên hệ cần chứng minh quyền sở hữu trước khi nhận đồ.
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-start' }}>
        <img
          src={formData.images[0] || 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80'}
          alt="Preview"
          style={{ width: 120, height: 120, objectFit: 'cover', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border)' }}
        />
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '8px' }}>
            {formData.itemName || 'Vật phẩm'}
          </h3>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
            <span className={`badge ${postType === 'lost' ? 'badge-lost' : 'badge-found'}`}>
              {postType === 'lost' ? 'Cần tìm' : 'Nhặt được'}
            </span>
          </div>
          <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <MapPin size={14} /> {formData.location || 'Chưa rõ địa điểm'}
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Clock size={14} /> {formData.date} {formData.time}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
