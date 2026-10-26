import React from 'react';
import { Mail, Ban } from 'lucide-react';

export default function UserDetailsHeader({ user, onSendWarning, onToggleBan }) {
  return (
    <div style={{
      background: 'white', borderRadius: '12px', padding: '20px 24px',
      border: '1px solid rgba(0,0,0,0.08)', boxShadow: '0 1px 3px rgba(0,0,0,0.02)',
      display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px'
    }}>
      <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
        <div style={{
          width: '56px', height: '56px', borderRadius: '10px',
          background: user.role === 'Admin' ? '#f3e8ff' : '#0d2b2b',
          color: user.role === 'Admin' ? '#7e22ce' : 'white',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '1.5rem', fontWeight: 800, flexShrink: 0
        }}>
          {user.name.charAt(0)}
        </div>

        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <h1 style={{ margin: 0, fontSize: '1.35rem', fontWeight: 800, color: 'var(--text-primary)', fontFamily: 'var(--font-display)' }}>
              {user.name}
            </h1>
            <span style={{ fontSize: '0.725rem', fontWeight: 600, color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', background: '#f1f5f9', padding: '2px 6px', borderRadius: '4px' }}>
              {user.id}
            </span>
          </div>
          <div style={{ display: 'flex', gap: '12px', fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '4px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}><Mail size={12} /> {user.email}</span>
            <span>•</span>
            <span style={{ color: user.status === 'Hoạt động' ? '#059669' : '#dc2626', fontWeight: 600 }}>
              {user.status === 'Hoạt động' ? 'Đang hoạt động' : 'Đã tạm khóa'}
            </span>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          onClick={onSendWarning}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '6px',
            padding: '8px 14px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.1)',
            background: 'white', color: 'var(--text-secondary)', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer'
          }}
        >
          Gửi thông báo
        </button>
        <button
          onClick={onToggleBan}
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '6px',
            padding: '8px 14px', borderRadius: '6px', border: 'none',
            background: user.status === 'Hoạt động' ? '#dc2626' : 'var(--teal-600)',
            color: 'white', fontSize: '0.8rem', fontWeight: 600, cursor: 'pointer'
          }}
        >
          <Ban size={14} />
          <span>{user.status === 'Hoạt động' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}</span>
        </button>
      </div>
    </div>
  );
}
