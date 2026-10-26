import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles, MapPin, Calendar, Clock, Eye, MoreHorizontal, ArrowRight, CheckSquare, Trash2 } from 'lucide-react';

const STATUS_CONFIG = {
  active:     { label: 'Đang hiển thị', dot: 'var(--status-found)' },
  resolved:   { label: 'Đã giải quyết', dot: 'var(--status-done)' },
  discussing: { label: 'Đang trao đổi', dot: 'var(--status-warning)' },
};

const TYPE_CONFIG = {
  found: { label: 'Nhặt được', cls: 'badge-found' },
  lost:  { label: 'Cần tìm',   cls: 'badge-lost' },
};

export default function MyPostCard({ post, onResolve, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const navigate = useNavigate();
  const s = STATUS_CONFIG[post.status] || STATUS_CONFIG.active;
  const t = TYPE_CONFIG[post.type] || TYPE_CONFIG.lost;

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '56px 1fr 140px 110px 70px 36px',
        gap: '16px', alignItems: 'center',
        padding: '16px 24px',
        borderBottom: '1px solid var(--border)',
        transition: 'background 150ms',
        cursor: 'pointer',
      }}
      onClick={() => navigate(`/chi-tiet/${post.id}`)}
      onMouseEnter={e => e.currentTarget.style.background = 'var(--bg-elevated)'}
      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
    >
      {/* Thumbnail */}
      <img
        src={post.img}
        alt={post.title}
        style={{ width: 56, height: 56, borderRadius: 'var(--radius-md)', objectFit: 'cover', border: '1px solid var(--border)' }}
        onError={e => { e.target.src = `https://picsum.photos/seed/${post.id}/56/56`; }}
      />

      {/* Title & badges */}
      <div style={{ minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px', flexWrap: 'wrap' }}>
          <span className={`badge ${t.cls}`} style={{ fontSize: '0.65rem', padding: '2px 8px' }}>{t.label}</span>
          {post.aiMatched ? (
            <span style={{
              fontSize: '0.65rem', fontWeight: 600, color: 'var(--status-found)',
              background: 'var(--status-found-bg)', border: '1px solid rgba(16,185,129,0.2)',
              padding: '2px 8px', borderRadius: '4px',
              display: 'flex', alignItems: 'center', gap: '4px',
            }}>
              <Sparkles size={10} /> AI khớp {post.matchScore}%
            </span>
          ) : (
            <span style={{
              fontSize: '0.65rem', fontWeight: 500, color: 'var(--text-muted)',
              background: 'var(--bg-canvas)', border: '1px solid var(--border)',
              padding: '2px 8px', borderRadius: '4px',
              display: 'flex', alignItems: 'center', gap: '4px',
            }}>
              <Sparkles size={10} /> Chưa quét AI
            </span>
          )}
        </div>
        <div style={{
          fontWeight: 600, fontSize: '0.9375rem', color: 'var(--text-primary)',
          marginBottom: '4px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
        }}>
          {post.title}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
          <MapPin size={11} /> {post.location}
        </div>
      </div>

      {/* Status */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '7px' }}>
        <span style={{ width: 7, height: 7, borderRadius: '50%', background: s.dot, display: 'inline-block', flexShrink: 0 }} />
        <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', fontWeight: 500 }}>{s.label}</span>
      </div>

      {/* Date */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '3px' }}>
        <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '4px' }}>
          <Calendar size={11} /> {post.date}
        </span>
        <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
          <Clock size={11} /> {post.time}
        </span>
      </div>

      {/* Views */}
      <div style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', display: 'flex', alignItems: 'center', gap: '4px' }}>
        <Eye size={13} /> {post.views}
      </div>

      {/* Actions menu */}
      <div style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          style={{
            width: 32, height: 32, borderRadius: 'var(--radius-md)',
            border: '1px solid var(--border)', background: 'transparent', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'var(--text-muted)', transition: 'all 150ms',
          }}
          onMouseEnter={e => { e.currentTarget.style.background = 'var(--bg-elevated)'; e.currentTarget.style.color = 'var(--text-primary)'; }}
          onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = 'var(--text-muted)'; }}
        >
          <MoreHorizontal size={15} />
        </button>
        {menuOpen && (
          <div style={{
            position: 'absolute', right: 0, top: '38px', zIndex: 50,
            background: 'var(--bg-surface)', border: '1px solid var(--border)',
            borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-xl)',
            overflow: 'hidden', minWidth: '180px',
          }}>
            {[
              { icon: ArrowRight, label: 'Xem chi tiết', color: 'var(--text-primary)', action: () => { navigate(`/chi-tiet/${post.id}`); setMenuOpen(false); } },
              { icon: CheckSquare, label: 'Đánh dấu đã xong', color: 'var(--status-found)', action: () => { onResolve(post.id); setMenuOpen(false); } },
              { icon: Trash2, label: 'Xóa bài đăng', color: '#ef4444', action: () => { onDelete(post.id); setMenuOpen(false); } },
            ].map((item, i) => {
              const Icon = item.icon;
              return (
                <button
                  key={i}
                  type="button"
                  onClick={item.action}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '10px',
                    width: '100%', padding: '10px 14px',
                    background: 'none', border: 'none',
                    color: item.color, fontSize: '0.8125rem', fontWeight: 500,
                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                    textAlign: 'left', transition: 'background 100ms',
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = 'var(--bg-canvas)'}
                  onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
                >
                  <Icon size={14} />
                  {item.label}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
