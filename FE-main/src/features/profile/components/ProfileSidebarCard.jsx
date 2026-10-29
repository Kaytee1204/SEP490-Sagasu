import React from 'react';
import { Link } from 'react-router-dom';
import {
  User,
  Mail,
  Phone,
  Award,
  Edit3,
  Package,
  Lock,
  LogOut,
  ChevronRight,
  AlertCircle,
  CheckCircle,
  ShieldCheck,
} from 'lucide-react';

export default function ProfileSidebarCard({
  user,
  isPureAdmin,
  editMode,
  onStartEdit,
  formValues,
  onChangeForm,
  onSaveEdit,
  onCancelEdit,
  saving,
  feedback,
}) {
  const disabledInputStyle = {
    background: '#f1f5f9',
    color: '#64748b',
    cursor: 'not-allowed',
    pointerEvents: 'none',
    userSelect: 'none',
  };

  const infoItems = [
    { icon: User, label: 'Họ và tên', value: user.name },
    { icon: Mail, label: 'Email liên hệ', value: user.email },
    { icon: Phone, label: 'Số điện thoại', value: user.phone },
    { icon: Award, label: 'Vai trò (Role)', value: user.role },
    {
      icon: ShieldCheck,
      label: 'Trạng thái tài khoản',
      value: user.status === 'ACTIVE' ? 'Đang hoạt động' : user.status,
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      {/* Personal info / edit card */}
      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid #e2e8f0',
          borderRadius: '10px',
          overflow: 'hidden',
          boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
        }}
      >
        <div
          style={{
            padding: '14px 18px',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
            Thông tin cá nhân
          </div>
          {!editMode && (
            <button
              type="button"
              onClick={onStartEdit}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '5px',
                fontSize: '0.8rem',
                color: 'var(--accent)',
                fontWeight: 600,
                background: 'none',
                border: 'none',
                cursor: 'pointer',
                fontFamily: 'var(--font-body)',
              }}
            >
              <Edit3 size={13} />
              Chỉnh sửa
            </button>
          )}
        </div>

        <div style={{ padding: '18px' }}>
          {feedback?.message && (
            <div
              style={{
                marginBottom: '14px',
                padding: '10px 12px',
                borderRadius: '8px',
                fontSize: '0.8rem',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                background:
                  feedback.type === 'error' ? 'rgba(239, 68, 68, 0.08)' : 'rgba(16, 185, 129, 0.08)',
                color: feedback.type === 'error' ? '#dc2626' : '#059669',
                border: `1px solid ${
                  feedback.type === 'error' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(16, 185, 129, 0.2)'
                }`,
              }}
            >
              {feedback.type === 'error' ? <AlertCircle size={15} /> : <CheckCircle size={15} />}
              <span>{feedback.message}</span>
            </div>
          )}

          {editMode ? (
            /* Edit form */
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {/* Email liên hệ - hiển thị nhưng không click chọn được */}
              <div className="input-group">
                <label className="input-label" htmlFor="edit-email">
                  Email liên hệ
                </label>
                <div className="input-icon-wrap" style={{ cursor: 'not-allowed' }}>
                  <span className="input-icon">
                    <Mail size={14} />
                  </span>
                  <input
                    id="edit-email"
                    type="email"
                    className="input"
                    value={formValues.email}
                    disabled
                    readOnly
                    tabIndex={-1}
                    style={disabledInputStyle}
                  />
                </div>
              </div>

              {/* Vai trò (Role) - hiển thị nhưng không click chọn/thay đổi được nếu không phải ADMIN */}
              <div className="input-group">
                <label className="input-label" htmlFor="edit-role">
                  Vai trò (Role)
                </label>
                <div
                  className="input-icon-wrap"
                  style={{ cursor: isPureAdmin ? 'pointer' : 'not-allowed' }}
                >
                  <span className="input-icon">
                    <Award size={14} />
                  </span>
                  {isPureAdmin ? (
                    <select
                      id="edit-role"
                      className="input"
                      value={formValues.role}
                      onChange={(e) => onChangeForm({ ...formValues, role: e.target.value })}
                      style={{ paddingLeft: '36px', cursor: 'pointer', background: 'white' }}
                    >
                      <option value="ADMIN">ADMIN</option>
                      <option value="MANAGER">MANAGER</option>
                      <option value="USER">USER</option>
                      <option value="CHECKER">CHECKER</option>
                      <option value="APPROVER">APPROVER</option>
                    </select>
                  ) : (
                    <input
                      id="edit-role"
                      type="text"
                      className="input"
                      value={formValues.role}
                      disabled
                      readOnly
                      tabIndex={-1}
                      style={disabledInputStyle}
                    />
                  )}
                </div>
              </div>

              {/* Số điện thoại */}
              <div className="input-group">
                <label className="input-label" htmlFor="edit-phone">
                  Số điện thoại
                </label>
                <div className="input-icon-wrap">
                  <span className="input-icon">
                    <Phone size={14} />
                  </span>
                  <input
                    id="edit-phone"
                    type="tel"
                    className="input"
                    placeholder="Nhập số điện thoại"
                    value={formValues.phone}
                    onChange={(e) => onChangeForm({ ...formValues, phone: e.target.value })}
                  />
                </div>
              </div>

              {/* Đổi mật khẩu */}
              <div className="input-group">
                <label className="input-label" htmlFor="edit-new-password">
                  Mật khẩu mới{' '}
                  <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', fontWeight: 400 }}>
                    (Để trống nếu giữ nguyên)
                  </span>
                </label>
                <div className="input-icon-wrap">
                  <span className="input-icon">
                    <Lock size={14} />
                  </span>
                  <input
                    id="edit-new-password"
                    type="password"
                    className="input"
                    placeholder="Nhập mật khẩu mới"
                    value={formValues.newPassword || ''}
                    onChange={(e) => onChangeForm({ ...formValues, newPassword: e.target.value })}
                  />
                </div>
              </div>

              {formValues.newPassword && (
                <div className="input-group">
                  <label className="input-label" htmlFor="edit-confirm-password">
                    Xác nhận mật khẩu mới
                  </label>
                  <div className="input-icon-wrap">
                    <span className="input-icon">
                      <Lock size={14} />
                    </span>
                    <input
                      id="edit-confirm-password"
                      type="password"
                      className="input"
                      placeholder="Nhập lại mật khẩu mới"
                      value={formValues.confirmPassword || ''}
                      onChange={(e) =>
                        onChangeForm({ ...formValues, confirmPassword: e.target.value })
                      }
                    />
                  </div>
                </div>
              )}

              <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                <button
                  id="save-profile-btn"
                  type="button"
                  className="btn btn-accent btn-sm"
                  style={{ flex: 1, borderRadius: '8px' }}
                  disabled={saving}
                  onClick={onSaveEdit}
                >
                  {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  style={{ borderRadius: '8px' }}
                  disabled={saving}
                  onClick={onCancelEdit}
                >
                  Huỷ
                </button>
              </div>
            </div>
          ) : (
            /* View mode */
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              {infoItems.map((item, i) => {
                const Icon = item.icon;
                return (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      gap: '12px',
                      alignItems: 'center',
                      padding: i === 0 ? '0 0 12px 0' : i === infoItems.length - 1 ? '12px 0 0 0' : '12px 0',
                      borderBottom: i < infoItems.length - 1 ? '1px solid #f1f5f9' : 'none',
                    }}
                  >
                    <div
                      style={{
                        width: 32,
                        height: 32,
                        borderRadius: '8px',
                        background: 'rgba(30, 107, 107, 0.07)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: 'var(--accent)',
                        flexShrink: 0,
                      }}
                    >
                      <Icon size={14} strokeWidth={1.8} />
                    </div>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <div
                        style={{
                          fontSize: '0.72rem',
                          color: 'var(--text-muted)',
                          marginBottom: '2px',
                          fontWeight: 500,
                        }}
                      >
                        {item.label}
                      </div>
                      <div
                        style={{
                          fontSize: '0.84rem',
                          color: 'var(--text-primary)',
                          fontWeight: 600,
                          lineHeight: 1.35,
                          wordBreak: 'break-word',
                        }}
                      >
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
      <div
        style={{
          background: 'var(--bg-surface)',
          border: '1px solid #e2e8f0',
          borderRadius: '10px',
          overflow: 'hidden',
          boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
        }}
      >
        <div
          style={{
            padding: '14px 18px',
            borderBottom: '1px solid #e2e8f0',
            background: '#f8fafc',
          }}
        >
          <div style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
            Thao tác nhanh
          </div>
        </div>
        <div style={{ padding: '8px' }}>
          <Link
            to="/dang-tin"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '10px',
              padding: '10px 12px',
              borderRadius: '8px',
              color: 'var(--accent)',
              textDecoration: 'none',
              fontSize: '0.85rem',
              fontWeight: 600,
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = '#f8fafc';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Package size={15} strokeWidth={1.8} />
              Đăng tin mới
            </div>
            <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />
          </Link>

          <button
            type="button"
            onClick={onStartEdit}
            style={{
              width: '100%',
              background: 'transparent',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '10px',
              padding: '10px 12px',
              borderRadius: '8px',
              color: 'var(--text-secondary)',
              fontSize: '0.85rem',
              fontWeight: 500,
              fontFamily: 'var(--font-body)',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = '#f8fafc';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <Lock size={15} strokeWidth={1.8} />
              Đổi mật khẩu / Số điện thoại
            </div>
            <ChevronRight size={14} style={{ color: 'var(--text-muted)' }} />
          </button>

          {/* Logout */}
          <Link
            to="/dang-nhap"
            id="sidebar-logout-btn"
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: '10px',
              padding: '10px 12px',
              borderRadius: '8px',
              color: '#dc2626',
              textDecoration: 'none',
              fontSize: '0.85rem',
              fontWeight: 500,
              borderTop: '1px solid #f1f5f9',
              paddingTop: '12px',
              marginTop: '6px',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = 'rgba(220, 38, 38, 0.06)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <LogOut size={15} strokeWidth={1.8} />
              Đăng xuất
            </div>
            <ChevronRight size={14} style={{ color: 'rgba(220, 38, 38, 0.45)' }} />
          </Link>
        </div>
      </div>
    </div>
  );
}
