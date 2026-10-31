// src/features/account/components/AccountFilterBar.jsx
import React from 'react';
import { Search, Filter } from 'lucide-react';

export default function AccountFilterBar({
  keyword,
  setKeyword,
  roleFilter,
  setRoleFilter,
  statusFilter,
  setStatusFilter,
  size,
  setSize,
}) {
  return (
    <div
      style={{
        padding: '16px 24px',
        background: '#fafbfc',
        borderBottom: '1px solid rgba(0,0,0,0.06)',
        display: 'flex',
        flexWrap: 'wrap',
        gap: '12px',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center', flex: 1 }}>
        {/* Search by Email or Name */}
        <div style={{ position: 'relative', minWidth: '280px', flex: '1 1 280px', maxWidth: '380px' }}>
          <Search
            size={15}
            style={{
              position: 'absolute',
              left: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--text-muted)',
            }}
          />
          <input
            type="text"
            placeholder="Tìm theo Tên hoặc Email / Gmail..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{
              width: '100%',
              padding: '8px 12px 8px 34px',
              borderRadius: '8px',
              border: '1px solid rgba(0,0,0,0.12)',
              fontSize: '0.85rem',
              background: 'white',
              outline: 'none',
            }}
          />
        </div>

        {/* Role Filter */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <Filter size={14} style={{ color: 'var(--text-muted)' }} />
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            style={{
              padding: '8px 12px',
              borderRadius: '8px',
              border: '1px solid rgba(0,0,0,0.12)',
              fontSize: '0.85rem',
              background: 'white',
              cursor: 'pointer',
            }}
          >
            <option value="ALL">Tất cả Role</option>
            <option value="ADMIN">ADMIN</option>
            <option value="MANAGER">MANAGER</option>
            <option value="CHECKER">CHECKER</option>
            <option value="APPROVER">APPROVER</option>
            <option value="USER">USER</option>
          </select>
        </div>

        {/* Status Filter */}
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{
            padding: '8px 12px',
            borderRadius: '8px',
            border: '1px solid rgba(0,0,0,0.12)',
            fontSize: '0.85rem',
            background: 'white',
            cursor: 'pointer',
          }}
        >
          <option value="ALL">Tất cả Trạng thái</option>
          <option value="ACTIVE">Đang hoạt động (ACTIVE)</option>
          <option value="SUSPENDED">Đang tạm khóa (SUSPENDED)</option>
          <option value="PENDING_ACTIVATION">Chờ kích hoạt (PENDING_ACTIVATION)</option>
          <option value="DELETED">Đã xóa mềm (DELETED)</option>
        </select>
      </div>

      {/* Page Size Selector */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
        <span>Hiển thị:</span>
        <select
          value={size}
          onChange={(e) => setSize(Number(e.target.value))}
          style={{
            padding: '6px 10px',
            borderRadius: '8px',
            border: '1px solid rgba(0,0,0,0.12)',
            fontSize: '0.82rem',
            background: 'white',
            cursor: 'pointer',
          }}
        >
          <option value={5}>5 dòng / trang</option>
          <option value={10}>10 dòng / trang</option>
          <option value={20}>20 dòng / trang</option>
          <option value={50}>50 dòng / trang</option>
        </select>
      </div>
    </div>
  );
}
