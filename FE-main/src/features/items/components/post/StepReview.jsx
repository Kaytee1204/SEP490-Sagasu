import React from 'react';
import { ShieldCheck, MapPin, Clock } from 'lucide-react';

export default function StepReview({ formData, postType, requiresReview = false }) {
  const officeHandover = postType === 'found' && formData.custodyMode === 'OFFICE';
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
            Tiêu đề, mô tả và ảnh là nội dung công khai khi bài được hiển thị. Không đưa số seri, thông tin cá nhân hoặc đặc điểm bí mật vào nội dung công khai.
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '20px', alignItems: 'flex-start' }}>
        {formData.images[0] && <img
          src={formData.images[0]}
          alt="Preview"
          style={{ width: 120, height: 120, objectFit: 'cover', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border)' }}
        />}
        <div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, marginBottom: '8px' }}>
            {formData.itemName || 'Vật phẩm'}
          </h3>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
            <span className={`badge ${postType === 'lost' ? 'badge-lost' : 'badge-found'}`}>
              {postType === 'lost' ? 'Cần tìm' : 'Nhặt được'}
            </span>
          </div>
          <p>Danh mục: {formData.categoryName}</p>
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
      <p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{formData.description}</p>
      {postType === 'found' && <p>Giữ đồ: {formData.custodyMode === 'OFFICE'
        ? 'Giao đồ cho Approver — chờ xét duyệt.' : 'Tự giữ để trao trả.'}</p>}
      {officeHandover && <p>Lịch giao đồ bạn đề xuất: <strong>{formData.handoverDate} {formData.handoverTime}</strong> (giờ Việt Nam).
        Lịch này đang chờ Approver xem xét, chưa phải lịch hẹn được xác nhận.</p>}
      <p>Dấu hiệu riêng tư: {formData.privateIdentifiers?.trim() ? 'Đã nhập, sẽ lưu riêng và không công khai.' : 'Chưa nhập; có thể bổ sung sau khi đăng.'}</p>
      <p>{requiresReview || officeHandover ? <>Bài đăng sẽ <strong>chờ Approver duyệt</strong> trước khi hiển thị công khai.</>
        : <>Bài đăng sẽ <strong>hiển thị ngay</strong> sau khi đăng thành công.</>}</p>
    </div>
  );
}
