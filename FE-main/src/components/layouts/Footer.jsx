import { Link } from 'react-router-dom';
import { MapPin, Mail, Phone, Share2, Play, Shield } from 'lucide-react';
import SagasuLogo from '@/assets/Sagasu_logo_white.svg';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="container">
        <div className="footer-grid">
          {/* Brand */}
          <div>
            <Link to="/" className="inline-block mb-4 group" aria-label="Sagasu">
              <img
                src={SagasuLogo}
                alt="Sagasu"
                className="h-14 sm:h-16 w-auto object-contain transition-transform duration-200 group-hover:scale-105"
              />
            </Link>
            <p className="footer-desc">
              Nền tảng vì cộng đồng — số hóa toàn diện quy trình tiếp nhận, xác minh
              quyền sở hữu và bàn giao an toàn vật phẩm thất lạc trong học đường và đô thị.
            </p>
            <div style={{ display: 'flex', gap: '12px' }}>
              {[Share2, Play, Mail].map((Icon, i) => (
                <a
                  key={i}
                  href="#"
                  style={{
                    width: 36, height: 36, borderRadius: '50%',
                    background: 'rgba(255,255,255,0.08)',
                    border: '1px solid rgba(255,255,255,0.12)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: 'rgba(255,255,255,0.6)',
                    transition: 'all 200ms',
                    textDecoration: 'none'
                  }}
                  onMouseEnter={e => { e.currentTarget.style.background = 'rgba(94,196,196,0.2)'; e.currentTarget.style.color = '#5ec4c4'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; e.currentTarget.style.color = 'rgba(255,255,255,0.6)'; }}
                >
                  <Icon size={15} />
                </a>
              ))}
            </div>
          </div>

          {/* Nền tảng */}
          <div>
            <div className="footer-col-title">Nền tảng</div>
            <ul className="footer-links">
              <li><a href="#">Tìm đồ thất lạc</a></li>
              <li><a href="#">Bản đồ điểm nhận</a></li>
              <li><a href="#">AI Smart Match</a></li>
              <li><a href="#">Xác thực 2 lớp (2FA)</a></li>
              <li><a href="#">Quy trình an toàn</a></li>
            </ul>
          </div>

          {/* Hỗ trợ */}
          <div>
            <div className="footer-col-title">Hỗ trợ</div>
            <ul className="footer-links">
              <li><a href="#">Hướng dẫn sử dụng</a></li>
              <li><a href="#">Câu hỏi thường gặp</a></li>
              <li><a href="#">Báo cáo sự cố</a></li>
              <li><a href="#">Liên hệ ban quản trị</a></li>
              <li><a href="#">Điều khoản sử dụng</a></li>
            </ul>
          </div>

          {/* Liên hệ */}
          <div>
            <div className="footer-col-title">Liên hệ</div>
            <ul className="footer-links" style={{ gap: '12px' }}>
              <li>
                <a href="#" style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                  <MapPin size={13} style={{ marginTop: 2, flexShrink: 0 }} />
                  <span>Đại học FPT Hà Nội, Khu Công nghệ cao Hòa Lạc, Km29 Đại lộ Thăng Long, Thạch Thất, Hà Nội</span>
                </a>
              </li>
              <li>
                <a href="mailto:support@sagasu.vn" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Mail size={13} style={{ flexShrink: 0 }} />
                  support@sagasu.vn
                </a>
              </li>
              <li>
                <a href="tel:19001234" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Phone size={13} style={{ flexShrink: 0 }} />
                  1900 1234 (7:00 – 22:00)
                </a>
              </li>
            </ul>
          </div>
        </div>

        <div className="footer-bottom">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Shield size={13} style={{ color: '#FF9F1C' }} />
            <span>© 2026 Sagasu. Nền tảng tìm kiếm & bàn giao vật phẩm thất lạc.</span>
          </div>
          <div className="security-links">
            <a href="#">Điều khoản</a>
            <a href="#">Chính sách bảo mật</a>

          </div>
        </div>
      </div>
    </footer>
  );
}
