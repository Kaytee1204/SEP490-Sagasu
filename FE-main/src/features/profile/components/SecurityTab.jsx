import React, { useState } from 'react';
import { Shield, AlertTriangle } from 'lucide-react';

export default function SecurityTab() {
  const [twoFA, setTwoFA] = useState(true);
  const [emailNotif, setEmailNotif] = useState(true);
  const [pushNotif, setPushNotif] = useState(false);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* 2FA block */}
      <div
        style={{
          background: twoFA ? 'rgba(16, 185, 129, 0.04)' : 'rgba(239, 68, 68, 0.04)',
          border: `1px solid ${twoFA ? 'rgba(16, 185, 129, 0.2)' : 'rgba(239, 68, 68, 0.2)'}`,
          borderRadius: '10px',
          padding: '18px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: 38,
                height: 38,
                borderRadius: '8px',
                background: twoFA ? 'var(--status-found-bg)' : 'rgba(239, 68, 68, 0.1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: twoFA ? 'var(--status-found)' : '#ef4444',
              }}
            >
              <Shield size={18} strokeWidth={1.8} />
            </div>
            <div>
              <div style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
                Xác thực 2 lớp (OTP qua Email)
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
                {twoFA ? 'Đang bảo vệ tài khoản của bạn khi đăng nhập' : 'Bật để bảo vệ tài khoản'}
              </div>
            </div>
          </div>
          {/* Toggle */}
          <button
            id="toggle-2fa-btn"
            type="button"
            onClick={() => setTwoFA(!twoFA)}
            style={{
              width: 44,
              height: 24,
              borderRadius: '999px',
              background: twoFA ? 'var(--status-found)' : 'var(--border-strong)',
              border: 'none',
              cursor: 'pointer',
              position: 'relative',
              transition: 'background 200ms',
              flexShrink: 0,
            }}
            aria-label="Toggle 2FA"
          >
            <div
              style={{
                width: 18,
                height: 18,
                borderRadius: '50%',
                background: 'white',
                position: 'absolute',
                top: '3px',
                left: twoFA ? '23px' : '3px',
                transition: 'left 200ms ease',
                boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
              }}
            />
          </button>
        </div>
      </div>

      {/* Notification settings */}
      <div
        style={{
          border: '1px solid #e2e8f0',
          borderRadius: '10px',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '12px 18px',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
          }}
        >
          <div style={{ fontWeight: 700, fontSize: '0.85rem', color: 'var(--text-primary)' }}>
            Cài đặt thông báo
          </div>
        </div>
        {[
          {
            id: 'email-notif',
            label: 'Thông báo qua Email',
            desc: 'Nhận thông báo khi có cập nhật về bài đăng',
            val: emailNotif,
            set: setEmailNotif,
          },
          {
            id: 'push-notif',
            label: 'Thông báo trình duyệt',
            desc: 'Cập nhật thời gian thực trên thiết bị',
            val: pushNotif,
            set: setPushNotif,
          },
        ].map((item, i, arr) => (
          <div
            key={item.id}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '16px',
              padding: '14px 18px',
              borderBottom: i < arr.length - 1 ? '1px solid #f1f5f9' : 'none',
            }}
          >
            <div>
              <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
                {item.label}
              </div>
              <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>{item.desc}</div>
            </div>
            <button
              id={item.id}
              type="button"
              onClick={() => item.set(!item.val)}
              style={{
                width: 44,
                height: 24,
                borderRadius: '999px',
                background: item.val ? 'var(--accent)' : 'var(--border-strong)',
                border: 'none',
                cursor: 'pointer',
                position: 'relative',
                transition: 'background 200ms',
                flexShrink: 0,
              }}
            >
              <div
                style={{
                  width: 18,
                  height: 18,
                  borderRadius: '50%',
                  background: 'white',
                  position: 'absolute',
                  top: '3px',
                  left: item.val ? '23px' : '3px',
                  transition: 'left 200ms ease',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
                }}
              />
            </button>
          </div>
        ))}
      </div>

      {/* Danger zone */}
      <div
        style={{
          border: '1px solid rgba(239, 68, 68, 0.2)',
          borderRadius: '10px',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '12px 18px',
            borderBottom: '1px solid rgba(239, 68, 68, 0.12)',
            background: 'rgba(239, 68, 68, 0.03)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={15} style={{ color: '#ef4444' }} strokeWidth={1.8} />
            <div style={{ fontWeight: 700, fontSize: '0.85rem', color: '#ef4444' }}>Vùng nguy hiểm</div>
          </div>
        </div>
        <div
          style={{
            padding: '14px 18px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '16px',
          }}
        >
          <div>
            <div style={{ fontWeight: 600, fontSize: '0.85rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
              Vô hiệu hóa tài khoản
            </div>
            <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>
              Tạm ngưng truy cập hệ thống đối với tài khoản này
            </div>
          </div>
          <button
            id="delete-account-btn"
            type="button"
            style={{
              padding: '7px 14px',
              borderRadius: '8px',
              background: 'none',
              border: '1px solid rgba(239, 68, 68, 0.4)',
              color: '#ef4444',
              fontSize: '0.8rem',
              fontWeight: 600,
              cursor: 'pointer',
              fontFamily: 'var(--font-body)',
              flexShrink: 0,
            }}
          >
            Khóa tài khoản
          </button>
        </div>
      </div>
    </div>
  );
}
