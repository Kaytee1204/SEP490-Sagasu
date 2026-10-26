import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Sparkles, ArrowRight, Share2, Flag, AlertTriangle } from 'lucide-react';

export default function ItemActionSection({ item, isOwner }) {
  const navigate = useNavigate();

  return (
    <div style={{
      marginTop: 'auto', paddingTop: '24px', borderTop: '1px solid var(--border)',
      display: 'flex', flexDirection: 'column', gap: '12px'
    }}>
      {/* Nút Quét AI — chỉ hiện với chủ bài đăng */}
      {isOwner && (
        <button
          type="button"
          onClick={() => navigate('/smart-match')}
          style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px',
            width: '100%', padding: '14px 24px',
            borderRadius: 'var(--radius-xl)',
            background: 'linear-gradient(135deg, var(--teal-800), var(--teal-600))',
            color: 'white', border: 'none', cursor: 'pointer',
            fontFamily: 'var(--font-body)', fontWeight: 700, fontSize: '0.9375rem',
            boxShadow: '0 4px 16px rgba(30,107,107,0.3)',
            transition: 'all 200ms',
          }}
          onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-1px)'; e.currentTarget.style.boxShadow = '0 8px 24px rgba(30,107,107,0.4)'; }}
          onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 4px 16px rgba(30,107,107,0.3)'; }}
        >
          <Sparkles size={18} />
          Kích hoạt AI Smart Match
        </button>
      )}

      <Link
        to={`/xac-minh/${item.id}`}
        className="btn btn-primary btn-full btn-lg"
        style={{
          background: item.type === 'found' ? 'var(--accent)' : 'var(--status-found)',
          boxShadow: 'var(--shadow-md)',
          textDecoration: 'none'
        }}
      >
        {item.type === 'found' ? 'Xác minh quyền sở hữu (Claim)' : 'Tôi đã nhặt được đồ này'}
        <ArrowRight size={18} />
      </Link>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
        <button type="button" className="btn btn-ghost btn-full" style={{ gap: '8px' }}>
          <Share2 size={16} /> Chia sẻ
        </button>
        <button type="button" className="btn btn-ghost btn-full" style={{ gap: '8px', color: 'var(--status-lost)' }}>
          <Flag size={16} /> Báo cáo
        </button>
      </div>

      {/* Security Hint */}
      <div style={{
        display: 'flex', alignItems: 'flex-start', gap: '10px',
        padding: '16px', background: 'rgba(245,158,11,0.05)',
        borderRadius: 'var(--radius-lg)', marginTop: '8px'
      }}>
        <AlertTriangle size={16} style={{ color: '#d97706', flexShrink: 0, marginTop: 2 }} />
        <div style={{ fontSize: '0.8125rem', color: '#b45309', lineHeight: 1.5 }}>
          <strong style={{ display: 'block', marginBottom: '2px' }}>Lưu ý an toàn:</strong>
          Tuyệt đối không chuyển tiền cọc hoặc phí chuộc đồ. Quá trình trao đổi cần thực hiện trực tiếp tại nơi an toàn.
        </div>
      </div>
    </div>
  );
}
