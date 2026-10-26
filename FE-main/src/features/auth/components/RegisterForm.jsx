import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Lock, User, Mail, Phone, Shield, ArrowRight } from 'lucide-react';

const GoogleIcon = () => (
  <svg width="16" height="16" viewBox="0 0 48 48">
    <path fill="#FFC107" d="M43.6 20H24v8h11.3C33.7 33.7 29.3 37 24 37c-7.2 0-13-5.8-13-13s5.8-13 13-13c3.1 0 5.9 1.1 8.1 2.9l6.4-6.4C34.6 4.1 29.6 2 24 2 11.8 2 2 11.8 2 24s9.8 22 22 22c11 0 21-8 21-22 0-1.3-.2-2.7-.4-4z" />
    <path fill="#FF3D00" d="m6.3 14.7 7.1 5.2C15.1 16.4 19.3 14 24 14c3.1 0 5.9 1.1 8.1 2.9l6.4-6.4C34.6 4.1 29.6 2 24 2 16.3 2 9.7 7.4 6.3 14.7z" />
    <path fill="#4CAF50" d="M24 46c5.4 0 10.3-1.9 14.1-5l-6.5-5.5C29.6 37 26.9 38 24 38c-5.2 0-9.6-3.3-11.2-8l-7.1 5.5C9.8 42.6 16.3 46 24 46z" />
    <path fill="#1565C0" d="M43.6 20H24v8h11.3c-.8 2.2-2.2 4-4 5.5l6.5 5.5C41.6 36.1 44 30.5 44 24c0-1.3-.2-2.7-.4-4z" />
  </svg>
);

export default function RegisterForm({ onSwitch }) {
  const [showPass, setShowPass] = useState(false);
  const [values, setValues] = useState({
    fullName: '',
    username: '',
    email: '',
    phone: '',
    password: '',
    confirm: '',
  });
  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();
    navigate('/');
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      {/* Full Name */}
      <div className="input-group">
        <label className="input-label" htmlFor="reg-fullname">Họ và tên đầy đủ</label>
        <div className="input-icon-wrap">
          <span className="input-icon"><User size={15} /></span>
          <input
            id="reg-fullname"
            type="text"
            className="input"
            placeholder="Nguyễn Văn An"
            value={values.fullName}
            onChange={e => setValues({ ...values, fullName: e.target.value })}
          />
        </div>
      </div>

      {/* Username + Phone side-by-side */}
      <div className="grid grid-cols-2 gap-3">
        <div className="input-group">
          <label className="input-label" htmlFor="reg-username">Tên đăng nhập</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><User size={15} /></span>
            <input
              id="reg-username"
              type="text"
              className="input"
              placeholder="nguyenvanan"
              value={values.username}
              onChange={e => setValues({ ...values, username: e.target.value })}
            />
          </div>
        </div>
        <div className="input-group">
          <label className="input-label" htmlFor="reg-phone">Số điện thoại</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><Phone size={15} /></span>
            <input
              id="reg-phone"
              type="tel"
              className="input"
              placeholder="0912 345 678"
              value={values.phone}
              onChange={e => setValues({ ...values, phone: e.target.value })}
            />
          </div>
        </div>
      </div>

      {/* Email */}
      <div className="input-group">
        <label className="input-label" htmlFor="reg-email">Email liên hệ</label>
        <div className="input-icon-wrap">
          <span className="input-icon"><Mail size={15} /></span>
          <input
            id="reg-email"
            type="email"
            className="input"
            placeholder="an.nguyen@gmail.com"
            value={values.email}
            onChange={e => setValues({ ...values, email: e.target.value })}
          />
        </div>
      </div>

      {/* Password */}
      <div className="input-group">
        <label className="input-label" htmlFor="reg-password">Mật khẩu</label>
        <div className="input-icon-wrap relative">
          <span className="input-icon"><Lock size={15} /></span>
          <input
            id="reg-password"
            type={showPass ? 'text' : 'password'}
            className="input pr-11"
            placeholder="Tối thiểu 8 ký tự"
            value={values.password}
            onChange={e => setValues({ ...values, password: e.target.value })}
          />
          <button
            type="button"
            onClick={() => setShowPass(!showPass)}
            className="absolute right-3 top-1/2 -translate-y-1/2 bg-transparent border-none cursor-pointer text-[var(--text-muted)] flex items-center"
          >
            {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
          </button>
        </div>
      </div>

      {/* Confirm password */}
      <div className="input-group">
        <label className="input-label" htmlFor="reg-confirm">Xác nhận mật khẩu</label>
        <div className="input-icon-wrap">
          <span className="input-icon"><Lock size={15} /></span>
          <input
            id="reg-confirm"
            type="password"
            className="input"
            placeholder="Nhập lại mật khẩu"
            value={values.confirm}
            onChange={e => setValues({ ...values, confirm: e.target.value })}
          />
        </div>
      </div>

      {/* Terms */}
      <label className="checkbox-wrap text-[0.8125rem]">
        <input type="checkbox" id="reg-terms" required />
        <span>
          Tôi đồng ý với{' '}
          <a href="#" className="text-[var(--accent)] no-underline">điều khoản sử dụng</a>
          {' '}và{' '}
          <a href="#" className="text-[var(--accent)] no-underline">chính sách bảo mật</a>
          {' '}của Sagasu
        </span>
      </label>

      {/* Submit */}
      <button type="submit" className="btn btn-primary btn-full btn-lg" id="register-submit-btn">
        Tạo Tài Khoản Mới
        <ArrowRight size={16} />
      </button>

      {/* Divider */}
      <div className="divider">Hoặc đăng nhập nhanh bằng danh tính</div>

      {/* Social */}
      <div className="flex flex-col gap-3">
        <button type="button" className="btn btn-social" id="register-google-btn">
          <GoogleIcon />
          Tài khoản Google
        </button>
      </div>

      {/* Switch to Login */}
      <div className="text-center text-xs text-[var(--text-secondary)] pt-1">
        Đã có tài khoản?{' '}
        <button
          type="button"
          onClick={onSwitch}
          className="text-[var(--accent)] font-semibold hover:underline bg-transparent border-none cursor-pointer p-0"
        >
          Đăng nhập ngay
        </button>
      </div>

      {/* Security */}
      <div className="security-notice">
        <Shield size={13} className="text-[var(--accent)] shrink-0" />
        <span>Bảo mật an toàn</span>
        <div className="ml-auto security-links">
          <a href="#">Chính sách bảo mật</a>
        </div>
      </div>
    </form>
  );
}
