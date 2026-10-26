// src/features/account/components/AccountCrudModal.jsx
import React from 'react';
import { useAuth } from '@/context/AuthContext';

export default function AccountCrudModal({
  crudModal,
  setCrudModal,
  formData,
  setFormData,
  submitCrud,
  submitting,
}) {
  const { isPureAdmin } = useAuth();

  if (!crudModal.isOpen) return null;

  const isCreate = crudModal.mode === 'create';

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1050,
        background: 'rgba(0,0,0,0.45)',
        backdropFilter: 'blur(3px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
      }}
    >
      <div
        style={{
          background: 'white',
          borderRadius: '12px',
          width: '100%',
          maxWidth: '500px',
          padding: '24px',
          boxShadow: '0 20px 40px rgba(0,0,0,0.15)',
          border: '1px solid rgba(0,0,0,0.08)',
        }}
      >
        <h3
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: '1.15rem',
            fontWeight: 700,
            margin: '0 0 16px',
            color: 'var(--text-primary)',
          }}
        >
          {isCreate ? 'Thêm tài khoản mới (Lưu vào PostgreSQL)' : `Cập nhật tài khoản #${crudModal.data?.id}`}
        </h3>

        <form onSubmit={submitCrud} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {/* Tên người dùng */}
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '0.8rem',
                fontWeight: 600,
                marginBottom: '6px',
                color: 'var(--text-secondary)',
              }}
            >
              Tên người dùng <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input
              type="text"
              required
              minLength={2}
              placeholder="VD: Nguyễn Văn A"
              value={formData.fullName}
              onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border)',
                outline: 'none',
                fontSize: '0.85rem',
              }}
            />
          </div>

          {/* Email / Gmail */}
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '0.8rem',
                fontWeight: 600,
                marginBottom: '6px',
                color: 'var(--text-secondary)',
              }}
            >
              Mail (Email / Gmail) <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input
              type="email"
              required
              placeholder="VD: user01@gmail.com"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border)',
                outline: 'none',
                fontSize: '0.85rem',
              }}
            />
          </div>

          {/* Mật khẩu */}
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '0.8rem',
                fontWeight: 600,
                marginBottom: '6px',
                color: 'var(--text-secondary)',
              }}
            >
              {isCreate ? 'Mật khẩu (Để trống hệ thống tự sinh ngẫu nhiên)' : 'Mật khẩu mới (Để trống nếu giữ nguyên)'}
            </label>
            <input
              type="text"
              placeholder={isCreate ? 'VD: 123456' : 'Nhập mật khẩu mới nếu muốn đổi'}
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              style={{
                width: '100%',
                padding: '9px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border)',
                outline: 'none',
                fontSize: '0.85rem',
              }}
            />
          </div>

          {/* Role & Status */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label
                style={{
                  display: 'block',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  marginBottom: '6px',
                  color: 'var(--text-secondary)',
                }}
              >
                Role (Vai trò)
              </label>
              <select
                value={formData.role}
                onChange={(e) => setFormData({ ...formData, role: e.target.value })}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: '6px',
                  border: '1px solid var(--border)',
                  outline: 'none',
                  background: 'white',
                  fontSize: '0.85rem',
                }}
              >
                <option value="USER">USER (Người dùng)</option>
                <option value="CHECKER">CHECKER (Kiểm duyệt)</option>
                <option value="APPROVER">APPROVER (Phê duyệt)</option>
                {isPureAdmin && <option value="MANAGER">MANAGER (Quản lý)</option>}
                {isPureAdmin && <option value="ADMIN">ADMIN (Quản trị viên)</option>}
              </select>
            </div>

            <div>
              <label
                style={{
                  display: 'block',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  marginBottom: '6px',
                  color: 'var(--text-secondary)',
                }}
              >
                Trạng thái
              </label>
              <select
                value={formData.status}
                onChange={(e) => setFormData({ ...formData, status: e.target.value })}
                style={{
                  width: '100%',
                  padding: '9px 12px',
                  borderRadius: '6px',
                  border: '1px solid var(--border)',
                  outline: 'none',
                  background: 'white',
                  fontSize: '0.85rem',
                }}
              >
                <option value="ACTIVE">ACTIVE (Hoạt động)</option>
                <option value="SUSPENDED">SUSPENDED (Tạm khóa)</option>
                <option value="PENDING_ACTIVATION">PENDING_ACTIVATION</option>
                {!isCreate && <option value="DELETED">DELETED (Đã xóa mềm)</option>}
              </select>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '8px' }}>
            <button
              type="button"
              onClick={() => setCrudModal({ isOpen: false, mode: 'create', data: null })}
              style={{
                padding: '8px 14px',
                borderRadius: '6px',
                background: '#f1f5f9',
                border: '1px solid rgba(0,0,0,0.06)',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={submitting}
              style={{
                padding: '8px 16px',
                borderRadius: '6px',
                background: 'var(--teal-600)',
                color: 'white',
                border: 'none',
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: 'pointer',
              }}
            >
              {submitting ? 'Đang lưu...' : isCreate ? 'Tạo tài khoản' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
