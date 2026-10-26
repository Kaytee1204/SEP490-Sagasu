import React from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck, Lock, Search, Sparkles, ArrowRight, UserCheck, CheckCircle2, Building2 } from 'lucide-react';
import SagasuLogoColor from '@/assets/Sagasu_logo_color.svg';

export default function LandingLoginPage() {
  return (
    <div className="flex-1 flex flex-col bg-[var(--bg-canvas)]">
      {/* ── TOP HERO BANNER ── */}
      <section className="relative overflow-hidden py-16 sm:py-24 border-b border-[var(--border)] bg-gradient-to-b from-teal-50/60 to-white">
        <div className="container mx-auto px-4 sm:px-6 max-w-6xl">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
            
            {/* Left Column: Intro */}
            <div className="lg:col-span-7 flex flex-col items-start text-left">
              <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-teal-100/70 border border-teal-200 text-teal-800 text-xs font-semibold uppercase tracking-wider mb-6">
                <Sparkles size={14} className="text-teal-600" />
                Hệ thống Trao trả Đồ Thất lạc · Đại học FPT
              </div>

              <h1 className="font-[var(--font-display)] text-3xl sm:text-5xl font-extrabold text-[var(--text-primary)] leading-tight tracking-tight mb-5">
                Bảo vệ thông tin,<br />
                <span className="text-[var(--accent)]">kết nối trao trả</span> an toàn.
              </h1>

              <p className="text-base sm:text-lg text-[var(--text-secondary)] leading-relaxed mb-8 max-w-xl">
                Cổng dịch vụ tìm kiếm và bàn giao tài sản thất lạc SAGASU ứng dụng nhận diện thông minh, 
                đảm bảo xác minh quyền sở hữu minh bạch và bảo vệ quyền riêng tư cho toàn bộ sinh viên, cán bộ giảng viên.
              </p>

              <div className="flex flex-wrap items-center gap-4">
                <Link
                  to="/dang-nhap"
                  className="btn btn-primary btn-lg shadow-lg shadow-teal-700/20 px-8 py-3.5 text-base font-bold flex items-center gap-2"
                >
                  <Lock size={18} />
                  Đăng Nhập Để Vào Hệ Thống
                  <ArrowRight size={18} />
                </Link>

                <Link
                  to="/tim-kiem"
                  className="btn btn-ghost btn-lg border border-[var(--border-strong)] px-6 py-3.5 text-base font-medium"
                >
                  <Search size={18} />
                  Tra cứu công khai
                </Link>
              </div>

              {/* Security badges */}
              <div className="grid grid-cols-3 gap-4 pt-8 mt-8 border-t border-[var(--border)] w-full max-w-lg">
                <div className="flex items-center gap-2 text-xs font-semibold text-[var(--text-secondary)]">
                  <ShieldCheck size={16} className="text-teal-600" />
                  Xác minh 2FA / OTP
                </div>
                <div className="flex items-center gap-2 text-xs font-semibold text-[var(--text-secondary)]">
                  <UserCheck size={16} className="text-teal-600" />
                  Tài khoản định danh
                </div>
                <div className="flex items-center gap-2 text-xs font-semibold text-[var(--text-secondary)]">
                  <Building2 size={16} className="text-teal-600" />
                  Tổ chức FPT Edu
                </div>
              </div>
            </div>

            {/* Right Column: Require Login Portal Card */}
            <div className="lg:col-span-5">
              <div className="bg-white rounded-2xl shadow-xl border border-[var(--border)] p-6 sm:p-8 relative">
                <div className="flex items-center justify-between pb-5 border-b border-[var(--border)] mb-6">
                  <div className="flex items-center gap-3">
                    <img src={SagasuLogoColor} alt="Sagasu" className="h-9 w-auto" />
                    <div>
                      <div className="text-xs uppercase tracking-wider font-bold text-teal-700">Cổng Dịch Vụ</div>
                      <div className="text-sm font-semibold text-gray-800">Yêu Cầu Xác Thực</div>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 bg-amber-50 text-amber-700 border border-amber-200 text-xs font-bold rounded-md">
                    Cần Đăng Nhập
                  </span>
                </div>

                <div className="space-y-4 mb-6">
                  <p className="text-sm text-gray-600 leading-relaxed">
                    Để xem danh sách chi tiết các món đồ nhặt được, gửi yêu cầu nhận lại (Claim), đăng tin thất lạc hoặc quản trị hệ thống, bạn cần đăng nhập tài khoản.
                  </p>

                  <div className="bg-gray-50 rounded-xl p-4 border border-gray-100 space-y-2.5 text-xs text-gray-600">
                    <div className="flex items-center gap-2 font-medium text-gray-800">
                      <CheckCircle2 size={15} className="text-teal-600 shrink-0" />
                      Đăng nhập bằng Email FPT Edu hoặc tài khoản nội bộ
                    </div>
                    <div className="flex items-center gap-2 font-medium text-gray-800">
                      <CheckCircle2 size={15} className="text-teal-600 shrink-0" />
                      Nhận mã xác thực OTP gửi trực tiếp về hòm thư
                    </div>
                    <div className="flex items-center gap-2 font-medium text-gray-800">
                      <CheckCircle2 size={15} className="text-teal-600 shrink-0" />
                      Phân quyền nghiệp vụ theo Khoa & Phòng ban
                    </div>
                  </div>
                </div>

                <Link
                  to="/dang-nhap"
                  className="btn btn-primary w-full py-3.5 text-center font-bold flex items-center justify-center gap-2 mb-4"
                >
                  <Lock size={16} />
                  Đăng Nhập Ngay
                </Link>

                {/* Test accounts hint */}
                <div className="text-[0.75rem] text-gray-500 bg-teal-50/60 p-3 rounded-lg border border-teal-100 text-center">
                  <span className="font-semibold text-teal-800">Tài khoản thử nghiệm:</span> admin / 123 (Admin), khanhtrinh1204@gmail.com / 123 (Manager)
                </div>
              </div>
            </div>

          </div>
        </div>
      </section>

      {/* ── 3 STEP PROCESS PREVIEW ── */}
      <section className="py-16 bg-white">
        <div className="container mx-auto px-4 max-w-5xl text-center">
          <h2 className="text-2xl sm:text-3xl font-extrabold text-[var(--text-primary)] mb-3">
            Quy trình tiếp nhận & trao trả tài sản chuẩn mực
          </h2>
          <p className="text-sm sm:text-base text-[var(--text-secondary)] mb-12 max-w-2xl mx-auto">
            Hệ thống Sagasu đảm bảo tính khách quan và an toàn thông tin tối đa cho người tìm thấy lẫn người nhận lại đồ.
          </p>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 text-left">
            <div className="p-6 rounded-xl border border-[var(--border)] bg-gray-50/50 hover:border-teal-400 transition-colors">
              <div className="w-10 h-10 rounded-lg bg-teal-600 text-white font-bold flex items-center justify-center mb-4">
                1
              </div>
              <h3 className="font-bold text-lg text-gray-900 mb-2">Đăng tin & So khớp AI</h3>
              <p className="text-sm text-gray-600 leading-relaxed">
                Người nhặt hoặc người mất đăng tin với hình ảnh và thông tin đặc điểm. Thuật toán AI tự động so khớp đối chiếu.
              </p>
            </div>

            <div className="p-6 rounded-xl border border-[var(--border)] bg-gray-50/50 hover:border-teal-400 transition-colors">
              <div className="w-10 h-10 rounded-lg bg-teal-600 text-white font-bold flex items-center justify-center mb-4">
                2
              </div>
              <h3 className="font-bold text-lg text-gray-900 mb-2">Xác minh quyền sở hữu</h3>
              <p className="text-sm text-gray-600 leading-relaxed">
                Chủ sở hữu trả lời câu hỏi bí mật về đặc điểm nhận dạng hoặc xuất trình giấy tờ liên quan để cán bộ kiểm duyệt xác minh.
              </p>
            </div>

            <div className="p-6 rounded-xl border border-[var(--border)] bg-gray-50/50 hover:border-teal-400 transition-colors">
              <div className="w-10 h-10 rounded-lg bg-teal-600 text-white font-bold flex items-center justify-center mb-4">
                3
              </div>
              <h3 className="font-bold text-lg text-gray-900 mb-2">Bàn giao tại quầy an toàn</h3>
              <p className="text-sm text-gray-600 leading-relaxed">
                Hai bên hẹn gặp tại quầy tiếp nhận chính thức của Trường dưới sự chứng kiến của Cán bộ (Officer) và ký biên bản điện tử.
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
