import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import ItemGallery from '../components/detail/ItemGallery';
import ItemMatchBreakdown from '../components/detail/ItemMatchBreakdown';
import ItemInfoSection from '../components/detail/ItemInfoSection';
import ItemFinderCard from '../components/detail/ItemFinderCard';
import ItemCharacteristics from '../components/detail/ItemCharacteristics';
import ItemActionSection from '../components/detail/ItemActionSection';

const ITEM = {
  id: '1',
  title: 'Ví da nam Pedro màu đen',
  type: 'found',
  category: 'Ví & Giấy tờ',
  date: '18/09/2025',
  time: '14:30',
  location: 'Toà H1, Đại học Bách Khoa Hà Nội',
  lat: 21.0041,
  lng: 105.8437,
  description: 'Nhặt được một ví da nam hiệu Pedro màu đen trên ghế đá gần sảnh Toà H1. Trong ví có một số giấy tờ tuỳ thân nhưng không tiện công khai. Mong chủ nhân liên hệ để nhận lại.',
  characteristics: [
    'Màu sắc: Đen',
    'Thương hiệu: Pedro',
    'Chất liệu: Da thật',
    'Đặc điểm phụ: Có vết xước nhỏ ở góc phải dưới'
  ],
  finder: {
    name: 'Thành viên uy tín',
    verified: true,
    avatar: 'https://api.dicebear.com/7.x/initials/svg?seed=TV&backgroundColor=1e6b6b'
  },
  images: [
    'https://images.unsplash.com/photo-1627123424574-724758594913?w=800&q=80',
    'https://images.unsplash.com/photo-1605333396914-232f50c05b8a?w=800&q=80'
  ],
  matchData: {
    score: 94.5,
    visual: 98,
    location: 90,
    time: 92
  }
};

export default function ItemDetailPage() {
  const { id } = useParams();
  const [activeImage, setActiveImage] = useState(0);
  const [isOwner] = useState(true);

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1, background: 'var(--bg-canvas)' }}>
        {/* ── BREADCRUMB ── */}
        <div style={{ background: 'white', borderBottom: '1px solid var(--border)', padding: '16px 0' }}>
          <div className="container">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
              <Link to="/" style={{ color: 'inherit', textDecoration: 'none' }}>Trang chủ</Link>
              <ChevronRight size={14} />
              <Link to="/tim-kiem" style={{ color: 'inherit', textDecoration: 'none' }}>Tìm kiếm</Link>
              <ChevronRight size={14} />
              <span style={{ color: 'var(--text-primary)', fontWeight: 500 }}>{ITEM.title}</span>
            </div>
          </div>
        </div>

        <div className="container" style={{ padding: '40px var(--space-6)', display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '48px' }}>
          {/* ── LEFT COLUMN: GALLERY & MATCH DATA ── */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <ItemGallery
              images={ITEM.images}
              title={ITEM.title}
              type={ITEM.type}
              activeImage={activeImage}
              onSelectImage={setActiveImage}
            />
            <ItemMatchBreakdown matchData={ITEM.matchData} />
          </div>

          {/* ── RIGHT COLUMN: DETAILS & ACTIONS ── */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            <ItemInfoSection item={ITEM} />
            <ItemFinderCard finder={ITEM.finder} />
            <ItemCharacteristics
              description={ITEM.description}
              characteristics={ITEM.characteristics}
            />
            <ItemActionSection item={ITEM} isOwner={isOwner} />
          </div>
        </div>
      </main>
    </div>
  );
}
