import React from 'react';
import { Search } from 'lucide-react';

export default function SearchHeaderBar({ query, onChangeQuery, onClearQuery, selectedCategory, onChangeCategory, categories, onSearch }) {
  return (
    <div style={{
      background: 'rgba(255, 255, 255, 0.92)',
      backdropFilter: 'blur(12px)',
      WebkitBackdropFilter: 'blur(12px)',
      borderBottom: '1px solid var(--border)',
      padding: '16px 0',
      position: 'sticky',
      top: '68px',
      zIndex: 40,
      boxShadow: '0 2px 8px rgba(0, 0, 0, 0.03)'
    }}>
      <div className="container">
        <form
          onSubmit={onSearch}
          className="search-bar"
          style={{
            maxWidth: '820px',
            margin: '0 auto',
            background: 'white',
            border: '1.5px solid var(--border-strong)',
            boxShadow: 'var(--shadow-sm)'
          }}
        >
          {/* Search Icon */}
          <Search size={18} style={{ color: 'var(--teal-600)', marginLeft: '18px', flexShrink: 0 }} />

          {/* Search Input */}
          <input
            type="text"
            className="search-input"
            value={query}
            onChange={(e) => onChangeQuery(e.target.value)}
            placeholder="Nhập từ khoá đồ thất lạc (vd: Ví Pedro, iPhone, CCCD...)"
            style={{ fontSize: '0.95rem' }}
          />

          {query && (
            <button
              type="button"
              onClick={onClearQuery}
              style={{ background: 'none', border: 'none', color: 'var(--text-muted)', padding: '0 8px', cursor: 'pointer', fontSize: '0.85rem' }}
              title="Xóa từ khóa"
            >
              ✕
            </button>
          )}

          <div className="search-divider" />

          {/* Category Select */}
          <select
            className="search-category"
            value={selectedCategory}
            onChange={(e) => onChangeCategory(e.target.value)}
            style={{
              cursor: 'pointer',
              border: 'none',
              outline: 'none',
              background: 'transparent',
              fontSize: '0.875rem',
              color: 'var(--text-secondary)',
              fontWeight: 500,
              padding: '0 12px'
            }}
          >
            {categories.map(c => (
              <option key={c.value} value={c.value}>{c.label}</option>
            ))}
          </select>

          {/* Submit Button */}
          <button
            type="submit"
            className="search-btn"
            style={{
              background: 'var(--teal-600)',
              color: 'white',
              border: 'none',
              padding: '10px 22px',
              fontSize: '0.9rem',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              borderRadius: 'var(--radius-lg)',
              transition: 'background 150ms ease'
            }}
            onMouseEnter={e => e.currentTarget.style.background = 'var(--teal-700)'}
            onMouseLeave={e => e.currentTarget.style.background = 'var(--teal-600)'}
          >
            <Search size={14} />
            <span>Tìm kiếm</span>
          </button>
        </form>
      </div>
    </div>
  );
}
