import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, ArrowUpDown } from 'lucide-react';
import MyPostsEmptyState from '../components/MyPostsEmptyState';
import MyPostsFilterBar from '../components/MyPostsFilterBar';
import MyPostCard from '../components/MyPostCard';

const MOCK_POSTS = [
  {
    id: 1, type: 'found', status: 'active',
    title: 'Ví da nam Pedro màu đen',
    location: 'Toà H1, ĐH Bách Khoa',
    date: '18/09/2025', time: '14:30',
    views: 47, aiMatched: true, matchScore: 92,
    img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=300&q=80',
  },
  {
    id: 2, type: 'lost', status: 'active',
    title: 'iPhone 15 Pro Max Xanh Titan',
    location: 'Tuyến xe 32 – Hà Nội',
    date: '15/09/2025', time: '09:15',
    views: 124, aiMatched: false, matchScore: null,
    img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=300&q=80',
  },
  {
    id: 3, type: 'found', status: 'resolved',
    title: 'MacBook Pro M2 Space Gray 14 inch',
    location: 'Thư viện Tạ Quang Bửu',
    date: '08/09/2025', time: '16:00',
    views: 88, aiMatched: true, matchScore: 97,
    img: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=300&q=80',
  },
  {
    id: 4, type: 'lost', status: 'discussing',
    title: 'Chìa khoá xe Honda Wave Alpha',
    location: 'Căng tin C2, ĐH Bách Khoa',
    date: '20/09/2025', time: '11:00',
    views: 12, aiMatched: false, matchScore: null,
    img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=300&q=80',
  },
  {
    id: 5, type: 'found', status: 'active',
    title: 'Thẻ sinh viên & Thẻ ATM Vietcombank',
    location: 'Sân bóng khu A, ĐH Bách Khoa',
    date: '21/09/2025', time: '18:45',
    views: 5, aiMatched: false, matchScore: null,
    img: 'https://images.unsplash.com/photo-1614680376408-81e91ffe3db7?w=300&q=80',
  },
];

const FILTERS = [
  { key: 'all',        label: 'Tất cả' },
  { key: 'active',     label: 'Đang hiển thị' },
  { key: 'discussing', label: 'Đang trao đổi' },
  { key: 'resolved',   label: 'Đã giải quyết' },
  { key: 'lost',       label: 'Cần tìm' },
  { key: 'found',      label: 'Nhặt được' },
];

export default function MyPostsPage() {
  const [posts, setPosts] = useState(MOCK_POSTS);
  const [filter, setFilter] = useState('all');
  const [query, setQuery] = useState('');
  const [sortAsc, setSortAsc] = useState(false);

  const handleResolve = (id) => {
    setPosts(prev => prev.map(p => p.id === id ? { ...p, status: 'resolved' } : p));
  };

  const handleDelete = (id) => {
    setPosts(prev => prev.filter(p => p.id !== id));
  };

  const filtered = posts
    .filter(p => {
      if (filter === 'all') return true;
      if (filter === 'lost' || filter === 'found') return p.type === filter;
      return p.status === filter;
    })
    .filter(p => {
      if (!query.trim()) return true;
      const q = query.toLowerCase();
      return p.title.toLowerCase().includes(q) || p.location.toLowerCase().includes(q);
    })
    .sort((a, b) => sortAsc ? a.id - b.id : b.id - a.id);

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1, background: 'var(--bg-canvas)', padding: '48px 0 80px' }}>
        <div className="container">
          {/* Header */}
          <div style={{
            display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between',
            gap: '24px', flexWrap: 'wrap', marginBottom: '32px',
          }}>
            <div>
              <h1 className="text-display-sm" style={{ marginBottom: '6px' }}>Tin đăng của tôi</h1>
              <p className="text-body" style={{ color: 'var(--text-secondary)' }}>
                Quản lý các tin tìm đồ và đồ nhặt được bạn đã đăng tải trên hệ thống.
              </p>
            </div>
            <Link to="/dang-tin" className="btn btn-primary" style={{ flexShrink: 0 }}>
              <Plus size={16} /> Đăng tin mới
            </Link>
          </div>

          {/* Filter & Search Bar */}
          <MyPostsFilterBar
            query={query}
            onChangeQuery={setQuery}
            filter={filter}
            onSelectFilter={setFilter}
            filters={FILTERS}
          />

          {/* Table / List Card */}
          <div style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-2xl)',
            overflow: 'hidden',
            boxShadow: 'var(--shadow-sm)',
          }}>
            {/* Table Header */}
            <div style={{
              display: 'grid',
              gridTemplateColumns: '56px 1fr 140px 110px 70px 36px',
              gap: '16px', alignItems: 'center',
              padding: '12px 24px',
              borderBottom: '1px solid var(--border)',
              background: 'var(--bg-canvas)',
              fontSize: '0.75rem', fontWeight: 600,
              color: 'var(--text-muted)', textTransform: 'uppercase',
              letterSpacing: '0.05em',
            }}>
              <span>Ảnh</span>
              <span>Thông tin vật phẩm</span>
              <span>Trạng thái</span>
              <button
                type="button"
                onClick={() => setSortAsc(!sortAsc)}
                style={{
                  display: 'flex', alignItems: 'center', gap: '4px',
                  background: 'none', border: 'none', cursor: 'pointer',
                  color: 'inherit', fontSize: 'inherit', fontWeight: 'inherit',
                  fontFamily: 'inherit', textTransform: 'inherit',
                  letterSpacing: 'inherit', padding: 0,
                }}
              >
                Ngày đăng <ArrowUpDown size={11} />
              </button>
              <span>Lượt xem</span>
              <span />
            </div>

            {/* List */}
            {filtered.length > 0 ? (
              filtered.map(post => (
                <MyPostCard
                  key={post.id}
                  post={post}
                  onResolve={handleResolve}
                  onDelete={handleDelete}
                />
              ))
            ) : (
              <MyPostsEmptyState />
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
