import { Link } from 'react-router-dom';
import { Calendar, Crown, MapPin } from 'lucide-react';

function getRankBadgeStyle(rank) {
  if (rank === 1) return { bg: '#fef9c3', color: '#b45309', label: 'Top 1' };
  if (rank === 2) return { bg: '#eef2ff', color: '#4f46e5', label: 'Top 2' };
  if (rank === 3) return { bg: '#ffedd5', color: '#ea580c', label: 'Top 3' };
  return { bg: '#f1f5f9', color: '#475569', label: `Top ${rank}` };
}

export default function SmartMatchCard({ item, rank = 1 }) {
  const badge = getRankBadgeStyle(rank);
  const score = Number(item.matchScore ?? 0).toFixed(1);
  const metrics = [
    { label: 'Hình ảnh', val: item.matchBreakdown?.visual ?? 0 },
    { label: 'Vị trí', val: item.matchBreakdown?.location ?? 0 },
    { label: 'Thời gian', val: item.matchBreakdown?.time ?? 0 },
  ];

  return (
    <article
      className="animate-fadeInUp"
      style={{
        display: 'flex',
        flexDirection: 'column',
        border: '1px solid #e2e8f0',
        background: '#ffffff',
        boxShadow: '0 2px 10px rgba(15, 23, 42, 0.04)',
        overflow: 'hidden',
        borderRadius: '14px',
      }}
    >
      {/* Top row: Rank pill + Score */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '14px 16px 10px',
          borderBottom: '1px solid #f1f5f9',
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            padding: '4px 10px',
            borderRadius: '999px',
            background: badge.bg,
            color: badge.color,
            fontSize: '0.78rem',
            fontWeight: 750,
          }}
        >
          {rank === 1 && <Crown size={13} fill="#eab308" color="#ca8a04" />}
          <span>{badge.label}</span>
        </span>

        <div style={{ textAlign: 'right' }}>
          <span style={{ fontSize: '1.05rem', fontWeight: 800, color: '#0f172a', fontVariantNumeric: 'tabular-nums' }}>
            {score}%
          </span>
          <span style={{ display: 'block', fontSize: '0.7rem', color: '#64748b' }}>Độ phù hợp</span>
        </div>
      </div>

      {/* Image */}
      <div
        style={{
          height: '180px',
          background: '#f8fafc',
          padding: '10px',
          display: 'grid',
          placeItems: 'center',
          borderBottom: '1px solid #f1f5f9',
        }}
      >
        <img
          src={item.img}
          alt={item.title}
          style={{ width: '100%', height: '100%', objectFit: 'contain', borderRadius: '8px' }}
        />
      </div>

      <div style={{ padding: '16px', display: 'flex', flexDirection: 'column', flex: 1 }}>
        <h3
          style={{
            fontSize: '1rem',
            fontWeight: 750,
            margin: '0 0 10px',
            color: '#0f172a',
            lineHeight: 1.35,
          }}
        >
          {item.title}
        </h3>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginBottom: '14px', fontSize: '0.8rem', color: '#64748b' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <MapPin size={14} style={{ color: '#64748b', flexShrink: 0 }} />
            <span>{item.location}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Calendar size={14} style={{ color: '#64748b', flexShrink: 0 }} />
            <span>{item.date}</span>
          </div>
        </div>

        {/* Clean neutral breakdown panel */}
        <div
          style={{
            background: '#f8fafc',
            borderRadius: '10px',
            padding: '12px',
            marginBottom: '16px',
            border: '1px solid #eef2f6',
          }}
        >
          <div style={{ fontSize: '0.74rem', fontWeight: 700, color: '#334155', marginBottom: '10px' }}>
            Chi tiết điểm đánh giá
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '10px' }}>
            {metrics.map((m) => (
              <div key={m.label}>
                <div style={{ fontSize: '0.7rem', color: '#64748b', marginBottom: '4px' }}>{m.label}</div>
                <div style={{ fontSize: '0.86rem', fontWeight: 750, color: '#0f172a', marginBottom: '5px', fontVariantNumeric: 'tabular-nums' }}>
                  {Number(m.val).toFixed(1)}%
                </div>
                <div style={{ height: 4, borderRadius: 99, background: '#e2e8f0', overflow: 'hidden' }}>
                  <div style={{ width: `${Math.min(100, Math.max(0, m.val))}%`, height: '100%', background: '#2563eb', borderRadius: 99 }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ marginTop: 'auto' }}>
          <Link
            to={`/chi-tiet/${item.id}`}
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '100%',
              padding: '9px 14px',
              borderRadius: '9px',
              background: '#2563eb',
              color: '#ffffff',
              fontSize: '0.84rem',
              fontWeight: 650,
              textDecoration: 'none',
            }}
          >
            Xem chi tiết &amp; Xác minh
          </Link>
        </div>
      </div>
    </article>
  );
}
