import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { CATEGORIES, QUICK_TAGS, STATS } from '../data/homeData';

export default function HeroSection() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('Tất cả danh mục');
  const navigate = useNavigate();

  const handleHeroSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/tim-kiem?q=${encodeURIComponent(searchQuery)}`);
    } else {
      navigate('/tim-kiem');
    }
  };

  return (
    <section className="hero">
      <div className="container">
        {/* Eyebrow */}
        <div className="text-center mb-0">
          <span className="hero-eyebrow">
            <span className="w-1.5 h-1.5 rounded-full bg-[var(--accent)] inline-block animate-[pulse-dot_2s_ease-in-out_infinite]" />
            Nền tảng kết nối &amp; tìm kiếm an toàn
          </span>
        </div>

        {/* Title */}
        <h1 className="hero-title animate-fadeInUp mt-4">
          Tìm lại đồ thất lạc<br />
          <span className="hero-title-accent">nhẹ nhàng &amp; an tâm</span>
        </h1>

        {/* Subtitle */}
        <p className="hero-subtitle animate-fadeInUp delay-1">
          So khớp quang học AI thông minh và xác minh danh tính bảo mật,
          hỗ trợ kết nối người tìm và người nhặt nhanh chóng.
        </p>

        {/* Search Bar */}
        <div className="animate-fadeInUp delay-2 max-w-[700px] mx-auto mb-4">
          <form className="search-bar" onSubmit={handleHeroSearch}>
            <Search size={16} className="text-zinc-500 ml-5 shrink-0" />
            <input
              type="text"
              className="search-input"
              placeholder="Nhập tên vật phẩm (vd: CCCD, điện thoại, chìa khóa...)"
              id="hero-search-input"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
            />
            <div className="search-divider" />
            <select
              className="search-category"
              value={selectedCategory}
              onChange={e => setSelectedCategory(e.target.value)}
              id="hero-category-select"
            >
              {CATEGORIES.map(c => <option key={c}>{c}</option>)}
            </select>
            <button className="search-btn" id="hero-search-btn" type="submit">
              <Search size={14} />
              Tìm kiếm
            </button>
          </form>

          {/* Quick tags */}
          <div className="flex items-center gap-2 mt-2.5 flex-wrap">
            <span className="text-xs text-zinc-500 font-medium">Gợi ý:</span>
            {QUICK_TAGS.map(tag => (
              <button
                key={tag}
                type="button"
                onClick={() => setSearchQuery(tag)}
                className="text-xs text-[var(--accent)] bg-[#1e6b6b14] border border-[#1e6b6b26] rounded px-2 py-0.5 cursor-pointer font-[var(--font-body)] transition-all duration-150 hover:bg-[#1e6b6b26]"
              >
                {tag}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* STATS ROW */}
      <div className="mt-12">
        <div className="stat-row container rounded-xl overflow-hidden">
          {STATS.map((s, i) => (
            <div key={i} className="stat-row-item animate-fadeInUp" style={{ animationDelay: `${i * 0.07}s` }}>
              <div className="stat-row-number">{s.number}</div>
              <div className="stat-row-label">{s.label}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
