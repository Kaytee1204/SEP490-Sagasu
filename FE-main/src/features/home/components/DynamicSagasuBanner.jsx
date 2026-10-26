// src/features/home/components/DynamicSagasuBanner.jsx
import React, { useState, useEffect } from 'react';
import { Sparkles, MapPin, RefreshCw } from 'lucide-react';

export default function DynamicSagasuBanner() {
  const [matchPercent, setMatchPercent] = useState(99.2);
  const [isScanning, setIsScanning] = useState(false);

  // Tự động mô phỏng quét radar AI
  useEffect(() => {
    const timer = setInterval(() => {
      setIsScanning(true);
      setTimeout(() => {
        setMatchPercent((prev) => (prev === 99.2 ? 98.6 : 99.2));
        setIsScanning(false);
      }, 1000);
    }, 8000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="relative w-full bg-gradient-to-b from-[#060e1a] via-[#03070f] to-[#020408] border-b border-cyan-500/20 overflow-hidden py-10 sm:py-14 lg:py-16">
      
      {/* ── NỀN ÁNH SÁNG CỰC QUANG TRÀN VIỀN 100% MÀN HÌNH ── */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {/* Vầng sáng Cyan lớn góc trái */}
        <div className="absolute -top-32 left-1/4 w-[600px] h-[600px] rounded-full bg-cyan-500/10 blur-[140px]" />
        {/* Vầng sáng Emerald góc phải */}
        <div className="absolute top-1/3 -right-20 w-[500px] h-[500px] rounded-full bg-emerald-500/10 blur-[130px]" />
        {/* Lưới tọa độ không gian */}
        <div 
          className="absolute inset-0 opacity-[0.06]" 
          style={{
            backgroundImage: 'radial-gradient(circle at 1px 1px, #38bdf8 1px, transparent 0)',
            backgroundSize: '40px 40px'
          }}
        />
        {/* Đường chân trời phát sáng mỏng */}
        <div className="absolute bottom-0 inset-x-0 h-px bg-gradient-to-r from-transparent via-cyan-400/40 to-transparent" />
      </div>

      {/* ── NỘI DUNG Ở GIỮA (TẬP TRUNG TẦM CỠ VỪA PHẢI, KHÔNG BỊ TRẢI QUÁ RỘNG) ── */}
      <div className="relative z-10 max-w-7xl mx-auto px-6 sm:px-10 lg:px-12">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-center">
          
          {/* ════════ CỘT TRÁI: THƯƠNG HIỆU & SEARCH HUD GỌN GÀNG ════════ */}
          <div className="lg:col-span-6 flex flex-col items-center lg:items-start text-center lg:text-left">
            
            {/* Tag thương hiệu AI */}
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-cyan-950/70 border border-cyan-400/30 text-cyan-300 text-xs font-semibold tracking-wider uppercase shadow-inner mb-3">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-cyan-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-2 w-2 bg-cyan-400" />
              </span>
              SAGASU AI PLATFORM • 探す
            </div>

            {/* Tên Dự Án Lớn & Slogan Súc Tích */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-black tracking-tight text-white leading-none mb-3">
              SAGASU
              <span className="block text-xl sm:text-2xl lg:text-3xl font-bold bg-gradient-to-r from-cyan-400 via-teal-300 to-emerald-400 bg-clip-text text-transparent mt-2">
                Mạng Lưới Tìm Đồ Bằng AI
              </span>
            </h1>

            {/* Dòng tagline ngắn gọn, không rườm rà */}
            <p className="text-zinc-300 text-sm sm:text-base max-w-lg leading-relaxed">
              Nhận diện thị giác &amp; định vị tức thì. Kết nối đồ thất lạc về tay chủ nhân an tâm trong tích tắc.
            </p>

          </div>

          {/* ════════ CỘT PHẢI: RADAR AI SO KHỚP ĐỘNG ════════ */}
          <div className="lg:col-span-6 relative flex flex-col items-center">
            
            <div className="relative w-full max-w-md aspect-square sm:h-[350px] flex items-center justify-center">
              
              {/* Vòng tròn Radar quét */}
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                <div className={`w-80 h-80 rounded-full border border-cyan-500/20 ${isScanning ? 'animate-ping' : ''}`} />
                <div className="w-64 h-64 rounded-full border border-dashed border-cyan-400/25 animate-[spin_24s_linear_infinite]" />
                <div className="w-48 h-48 rounded-full border border-emerald-500/20" />
                <div className="w-2.5 h-2.5 rounded-full bg-cyan-400 shadow-[0_0_15px_#22d3ee]" />
              </div>

              {/* CARD 1: Tin báo mất đồ */}
              <div className={`absolute top-1 left-0 sm:left-2 w-60 p-3 rounded-2xl bg-zinc-900/90 backdrop-blur-xl border border-rose-500/30 shadow-xl transition-all duration-500 ${isScanning ? 'scale-95 opacity-70' : 'scale-100'}`}>
                <div className="flex items-center justify-between mb-1">
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-black uppercase tracking-wider bg-rose-500/20 text-rose-300 border border-rose-500/30">
                    BÁO MẤT
                  </span>
                  <span className="text-[10px] text-zinc-400">10p trước</span>
                </div>
                <div className="text-xs font-bold text-white truncate">Ví da nam Pedro &amp; CCCD</div>
                <div className="flex items-center gap-1 text-[11px] text-zinc-400 mt-0.5">
                  <MapPin size={11} className="text-rose-400 shrink-0" />
                  <span className="truncate">Bãi xe ĐH Bách Khoa</span>
                </div>
              </div>

              {/* CARD 2: Tin báo nhặt được */}
              <div className={`absolute bottom-1 right-0 sm:right-2 w-60 p-3 rounded-2xl bg-zinc-900/90 backdrop-blur-xl border border-emerald-500/30 shadow-xl transition-all duration-500 ${isScanning ? 'scale-95 opacity-70' : 'scale-100'}`}>
                <div className="flex items-center justify-between mb-1">
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-black uppercase tracking-wider bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                    NHẶT ĐƯỢC
                  </span>
                  <span className="text-[10px] text-zinc-400">Vừa xong</span>
                </div>
                <div className="text-xs font-bold text-white truncate">Ví da màu đen có thẻ ATM</div>
                <div className="flex items-center gap-1 text-[11px] text-zinc-400 mt-0.5">
                  <MapPin size={11} className="text-emerald-400 shrink-0" />
                  <span className="truncate">Thư viện Tạ Quang Bửu</span>
                </div>
              </div>

              {/* TRUNG TÂM: HUD SO KHỚP AI */}
              <div className="relative z-20 flex flex-col items-center gap-1 bg-gradient-to-b from-zinc-900 to-black px-5 py-3.5 rounded-2xl border border-cyan-400/50 shadow-[0_0_35px_rgba(6,182,212,0.35)]">
                <div className="flex items-center gap-1 text-cyan-300 text-[10px] font-extrabold uppercase tracking-wider">
                  <Sparkles size={12} className="animate-spin text-cyan-400" />
                  <span>AI MATCH ENGINE</span>
                </div>
                
                <div className="text-3xl font-black text-white tracking-tight flex items-baseline gap-0.5 my-0.5">
                  <span className="text-cyan-400">{matchPercent}</span>
                  <span className="text-base text-emerald-400">%</span>
                </div>

                <div className="text-[10px] font-semibold text-emerald-300 px-2 py-0.5 rounded-full bg-emerald-950/80 border border-emerald-500/40">
                  {isScanning ? 'Đang quét dữ liệu...' : '✓ Khớp đặc điểm & vị trí'}
                </div>

                <button
                  type="button"
                  onClick={() => {
                    setIsScanning(true);
                    setTimeout(() => setIsScanning(false), 900);
                  }}
                  disabled={isScanning}
                  className="mt-1 flex items-center gap-1 text-[10px] text-zinc-400 hover:text-white transition-colors cursor-pointer"
                >
                  <RefreshCw size={10} className={isScanning ? 'animate-spin' : ''} />
                  <span>Quét lại radar</span>
                </button>
              </div>

            </div>

            {/* Dòng trạng thái trực tuyến */}
            <div className="text-center mt-1">
              <span className="inline-flex items-center gap-2 text-xs text-zinc-300 bg-white/[0.04] border border-white/10 px-3.5 py-1 rounded-full">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                Hệ thống: <strong className="text-white">1,842</strong> tin đăng • <strong className="text-emerald-400">1,420+</strong> đã trao trả
              </span>
            </div>

          </div>

        </div>
      </div>
    </div>
  );
}
