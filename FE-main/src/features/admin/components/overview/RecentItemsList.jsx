import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, Package } from 'lucide-react';

export default function RecentItemsList({ recentItems }) {
  const navigate = useNavigate();

  return (
    <div style={{
      background: 'white', borderRadius: '10px',
      border: '1px solid rgba(0,0,0,0.07)',
      boxShadow: '0 1px 2px rgba(0,0,0,0.02)',
      overflow: 'hidden'
    }}>
      {/* Header box */}
      <div style={{
        padding: '14px 18px', borderBottom: '1px solid rgba(0,0,0,0.06)',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Package size={15} style={{ color: 'var(--teal-600)' }} />
          <h2 style={{ fontSize: '0.9rem', fontWeight: 700, margin: 0, color: 'var(--text-primary)' }}>
            Tin đăng gần đây
          </h2>
        </div>
        <button
          onClick={() => navigate('/admin/items')}
          style={{
            background: 'none', border: 'none', color: 'var(--teal-700)',
            fontSize: '0.75rem', fontWeight: 600, cursor: 'pointer',
            display: 'flex', alignItems: 'center', gap: '2px'
          }}
        >
          Xem tất cả <ChevronRight size={13} />
        </button>
      </div>

      {/* List items */}
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {recentItems.map((item, idx) => (
          <div
            key={item.id}
            onClick={() => navigate('/admin/items')}
            style={{
              padding: '12px 18px',
              borderBottom: idx < recentItems.length - 1 ? '1px solid rgba(0,0,0,0.04)' : 'none',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '12px',
              cursor: 'pointer', transition: 'background 120ms'
            }}
            onMouseEnter={e => { e.currentTarget.style.background = '#f8fafc'; }}
            onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
              <img
                src={item.img}
                alt={item.title}
                style={{ width: '36px', height: '36px', borderRadius: '6px', objectFit: 'cover', flexShrink: 0, border: '1px solid rgba(0,0,0,0.06)' }}
              />
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: '0.825rem', color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {item.title}
                </div>
                <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: '1px' }}>
                  {item.location} · {item.reporter}
                </div>
              </div>
            </div>

            <span style={{
              fontSize: '0.675rem', fontWeight: 600, padding: '2px 7px', borderRadius: '4px', flexShrink: 0,
              background: item.type === 'lost' ? '#fef2f2' : 'rgba(45,212,191,0.12)',
              color: item.type === 'lost' ? '#dc2626' : 'var(--teal-700)'
            }}>
              {item.type === 'lost' ? 'Tìm đồ' : 'Nhặt đồ'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
