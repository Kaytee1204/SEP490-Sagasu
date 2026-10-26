import React from 'react';
import { Link } from 'react-router-dom';
import { Plus } from 'lucide-react';

export default function DualCtaSection() {
  return (
    <section className="section-sm bg-[var(--bg-surface)] py-12">
      <div className="container">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {/* Lost CTA */}
          <div className="bg-[var(--teal-900)] rounded-2xl p-10 md:p-11 text-white relative overflow-hidden">
            <div className="absolute top-0 right-0 w-45 h-45 bg-[radial-gradient(circle,#5ec4c41f_0%,transparent_70%)] rounded-full" />
            <div className="inline-flex items-center gap-1.5 text-[0.7rem] font-bold uppercase tracking-wide text-[#5ec4c4] bg-[#5ec4c41f] border border-[#5ec4c433] px-2.5 py-1 rounded mb-5">
              Mất đồ
            </div>
            <h3 className="font-[var(--font-display)] text-2xl font-extrabold leading-tight mb-3 text-white">
              Bạn đang cần<br />tìm đồ thất lạc?
            </h3>
            <p className="text-sm text-white/60 leading-relaxed mb-7 max-w-[34ch]">
              Đăng tin ngay để AI tự động quét và so khớp
              với 1,420+ vật phẩm đã được ghi nhận.
            </p>
            <div className="flex gap-2.5 flex-wrap">
              <Link to="/dang-tin" className="btn btn-lg bg-white text-[var(--teal-900)] font-bold hover:bg-zinc-100" id="cta-lost-btn">
                <Plus size={16} />
                Đăng tin thất lạc
              </Link>
              <Link to="/tim-kiem" className="btn btn-outline-white" id="cta-browse-btn">
                Tìm kiếm bài đăng hiện có
              </Link>
            </div>
          </div>

          {/* Found CTA */}
          <div className="bg-[var(--bg-canvas)] rounded-2xl border-1.5 border-[var(--border-strong)] p-10 md:p-11 relative overflow-hidden">
            <div className="absolute top-0 right-0 w-45 h-45 bg-[radial-gradient(circle,#10b9810f_0%,transparent_70%)] rounded-full" />
            <div className="inline-flex items-center gap-1.5 text-[0.7rem] font-bold uppercase tracking-wide text-[var(--status-found)] bg-[var(--status-found-bg)] border border-[#10b98133] px-2.5 py-1 rounded mb-5">
              Cộng đồng văn minh
            </div>
            <h3 className="font-[var(--font-display)] text-2xl font-extrabold leading-tight mb-3">
              Nhặt được<br />đồ rơi?
            </h3>
            <p className="text-sm text-[var(--text-secondary)] leading-relaxed mb-7 max-w-[34ch]">
              Mỗi hành động tử tế đều được mã hóa bảo mật
              thông tin cá nhân và trao gửi đúng chủ sở hữu.
            </p>
            <div className="flex gap-2.5 flex-wrap items-center">
              <Link to="/dang-tin?type=found" className="btn btn-accent btn-lg" id="cta-found-btn">
                <Plus size={16} />
                Đăng tin nhặt được
              </Link>
              <Link to="/quy-trinh-an-toan" className="btn btn-ghost" id="cta-safe-btn">
                Sổ tay an toàn
              </Link>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
