import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Lock, Mail, Shield, ArrowRight, ArrowLeft, KeyRound, AlertCircle, CheckCircle } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function LoginForm() {
  const {
    login,
    requestChangePasswordOtp,
    confirmChangePassword,
    forgotPassword,
    resetPassword,
  } = useAuth();
  const navigate = useNavigate();

  // Mode: 'credentials' | 'change_pass_req' | 'change_pass_confirm' | 'forgot_email' | 'forgot_reset'
  const [mode, setMode] = useState('credentials');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [remember, setRemember] = useState(true);

  // OTP & Change/Reset password state
  const [otp, setOtp] = useState('');
  const [otpHint, setOtpHint] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Feedback messages
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [loading, setLoading] = useState(false);

  const handleQuickFill = (testEmail, testPass) => {
    setEmail(testEmail);
    setPassword(testPass);
    setError('');
  };

  // 1. Submit Login Credentials
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    if (!email.trim() || !password) {
      setError('Vui lòng nhập đầy đủ Tài khoản/Email và Mật khẩu');
      return;
    }

    setLoading(true);
    try {
      const res = await login(email.trim(), password);
      const userRole = String(res?.account?.role || '').toUpperCase();
      if (userRole === 'ADMIN' || userRole === 'MANAGER') {
        navigate('/admin');
      } else {
        navigate('/');
      }
    } catch (err) {
      setError(err?.message || 'Tài khoản hoặc mật khẩu không chính xác');
    } finally {
      setLoading(false);
    }
  };

  // 2. Request Change Password OTP (for khanhtrinh1204@gmail.com)
  const handleRequestChangePassOtp = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    if (!email.trim() || !currentPassword) {
      setError('Vui lòng nhập Email và Mật khẩu hiện tại');
      return;
    }

    setLoading(true);
    try {
      const res = await requestChangePasswordOtp(email.trim(), currentPassword);
      setOtpHint(res?.note || '');
      setSuccessMsg(res?.note || `Đã gửi mã OTP 6 số tới ${email.trim()}`);
      setMode('change_pass_confirm');
    } catch (err) {
      setError(err?.message || 'Không thể gửi mã OTP đổi mật khẩu');
    } finally {
      setLoading(false);
    }
  };

  // 3. Confirm Change Password with OTP
  const handleConfirmChangePassword = async (e) => {
    e.preventDefault();
    setError('');
    if (!otp.trim() || otp.trim().length !== 6) {
      setError('Vui lòng nhập đúng mã OTP 6 chữ số');
      return;
    }
    if (newPassword.length < 3) {
      setError('Mật khẩu mới phải từ 3 ký tự trở lên');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp');
      return;
    }

    setLoading(true);
    try {
      await confirmChangePassword(email.trim(), otp.trim(), newPassword);
      setSuccessMsg('Đổi mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới.');
      setPassword(newPassword);
      setOtp('');
      setNewPassword('');
      setConfirmPassword('');
      setMode('credentials');
    } catch (err) {
      setError(err?.message || 'Xác thực OTP hoặc đổi mật khẩu thất bại');
    } finally {
      setLoading(false);
    }
  };

  // 4. Request Forgot Password OTP
  const handleForgotEmailSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    if (!email.trim()) {
      setError('Vui lòng nhập email tài khoản của bạn');
      return;
    }

    setLoading(true);
    try {
      const res = await forgotPassword(email.trim());
      setOtpHint(res?.note || '');
      setSuccessMsg(res?.note || 'Mã OTP khôi phục mật khẩu đã được gửi đến email!');
      setMode('forgot_reset');
    } catch (err) {
      setError(err?.message || 'Không thể gửi yêu cầu khôi phục mật khẩu');
    } finally {
      setLoading(false);
    }
  };

  // 5. Submit Reset Password
  const handleResetPasswordSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!otp.trim()) {
      setError('Vui lòng nhập mã OTP');
      return;
    }
    if (newPassword.length < 3) {
      setError('Mật khẩu mới phải từ 3 ký tự trở lên');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp');
      return;
    }

    setLoading(true);
    try {
      await resetPassword(email.trim(), otp.trim(), newPassword);
      setSuccessMsg('Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại bằng mật khẩu mới');
      setMode('credentials');
      setPassword(newPassword);
      setOtp('');
    } catch (err) {
      setError(err?.message || 'Khôi phục mật khẩu thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col gap-5">
      {/* Notifications / Alerts */}
      {error && (
        <div
          style={{
            padding: '10px 14px',
            borderRadius: '8px',
            background: 'rgba(239, 68, 68, 0.1)',
            color: '#dc2626',
            border: '1px solid rgba(239, 68, 68, 0.2)',
            fontSize: '0.85rem',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <AlertCircle size={16} className="shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {successMsg && (
        <div
          style={{
            padding: '10px 14px',
            borderRadius: '8px',
            background: 'rgba(16, 185, 129, 0.1)',
            color: '#059669',
            border: '1px solid rgba(16, 185, 129, 0.2)',
            fontSize: '0.85rem',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <CheckCircle size={16} className="shrink-0" />
          <span>{successMsg}</span>
        </div>
      )}

      {/* ── MODE 1: CREDENTIALS (EMAIL & PASSWORD) ── */}
      {mode === 'credentials' && (
        <form onSubmit={handleLoginSubmit} className="flex flex-col gap-4">
          {/* Quick tester helper */}
          <div
            style={{
              fontSize: '0.75rem',
              padding: '8px 12px',
              background: 'var(--bg-canvas)',
              borderRadius: '6px',
              border: '1px dashed var(--border)',
              color: 'var(--text-muted)',
            }}
          >
            <span style={{ fontWeight: 600 }}>Điền nhanh tài khoản mẫu: </span>
            <button
              type="button"
              onClick={() => handleQuickFill('admin', '123')}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--accent)',
                cursor: 'pointer',
                padding: '0 4px',
                textDecoration: 'underline',
                fontWeight: 600,
              }}
            >
              Admin (admin / 123)
            </button>
            ·
            <button
              type="button"
              onClick={() => handleQuickFill('khanhtrinh1204@gmail.com', '123')}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--accent)',
                cursor: 'pointer',
                padding: '0 4px',
                textDecoration: 'underline',
                fontWeight: 600,
              }}
            >
              Manager (khanhtrinh1204@gmail.com / 123)
            </button>
          </div>

          {/* Email / Username */}
          <div className="input-group">
            <label className="input-label" htmlFor="login-email">
              Email hoặc Tên tài khoản (Dành cho mọi Role)
            </label>
            <div className="input-icon-wrap">
              <span className="input-icon">
                <Mail size={15} />
              </span>
              <input
                id="login-email"
                type="text"
                className="input"
                placeholder="VD: admin hoặc khanhtrinh1204@gmail.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="username"
                required
              />
            </div>
          </div>

          {/* Password */}
          <div className="input-group">
            <div className="flex justify-between items-center">
              <label className="input-label" htmlFor="login-password">
                Mật khẩu
              </label>
              <div className="flex items-center gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setCurrentPassword(password || '123');
                    if (!email) setEmail('khanhtrinh1204@gmail.com');
                    setMode('change_pass_req');
                    setError('');
                    setSuccessMsg('');
                  }}
                  className="text-[0.8125rem] text-[var(--accent)] font-semibold hover:underline bg-transparent border-none cursor-pointer p-0"
                >
                  Đổi mật khẩu (OTP Gmail)
                </button>
                <span className="text-gray-300">|</span>
                <button
                  type="button"
                  onClick={() => {
                    setMode('forgot_email');
                    setError('');
                    setSuccessMsg('');
                  }}
                  className="text-[0.8125rem] text-[var(--accent)] hover:underline bg-transparent border-none cursor-pointer p-0"
                >
                  Quên mật khẩu?
                </button>
              </div>
            </div>
            <div className="input-icon-wrap relative">
              <span className="input-icon">
                <Lock size={15} />
              </span>
              <input
                id="login-password"
                type={showPass ? 'text' : 'password'}
                className="input pr-11"
                placeholder="Nhập mật khẩu"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
              />
              <button
                type="button"
                onClick={() => setShowPass(!showPass)}
                className="absolute right-3 top-1/2 -translate-y-1/2 bg-transparent border-none cursor-pointer text-[var(--text-muted)] flex items-center p-1"
                aria-label={showPass ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              >
                {showPass ? <EyeOff size={15} /> : <Eye size={15} />}
              </button>
            </div>
          </div>

          {/* Remember */}
          <div className="flex items-center justify-between">
            <label className="checkbox-wrap">
              <input
                type="checkbox"
                id="remember-me"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
              />
              Ghi nhớ đăng nhập
            </label>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              ADMIN & MANAGER tự động vào Dashboard
            </span>
          </div>

          {/* Submit */}
          <button
            type="submit"
            className="btn btn-primary btn-full btn-lg"
            disabled={loading}
            id="login-submit-btn"
          >
            {loading ? 'Đang đăng nhập...' : 'Đăng nhập vào hệ thống'}
            <ArrowRight size={16} />
          </button>
        </form>
      )}

      {/* ── MODE 2: REQUEST CHANGE PASSWORD OTP VIA GMAIL ── */}
      {mode === 'change_pass_req' && (
        <form onSubmit={handleRequestChangePassOtp} className="flex flex-col gap-4">
          <div style={{ textAlign: 'center', marginBottom: '4px' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0 0 4px 0' }}>
              Đổi mật khẩu xác thực OTP qua Gmail
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', margin: 0 }}>
              Nhập Gmail và mật khẩu hiện tại để nhận mã OTP 6 số gửi về hộp thư
            </p>
          </div>

          <div className="input-group">
            <label className="input-label">Email (Gmail)</label>
            <input
              type="text"
              className="input"
              placeholder="khanhtrinh1204@gmail.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="input-group">
            <label className="input-label">Mật khẩu hiện tại</label>
            <input
              type="password"
              className="input"
              placeholder="Nhập mật khẩu hiện tại (VD: 123)"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
            {loading ? 'Đang gửi OTP...' : 'Gửi mã OTP về Gmail'}
            <ArrowRight size={16} />
          </button>

          <button
            type="button"
            onClick={() => {
              setMode('credentials');
              setError('');
            }}
            className="btn btn-ghost btn-full"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
          >
            <ArrowLeft size={15} />
            Quay lại đăng nhập
          </button>
        </form>
      )}

      {/* ── MODE 3: CONFIRM CHANGE PASSWORD WITH OTP ── */}
      {mode === 'change_pass_confirm' && (
        <form onSubmit={handleConfirmChangePassword} className="flex flex-col gap-4">
          <div style={{ textAlign: 'center', marginBottom: '4px' }}>
            <div
              style={{
                width: '44px',
                height: '44px',
                borderRadius: '50%',
                background: 'rgba(30, 107, 107, 0.1)',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'var(--accent)',
                marginBottom: '6px',
              }}
            >
              <KeyRound size={22} />
            </div>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0 0 4px 0' }}>
              Xác nhận OTP & Đặt mật khẩu mới
            </h3>
            <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', margin: 0 }}>
              Mã OTP 6 chữ số đã được gửi tới <b>{email}</b>
            </p>
            {otpHint && (
              <div
                style={{
                  marginTop: '8px',
                  padding: '6px 10px',
                  background: 'rgba(16, 185, 129, 0.08)',
                  border: '1px solid rgba(16, 185, 129, 0.25)',
                  borderRadius: '6px',
                  fontSize: '0.78rem',
                  color: '#047857',
                }}
              >
                {otpHint}
              </div>
            )}
          </div>

          <div className="input-group">
            <label className="input-label">Mã OTP (6 chữ số)</label>
            <input
              type="text"
              maxLength={6}
              className="input"
              style={{ letterSpacing: '4px', fontSize: '1.1rem', fontWeight: 700, textAlign: 'center' }}
              placeholder="------"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              required
            />
          </div>

          <div className="input-group">
            <label className="input-label">Mật khẩu mới</label>
            <input
              type="password"
              className="input"
              placeholder="Nhập mật khẩu mới"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
          </div>

          <div className="input-group">
            <label className="input-label">Xác nhận mật khẩu mới</label>
            <input
              type="password"
              className="input"
              placeholder="Nhập lại mật khẩu mới"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
            {loading ? 'Đang lưu...' : 'Xác nhận đổi mật khẩu'}
            <ArrowRight size={16} />
          </button>

          <button
            type="button"
            onClick={() => {
              setMode('credentials');
              setError('');
            }}
            className="btn btn-ghost btn-full"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
          >
            <ArrowLeft size={15} />
            Quay lại đăng nhập
          </button>
        </form>
      )}

      {/* ── MODE 4: FORGOT PASSWORD - EMAIL ── */}
      {mode === 'forgot_email' && (
        <form onSubmit={handleForgotEmailSubmit} className="flex flex-col gap-4">
          <div style={{ textAlign: 'center', marginBottom: '8px' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0 0 4px 0' }}>Khôi phục mật khẩu</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', margin: 0 }}>
              Nhập email tài khoản của bạn để nhận mã OTP đặt lại mật khẩu
            </p>
          </div>

          <div className="input-group">
            <label className="input-label" htmlFor="forgot-email">
              Email tài khoản
            </label>
            <div className="input-icon-wrap">
              <span className="input-icon">
                <Mail size={15} />
              </span>
              <input
                id="forgot-email"
                type="text"
                className="input"
                placeholder="VD: khanhtrinh1204@gmail.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
          </div>

          <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
            {loading ? 'Đang gửi mã...' : 'Gửi mã OTP khôi phục'}
            <ArrowRight size={16} />
          </button>

          <button
            type="button"
            onClick={() => {
              setMode('credentials');
              setError('');
            }}
            className="btn btn-ghost btn-full"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
          >
            <ArrowLeft size={15} />
            Quay lại đăng nhập
          </button>
        </form>
      )}

      {/* ── MODE 5: FORGOT PASSWORD - RESET WITH OTP ── */}
      {mode === 'forgot_reset' && (
        <form onSubmit={handleResetPasswordSubmit} className="flex flex-col gap-4">
          <div style={{ textAlign: 'center', marginBottom: '8px' }}>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0 0 4px 0' }}>Đặt mật khẩu mới</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', margin: 0 }}>
              Nhập mã OTP đã gửi đến <b>{email}</b> và thiết lập mật khẩu mới
            </p>
          </div>

          <div className="input-group">
            <label className="input-label" htmlFor="reset-otp">
              Mã OTP (6 chữ số)
            </label>
            <input
              id="reset-otp"
              type="text"
              maxLength={6}
              className="input"
              style={{ letterSpacing: '4px', fontSize: '1.1rem', fontWeight: 700, textAlign: 'center' }}
              placeholder="------"
              value={otp}
              onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
              required
            />
          </div>

          <div className="input-group">
            <label className="input-label" htmlFor="reset-new-pass">
              Mật khẩu mới
            </label>
            <input
              id="reset-new-pass"
              type="password"
              className="input"
              placeholder="Tối thiểu 3 ký tự"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
          </div>

          <div className="input-group">
            <label className="input-label" htmlFor="reset-confirm-pass">
              Xác nhận mật khẩu mới
            </label>
            <input
              id="reset-confirm-pass"
              type="password"
              className="input"
              placeholder="Nhập lại mật khẩu mới"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
            {loading ? 'Đang lưu...' : 'Đặt lại mật khẩu mới'}
            <ArrowRight size={16} />
          </button>

          <button
            type="button"
            onClick={() => {
              setMode('credentials');
              setError('');
            }}
            className="btn btn-ghost btn-full"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
          >
            <ArrowLeft size={15} />
            Hủy và quay lại
          </button>
        </form>
      )}

      {/* Security notice footer */}
      <div className="security-notice mt-2">
        <Shield size={13} className="text-[var(--accent)] shrink-0" />
        <span>Bảo mật danh tính & kiểm tra phiên JWT</span>
        <div className="ml-auto security-links">
          <a href="#">Điều khoản</a>
          <span>·</span>
          <a href="#">Bảo mật FPT</a>
        </div>
      </div>
    </div>
  );
}
