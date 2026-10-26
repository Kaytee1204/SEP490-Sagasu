import React from 'react';
import { STEPS } from '../data/homeData';

export default function ProcessSection() {
  return (
    <section className="section bg-[var(--bg-canvas)]">
      <div className="container">
        <div className="text-center mb-12">
          <div className="inline-flex items-center gap-1.5 text-[0.72rem] font-bold uppercase tracking-wide text-[var(--accent)] bg-[#1e6b6b17] border border-[#1e6b6b2e] px-3 py-1 rounded-full mb-4.5">
            Quy trình đơn giản
          </div>
          <h2 className="font-[var(--font-display)] text-[clamp(1.5rem,3vw,2.25rem)] font-extrabold tracking-tight mb-3">
            4 bước xác minh &amp; nhận lại đồ
          </h2>
          <p className="text-[0.9375rem] text-[var(--text-secondary)] max-w-[50ch] mx-auto leading-relaxed">
            Quy trình khép kín giúp hạn chế tối đa rủi ro nhận hàng giả mạo danh
          </p>
        </div>

        {/* Steps grid */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-0 relative">
          {/* Connector line behind cards */}
          <div className="hidden md:block absolute top-9 left-[12.5%] right-[12.5%] h-0.5 bg-gradient-to-r from-[var(--teal-300)] to-[var(--teal-500)] z-0 opacity-35" />

          {STEPS.map((step, i) => {
            const Icon = step.icon;
            return (
              <div
                key={i}
                className="animate-fadeInUp group relative z-10 px-6 py-7 bg-[var(--bg-surface)] border border-[var(--border)] transition-all duration-250 hover:bg-[var(--teal-50)] hover:-translate-y-1 hover:z-20 hover:shadow-md hover:border-[#1e6b6b4d] hover:rounded-xl md:border-r-0 md:last:border-r"
                style={{
                  animationDelay: `${i * 0.09}s`,
                  borderRadius: i === 0 ? 'var(--radius-xl) 0 0 var(--radius-xl)' : i === 3 ? '0 var(--radius-xl) var(--radius-xl) 0' : '0',
                }}
              >
                {/* Step icon circle */}
                <div className="w-13 h-13 rounded-full bg-[var(--teal-50)] border-2 border-[#1e6b6b26] flex items-center justify-center mb-5 text-[var(--accent)] group-hover:bg-white group-hover:border-[var(--accent)] transition-colors">
                  <Icon size={22} strokeWidth={1.8} />
                </div>

                {/* Label */}
                <div className="text-[0.68rem] font-bold uppercase tracking-wide text-[var(--accent)] mb-1.5">
                  {step.label}
                </div>

                <h3 className="font-[var(--font-display)] text-base font-bold mb-2 text-[var(--text-primary)]">
                  {step.title}
                </h3>
                <p className="text-[0.8375rem] text-[var(--text-secondary)] leading-relaxed">
                  {step.desc}
                </p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
