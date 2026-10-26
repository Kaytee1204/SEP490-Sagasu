import React from 'react';
import { Link } from 'react-router-dom';
import SagasuLogoWhite from '@/assets/Sagasu_logo_white.svg';

export default function AuthLeftPanel() {
  return (
    <div className="auth-panel-left">
      {/* Top Header with Sagasu Logo */}
      <div className="flex items-center gap-3 mb-10 relative z-10">
        <Link to="/" className="inline-block group cursor-pointer hover:opacity-95 transition-opacity" aria-label="Sagasu">
          <img
            src={SagasuLogoWhite}
            alt="Sagasu"
            className="h-12 sm:h-14 w-auto object-contain transition-transform duration-200 group-hover:scale-105"
          />
        </Link>
        <div className="text-[0.6875rem] font-semibold bg-[#5ec4c433] text-[#5ec4c4] border border-[#5ec4c44d] px-2.5 py-1 rounded-full whitespace-nowrap">
          Cổng Dịch Vụ Tìm & Bàn Giao Thất Lạc
        </div>
      </div>

      {/* Hero text */}
      <div className="relative z-10 mb-auto">
        <div className="text-[0.75rem] font-bold uppercase tracking-[0.1em] text-[#5ec4c4e6] mb-4">
          Nền tảng vì cộng đồng
        </div>
        <div className="animated-text-container">
          <h1 className="animated-text-item font-[var(--font-display)] font-extrabold text-[clamp(2.25rem,4vw,3.25rem)] leading-[1.2] tracking-[-0.02em] text-white m-0">
            Bảo vệ thông tin,<br />kết nối lòng tốt.
          </h1>
          <h1 className="animated-text-item font-[var(--font-display)] font-extrabold text-[clamp(2.25rem,4vw,3.25rem)] leading-[1.2] tracking-[-0.02em] text-white m-0">
            Tìm lại đồ thất lạc<br />nhẹ nhàng & an tâm.
          </h1>
          <h1 className="animated-text-item font-[var(--font-display)] font-extrabold text-[clamp(2.25rem,4vw,3.25rem)] leading-[1.2] tracking-[-0.02em] text-white m-0">
            Cộng đồng văn minh,<br />chia sẻ trách nhiệm.
          </h1>
        </div>
      </div>
    </div>
  );
}
