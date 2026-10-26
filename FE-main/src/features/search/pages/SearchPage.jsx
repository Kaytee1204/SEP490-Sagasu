import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import SearchHeaderBar from '../components/SearchHeaderBar';
import SearchFilterSidebar from '../components/SearchFilterSidebar';
import SearchMatchCard from '../components/SearchMatchCard';
import SearchEmptyState from '../components/SearchEmptyState';

const TOP_MATCHES = [
  {
    id: 1, title: 'Ví da nam Pedro đen', location: 'Toà H1, ĐHBK Hà Nội',
    category: 'Ví & Giấy tờ',
    date: '18/09/2025', time: '14:30',
    matchScore: 94.5,
    matchBreakdown: { visual: 98, location: 90, time: 92 },
    img: 'https://images.unsplash.com/photo-1627123424574-724758594913?w=400&q=80',
    status: 'found',
  },
  {
    id: 2, title: 'Ví nam Pedro viền nâu', location: 'Tuyến bus 32, Cầu Giấy',
    category: 'Ví & Giấy tờ',
    date: '17/09/2025', time: '08:15',
    matchScore: 82.1,
    matchBreakdown: { visual: 85, location: 60, time: 90 },
    img: 'https://images.unsplash.com/photo-1605333396914-232f50c05b8a?w=400&q=80',
    status: 'found',
  },
  {
    id: 3, title: 'Bóp da đen nam', location: 'Khu A, ĐH Kinh tế Quốc dân',
    category: 'Ví & Giấy tờ',
    date: '16/09/2025', time: '17:00',
    img: 'https://images.unsplash.com/photo-1559591937-bea52fc059ba?w=400&q=80',
    status: 'found',
  }
];

const MORE_RESULTS = [
  {
    id: 4, title: 'Ví đen không rõ hiệu', location: 'Hồ Hoàn Kiếm',
    category: 'Ví & Giấy tờ',
    date: '10/09/2025', time: '20:00',
    matchScore: 58.2,
    img: 'https://images.unsplash.com/photo-1628151015968-3a4429e9ef04?w=400&q=80',
    status: 'found',
  },
  {
    id: 5, title: 'Ví da cá sấu đen', location: 'Aeon Mall Hà Đông',
    category: 'Ví & Giấy tờ',
    date: '18/09/2025', time: '19:45',
    matchScore: 55.4,
    img: 'https://images.unsplash.com/photo-1543163521-1bf539c55dd2?w=400&q=80',
    status: 'found',
  },
  {
    id: 6, title: 'iPhone 15 Pro Titan', location: 'Tuyến bus 32, Cầu Giấy',
    category: 'Thiết bị điện tử',
    date: '19/09/2025', time: '10:15',
    matchScore: 89.0,
    img: 'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=400&q=80',
    status: 'lost',
  },
  {
    id: 7, title: 'Chìa khóa smartkey Honda', location: 'Vinhomes Central Park, TP.HCM',
    category: 'Chìa khóa',
    date: '18/09/2025', time: '15:20',
    matchScore: 78.5,
    img: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80',
    status: 'found',
  },
  {
    id: 8, title: 'Mèo Anh lông ngắn xám', location: 'KĐT Sala, TP. Thủ Đức',
    category: 'Thú cưng',
    date: '17/09/2025', time: '09:00',
    matchScore: 72.0,
    img: 'https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba?w=400&q=80',
    status: 'lost',
  },
];

const CATEGORIES = [
  { value: 'all', label: 'Tất cả danh mục' },
  { value: 'Ví & Giấy tờ', label: 'Ví & Giấy tờ' },
  { value: 'Thiết bị điện tử', label: 'Thiết bị điện tử' },
  { value: 'Chìa khóa', label: 'Chìa khóa' },
  { value: 'Thú cưng', label: 'Thú cưng' },
  { value: 'Khác', label: 'Khác' },
];

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialQuery = searchParams.get('q') || '';
  const initialCategory = searchParams.get('cat') || 'all';

  const [query, setQuery] = useState(initialQuery);
  const [selectedCategory, setSelectedCategory] = useState(initialCategory);
  const [activeSearch, setActiveSearch] = useState({ q: initialQuery, cat: initialCategory });

  useEffect(() => {
    const q = searchParams.get('q') || '';
    const cat = searchParams.get('cat') || 'all';
    setQuery(q);
    setSelectedCategory(cat);
    setActiveSearch({ q, cat });
  }, [searchParams]);

  const handleSearch = (e) => {
    e?.preventDefault();
    const newParams = {};
    if (query.trim()) newParams.q = query.trim();
    if (selectedCategory && selectedCategory !== 'all') newParams.cat = selectedCategory;
    setSearchParams(newParams);
    setActiveSearch({ q: query.trim(), cat: selectedCategory });
  };

  const filterItem = (item) => {
    const keyword = activeSearch.q.toLowerCase().trim();
    const matchesQ = !keyword ||
      item.title.toLowerCase().includes(keyword) ||
      item.location.toLowerCase().includes(keyword);
    const matchesCat = activeSearch.cat === 'all' || item.category === activeSearch.cat;
    return matchesQ && matchesCat;
  };

  const filteredTopMatches = TOP_MATCHES.filter(filterItem);
  const filteredMoreResults = MORE_RESULTS.filter(filterItem);
  const totalResults = filteredTopMatches.length + filteredMoreResults.length;

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      {/* ── HEADER / SEARCH BAR ── */}
      <SearchHeaderBar
        query={query}
        onChangeQuery={setQuery}
        onClearQuery={() => setQuery('')}
        selectedCategory={selectedCategory}
        onChangeCategory={setSelectedCategory}
        categories={CATEGORIES}
        onSearch={handleSearch}
      />

      <main className="container" style={{ flex: 1, padding: '40px var(--space-6)', display: 'grid', gridTemplateColumns: '280px 1fr', gap: '40px' }}>
        {/* ── SIDEBAR FILTERS ── */}
        <SearchFilterSidebar />

        {/* ── RESULTS AREA ── */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
            <h2 className="text-heading" style={{ margin: 0 }}>
              {activeSearch.q ? `Kết quả tìm kiếm cho "${activeSearch.q}"` : 'Tất cả kết quả tìm kiếm'}
            </h2>
            <div style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
              {totalResults} kết quả
            </div>
          </div>

          {totalResults > 0 ? (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '24px' }}>
              {filteredTopMatches.map((item, i) => (
                <div key={item.id} style={{ animationDelay: `${i * 0.1}s` }}>
                  <SearchMatchCard item={item} isTopMatch={true} />
                </div>
              ))}
              {filteredMoreResults.map((item, i) => (
                <div key={item.id} style={{ animationDelay: `${(i + 3) * 0.1}s` }}>
                  <SearchMatchCard item={item} />
                </div>
              ))}
            </div>
          ) : (
            <SearchEmptyState query={activeSearch.q} />
          )}
        </div>
      </main>
    </div>
  );
}
