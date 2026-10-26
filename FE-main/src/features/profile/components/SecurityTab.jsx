import React, { useState } from 'react';
import { Shield, AlertTriangle } from 'lucide-react';

export default function SecurityTab() {
  const [twoFA, setTwoFA] = useState(true);
  const [emailNotif, setEmailNotif] = useState(true);
  const [pushNotif, setPushNotif] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* 2FA block */}
      <div style={{
        background: twoFA ? 'rgba(16,185,129,0.04)' : 'rgba(239,68,68,0.04)',
        border: `1px solid ${twoFA ? 'rgba(16,185,129,0.2)' : 'rgba(239,68,68,0.2)'}`,
        borderRadius: 'var(--radius-xl)', padding: '20px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              width: 40, height: 40, borderRadius: 'var(--radius-md)',
              background: twoFA ? 'var(--status-found-bg)' : 'rgba(239,68,68,0.1)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: twoFA ? 'var(--status-found)' : '#ef4444',
            }}>
              <Shield size={18} strokeWidth={1.8} />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.9375rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
                Xác thực 2 lớp (2FA)
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                {twoFA ? 'Đang bảo vệ tài khoản của bạn' : 'Bật để bảo vệ tài khoản'}
              </div>
            </div>
          </div>
          {/* Toggle */}
          <button
            id="toggle-2fa-btn"
            type="button"
            onClick={() => setTwoFA(!twoFA)}
            style={{
              width: 46, height: 26, borderRadius: '999px',
              background: twoFA ? 'var(--status-found)' : 'var(--border-strong)',
              border: 'none', cursor: 'pointer', position: 'relative',
              transition: 'background 200ms',
              flexShrink: 0,
            }}
            aria-label="Toggle 2FA"
          >
            <div style={{
              width: 20, height: 20, borderRadius: '50%', background: 'white',
              position: 'absolute', top: '3px',
              left: twoFA ? '23px' : '3px',
              transition: 'left 200ms cubic-bezier(0.34,1.56,0.64,1)',
              boxShadow: '0 1px 4px rgba(0,0,0,0.2)',
            }} />
          </button>
        </div>
      </div>

      {/* Notification settings */}
      <div style={{
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-xl)',
        overflow: 'hidden',
      }}>
        <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--border)', background: 'var(--bg-canvas)' }}>
          <div style={{ fontWeight: 700, fontSize: '0.875rem', color: 'var(--text-primary)' }}>Thông báo</div>
        </div>
        {[
          { id: 'email-notif', label: 'Thông báo qua Email', desc: 'Khi có kết quả AI so khớp mới', val: emailNotif, set: setEmailNotif },
          { id: 'push-notif', label: 'Thông báo đẩy trình duyệt', desc: 'Cập nhật thời gian thực trên thiết bị', val: pushNotif, set: setPushNotif },
        ].map((item, i, arr) => (
          <div
            key={item.id}
            style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px',
              padding: '16px 18px',
              borderBottom: i < arr.length - 1 ? '1px solid var(--border)' : 'none',
            }}
          >
            <div>
              <div style={{ fontWeight: 600, fontSize: '0.875rem', color: 'var(--text-primary)', marginBottom: '2px' }}>{item.label}</div>
              <div style={{ fontSize: '0.775rem', color: 'var(--text-muted)' }}>{item.desc}</div>
            </div>
            <button
              id={item.id}
              type="button"
              onClick={() => item.set(!item.val)}
              style={{
                width: 46, height: 26, borderRadius: '999px',
                background: item.val ? 'var(--accent)' : 'var(--border-strong)',
                border: 'none', cursor: 'pointer', position: 'relative',
                transition: 'background 200ms', flexShrink: 0,
              }}
            >
              <div style={{
                width: 20, height: 20, borderRadius: '50%', background: 'white',
                position: 'absolute', top: '3px',
                left: item.val ? '23px' : '3px',
                transition: 'left 200ms cubic-bezier(0.34,1.56,0.64,1)',
                boxShadow: '0 1px 4px rgba(0,0,0,0.2)',
              }} />
            </button>
          </div>
        ))}
      </div>

      {/* Danger zone */}
      <div style={{
        border: '1px solid rgba(239,68,68,0.2)',
        borderRadius: 'var(--radius-xl)',
        overflow: 'hidden',
      }}>
        <div style={{ padding: '14px 18px', borderBottom: '1px solid rgba(239,68,68,0.1)', background: 'rgba(239,68,68,0.03)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={15} style={{ color: '#ef4444' }} strokeWidth={1.8} />
            <div style={{ fontWeight: 700, fontSize: '0.875rem', color: '#ef4444' }}>Vùng nguy hiểm</div>
          </div>
        </div>
        <div style={{ padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
          <div>
            <div style={{ fontWeight: 600, fontSize: '0.875rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
              Xóa tài khoản vĩnh viễn
            </div>
            <div style={{ fontSize: '0.775rem', color: 'var(--text-muted)' }}>
              Toàn bộ dữ liệu sẽ bị xóa và không thể khôi phục
            </div>
          </div>
          <button
            id="delete-account-btn"
            type="button"
            style={{
              padding: '7px 16px', borderRadius: 'var(--radius-md)',
              background: 'none', border: '1.5px solid rgba(239,68,68,0.4)',
              color: '#ef4444', fontSize: '0.8125rem', fontWeight: 600,
              cursor: 'pointer', fontFamily: 'var(--font-body)',
              transition: 'all 150ms', flexShrink: 0,
            }}
            onMouseEnter={e => { e.currentTarget.style.background = 'rgba(239,68,68,0.08)'; }}
            onMouseLeave={e => { e.currentTarget.style.background = 'none'; }}
          >
            Xóa tài khoản
          </button>
        </div>
      </div>
    </div>
  );
}
