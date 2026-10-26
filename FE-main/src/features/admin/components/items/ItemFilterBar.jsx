import React from 'react';
import { RotateCcw, Search } from 'lucide-react';

export default function ItemFilterBar({
  filterType, setFilterType,
  searchQuery, setSearchQuery,
  filterLocation, setFilterLocation,
  filterStatus, setFilterStatus,
  counts, hasActiveFilters, resetFilters
}) {
  return (
    <div style={{
      padding: '14px 24px', background: '#fafbfc', borderBottom: '1px solid rgba(0,0,0,0.06)',
      display: 'flex', flexDirection: 'column', gap: '12px'
    }}>
      {/* Row 1: Fast Type Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '10px' }}>
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          {[
            { id: 'all', label: 'Tất cả tin', count: counts.total },
            { id: 'lost', label: 'Cần tìm đồ', count: counts.lost },
            { id: 'found', label: 'Nhặt được', count: counts.found },
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setFilterType(tab.id)}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: '6px',
                padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 600,
                border: filterType === tab.id ? '1px solid var(--teal-600)' : '1px solid transparent',
                background: filterType === tab.id ? 'white' : 'transparent',
                color: filterType === tab.id ? 'var(--teal-700)' : 'var(--text-secondary)',
                cursor: 'pointer', transition: 'all 120ms'
              }}
            >
              <span>{tab.label}</span>
              <span style={{
                fontSize: '0.7rem', padding: '1px 6px', borderRadius: '10px',
                background: filterType === tab.id ? 'var(--teal-50)' : 'rgba(0,0,0,0.04)',
                color: filterType === tab.id ? 'var(--teal-700)' : 'var(--text-muted)'
              }}>
                {tab.count}
              </span>
            </button>
          ))}
        </div>

        {hasActiveFilters && (
          <button
            onClick={resetFilters}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: '4px',
              background: 'none', border: 'none', color: 'var(--teal-700)',
              fontSize: '0.775rem', fontWeight: 600, cursor: 'pointer'
            }}
          >
            <RotateCcw size={13} />
            <span>Đặt lại lọc</span>
          </button>
        )}
      </div>

      {/* Row 2: Search + Select Filters */}
      <div style={{ display: 'flex', gap: '10px', alignItems: 'center', flexWrap: 'wrap' }}>
        {/* Search Input */}
        <div style={{
          flex: '1 1 280px', display: 'flex', alignItems: 'center', gap: '8px',
          background: 'white', padding: '7px 12px', borderRadius: '6px',
          border: '1px solid rgba(0,0,0,0.12)'
        }}>
          <Search size={14} style={{ color: 'var(--text-muted)' }} />
          <input
            type="text"
            placeholder="Tìm theo tên đồ, mã ID, người đăng, SĐT..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            style={{ border: 'none', background: 'transparent', outline: 'none', width: '100%', fontSize: '0.825rem', fontFamily: 'var(--font-body)' }}
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', fontSize: '0.75rem', cursor: 'pointer', padding: '0 2px' }}
            >
              ✕
            </button>
          )}
        </div>

        {/* Location Select */}
        <select
          value={filterLocation}
          onChange={e => setFilterLocation(e.target.value)}
          style={{
            padding: '7px 12px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.12)',
            fontSize: '0.8rem', background: 'white', color: 'var(--text-secondary)',
            outline: 'none', cursor: 'pointer', minWidth: '140px'
          }}
        >
          <option value="all">Mọi địa điểm</option>
          <option value="Hà Nội">Hà Nội</option>
          <option value="TP.HCM">TP. Hồ Chí Minh</option>
        </select>

        {/* Status Select */}
        <select
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}
          style={{
            padding: '7px 12px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.12)',
            fontSize: '0.8rem', background: 'white', color: 'var(--text-secondary)',
            outline: 'none', cursor: 'pointer', minWidth: '140px'
          }}
        >
          <option value="all">Mọi trạng thái</option>
          <option value="active">Đang hiển thị</option>
          <option value="matched">Đã khớp AI</option>
          <option value="resolved">Đã trao trả</option>
          <option value="hidden">Đã bị ẩn ({counts.hidden})</option>
        </select>
      </div>
    </div>
  );
}
