import { ShieldCheck } from 'lucide-react';
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
  }
];

export default function SmartMatchPage() {
  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1, background: 'var(--bg-canvas)' }}>
        {/* ── SUCCESS HERO ── */}
        <SmartMatchHero postedItem={POSTED_ITEM} />

        {/* ── AI MATCH RESULTS ── */}
        <div className="container" style={{ padding: '60px var(--space-6) 100px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '32px' }}>
            <ShieldCheck size={28} style={{ color: 'var(--status-found)' }} />
            <div>
              <h2 className="text-heading" style={{ margin: 0 }}>Gợi ý thông minh (AI Matches)</h2>
              <div style={{ fontSize: '0.9375rem', color: 'var(--text-secondary)' }}>
                Đã tìm thấy <strong style={{ color: 'var(--text-primary)' }}>{AI_MATCHES.length} kết quả</strong> có độ tương đồng trên 60%.
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '32px' }}>
            {AI_MATCHES.map((item, i) => (
              <div key={item.id} style={{ animationDelay: `${i * 0.1}s` }}>
                <SmartMatchCard item={item} />
              </div>
            ))}
          </div>

          {/* Help notice if not found */}
          <SmartMatchHelpCard />
        </div>
      </main>
    </div>
  );
}
