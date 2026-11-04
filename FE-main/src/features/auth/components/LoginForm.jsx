// src/features/auth/components/LoginForm.jsx
import React, { useState } from 'react';
import { ShieldCheck, ArrowLeft, RefreshCw, Eye, EyeOff, KeyRound, CheckCircle2, Lock } from 'lucide-react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { authApi } from '../api/authApi';

export default function LoginForm({ initialMode = 'login' }) {
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const isForgotQuery = queryParams.get('mode') === 'forgot';

  // Mode: 'login' | 'otp' | 'forgot_email' | 'forgot_otp' | 'forgot_new_password'
  const [mode, setMode] = useState(isForgotQuery ? 'forgot_email' : initialMode);

  // Login states
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(true);

  // OTP Login states (nếu luồng đăng nhập yêu cầu OTP)
  const [otpCode, setOtpCode] = useState('');
  const [pendingEmail, setPendingEmail] = useState('');
  const [otpMessage, setOtpMessage] = useState('');

  // Forgot password & Unlock account states
  const [forgotEmail, setForgotEmail] = useState('');
  const [forgotOtp, setForgotOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [forgotMessage, setForgotMessage] = useState('');

  // General feedback states
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const { login, verifyOtp } = useAuth();
  const navigate = useNavigate();

  // ── XỬ LÝ KẾT QUẢ ĐĂNG NHẬP ──
  const handleLoginResult = (result) => {
    if (result?.requiresOtp) {
      setMode('otp');
      setPendingEmail(result.email || email.trim());
      setOtpMessage(
        result.message ||
          `Mã xác thực OTP (6 chữ số) đã được gửi về email ${result.email || email.trim()}. Vui lòng nhập mã để hoàn tất đăng nhập.`
      );
      setOtpCode('');
      return;
    }

    const role = result?.account?.role;
    if (role === 'ADMIN' || role === 'MANAGER') {
      navigate('/admin', { replace: true });
    } else {
      navigate('/', { replace: true });
    }
  };

  // ── BẤM ĐĂNG NHẬP ──
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const trimmedInput = email.trim();
    if (!trimmedInput || !password) {
      setError('Vui lòng nhập đầy đủ Email và mật khẩu');
      return;
    }

    if (/\s/.test(trimmedInput)) {
      setError('Email không được chứa khoảng trắng');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (trimmedInput.toLowerCase() !== 'admin' && trimmedInput.toLowerCase() !== 'user' && !emailRegex.test(trimmedInput)) {
      setError('Vui lòng nhập địa chỉ Email hợp lệ (VD: example@gmail.com)');
      return;
    }

    setLoading(true);
    try {
      const result = await login(trimmedInput, password);
      handleLoginResult(result);
    } catch (err) {
      // Khi sai 5 lần liên tiếp: tài khoản bị khóa và BE tự động gửi OTP đến email
      if (err.code === 'ACC_403_LOCKED' || err.message?.includes('khóa') || err.message?.includes('5 lần')) {
        setForgotEmail(trimmedInput);
        setForgotOtp('');
        setForgotMessage(
          err.message ||
            'Tài khoản đã bị khóa do nhập sai mật khẩu 5 lần liên tiếp. Hệ thống đã gửi mã OTP đặt lại mật khẩu đến email của bạn. Vui lòng kiểm tra email và nhập OTP để mở khóa.'
        );
        setError('');
        setSuccess('');
        setMode('forgot_otp');
        return;
      }
      setError(err.message || 'Email hoặc mật khẩu không chính xác');
    } finally {
      setLoading(false);
    }
  };

  // ── BẤM XÁC THỰC OTP ĐĂNG NHẬP (USER/MFA) ──
  const handleVerifyLoginOtp = async (e) => {
    e.preventDefault();
    setError('');

    const cleanOtp = otpCode.trim();
    if (!/^[0-9]{6}$/.test(cleanOtp)) {
      setError('Vui lòng nhập đầy đủ mã OTP gồm đúng 6 chữ số');
      return;
    }

    setLoading(true);
    try {
      const result = await verifyOtp(pendingEmail, cleanOtp, 'LOGIN');
      const role = result?.account?.role;
      if (role === 'ADMIN' || role === 'MANAGER') {
        navigate('/admin', { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    } catch (err) {
      setError(err.message || 'Mã OTP không chính xác hoặc đã hết hạn');
    } finally {
      setLoading(false);
    }
  };

  // ── GỬI LẠI MÃ OTP ĐĂNG NHẬP ──
  const handleResendLoginOtp = async () => {
    if (!password) return;
    setError('');
    setLoading(true);
    try {
      const result = await login(pendingEmail || email.trim(), password);
      if (result?.requiresOtp) {
        setOtpMessage(result.message || 'Đã gửi lại mã OTP mới về email của bạn.');
      }
    } catch (err) {
      setError(err.message || 'Không thể gửi lại mã OTP');
    } finally {
      setLoading(false);
    }
  };

  // ── QUÊN MẬT KHẨU / MỞ KHÓA: BƯỚC 1 - GỬI YÊU CẦU OTP QUA EMAIL ──
  const handleRequestForgotOtp = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const trimmedEmail = (forgotEmail || '').trim();
    if (!trimmedEmail) {
      setError('Vui lòng nhập địa chỉ Email tài khoản của bạn');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (trimmedEmail.toLowerCase() !== 'admin' && trimmedEmail.toLowerCase() !== 'user' && !emailRegex.test(trimmedEmail)) {
      setError('Vui lòng nhập đúng định dạng Email (VD: example@gmail.com)');
      return;
    }

    setLoading(true);
    try {
      const res = await authApi.forgotPassword(trimmedEmail);
      const data = res?.data || res;
      setForgotMessage(
        data?.note ||
          `Mã xác thực OTP (hiệu lực 5 phút) đã được gửi tới email ${trimmedEmail}. Vui lòng kiểm tra hộp thư.`
      );
      setForgotOtp('');
      setNewPassword('');
      setConfirmPassword('');
      setMode('forgot_otp');
    } catch (err) {
      setError(err.message || 'Không tìm thấy tài khoản với email này hoặc lỗi hệ thống');
    } finally {
      setLoading(false);
    }
  };

  // ── GỬI LẠI MÃ OTP CHO QUÊN MẬT KHẨU / MỞ KHÓA ──
  const handleResendForgotOtp = async () => {
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      const res = await authApi.forgotPassword(forgotEmail.trim());
      const data = res?.data || res;
      setForgotMessage(
        data?.note || 'Đã gửi lại mã xác thực OTP mới về email của bạn (hiệu lực 5 phút).'
      );
    } catch (err) {
      setError(err.message || 'Không thể gửi lại mã OTP');
    } finally {
      setLoading(false);
    }
  };

  // ── QUÊN MẬT KHẨU / MỞ KHÓA: BƯỚC 2 - XÁC THỰC MÃ OTP (CHỈ KHI HỢP LỆ MỚI CHUYỂN BƯỚC) ──
  const handleVerifyResetOtp = async (e) => {
    e.preventDefault();
    setError('');

    const cleanOtp = forgotOtp.trim();
    if (!/^[0-9]{6}$/.test(cleanOtp)) {
      setError('Mã OTP phải gồm đúng 6 chữ số');
      return;
    }

    setLoading(true);
    try {
      await authApi.verifyResetOtp(forgotEmail.trim(), cleanOtp);
      // OTP hợp lệ! Cho phép chuyển sang bước đặt mật khẩu mới
      setError('');
      setNewPassword('');
      setConfirmPassword('');
      setMode('forgot_new_password');
    } catch (err) {
      // OTP sai hoặc hết hạn -> Không cho phép chuyển bước
      setError(err.message || 'Mã OTP không chính xác hoặc đã hết hạn (5 phút)');
    } finally {
      setLoading(false);
    }
  };

  // ── QUÊN MẬT KHẨU / MỞ KHÓA: BƯỚC 3 - ĐẶT MẬT KHẨU MỚI & MỞ KHÓA ──
  const handleConfirmResetPassword = async (e) => {
    e.preventDefault();
    setError('');

    const cleanOtp = forgotOtp.trim();
    if (!/^[0-9]{6}$/.test(cleanOtp)) {
      setError('Mã OTP không hợp lệ, vui lòng quay lại bước nhập OTP');
      return;
    }

    if (!newPassword) {
      setError('Vui lòng nhập mật khẩu mới');
      return;
    }

    if (newPassword.length < 8 || newPassword.length > 16 || /\s/.test(newPassword) || !/[^a-zA-Z0-9\s]/.test(newPassword)) {
      setError('Mật khẩu mới phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Mật khẩu xác nhận không trùng khớp với mật khẩu mới');
      return;
    }

    setLoading(true);
    try {
      await authApi.resetPassword(forgotEmail.trim(), cleanOtp, newPassword);
      setSuccess('Đặt lại mật khẩu và mở khóa tài khoản thành công! Vui lòng đăng nhập bằng mật khẩu mới.');
      setEmail(forgotEmail.trim());
      setPassword('');
      setError('');
      setMode('login');
    } catch (err) {
      setError(err.message || 'Không thể đặt lại mật khẩu. Vui lòng kiểm tra lại mã OTP.');
    } finally {
      setLoading(false);
    }
  };

  // Common UI styles
  const cardContainerStyle = {
    width: '100%',
    background: '#ffffff',
    borderRadius: '16px',
    boxShadow: '0 10px 35px rgba(15, 23, 42, 0.07), 0 1px 4px rgba(15, 23, 42, 0.03)',
    border: '1px solid #f1f5f9',
    padding: '44px 36px',
    boxSizing: 'border-box',
  };

  const notchedLabelStyle = {
    position: 'absolute',
    top: '-9px',
    left: '12px',
    background: '#ffffff',
    padding: '0 6px',
    fontSize: '0.75rem',
    color: '#94a3b8',
    fontWeight: 500,
    pointerEvents: 'none',
  };

  const inputStyle = {
    width: '100%',
    padding: '14px 16px',
    borderRadius: '6px',
    border: '1px solid #cbd5e1',
    background: '#ffffff',
    fontSize: '0.92rem',
    color: '#1e293b',
    outline: 'none',
    boxSizing: 'border-box',
    transition: 'border-color 150ms',
  };

  const primaryBtnStyle = {
    width: '100%',
    padding: '13.5px 16px',
    borderRadius: '8px',
    border: 'none',
    background: '#5848f4',
    color: '#ffffff',
    fontSize: '0.92rem',
    fontWeight: 600,
    cursor: loading ? 'not-allowed' : 'pointer',
    opacity: loading ? 0.75 : 1,
    boxShadow: '0 6px 16px rgba(88, 72, 244, 0.32)',
    transition: 'background 150ms, transform 150ms',
  };

  const errorBoxStyle = {
    padding: '10px 14px',
    borderRadius: '8px',
    background: '#fef2f2',
    border: '1px solid #fecaca',
    color: '#dc2626',
    fontSize: '0.82rem',
    lineHeight: 1.4,
  };

  const successBoxStyle = {
    padding: '10px 14px',
    borderRadius: '8px',
    background: '#ecfdf5',
    border: '1px solid #a7f3d0',
    color: '#065f46',
    fontSize: '0.82rem',
    lineHeight: 1.4,
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  };

  const eyeToggleBtnStyle = {
    position: 'absolute',
    right: '14px',
    top: '50%',
    transform: 'translateY(-50%)',
    background: 'none',
    border: 'none',
    padding: '4px',
    cursor: 'pointer',
    color: '#94a3b8',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  };

  // ═════════════════════════════════════════════════════════════════════
  // GIAO DIỆN 1: QUÊN MẬT KHẨU / MỞ KHÓA - BƯỚC 1 (NHẬP EMAIL)
  // ═════════════════════════════════════════════════════════════════════
  if (mode === 'forgot_email') {
    return (
      <div style={cardContainerStyle}>
        <div style={{ textAlign: 'center', marginBottom: '26px' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: '12px',
              background: '#eef2ff',
              border: '1px solid #c7d2fe',
              color: '#5848f4',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '14px',
            }}
          >
            <KeyRound size={26} />
          </div>
          <h2
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.55rem',
              fontWeight: 700,
              color: '#2b2b3c',
              margin: '0 0 8px',
            }}
          >
            Quên mật khẩu / Mở khóa
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#8e8ea9', lineHeight: 1.5, margin: 0 }}>
            Nhập địa chỉ email tài khoản của bạn để nhận mã xác thực OTP đặt lại mật khẩu mới và mở khóa tài khoản.
          </p>
        </div>

        <form onSubmit={handleRequestForgotOtp} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ position: 'relative' }}>
            <label htmlFor="forgot-email" style={notchedLabelStyle}>
              Email
            </label>
            <input
              id="forgot-email"
              type="text"
              placeholder="example@gmail.com"
              value={forgotEmail}
              onChange={(e) => setForgotEmail(e.target.value)}
              autoFocus
              style={inputStyle}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = '#5848f4';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = '#cbd5e1';
              }}
            />
          </div>

          {error && <div style={errorBoxStyle}>{error}</div>}

          <button
            type="submit"
            disabled={loading}
            style={primaryBtnStyle}
            onMouseEnter={(e) => {
              if (!loading) e.currentTarget.style.background = '#4b3be0';
            }}
            onMouseLeave={(e) => {
              if (!loading) e.currentTarget.style.background = '#5848f4';
            }}
          >
            {loading ? 'Đang gửi mã...' : 'Gửi mã xác thực OTP'}
          </button>

          <div style={{ textAlign: 'center', marginTop: '4px' }}>
            <button
              type="button"
              onClick={() => {
                setError('');
                setMode('login');
              }}
              style={{
                background: 'none',
                border: 'none',
                color: '#64748b',
                fontSize: '0.84rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: '4px 8px',
              }}
            >
              <ArrowLeft size={14} /> Quay lại đăng nhập
            </button>
          </div>
        </form>
      </div>
    );
  }

  // ═════════════════════════════════════════════════════════════════════
  // GIAO DIỆN 2: QUÊN MẬT KHẨU / MỞ KHÓA - BƯỚC 2 (NHẬP MÃ OTP)
  // "OTP hợp lệ mới được chuyển sang bước đặt mật khẩu mới"
  // "Nếu OTP sai hoặc hết hạn: không cho phép đặt lại mật khẩu"
  // ═════════════════════════════════════════════════════════════════════
  if (mode === 'forgot_otp') {
    return (
      <div style={cardContainerStyle}>
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: '12px',
              background: '#fef2f2',
              border: '1px solid #fecaca',
              color: '#ef4444',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '14px',
            }}
          >
            <Lock size={26} />
          </div>
          <h2
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.55rem',
              fontWeight: 700,
              color: '#2b2b3c',
              margin: '0 0 8px',
            }}
          >
            Nhập mã xác thực OTP
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#64748b', lineHeight: 1.5, margin: 0 }}>
            {forgotMessage ||
              `Mã OTP gồm 6 chữ số (hiệu lực 5 phút, dùng 1 lần) đã được gửi đến email ${forgotEmail}. Vui lòng nhập mã để tiếp tục.`}
          </p>
        </div>

        <form onSubmit={handleVerifyResetOtp} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          {/* OTP Input */}
          <div style={{ position: 'relative' }}>
            <label htmlFor="forgot-otp-input" style={notchedLabelStyle}>
              Mã OTP (6 chữ số)
            </label>
            <input
              id="forgot-otp-input"
              type="text"
              maxLength={6}
              placeholder="••••••"
              value={forgotOtp}
              onChange={(e) => setForgotOtp(e.target.value.replace(/\D/g, ''))}
              autoFocus
              style={{
                ...inputStyle,
                fontSize: '1.25rem',
                fontWeight: 700,
                letterSpacing: '0.35em',
                textAlign: 'center',
              }}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = '#5848f4';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = '#cbd5e1';
              }}
            />
          </div>

          {error && <div style={errorBoxStyle}>{error}</div>}

          <button
            type="submit"
            disabled={loading}
            style={primaryBtnStyle}
            onMouseEnter={(e) => {
              if (!loading) e.currentTarget.style.background = '#4b3be0';
            }}
            onMouseLeave={(e) => {
              if (!loading) e.currentTarget.style.background = '#5848f4';
            }}
          >
            {loading ? 'Đang xác thực...' : 'Xác nhận mã OTP'}
          </button>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '2px' }}>
            <button
              type="button"
              onClick={() => {
                setError('');
                setMode('login');
              }}
              style={{
                background: 'none',
                border: 'none',
                color: '#64748b',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: 0,
              }}
            >
              <ArrowLeft size={14} /> Quay lại đăng nhập
            </button>

            <button
              type="button"
              onClick={handleResendForgotOtp}
              disabled={loading}
              style={{
                background: 'none',
                border: 'none',
                color: '#5848f4',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: 0,
              }}
            >
              <RefreshCw size={13} /> Gửi lại mã OTP
            </button>
          </div>
        </form>
      </div>
    );
  }

  // ═════════════════════════════════════════════════════════════════════
  // GIAO DIỆN 3: ĐẶT MẬT KHẨU MỚI & MỞ KHÓA TÀI KHOẢN (BƯỚC 3)
  // "Đặt mật khẩu mới thành công: cập nhật password hash, reset số lần sai về 0 và mở khóa"
  // "Người dùng quay lại trang Login để đăng nhập bằng mật khẩu mới"
  // ═════════════════════════════════════════════════════════════════════
  if (mode === 'forgot_new_password') {
    return (
      <div style={cardContainerStyle}>
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: '12px',
              background: '#eef2ff',
              border: '1px solid #c7d2fe',
              color: '#5848f4',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '14px',
            }}
          >
            <ShieldCheck size={26} />
          </div>
          <h2
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.55rem',
              fontWeight: 700,
              color: '#2b2b3c',
              margin: '0 0 8px',
            }}
          >
            Đặt mật khẩu mới
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#8e8ea9', lineHeight: 1.5, margin: 0 }}>
            Mã OTP hợp lệ! Hãy nhập mật khẩu mới để cập nhật mật khẩu, mở khóa tài khoản và thiết lập lại số lần đăng nhập sai về 0.
          </p>
        </div>

        <form onSubmit={handleConfirmResetPassword} style={{ display: 'flex', flexDirection: 'column', gap: '18px' }}>
          {/* New Password */}
          <div style={{ position: 'relative' }}>
            <label htmlFor="new-password" style={notchedLabelStyle}>
              Mật khẩu mới
            </label>
            <input
              id="new-password"
              type={showNewPassword ? 'text' : 'password'}
              placeholder="8-16 ký tự, có ký tự đặc biệt"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoFocus
              style={{ ...inputStyle, paddingRight: '44px' }}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = '#5848f4';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = '#cbd5e1';
              }}
            />
            <button
              type="button"
              onClick={() => setShowNewPassword(!showNewPassword)}
              style={eyeToggleBtnStyle}
              aria-label={showNewPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              tabIndex={-1}
            >
              {showNewPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>

          {/* Confirm New Password */}
          <div style={{ position: 'relative' }}>
            <label htmlFor="confirm-password" style={notchedLabelStyle}>
              Xác nhận mật khẩu mới
            </label>
            <input
              id="confirm-password"
              type={showConfirmPassword ? 'text' : 'password'}
              placeholder="Nhập lại mật khẩu mới"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              style={{ ...inputStyle, paddingRight: '44px' }}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = '#5848f4';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = '#cbd5e1';
              }}
            />
            <button
              type="button"
              onClick={() => setShowConfirmPassword(!showConfirmPassword)}
              style={eyeToggleBtnStyle}
              aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              tabIndex={-1}
            >
              {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>

          {error && <div style={errorBoxStyle}>{error}</div>}

          <button
            type="submit"
            disabled={loading}
            style={primaryBtnStyle}
            onMouseEnter={(e) => {
              if (!loading) e.currentTarget.style.background = '#4b3be0';
            }}
            onMouseLeave={(e) => {
              if (!loading) e.currentTarget.style.background = '#5848f4';
            }}
          >
            {loading ? 'Đang cập nhật & Mở khóa...' : 'Xác nhận đổi mật khẩu & Mở khóa'}
          </button>

          <div style={{ display: 'flex', justifyContent: 'flex-start', alignItems: 'center', marginTop: '2px' }}>
            <button
              type="button"
              onClick={() => {
                setError('');
                setMode('forgot_otp');
              }}
              style={{
                background: 'none',
                border: 'none',
                color: '#64748b',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: 0,
              }}
            >
              <ArrowLeft size={14} /> Quay lại nhập OTP
            </button>
          </div>
        </form>
      </div>
    );
  }

  // ═════════════════════════════════════════════════════════════════════
  // GIAO DIỆN 4: XÁC THỰC OTP ĐĂNG NHẬP (NẾU BẬT MFA CHO USER)
  // ═════════════════════════════════════════════════════════════════════
  if (mode === 'otp') {
    return (
      <div style={cardContainerStyle}>
        <div style={{ textAlign: 'center', marginBottom: '26px' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: '12px',
              background: '#eef2ff',
              border: '1px solid #c7d2fe',
              color: '#5848f4',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginBottom: '14px',
            }}
          >
            <ShieldCheck size={26} />
          </div>
          <h2
            style={{
              fontFamily: 'var(--font-display)',
              fontSize: '1.55rem',
              fontWeight: 700,
              color: '#2b2b3c',
              margin: '0 0 8px',
            }}
          >
            Xác thực mã OTP
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#8e8ea9', lineHeight: 1.5, margin: 0 }}>
            {otpMessage}
          </p>
        </div>

        <form onSubmit={handleVerifyLoginOtp} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ position: 'relative' }}>
            <label
              htmlFor="login-otp"
              style={notchedLabelStyle}
            >
              Mã OTP (6 chữ số)
            </label>
            <input
              id="login-otp"
              type="text"
              maxLength={6}
              placeholder="••••••"
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
              autoFocus
              style={{
                ...inputStyle,
                fontSize: '1.25rem',
                fontWeight: 700,
                letterSpacing: '0.35em',
                textAlign: 'center',
              }}
              onFocus={(e) => {
                e.currentTarget.style.borderColor = '#5848f4';
              }}
              onBlur={(e) => {
                e.currentTarget.style.borderColor = '#cbd5e1';
              }}
            />
          </div>

          {error && <div style={errorBoxStyle}>{error}</div>}

          <button
            type="submit"
            disabled={loading}
            style={primaryBtnStyle}
            onMouseEnter={(e) => {
              if (!loading) e.currentTarget.style.background = '#4b3be0';
            }}
            onMouseLeave={(e) => {
              if (!loading) e.currentTarget.style.background = '#5848f4';
            }}
          >
            {loading ? 'Đang xác thực...' : 'Xác nhận OTP'}
          </button>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '2px' }}>
            <button
              type="button"
              onClick={() => {
                setMode('login');
                setOtpCode('');
                setError('');
              }}
              style={{
                background: 'none',
                border: 'none',
                color: '#64748b',
                fontSize: '0.82rem',
                fontWeight: 600,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                gap: '6px',
                padding: 0,
              }}
            >
              <ArrowLeft size={14} /> Quay lại
            </button>

            {password && (
              <button
                type="button"
                onClick={handleResendLoginOtp}
                disabled={loading}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#5848f4',
                  fontSize: '0.82rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: 0,
                }}
              >
                <RefreshCw size={13} /> Gửi lại mã OTP
              </button>
            )}
          </div>
        </form>
      </div>
    );
  }

  // ═════════════════════════════════════════════════════════════════════
  // GIAO DIỆN 5: ĐĂNG NHẬP CHÍNH (EMAIL + MẬT KHẨU)
  // ═════════════════════════════════════════════════════════════════════
  return (
    <div style={cardContainerStyle}>
      {/* Header */}
      <div style={{ textAlign: 'center', marginBottom: '30px' }}>
        <h1
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: '2rem',
            fontWeight: 700,
            color: '#2b2b3c',
            margin: '0 0 10px',
            letterSpacing: '-0.02em',
          }}
        >
          Đăng nhập
        </h1>
        <p
          style={{
            fontSize: '0.875rem',
            color: '#8e8ea9',
            margin: 0,
            fontWeight: 400,
          }}
        >
          Nhập thông tin tài khoản để truy cập hệ thống
        </p>
      </div>

      {/* Form */}
      <form onSubmit={handleLoginSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
        {/* Email Field (Notched border label) */}
        <div style={{ position: 'relative' }}>
          <label
            htmlFor="login-email"
            style={notchedLabelStyle}
          >
            Email
          </label>
          <input
            id="login-email"
            type="text"
            placeholder="example@gmail.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            style={inputStyle}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = '#5848f4';
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderColor = '#cbd5e1';
            }}
          />
        </div>

        {/* Password Field (Notched border label with show/hide toggle) */}
        <div style={{ position: 'relative' }}>
          <label
            htmlFor="login-password"
            style={notchedLabelStyle}
          >
            Mật khẩu
          </label>
          <input
            id="login-password"
            type={showPassword ? 'text' : 'password'}
            placeholder="•••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            style={{
              ...inputStyle,
              paddingRight: '44px',
            }}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = '#5848f4';
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderColor = '#cbd5e1';
            }}
          />
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            style={eyeToggleBtnStyle}
            aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
            tabIndex={-1}
          >
            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        </div>

        {/* Remember me & Forgot Password */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: '-4px',
          }}
        >
          <label
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '0.86rem',
              color: '#475569',
              cursor: 'pointer',
              userSelect: 'none',
            }}
          >
            <input
              type="checkbox"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              style={{
                width: '16px',
                height: '16px',
                accentColor: '#5848f4',
                cursor: 'pointer',
                borderRadius: '4px',
              }}
            />
            <span>Ghi nhớ đăng nhập</span>
          </label>

          <button
            type="button"
            onClick={() => {
              setError('');
              setSuccess('');
              setForgotEmail(email.trim());
              setMode('forgot_email');
            }}
            style={{
              background: 'none',
              border: 'none',
              color: '#5848f4',
              fontSize: '0.85rem',
              fontWeight: 600,
              cursor: 'pointer',
              padding: 0,
              textDecoration: 'none',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.textDecoration = 'underline';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.textDecoration = 'none';
            }}
          >
            Quên mật khẩu?
          </button>
        </div>

        {/* Error message */}
        {error && <div style={errorBoxStyle}>{error}</div>}

        {/* Success message */}
        {success && (
          <div style={successBoxStyle}>
            <CheckCircle2 size={16} style={{ flexShrink: 0 }} />
            <span>{success}</span>
          </div>
        )}

        {/* Submit Button */}
        <button
          type="submit"
          disabled={loading}
          style={{
            ...primaryBtnStyle,
            marginTop: '2px',
          }}
          onMouseEnter={(e) => {
            if (!loading) e.currentTarget.style.background = '#4b3be0';
          }}
          onMouseLeave={(e) => {
            if (!loading) e.currentTarget.style.background = '#5848f4';
          }}
        >
          {loading ? 'Đang xử lý...' : 'Đăng nhập'}
        </button>
      </form>
    </div>
  );
}
