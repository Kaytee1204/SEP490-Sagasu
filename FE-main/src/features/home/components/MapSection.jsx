import React from 'react';
import { Link } from 'react-router-dom';
import { MapPin, ChevronRight, ArrowRight } from 'lucide-react';
import { MAP_CLUSTERS } from '../data/homeData';

export default function MapSection() {
  return (
    <section className="section bg-[var(--bg-surface)]">
      <div className="container">
        <div className="grid grid-cols-1 md:grid-cols-[1fr_1.5fr] gap-12 items-center">
          {/* Left: text + cluster list */}
          <div>
            <div className="inline-flex items-center gap-1.5 text-[0.72rem] font-bold uppercase tracking-wide text-[var(--accent)] bg-[#1e6b6b17] border border-[#1e6b6b2e] px-3 py-1 rounded-full mb-4.5">
              <span className="w-1.5 h-1.5 rounded-full bg-[var(--accent)] inline-block animate-[pulse-dot_2s_ease-in-out_infinite]" />
              Bản đồ tương tác
            </div>
            <h2 className="font-[var(--font-display)] text-[clamp(1.5rem,2.8vw,2rem)] font-extrabold tracking-tight leading-tight mb-3.5">
              Tra cứu theo bán kính<br />&amp; điểm nóng
            </h2>
            <p className="text-[0.9rem] text-[var(--text-secondary)] leading-relaxed mb-7 max-w-[36ch]">
              Tự động gom cụm các trạm xe buýt, sảnh thư viện, ký túc xá
              giúp người dùng xác định vị trí đồ rơi chuẩn xác.
            </p>

            {/* Cluster list */}
            <div className="flex flex-col gap-2.5">
              {MAP_CLUSTERS.map((c, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between bg-[var(--bg-canvas)] border border-[var(--border)] rounded-lg px-4.5 py-3.5 cursor-pointer transition-all duration-200 gap-3 hover:border-[var(--accent)] hover:bg-[var(--teal-50)] group"
                >
                  <div>
                    <div className="font-semibold text-sm text-[var(--text-primary)] mb-1">{c.name}</div>
                    <div className="text-[0.775rem] text-[var(--text-muted)]">
                      <span className="text-[var(--status-lost)] font-semibold">{c.lost} tin báo mất</span>
                      {' · '}
                      <span className="text-[var(--status-found)] font-semibold">{c.found} tin nhặt được</span>
                    </div>
                  </div>
                  <ChevronRight size={15} className="text-[var(--text-muted)] shrink-0 group-hover:text-[var(--accent)] transition-colors" />
                </div>
              ))}
            </div>

            <Link to="/ban-do" className="btn btn-ghost mt-5 w-fit" id="map-view-all-btn">
              <MapPin size={15} />
              Xem toàn bộ bản đồ
              <ArrowRight size={14} />
            </Link>
          </div>

          {/* Right: Map embed */}
          <div className="relative">
            {/* Live badge */}
            <div className="absolute top-4 left-4 z-10 inline-flex items-center gap-1.5 bg-white/95 border border-[var(--border)] rounded-full px-3 py-1 text-xs font-semibold shadow-md backdrop-blur-sm">
              <span className="w-2 h-2 rounded-full bg-[var(--status-found)] inline-block animate-[pulse-dot_2s_ease-in-out_infinite]" />
              Đang hiển thị 120 điểm tiếp nhận
            </div>

            {/* Embedded OpenStreetMap */}
            <div className="rounded-2xl overflow-hidden border border-[var(--border)] shadow-lg h-[380px] bg-[#e8f4f0]">
              <iframe
                title="Bản đồ điểm tiếp nhận TimDo"
                src="https://www.openstreetmap.org/export/embed.html?bbox=105.7800%2C20.9800%2C105.9200%2C21.0700&layer=mapnik&marker=21.0285%2C105.8542"
                className="w-full h-full border-none"
                loading="lazy"
                allowFullScreen
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
