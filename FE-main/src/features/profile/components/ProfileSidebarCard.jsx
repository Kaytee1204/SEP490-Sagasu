import React from 'react';
import { Link } from 'react-router-dom';
import { User, Mail, Phone, Award, MapPin, Edit3, Package, Lock, Bell, Settings, LogOut, ChevronRight } from 'lucide-react';

export default function ProfileSidebarCard({ user, editMode, onStartEdit, formValues, onChangeForm, onSaveEdit, onCancelEdit }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* Personal info / edit card */}
      <div style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-2xl)',
        overflow: 'hidden',
        boxShadow: 'var(--shadow-sm)',
      }}>
        <div style={{
          padding: '16px 20px', borderBottom: '1px solid var(--border)',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        }}>
          <div style={{ fontWeight: 700, fontSize: '0.9375rem', color: 'var(--text-primary)' }}>
            Thông tin cá nhân
          </div>
          {!editMode && (
            <button
              type="button"
              onClick={onStartEdit}
              style={{
                display: 'flex', alignItems: 'center', gap: '5px',
                fontSize: '0.8rem', color: 'var(--accent)', fontWeight: 600,
                background: 'none', border: 'none', cursor: 'pointer',
                fontFamily: 'var(--font-body)',
              }}
            >
              <Edit3 size={13} />
              Sửa
            </button>
          )}
        </div>
        <div style={{ padding: '20px' }}>
          {editMode ? (
            /* Edit form */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {[
                { id: 'edit-name', label: 'Họ và tên', key: 'name', icon: User, type: 'text' },
                { id: 'edit-email', label: 'Email liên hệ', key: 'email', icon: Mail, type: 'email' },
                { id: 'edit-phone', label: 'Số điện thoại', key: 'phone', icon: Phone, type: 'tel' },
                { id: 'edit-job', label: 'Nghề nghiệp', key: 'job', icon: Award, type: 'text' },
              ].map(field => {
                const Icon = field.icon;
                return (
                  <div key={field.key} className="input-group">
                    <label className="input-label" htmlFor={field.id}>{field.label}</label>
                    <div className="input-icon-wrap">
                      <span className="input-icon"><Icon size={14} /></span>
                      <input
                        id={field.id}
                        type={field.type}
                        className="input"
                        value={formValues[field.key]}
                        onChange={e => onChangeForm({ ...formValues, [field.key]: e.target.value })}
                      />
                    </div>
                  </div>
                );
              })}
              <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                <button
                  id="save-profile-btn"
                  type="button"
                  className="btn btn-accent btn-sm"
                  style={{ flex: 1 }}
                  onClick={onSaveEdit}
                >
                  Lưu thay đổi
                </button>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={onCancelEdit}
                >
                  Huỷ
                </button>
              </div>
            </div>
          ) : (
            /* View mode */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {[
                { icon: User, label: 'Họ và tên', value: user.name },
                { icon: Mail, label: 'Email liên hệ', value: user.email },
                { icon: Phone, label: 'Điện thoại', value: user.phone },
                { icon: Award, label: 'Nghề nghiệp', value: user.job },
                { icon: MapPin, label: 'Địa chỉ', value: user.address },
              ].map((item, i) => {
                const Icon = item.icon;
                return (
                  <div key={i} style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
                    <div style={{
                      width: 32, height: 32, borderRadius: 'var(--radius-md)',
                      background: 'rgba(30,107,107,0.07)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      color: 'var(--accent)', flexShrink: 0, marginTop: '1px',
                    }}>
                      <Icon size={14} strokeWidth={1.8} />
                    </div>
                    <div>
                      <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginBottom: '1px', fontWeight: 500 }}>
                        {item.label}
                      </div>
                      <div style={{ fontSize: '0.8375rem', color: 'var(--text-primary)', fontWeight: 500, lineHeight: 1.4 }}>
                        {item.value}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Quick actions */}
      <div style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-2xl)',
        overflow: 'hidden',
        boxShadow: 'var(--shadow-sm)',
      }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border)' }}>
          <div style={{ fontWeight: 700, fontSize: '0.9375rem', color: 'var(--text-primary)' }}>
            Thao tác nhanh
          </div>
        </div>
        <div style={{ padding: '8px' }}>
          {[
            { icon: Package, label: 'Đăng tin mới', to: '/dang-tin', accent: true },
            { icon: Lock, label: 'Đổi mật khẩu', to: '/cai-dat/mat-khau' },
            { icon: Bell, label: 'Cài đặt thông báo', to: '/cai-dat/thong-bao' },
            { icon: Settings, label: 'Cài đặt tài khoản', to: '/cai-dat' },
          ].map((item, i) => {
            const Icon = item.icon;
            return (
              <Link
                key={i}
                to={item.to}
                style={{
                  display: 'flex', alignItems: 'center', gap: '10px',
                  padding: '10px 12px',
                  borderRadius: 'var(--radius-md)',
                  color: item.accent ? 'var(--accent)' : 'var(--text-secondary)',
                  textDecoration: 'none', fontSize: '0.875rem', fontWeight: 500,
                  transition: 'all 150ms',
                  justifyContent: 'space-between',
                }}
                onMouseEnter={e => { e.currentTarget.style.background = 'var(--bg-canvas)'; e.currentTarget.style.color = 'var(--text-primary)'; }}
                onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = item.accent ? 'var(--accent)' : 'var(--text-secondary)'; }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <Icon size={15} strokeWidth={1.8} />
                  {item.label}
                </div>
                <ChevronRight size={13} style={{ color: 'var(--text-muted)' }} />
              </Link>
            );
          })}

          {/* Logout */}
          <Link
            to="/dang-nhap"
            id="sidebar-logout-btn"
            style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px',
              padding: '10px 12px',
              borderRadius: 'var(--radius-md)',
              color: '#e03e2d', textDecoration: 'none', fontSize: '0.875rem', fontWeight: 500,
              transition: 'all 150ms',
              borderTop: '1px solid var(--border)',
              paddingTop: '14px', marginTop: '8px',
            }}
            onMouseEnter={e => { e.currentTarget.style.background = 'rgba(224,62,45,0.07)'; }}
            onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <LogOut size={15} strokeWidth={1.8} />
              Đăng xuất
            </div>
            <ChevronRight size={13} style={{ color: 'rgba(224,62,45,0.5)' }} />
          </Link>
        </div>
      </div>
    </div>
  );
}
