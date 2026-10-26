import React from 'react';
import { Search, Box } from 'lucide-react';

export default function PostTypeSelector({ postType, onSelectType }) {
  return (
    <div style={{
      display: 'flex', background: 'var(--bg-surface)', padding: '6px',
      borderRadius: 'var(--radius-xl)', border: '1px solid var(--border)',
      marginBottom: '40px', boxShadow: 'var(--shadow-sm)'
    }}>
      <button
        type="button"
        onClick={() => onSelectType('lost')}
        style={{
          flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
          padding: '12px 24px', borderRadius: 'var(--radius-lg)',
          background: postType === 'lost' ? 'var(--status-lost-bg)' : 'transparent',
          color: postType === 'lost' ? 'var(--status-lost)' : 'var(--text-secondary)',
          fontWeight: 600, fontSize: '0.9375rem', border: 'none', cursor: 'pointer',
          transition: 'all 200ms', fontFamily: 'var(--font-body)'
        }}
      >
        <Search size={18} />
        Cần tìm đồ mất
      </button>
      <button
        type="button"
        onClick={() => onSelectType('found')}
        style={{
          flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
          padding: '12px 24px', borderRadius: 'var(--radius-lg)',
          background: postType === 'found' ? 'var(--status-found-bg)' : 'transparent',
          color: postType === 'found' ? 'var(--status-found)' : 'var(--text-secondary)',
          fontWeight: 600, fontSize: '0.9375rem', border: 'none', cursor: 'pointer',
          transition: 'all 200ms', fontFamily: 'var(--font-body)'
        }}
      >
        <Box size={18} />
        Đã nhặt được đồ
      </button>
    </div>
  );
}
