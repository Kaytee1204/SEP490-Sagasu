import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Search,
  UploadCloud,
  Cpu,
  ShieldCheck,
  Handshake,
  Plus,
  Box,
  FileText,
  ArrowRight,
  Newspaper,
  Sparkles,
  BookOpen,
  Megaphone,
  Clock3,
  User,
  X,
  PlusCircle,
  MessageSquareHeart,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import campusBanner from '@/assets/fpt_campus_banner.jpg';
import { newsApi } from '@/features/news/api/newsApi';
import {
  formatNewsFullDate,
  formatNewsRelativeTime,
  getNewsCategoryMeta,
} from '@/features/news/utils/newsUtils';
import './HomePage.css';

const PROCESS_STEPS = [
  {
    code: 'BƯỚC 01',
    title: 'Đăng thông tin',
    desc: 'Tải ảnh, vị trí và thời gian.',
    icon: UploadCloud,
  },
  {
    code: 'BƯỚC 02',
    title: 'AI so khớp',
    desc: 'Tự động gợi ý bài đăng phù hợp.',
    icon: Cpu,
  },
  {
    code: 'BƯỚC 03',
    title: 'Xác minh bí mật',
    desc: 'Trả lời câu hỏi xác nhận chủ sở hữu.',
    icon: ShieldCheck,
  },
  {
    code: 'BƯỚC 04',
    title: 'Trao trả an toàn',
    desc: 'Hẹn gặp trực tiếp hoặc tại Văn phòng.',
    icon: Handshake,
  },
];

const UTILITY_CARDS = [
  {
    title: 'Tìm đồ thất lạc',
    desc: 'Lọc nhanh theo từ khóa',
    to: '/tim-kiem',
    icon: Search,
    tone: 'indigo',
  },
  {
    title: 'Đăng bài tìm đồ',
    desc: 'Tạo tin báo mất đồ',
    to: '/dang-tin',
    icon: Plus,
    tone: 'mint',
  },
  {
    title: 'Đăng bài nhặt được',
    desc: 'Chia sẻ đồ vừa nhặt',
    to: '/dang-tin-nhat-duoc',
    icon: Box,
    tone: 'sky',
  },
  {
    title: 'Xem báo cáo',
    desc: 'Theo dõi phản ánh',
    to: '/bao-cao-cua-toi',
    icon: FileText,
    tone: 'slate',
  },
];

const CATEGORY_ICONS = {
  UPDATE: Newspaper,
  FEATURE: Sparkles,
  FEEDBACK: MessageSquareHeart,
  GUIDE: BookOpen,
  ANNOUNCEMENT: Megaphone,
};

function NewsCover({ article }) {
  const meta = getNewsCategoryMeta(article.category);
  const Icon = CATEGORY_ICONS[article.category] || Newspaper;

  if (article.coverImageUrl) {
    return (
      <div className="home-news-cover">
        <img src={article.coverImageUrl} alt={article.title} loading="lazy" />
        <span className="home-news-pill">{meta.shortLabel}</span>
      </div>
    );
  }

  return (
    <div className="home-news-cover is-fallback" style={{ background: meta.gradient }}>
      <span className="home-news-pill">{meta.shortLabel}</span>
      <div className="home-news-cover-graphic" style={{ color: meta.accent }} aria-hidden="true">
        <Icon size={40} strokeWidth={1.65} />
      </div>
    </div>
  );
}

