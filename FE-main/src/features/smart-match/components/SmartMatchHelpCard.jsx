import React from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertTriangle } from 'lucide-react';

export default function SmartMatchHelpCard() {
  const navigate = useNavigate();

  return (
    <div style={{
      marginTop: '60px', padding: '32px', background: 'white',
      borderRadius: 'var(--radius-xl)', border: '1px solid var(--border)',
      display: 'flex', alignItems: 'flex-start', gap: '20px'
    }}>
      <div style={{
        width: 48, height: 48, borderRadius: '50%', background: 'var(--status-warning-bg)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
      }}>
        <AlertTriangle size={24} style={{ color: 'var(--status-warning)' }} />
      </div>
      <div>
        <h3 style={{ fontSize: '1.125rem', fontWeight: 700, marginBottom: '8px' }}>Chưa thấy món đồ của bạn?</h3>
        <p style={{ color: 'var(--text-secondary)', marginBottom: '20px', lineHeight: 1.6 }}>
          Đừng lo lắng! Tin đăng của bạn vẫn đang hoạt động. Bất cứ khi nào có người đăng tin nhặt được một món đồ khớp với mô tả của bạn (tỉ lệ &gt; 60%), hệ thống sẽ tự động gửi thông báo (Notification/Email) ngay lập tức.
        </p>
        <button
          type="button"
          className="btn btn-outline"
          onClick={() => navigate('/')}
        >
          Về trang chủ
        </button>
      </div>
    </div>
  );
}
