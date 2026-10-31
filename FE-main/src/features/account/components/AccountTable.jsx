// src/features/account/components/AccountTable.jsx
import React from 'react';
import { Shield, User, Edit2, Ban, UserCheck, Trash2, Mail } from 'lucide-react';

const ROLE_STYLES = {
  ADMIN: { bg: '#f3e8ff', color: '#7e22ce', label: 'ADMIN' },
  MANAGER: { bg: '#e0f2fe', color: '#0369a1', label: 'MANAGER' },
  CHECKER: { bg: '#fef3c7', color: '#b45309', label: 'CHECKER' },
  APPROVER: { bg: '#fce7f3', color: '#be185d', label: 'APPROVER' },
  USER: { bg: '#f1f5f9', color: '#334155', label: 'USER' },
};

const STATUS_STYLES = {
  ACTIVE: { bg: '#ecfdf5', color: '#059669', dot: '#10b981', label: 'Hoạt động (ACTIVE)' },
  SUSPENDED: { bg: '#fff7ed', color: '#ea580c', dot: '#f97316', label: 'Tạm khóa (SUSPENDED)' },
  PENDING_ACTIVATION: { bg: '#eff6ff', color: '#2563eb', dot: '#3b82f6', label: 'Chờ kích hoạt' },
  DELETED: { bg: '#fef2f2', color: '#dc2626', dot: '#ef4444', label: 'Đã xóa mềm (DELETED)' },
};

export default function AccountTable({
  accounts,
  loading,
  openCrudModal,
  onToggleLock,
  onSoftDelete,
}) {
  if (loading) {
    return (
      <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
        Đang tải danh sách tài khoản từ cơ sở dữ liệu...
      </div>
    );
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
        <thead>
          <tr style={{ background: '#f8fafc', borderBottom: '1px solid rgba(0,0,0,0.06)', color: 'var(--text-secondary)' }}>
            <th style={{ padding: '12px 20px', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', width: '80px' }}>
              ID
            </th>
            <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Tên người dùng
            </th>
            <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Role (Vai trò)
            </th>
            <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Mail (Email / Gmail)
            </th>
            <th style={{ padding: '12px 18px', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Trạng thái
            </th>
            <th style={{ padding: '12px 24px', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', textAlign: 'right' }}>
              Thao tác CRUD
            </th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((acc) => {
            const roleStyle = ROLE_STYLES[acc.role] || ROLE_STYLES.USER;
            const statusStyle = STATUS_STYLES[acc.status] || STATUS_STYLES.ACTIVE;
            const displayName = String(acc.fullName || acc.email).replace(/\s*\((Manager|Admin)\)/gi, '');

            return (
              <tr
                key={acc.id}
                style={{
                  borderBottom: '1px solid rgba(0,0,0,0.05)',
                  transition: 'background 120ms',
                  opacity: acc.status === 'DELETED' ? 0.65 : 1,
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#f8fafc';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'white';
                }}
              >
                {/* 1. ID tự tăng */}
                <td style={{ padding: '14px 20px', fontWeight: 700, fontFamily: 'var(--font-mono)', color: 'var(--teal-700)' }}>
                  #{acc.id}
                </td>

                {/* 2. Tên */}
                <td style={{ padding: '14px 18px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <div
                      style={{
                        width: '34px',
                        height: '34px',
                        borderRadius: '8px',
                        background: roleStyle.bg,
                        color: roleStyle.color,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 700,
                        fontSize: '0.85rem',
                        flexShrink: 0,
                      }}
                    >
                      {String(displayName).charAt(0).toUpperCase()}
                    </div>
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{displayName}</div>
                  </div>
                </td>

                {/* 3. Role */}
                <td style={{ padding: '14px 18px' }}>
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '5px',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      padding: '4px 10px',
                      borderRadius: '6px',
                      background: roleStyle.bg,
                      color: roleStyle.color,
                    }}
                  >
                    {acc.role === 'ADMIN' || acc.role === 'MANAGER' ? <Shield size={12} /> : <User size={12} />}
                    {roleStyle.label}
                  </span>
                </td>

                {/* 4. Mail */}
                <td style={{ padding: '14px 18px' }}>
                  <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', color: 'var(--text-secondary)', fontWeight: 500 }}>
                    <Mail size={13} style={{ color: 'var(--text-muted)' }} />
                    <span>{acc.email}</span>
                  </div>
                </td>

                {/* 5. Trạng thái */}
                <td style={{ padding: '14px 18px' }}>
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      fontSize: '0.75rem',
                      fontWeight: 600,
                      padding: '4px 10px',
                      borderRadius: '6px',
                      background: statusStyle.bg,
                      color: statusStyle.color,
                    }}
                  >
                    <span
                      style={{
                        width: 6,
                        height: 6,
                        borderRadius: '50%',
                        background: statusStyle.dot,
                      }}
                    />
                    {statusStyle.label}
                  </span>
                </td>

                {/* 6. Thao tác CRUD */}
                <td style={{ padding: '14px 24px', textAlign: 'right' }}>
                  <div style={{ display: 'inline-flex', gap: '6px' }}>
                    <button
                      type="button"
                      onClick={() => openCrudModal('edit', acc)}
                      style={{
                        padding: '6px 10px',
                        borderRadius: '6px',
                        border: '1px solid rgba(0,0,0,0.12)',
                        background: 'white',
                        color: 'var(--text-secondary)',
                        cursor: 'pointer',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '4px',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                      }}
                      title="Sửa tài khoản"
                    >
                      <Edit2 size={13} /> Sửa
                    </button>

                    {acc.status !== 'DELETED' && (
                      <button
                        type="button"
                        onClick={() => onToggleLock(acc)}
                        style={{
                          padding: '6px 10px',
                          borderRadius: '6px',
                          border: acc.status === 'SUSPENDED' ? '1px solid #a7f3d0' : '1px solid #fed7aa',
                          background: acc.status === 'SUSPENDED' ? '#ecfdf5' : '#fff7ed',
                          color: acc.status === 'SUSPENDED' ? '#059669' : '#ea580c',
                          cursor: 'pointer',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontSize: '0.75rem',
                          fontWeight: 600,
                        }}
                        title={acc.status === 'SUSPENDED' ? 'Mở khóa tài khoản' : 'Tạm khóa tài khoản'}
                      >
                        {acc.status === 'SUSPENDED' ? <UserCheck size={13} /> : <Ban size={13} />}
                        {acc.status === 'SUSPENDED' ? 'Mở khóa' : 'Khóa'}
                      </button>
                    )}

                    {acc.status !== 'DELETED' && (
                      <button
                        type="button"
                        onClick={() => onSoftDelete(acc)}
                        style={{
                          padding: '6px 10px',
                          borderRadius: '6px',
                          border: '1px solid #fecaca',
                          background: '#fef2f2',
                          color: '#dc2626',
                          cursor: 'pointer',
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '4px',
                          fontSize: '0.75rem',
                          fontWeight: 600,
                        }}
                        title="Xóa mềm tài khoản"
                      >
                        <Trash2 size={13} /> Xóa mềm
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}

          {accounts.length === 0 && (
            <tr>
              <td colSpan={6} style={{ textAlign: 'center', padding: '48px', color: 'var(--text-muted)' }}>
                Không tìm thấy tài khoản nào phù hợp với bộ lọc hiện tại.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