export default function HomePage() {
  const navigate = useNavigate();
  const { user, isApprover } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [newsState, setNewsState] = useState({ loading: true, items: [], total: 0, error: '' });
  const [showAllNews, setShowAllNews] = useState(false);
  const [selectedNews, setSelectedNews] = useState(null);

  const orgName = user?.organizationName || user?.unitName || 'Trường Đại học Sakura';

  useEffect(() => {
    let active = true;
    newsApi
      .listPublished(1, 12)
      .then((res) => {
        if (!active) return;
        setNewsState({
          loading: false,
          items: Array.isArray(res?.content) ? res.content : [],
          total: Number(res?.totalElements ?? 0) || 0,
          error: '',
        });
      })
      .catch(() => {
        if (!active) return;
        setNewsState({
          loading: false,
          items: [],
          total: 0,
          error: 'Chưa tải được danh sách tin tức hệ thống.',
        });
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedNews) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') setSelectedNews(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [selectedNews]);

  const handleSearchSubmit = (event) => {
    event.preventDefault();
    const trimmed = keyword.trim();
    if (!trimmed) {
      navigate('/tim-kiem');
      return;
    }
    navigate(`/tim-kiem?keyword=${encodeURIComponent(trimmed)}`);
  };

  const visibleNews = showAllNews ? newsState.items : newsState.items.slice(0, 4);

  return (
    <div className="home-v2-page">
      {/* 1. Hero Banner */}
      <section
        className="home-v2-hero"
        style={{ '--home-campus-banner': `url(${campusBanner})` }}
        aria-label="Giới thiệu Sagasu"
      >
        <div className="home-v2-container">
          <div className="home-v2-hero-content">
            <span className="home-v2-brand-tag">Sagasu</span>
            <h1>
              Kết nối cộng đồng,
              <br />
              tìm lại những điều quan trọng
            </h1>
            <p className="home-v2-hero-desc">
              Tìm lại và trao trả đồ thất lạc nhanh chóng tại {orgName}.
            </p>

            <form className="home-v2-search" onSubmit={handleSearchSubmit} role="search">
              <Search size={18} className="home-v2-search-icon" aria-hidden="true" />
              <input
                type="search"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder="Tìm kiếm đồ thất lạc..."
                aria-label="Tìm kiếm đồ thất lạc"
              />
              <button type="submit" className="home-v2-search-btn">
                Tìm kiếm
              </button>
            </form>
          </div>
        </div>
      </section>

      <div className="home-v2-container home-v2-main">
        {/* 2. 4-Step Verification Process */}
        <section className="home-v2-process" aria-labelledby="home-process-heading">
          <div className="home-v2-process-head">
            <span className="home-v2-eyebrow-pill">QUY TRÌNH ĐƠN GIẢN</span>
            <h2 id="home-process-heading">4 bước xác minh &amp; nhận lại đồ</h2>
          </div>

          <div className="home-v2-steps-grid">
            {PROCESS_STEPS.map((step) => {
              const Icon = step.icon;
              return (
                <article key={step.code} className="home-v2-step">
                  <span className="home-v2-step-icon" aria-hidden="true">
                    <Icon size={20} strokeWidth={1.9} />
                  </span>
                  <div className="home-v2-step-body">
                    <span className="home-v2-step-code">{step.code}</span>
                    <h3>{step.title}</h3>
                    <p>{step.desc}</p>
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        {/* 3. Tiện ích nổi bật */}
        <section className="home-v2-section" aria-labelledby="home-utilities-heading">
          <div className="home-v2-section-head">
            <h2 id="home-utilities-heading">
              <span className="home-v2-section-bar" aria-hidden="true" />
              Tiện ích nổi bật
            </h2>
            <Link to="/tim-kiem" className="home-v2-section-link">
              <span>Khám phá thêm</span>
              <ArrowRight size={15} aria-hidden="true" />
            </Link>
          </div>

          <div className="home-v2-utilities-grid">
            {UTILITY_CARDS.map((card) => {
              const Icon = card.icon;
              return (
                <Link key={card.title} to={card.to} className="home-v2-utility-card">
                  <span className={`home-v2-utility-icon is-${card.tone}`} aria-hidden="true">
                    <Icon size={20} strokeWidth={2} />
                  </span>
                  <span className="home-v2-utility-text">
                    <strong>{card.title}</strong>
                    <small>{card.desc}</small>
                  </span>
                  <ArrowRight size={16} className="home-v2-utility-arrow" aria-hidden="true" />
                </Link>
              );
            })}
          </div>
        </section>

        {/* 4. Tin tức mới nhất về hệ thống */}
        <section className="home-v2-section" aria-labelledby="home-news-heading">
          <div className="home-v2-section-head">
            <h2 id="home-news-heading">
              <span className="home-v2-section-bar" aria-hidden="true" />
              Tin tức mới nhất
              {newsState.total > 0 && <span className="home-v2-news-count">{newsState.total}</span>}
            </h2>

            <div className="home-v2-news-actions">
              {isApprover && (
                <Link to="/approver/news" className="home-v2-create-news-btn">
                  <PlusCircle size={15} aria-hidden="true" />
                  <span>Tạo bài tin tức</span>
                </Link>
              )}
              {newsState.items.length > 4 && (
                <button
                  type="button"
                  className="home-v2-section-link is-btn"
                  onClick={() => setShowAllNews((prev) => !prev)}
                >
                  <span>{showAllNews ? 'Thu gọn' : `Xem tất cả (${newsState.total})`}</span>
                  <ArrowRight size={15} aria-hidden="true" />
                </button>
              )}
            </div>
          </div>

          {newsState.loading ? (
            <div className="home-v2-news-grid" aria-busy="true">
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="home-news-card is-skeleton" aria-hidden="true">
                  <div className="home-news-cover-skel" />
                  <div className="home-news-body-skel">
                    <span className="skel-line w-80" />
                    <span className="skel-line w-100" />
                    <span className="skel-line w-50" />
                  </div>
                </div>
              ))}
            </div>
          ) : newsState.error ? (
            <div className="home-news-empty">{newsState.error}</div>
          ) : visibleNews.length === 0 ? (
            <div className="home-news-empty">
              Chưa có bài tin tức nào được đăng tải.
              {isApprover && (
                <Link to="/approver/news" className="home-news-empty-cta">
                  Tạo bài tin tức đầu tiên
                </Link>
              )}
            </div>
          ) : (
            <div className="home-v2-news-grid">
              {visibleNews.map((article) => (
                <article
                  key={article.id}
                  className="home-news-card"
                  onClick={() => setSelectedNews(article)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setSelectedNews(article);
                    }
                  }}
                  tabIndex={0}
                  role="button"
                  aria-label={`Đọc bài viết: ${article.title}`}
                >
                  <NewsCover article={article} />
                  <div className="home-news-body">
                    <h3>{article.title}</h3>
                    <div className="home-news-meta">
                      <span className="home-news-author">
                        <User size={13} aria-hidden="true" />
                        {article.authorName || 'Ban quản trị Sagasu'}
                      </span>
                      <span className="home-news-time">
                        <Clock3 size={13} aria-hidden="true" />
                        {formatNewsRelativeTime(article.createdAt)}
                      </span>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>

      {/* News Article Reader Modal */}
      {selectedNews && (
        <div
          className="home-news-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="news-reader-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setSelectedNews(null);
          }}
        >
          <article className="home-news-modal">
            <button
              type="button"
              className="home-news-modal-close"
              onClick={() => setSelectedNews(null)}
              aria-label="Đóng bài viết"
            >
              <X size={18} />
            </button>

            <NewsCover article={selectedNews} />

            <div className="home-news-modal-body">
              <div className="home-news-modal-meta">
                <span
                  className="home-news-modal-cat"
                  style={{
                    background: getNewsCategoryMeta(selectedNews.category).softBg,
                    color: getNewsCategoryMeta(selectedNews.category).accent,
                  }}
                >
                  {selectedNews.categoryLabel || getNewsCategoryMeta(selectedNews.category).label}
                </span>
                <span>
                  <User size={14} aria-hidden="true" />
                  {selectedNews.authorName || 'Ban quản trị Sagasu'}
                </span>
                <span>
                  <Clock3 size={14} aria-hidden="true" />
                  {formatNewsFullDate(selectedNews.createdAt)}
                </span>
              </div>

              <h2 id="news-reader-title">{selectedNews.title}</h2>
              <p className="home-news-modal-summary">{selectedNews.summary}</p>

              <div className="home-news-modal-content">
                {String(selectedNews.content || '')
                  .split(/\n+/)
                  .filter((p) => p.trim())
                  .map((paragraph, idx) => (
                    <p key={idx}>{paragraph}</p>
                  ))}
              </div>
            </div>
          </article>
        </div>
      )}
    </div>
  );
}
