import { useState } from 'react';
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
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import campusBanner from '@/assets/fpt_campus_banner.jpg';
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

export default function HomePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [keyword, setKeyword] = useState('');

  const orgName = user?.organizationName || user?.unitName || 'Trường học';

  const handleSearchSubmit = (event) => {
    event.preventDefault();
    const trimmed = keyword.trim();
    if (!trimmed) {
      navigate('/tim-kiem');
      return;
    }
    navigate(`/tim-kiem?keyword=${encodeURIComponent(trimmed)}`);
  };

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
      </div>
    </div>
  );
}
