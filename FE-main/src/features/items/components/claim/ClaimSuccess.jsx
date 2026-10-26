import React from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle } from 'lucide-react';

export default function ClaimSuccess() {
  const navigate = useNavigate();

  return (
    <div className="card animate-scaleIn" style={{ padding: '60px 40px', textAlign: 'center', boxShadow: 'var(--shadow-lg)' }}>
      <div style={{
        width: 80, height: 80, borderRadius: '50%', background: 'var(--status-found-bg)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 24px'
      }}>
        <CheckCircle size={40} color="var(--status-found)" />
      </div>
      <h2 className="text-display-sm" style={{ marginBottom: '16px' }}>Đã gửi yêu cầu xác minh!</h2>
      <p className="text-body" style={{ color: 'var(--text-secondary)', maxWidth: '400px', margin: '0 auto 32px' }}>
        Yêu cầu của bạn đã được gửi tới người nhặt. Vui lòng theo dõi trạng thái tại mục <strong>Đơn xác minh của tôi</strong>. Hệ thống sẽ thông báo ngay khi có phản hồi.
      </p>

      <div style={{ display: 'flex', justifyContent: 'center', gap: '16px' }}>
        <button type="button" className="btn btn-outline" onClick={() => navigate('/')}>
          Về trang chủ
        </button>
        <button type="button" className="btn btn-primary" onClick={() => navigate('/ho-so')}>
          Xem đơn xác minh
        </button>
      </div>
    </div>
  );
}
