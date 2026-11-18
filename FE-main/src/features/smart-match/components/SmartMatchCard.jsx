import { Link } from 'react-router-dom';
import { Award, Clock, MapPin, Sparkles, Trophy } from 'lucide-react';

function getCardTierTheme(rank) {
  if (rank === 1) {
    return {
      rankLabel: 'TOP 1 • Đề xuất tối ưu',
      badgeGradient: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
      border: '2px solid #f59e0b',
      cardBg: 'linear-gradient(180deg, #fffbeb 0%, #ffffff 45%)',
      shadow: '0 14px 30px -8px rgba(245, 158, 11, 0.25)',
      accentText: '#b45309',
      accentBg: '#fef3c7',
      accentBorder: '#f59e0b',
      buttonBg: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
    };
  }
  if (rank === 2) {
    return {
      rankLabel: 'TOP 2 • Tương đồng cao',
      badgeGradient: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
      border: '2px solid #818cf8',
      cardBg: 'linear-gradient(180deg, #eef2ff 0%, #ffffff 45%)',
      shadow: '0 12px 26px -8px rgba(99, 102, 241, 0.22)',
      accentText: '#3730a3',
      accentBg: '#e0e7ff',
      accentBorder: '#818cf8',
      buttonBg: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
    };
  }
  if (rank === 3) {
    return {
      rankLabel: 'TOP 3 • Khả năng khớp',
      badgeGradient: 'linear-gradient(135deg, #ea580c 0%, #be123c 100%)',
      border: '2px solid #fb923c',
      cardBg: 'linear-gradient(180deg, #fff7ed 0%, #ffffff 45%)',
      shadow: '0 12px 26px -8px rgba(234, 88, 12, 0.2)',
      accentText: '#c2410c',
      accentBg: '#ffedd5',
      accentBorder: '#fb923c',
      buttonBg: 'linear-gradient(135deg, #ea580c 0%, #c2410c 100%)',
    };
  }
  return {
    rankLabel: `HẠNG #${rank} • Gợi ý mở rộng`,
    badgeGradient: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
    border: '1.5px solid #7dd3fc',
    cardBg: 'linear-gradient(180deg, #f0f9ff 0%, #ffffff 45%)',
    shadow: '0 8px 20px -6px rgba(2, 132, 199, 0.15)',
    accentText: '#0369a1',
    accentBg: '#e0f2fe',
    accentBorder: '#7dd3fc',
    buttonBg: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
  };
}

export default function SmartMatchCard({ item, rank = 1 }) {
  const theme = getCardTierTheme(rank);
  const RankIcon = rank === 1 ? Trophy : rank <= 3 ? Award : Sparkles;

  return (
    <div
      className="card animate-fadeInUp"
      style={{
        display: 'flex',
        flexDirection: 'column',
        border: theme.border,
        background: theme.cardBg,
        boxShadow: theme.shadow,
        overflow: 'hidden',
        position: 'relative',
        borderRadius: '16px',
      }}
    >
      {/* Top-Left Rank Badge */}
      <div
        style={{
          position: 'absolute',
          top: 12,
          left: 12,
          zIndex: 10,
          background: theme.badgeGradient,
          color: 'white',
          fontWeight: 800,
          fontSize: '0.74rem',
          padding: '6px 11px',
          borderRadius: '999px',
          boxShadow: '0 4px 12px rgba(0,0,0,0.22)',
          display: 'inline-flex',
          alignItems: 'center',
          gap: '5px',
          letterSpacing: '0.02em',
        }}
      >
        <RankIcon size={13} />
        <span>{theme.rankLabel}</span>
      </div>

      {/* Top-Right Score Badge */}
      <div
        style={{
          position: 'absolute',
          top: 12,
          right: 12,
          zIndex: 10,
          background: theme.accentBg,
          color: theme.accentText,
          border: `1.5px solid ${theme.accentBorder}`,
          fontWeight: 800,
          fontSize: '0.9rem',
          padding: '5px 12px',
          borderRadius: '999px',
          boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
          display: 'flex',
          alignItems: 'center',
          gap: '4px',
        }}
      >
        {item.matchScore}%
        <span style={{ fontSize: '0.65rem', fontWeight: 700, textTransform: 'uppercase' }}>Match</span>
      </div>

      <div style={{ position: 'relative', paddingTop: '65%' }}>
        <img
          src={item.img}
          alt={item.title}
          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
        />
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background: 'linear-gradient(to top, rgba(0,0,0,0.45) 0%, transparent 45%)',
          }}
        />
      </div>

      <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', flex: 1 }}>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
          <span className="badge badge-found">Nhặt được</span>
          <span className="badge badge-accent">{item.category || 'Ví & Giấy tờ'}</span>
        </div>

        <h3
          style={{
            fontSize: '1.125rem',
            fontWeight: 800,
            marginBottom: '12px',
            color: 'var(--text-primary)',
            lineHeight: 1.3,
          }}
        >
          {item.title}
        </h3>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '18px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
            <MapPin size={14} style={{ color: theme.accentText }} />
            {item.location}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
            <Clock size={14} style={{ color: theme.accentText }} />
            {item.time} — {item.date}
          </div>
        </div>

        <div
          style={{
            background: '#ffffff',
            borderRadius: '12px',
            padding: '12px',
            marginBottom: '18px',
            border: `1px solid ${theme.accentBorder}`,
          }}
        >
          <div
            style={{
              fontSize: '0.72rem',
              fontWeight: 700,
              color: theme.accentText,
              marginBottom: '8px',
              textTransform: 'uppercase',
            }}
          >
            Chi tiết AI Ranking (#{rank})
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '8px' }}>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Hình ảnh</div>
              <div style={{ fontSize: '0.875rem', fontWeight: 800, color: theme.accentText }}>
                {item.matchBreakdown?.visual ?? 0}%
              </div>
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Địa điểm</div>
              <div style={{ fontSize: '0.875rem', fontWeight: 800, color: theme.accentText }}>
                {item.matchBreakdown?.location ?? 0}%
              </div>
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>Thời gian</div>
              <div style={{ fontSize: '0.875rem', fontWeight: 800, color: theme.accentText }}>
                {item.matchBreakdown?.time ?? 0}%
              </div>
            </div>
          </div>
        </div>

        <div style={{ marginTop: 'auto' }}>
          <Link
            to={`/chi-tiet/${item.id}`}
            className="btn btn-full"
            style={{
              background: theme.buttonBg,
              color: '#ffffff',
              fontWeight: 700,
              border: 'none',
            }}
          >
            Xem chi tiết & Xác minh
          </Link>
        </div>
      </div>
    </div>
  );
}
