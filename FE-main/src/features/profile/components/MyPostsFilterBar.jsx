import React from 'react';
import { Search } from 'lucide-react';

export default function MyPostsFilterBar({ query, onChangeQuery, filter, onSelectFilter, filters }) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      gap: '16px', flexWrap: 'wrap', marginBottom: '24px',
    }}>
      {/* Filter chips */}
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
        {filters.map(f => (
          <button
            key={f.key}
            type="button"
            className={`chip ${filter === f.key ? 'active' : ''}`}
            onClick={() => onSelectFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Search box */}
      <div style={{ position: 'relative', minWidth: '220px' }}>
        <Search size={14} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
        <input
          type="text"
          className="input"
          placeholder="Tìm bài đăng..."
          value={query}
          onChange={e => onChangeQuery(e.target.value)}
          style={{ paddingLeft: '34px', height: '36px', fontSize: '0.8125rem' }}
        />
      </div>
    </div>
  );
}
