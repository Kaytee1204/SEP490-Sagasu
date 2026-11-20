import { Trophy } from 'lucide-react';
import SmartMatchHero from '../components/SmartMatchHero';
import SmartMatchCard from '../components/SmartMatchCard';
import SmartMatchHelpCard from '../components/SmartMatchHelpCard';

const POSTED_ITEM = {
  id: 'REQ-8821',
  title: 'Ví da nam Pedro màu đen',
  type: 'lost',
  date: '18/09/2025',
  time: '14:30',
  location: 'Khu vực Bách Khoa, Hai Bà Trưng',
  img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80',
};

const AI_MATCHES = [
  {
    id: 1, title: 'Ví da nam Pedro đen', location: 'Toà H1, ĐHBK Hà Nội',
    date: '18/09/2025', time: '15:10',
    matchScore: 94.5,
    matchBreakdown: { visual: 98, location: 90, time: 92 },
    img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=400&q=80',
    status: 'found',
  },
  {
    id: 2, title: 'Ví nam Pedro viền nâu', location: 'Tuyến bus 32, Cầu Giấy',
    date: '17/09/2025', time: '08:15',
    matchScore: 82.1,
    matchBreakdown: { visual: 85, location: 60, time: 90 },
    img: 'https://images.unsplash.com/photo-1605333396914-232f50c05b8a?w=400&q=80',
    status: 'found',
  },
  {
    id: 3, title: 'Bóp da đen nam', location: 'Khu A, ĐH Kinh tế Quốc dân',
    date: '16/09/2025', time: '17:00',
    matchScore: 76.8,
    matchBreakdown: { visual: 70, location: 80, time: 75 },
    img: 'https://images.unsplash.com/photo-1559591937-bea52fc059ba?w=400&q=80',
    status: 'found',
  },
];

export default function SmartMatchPage() {
  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1, background: 'var(--bg-canvas)' }}>
        <SmartMatchHero postedItem={POSTED_ITEM} />

        <div className="container" style={{ padding: '48px var(--space-6) 88px' }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: '16px', marginBottom: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div
                style={{
                  width: 42,
                  height: 42,
                  borderRadius: '12px',
                  background: '#eff6ff',
                  color: '#2563eb',
                  display: 'grid',
                  placeItems: 'center',
                }}
              >
                <Trophy size={20} />
              </div>
              <div>
                <h2 className="text-heading" style={{ margin: 0, fontSize: '1.2rem' }}>Bảng xếp hạng kết quả tìm kiếm</h2>
                <div style={{ fontSize: '0.86rem', color: '#64748b', marginTop: '2px' }}>
                  Tìm thấy <strong style={{ color: '#2563eb' }}>{AI_MATCHES.length}</strong> bài đăng FOUND phù hợp nhất được sắp xếp theo mức độ tương đồng.
                </div>
              </div>
            </div>

            <div
              style={{
                display: 'inline-flex',
                flexWrap: 'wrap',
                alignItems: 'center',
                gap: '16px',
                padding: '9px 16px',
                borderRadius: '10px',
                background: '#f8fafc',
                border: '1px solid #eef2f6',
                fontSize: '0.78rem',
                fontWeight: 600,
                color: '#334155',
              }}
            >
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#eab308' }} /> Top 1 (Vàng)
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#6366f1' }} /> Top 2 (Tím)
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#f97316' }} /> Top 3 (Cam)
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span style={{ width: 8, height: 8, borderRadius: '50%', background: '#94a3b8' }} /> Top 4+
              </span>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(min(300px, 100%), 1fr))', gap: '20px' }}>
            {AI_MATCHES.map((item, i) => (
              <div key={item.id} style={{ animationDelay: `${i * 0.08}s` }}>
                <SmartMatchCard item={item} rank={i + 1} />
              </div>
            ))}
          </div>

          <SmartMatchHelpCard />
        </div>
      </main>
    </div>
  );
}
