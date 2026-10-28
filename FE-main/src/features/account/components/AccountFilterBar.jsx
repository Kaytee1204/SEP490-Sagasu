// src/features/account/components/AccountFilterBar.jsx
import React from 'react';
import { Search } from 'lucide-react';

export default function AccountFilterBar({
  keyword,
  setKeyword,
  roleFilter,
  setRoleFilter,
  statusFilter,
  setStatusFilter,
  includeDeleted,
  setIncludeDeleted,
  size,
  setSize,
}) {
  return (
    <div
      style={{
        padding: '14px 24px',
        background: '#fafbfc',
        borderBottom: '1px solid rgba(0,0,0,0.06)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '12px',
      }}
    >
      {/* Left: Role & Status Filters */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value)}
          style={{
            padding: '7px 12px',
            borderRadius: '8px',
            border: '1px solid rgba(0,0,0,0.12)',
            background: 'white',
            fontSize: '0.825rem',
            fontWeight: 600,
            color: 'var(--text-primary)',
            outline: 'none',
          }}
        >
          <option value="ALL">Tất cả Role</option>
          <option value="ADMIN">ADMIN</option>
          <option value="MANAGER">MANAGER</option>
          <option value="USER">USER</option>
          <option value="EXTERNAL_USER">EXTERNAL_USER</option>
          <option value="CHECKER">CHECKER</option>
          <option value="APPROVER">APPROVER</option>
        </select>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            padding: '7px 12px',
            borderRadius: '8px',
            border: '1px solid rgba(0,0,0,0.12)',
            background: 'white',
            fontSize: '0.825rem',
            fontWeight: 600,
            color: 'var(--text-primary)',
            outline: 'none',
          }}
        >
          <option value="ALL">Tất cả Trạng thái</option>
          <option value="ACTIVE">Đang hoạt động (ACTIVE)</option>
          <option value="SUSPENDED">Đã tạm khóa (SUSPENDED)</option>
          <option value="PENDING_ACTIVATION">Chờ kích hoạt (PENDING)</option>
          <option value="DELETED">Đã xóa mềm (DELETED)</option>
        </select>

        <label
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '0.8rem',
            color: 'var(--text-secondary)',
            cursor: 'pointer',
            userSelect: 'none',
          }}
        >
          <input
            type="checkbox"
            checked={includeDeleted}
            onChange={(e) => setIncludeDeleted(e.target.checked)}
          />
          Hiện cả tài khoản đã xóa mềm
        </label>
      </div>

      {/* Right: Search & Page Size */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            background: 'white',
            padding: '7px 12px',
            borderRadius: '8px',
            border: '1px solid rgba(0,0,0,0.12)',
            width: '280px',
          }}
        >
          <Search size={14} style={{ color: 'var(--text-muted)' }} />
          <input
            type="text"
            placeholder="Tìm theo ID, Tên, Email..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{
              border: 'none',
              background: 'transparent',
              outline: 'none',
              width: '100%',
              fontSize: '0.825rem',
            }}
          />
          {keyword && (
            <button
              type="button"
              onClick={() => setKeyword('')}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--text-muted)',
                fontSize: '0.75rem',
                cursor: 'pointer',
                padding: '0 2px',
              }}
            >
              ✕
            </button>
          )}
        </div>

        <select
          value={size}
          onChange={(e) => setSize(Number(e.target.value))}
          style={{
            padding: '7px 10px',
            borderRadius: '8px',
            border: '1px solid rgba(0,0,0,0.12)',
            background: 'white',
            fontSize: '0.8rem',
            color: 'var(--text-secondary)',
          }}
          title="Số dòng trên mỗi trang"
        >
          <option value={5}>5 / trang</option>
          <option value={10}>10 / trang</option>
          <option value={20}>20 / trang</option>
          <option value={50}>50 / trang</option>
        </select>
      </div>
    </div>
  );
}
