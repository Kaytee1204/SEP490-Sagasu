import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { ITEMS, FILTER_TABS } from '../data/homeData';
import ItemCard from './ItemCard';

export default function RecentListingsSection() {
  const [activeFilter, setActiveFilter] = useState('all');

  const filteredItems = ITEMS.filter(item => {
    return activeFilter === 'all' || item.status === activeFilter;
  });

  return (
    <section className="section bg-[var(--bg-canvas)] flex-1">
      <div className="container">
        {/* Section header */}
        <div className="flex items-start justify-between mb-7 gap-4 flex-wrap">
          <div>
            <h2 className="font-[var(--font-display)] text-2xl font-bold tracking-tight mb-1">
              Tin đăng gần đây
            </h2>
            <p className="text-sm text-[var(--text-secondary)]">
              Cập nhật theo thời gian thực từ các điểm tiếp nhận và cộng đồng
            </p>
          </div>

          {/* Filter tabs */}
          <div className="flex items-center gap-2 flex-wrap">
            {FILTER_TABS.map(tab => (
              <button
                key={tab.key}
                type="button"
                id={`filter-${tab.key}`}
                className={`chip ${activeFilter === tab.key ? 'active' : ''}`}
                onClick={() => setActiveFilter(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        {/* Grid */}
        <div className="items-grid">
          {filteredItems.map((item, i) => (
            <div
              key={item.id}
              style={{ opacity: 0, animation: `fadeInUp 0.45s cubic-bezier(0.34,1.56,0.64,1) ${i * 0.06}s forwards` }}
            >
              <ItemCard item={item} />
            </div>
          ))}
        </div>

        {/* Load more */}
        <div className="text-center mt-10">
          <Link to="/tim-do-that-lac" className="btn btn-ghost btn-lg" id="view-all-btn">
            Xem tất cả tin đăng
            <ArrowRight size={16} />
          </Link>
        </div>
      </div>
    </section>
  );
}
