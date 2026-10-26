import React from 'react';
import { Search } from 'lucide-react';

export default function UserFilterBar({ statusFilter, setStatusFilter, searchQuery, setSearchQuery, counts }) {
  return (
    <div style={{
      padding: '12px 24px', background: '#fafbfc', borderBottom: '1px solid rgba(0,0,0,0.06)',
      display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px'
    }}>
      {/* Status Tabs */}
      <div style={{ display: 'flex', gap: '6px' }}>
        {[
          { id: 'all', label: 'Tất cả', count: counts.total },
          { id: 'active', label: 'Đang hoạt động', count: counts.active },
          { id: 'banned', label: 'Đã tạm khóa', count: counts.banned },
          { id: 'warning', label: 'Cần chú ý', count: counts.warning },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setStatusFilter(tab.id)}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: '6px',
              padding: '6px 12px', borderRadius: '6px', fontSize: '0.8rem', fontWeight: 600,
              border: statusFilter === tab.id ? '1px solid var(--teal-600)' : '1px solid transparent',
              background: statusFilter === tab.id ? 'white' : 'transparent',
              color: statusFilter === tab.id ? 'var(--teal-700)' : 'var(--text-secondary)',
              cursor: 'pointer', transition: 'all 120ms'
            }}
          >
            <span>{tab.label}</span>
            <span style={{
              fontSize: '0.7rem', padding: '1px 6px', borderRadius: '10px',
              background: statusFilter === tab.id ? 'var(--teal-50)' : 'rgba(0,0,0,0.04)',
              color: statusFilter === tab.id ? 'var(--teal-700)' : 'var(--text-muted)'
            }}>
              {tab.count}
            </span>
          </button>
        ))}
      </div>

      {/* Search Input */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: '8px',
        background: 'white', padding: '6px 12px', borderRadius: '6px',
        border: '1px solid rgba(0,0,0,0.12)', width: '260px'
      }}>
        <Search size={14} style={{ color: 'var(--text-muted)' }} />
        <input
          type="text"
          placeholder="Tìm theo tên, email, ID..."
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
    </div>
  );
}
