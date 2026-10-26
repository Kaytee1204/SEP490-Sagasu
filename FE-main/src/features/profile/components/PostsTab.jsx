import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Calendar, ChevronRight, ArrowRight } from 'lucide-react';

const STATUS_MAP = {
  found: { label: 'Nhặt được', cls: 'badge-found' },
  lost: { label: 'Cần tìm', cls: 'badge-lost' },
  done: { label: 'Đã trả', cls: 'badge-done' },
};

function PostRow({ post }) {
  const s = STATUS_MAP[post.status];
  return (
    <div
      style={{
        display: 'grid', gridTemplateColumns: '52px 1fr auto',
        gap: '14px', alignItems: 'center',
        padding: '14px 0',
        borderBottom: '1px solid var(--border)',
        transition: 'background 150ms',
        cursor: 'pointer',
      }}
    >
      <img
        src={post.img}
        alt={post.title}
        style={{ width: 52, height: 52, borderRadius: 'var(--radius-md)', objectFit: 'cover', flexShrink: 0 }}
        onError={e => { e.target.src = `https://picsum.photos/seed/${post.id}/52/52`; }}
      />
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '3px', flexWrap: 'wrap' }}>
          <span className={`badge ${s.cls}`} style={{ fontSize: '0.65rem', padding: '1px 7px' }}>{s.label}</span>
          {post.matched && (
            <span style={{
              fontSize: '0.65rem', fontWeight: 600, color: 'var(--status-found)',
              background: 'var(--status-found-bg)', border: '1px solid rgba(16,185,129,0.2)',
              padding: '1px 7px', borderRadius: '4px',
            }}>
              AI khớp
            </span>
          )}
        </div>
        <div style={{ fontWeight: 600, fontSize: '0.875rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
          {post.title}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <MapPin size={11} /> {post.location}
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <Calendar size={11} /> {post.date}
          </span>
        </div>
      </div>
      <ChevronRight size={15} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
    </div>
  );
}

export default function PostsTab({ posts }) {
  const [filter, setFilter] = useState('all');
  const filters = [
    { key: 'all', label: 'Tất cả (12)' },
    { key: 'lost', label: 'Cần tìm (3)' },
    { key: 'found', label: 'Nhặt được (6)' },
    { key: 'done', label: 'Đã trao trả (3)' },
  ];

  const shown = filter === 'all' ? posts : posts.filter(p => p.status === filter);

  return (
    <div>
      {/* Filter chips */}
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '20px' }}>
        {filters.map(f => (
          <button
            key={f.key}
            type="button"
            className={`chip ${filter === f.key ? 'active' : ''}`}
            onClick={() => setFilter(f.key)}
          >
            {f.label}
          </button>
        ))}
      </div>
      {/* Post list */}
      <div>
        {shown.map(post => <PostRow key={post.id} post={post} />)}
      </div>
      <Link
        to="/bai-dang-cua-toi"
        className="btn btn-ghost"
        style={{ marginTop: '20px', width: 'fit-content' }}
      >
        Xem tất cả bài đăng
        <ArrowRight size={14} />
      </Link>
    </div>
  );
}
