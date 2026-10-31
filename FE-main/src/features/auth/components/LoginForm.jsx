// src/features/auth/components/LoginForm.jsx
import React, { useState } from 'react';
import { Eye, EyeOff, Lock, Mail, User, ShieldCheck, ArrowLeft, RefreshCw, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import authService from '@/services/authService';

export default function LoginForm() {
  // 'student' = Sinh viên (chỉ cho phép role USER), 'staff' = Cán bộ & Phòng ban (chỉ cho phép ADMIN, MANAGER, APPROVER, CHECKER)
  const [userGroup, setUserGroup] = useState('student');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);
  const [showPass, setShowPass] = useState(false);

  // OTP Step state (dành cho các role không phải ADMIN/MANAGER)
  const [otpStep, setOtpStep] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [pendingEmail, setPendingEmail] = useState('');
  const [otpMessage, setOtpMessage] = useState('');

  // Google Login Modal state (cho phép chọn tài khoản Google đã được hệ thống cung cấp)
  const [googleModalOpen, setGoogleModalOpen] = useState(false);
  const [googleAccounts, setGoogleAccounts] = useState([]);
  const [loadingGoogleAccounts, setLoadingGoogleAccounts] = useState(false);

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { login, verifyOtp, loginWithGoogle } = useAuth();
  const navigate = useNavigate();

  const handleLoginResult = (result) => {
    if (result?.requiresOtp) {
      setOtpStep(true);
      setPendingEmail(result.email || email.trim());
      setOtpMessage(
        result.message ||
          `Mã xác thực OTP (6 chữ số) đã được gửi về email ${result.email || email.trim()}. Vui lòng nhập mã để hoàn tất đăng nhập.`
      );
      setOtpCode('');
      setGoogleModalOpen(false);
      return;
    }

    const role = result?.account?.role;
    setGoogleModalOpen(false);
    if (role === 'ADMIN' || role === 'MANAGER') {
      navigate('/admin', { replace: true });
    } else {
      navigate('/', { replace: true });
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const trimmedInput = email.trim();
    if (!trimmedInput || !password) {
      setError(
        userGroup === 'student'
          ? 'Vui lòng nhập đầy đủ Email và mật khẩu'
          : 'Vui lòng nhập đầy đủ Tên tài khoản (Account) và mật khẩu'
      );
      return;
    }

    // Sinh viên bắt buộc phải đăng nhập bằng địa chỉ Email hợp lệ
    if (userGroup === 'student') {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(trimmedInput)) {
        setError('Đối với Sinh viên, vui lòng đăng nhập bằng địa chỉ Email hợp lệ (VD: ten@gmail.com)');
        return;
      }
    }

    setLoading(true);
    try {
      const result = await login(trimmedInput, password, userGroup);
      handleLoginResult(result);
    } catch (err) {
      setError(err.message || 'Thông tin đăng nhập hoặc mật khẩu không chính xác');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setError('');

    if (!otpCode || otpCode.trim().length < 6) {
      setError('Vui lòng nhập đầy đủ mã OTP 6 chữ số đã gửi về email');
      return;
    }

    setLoading(true);
    try {
      const result = await verifyOtp(pendingEmail, otpCode.trim(), 'LOGIN', userGroup);
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

  const handleResendOtp = async () => {
    if (!password) return;
    setError('');
    setLoading(true);
    try {
      const result = await login(pendingEmail || email.trim(), password, userGroup);
      if (result?.requiresOtp) {
        setOtpMessage('Đã gửi lại mã OTP mới về email của bạn.');
      }
    } catch (err) {
      setError(err.message || 'Không thể gửi lại mã OTP');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenGoogleModal = async () => {
    setError('');
    setGoogleModalOpen(true);
    setLoadingGoogleAccounts(true);
    try {
      const list = await authService.getGoogleAccounts();
      const studentAccounts = Array.isArray(list)
        ? list.filter((acc) => String(acc.role).toUpperCase() === 'USER')
        : [];
      setGoogleAccounts(studentAccounts);
    } catch (err) {
      setGoogleAccounts([]);
    } finally {
      setLoadingGoogleAccounts(false);
    }
  };

  const handleSelectGoogleAccount = async (selectedEmail) => {
    if (!selectedEmail) return;
    setError('');
    setLoading(true);
    try {
      const result = await loginWithGoogle(selectedEmail.trim(), 'student');
      handleLoginResult(result);
    } catch (err) {
      setError(err.message || 'Tài khoản Google này chưa được cấp quyền trong hệ thống');
      setGoogleModalOpen(false);
    } finally {
      setLoading(false);
    }
  };

  // Giao diện bước 2: Xác thực OTP
  if (otpStep) {
    return (
      <div style={{ width: '100%' }}>
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <div
            style={{
              width: 52,
              height: 52,
              borderRadius: '12px',
              background: '#f0fdfa',
              border: '1px solid #99f6e4',
              color: '#0f766e',
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
              fontSize: '1.45rem',
              fontWeight: 700,
              color: '#0f172a',
              margin: '0 0 8px',
            }}
          >
            Xác thực mã OTP
          </h2>
          <p style={{ fontSize: '0.84rem', color: '#64748b', lineHeight: 1.5, margin: 0 }}>
            {otpMessage}
          </p>
        </div>

        <form onSubmit={handleVerifyOtp} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <div>
            <label
              style={{
                display: 'block',
                fontSize: '0.78rem',
                fontWeight: 700,
                color: '#334155',
                marginBottom: '6px',
              }}
            >
              MÃ OTP (6 CHỮ SỐ)
            </label>
            <input
              type="text"
              maxLength={6}
              placeholder="Nhập 6 chữ số OTP..."
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
              autoFocus
              style={{
                width: '100%',
                padding: '13px 16px',
                borderRadius: '8px',
                border: '1.5px solid #cbd5e1',
                background: '#f8fafc',
                fontSize: '1.25rem',
                fontWeight: 800,
                letterSpacing: '0.35em',
                textAlign: 'center',
                color: '#0f172a',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>

          {error && (
            <div
              style={{
                padding: '10px 14px',
                borderRadius: '8px',
                background: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#dc2626',
                fontSize: '0.82rem',
              }}
            >
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: '12px 16px',
              borderRadius: '8px',
              border: 'none',
              background: '#0f514c',
              color: 'white',
              fontSize: '0.92rem',
              fontWeight: 700,
              cursor: loading ? 'not-allowed' : 'pointer',
              opacity: loading ? 0.7 : 1,
            }}
          >
            {loading ? 'Đang xác thực OTP...' : 'Xác nhận & Đăng nhập'}
          </button>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '4px' }}>
            <button
              type="button"
              onClick={() => {
                setOtpStep(false);
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
              }}
            >
              <ArrowLeft size={14} /> Quay lại
            </button>

            {password && (
              <button
                type="button"
                onClick={handleResendOtp}
                disabled={loading}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#0f766e',
                  fontSize: '0.82rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
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

  return (
    <div style={{ width: '100%' }}>
      {/* Tiêu đề chính */}
      <div style={{ textAlign: 'center', marginBottom: '22px' }}>
        <h1
          style={{
            fontFamily: 'var(--font-display)',
            fontSize: '1.65rem',
            fontWeight: 800,
            color: '#0f172a',
            margin: '0 0 6px',
            letterSpacing: '-0.02em',
          }}
        >
          Hệ thống quản lý đồ thất lạc
        </h1>
        <p style={{ fontSize: '0.92rem', color: '#64748b', margin: 0, fontWeight: 500 }}>
          Đăng nhập
        </p>
      </div>

      {/* Segmented Tabs: Sinh viên | Cán bộ & Phòng ban */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          background: '#e6effa',
          padding: '4px',
          borderRadius: '10px',
          marginBottom: '12px',
        }}
      >
        <button
          type="button"
          onClick={() => {
            setUserGroup('student');
            setError('');
          }}
          style={{
            padding: '10px 12px',
            borderRadius: '8px',
            border: 'none',
            background: userGroup === 'student' ? '#ffffff' : 'transparent',
            color: userGroup === 'student' ? '#0f172a' : '#475569',
            fontWeight: userGroup === 'student' ? 700 : 500,
            fontSize: '0.85rem',
            cursor: 'pointer',
            boxShadow: userGroup === 'student' ? '0 1px 3px rgba(15, 23, 42, 0.08)' : 'none',
            transition: 'all 150ms ease',
          }}
        >
          Sinh viên
        </button>
        <button
          type="button"
          onClick={() => {
            setUserGroup('staff');
            setError('');
          }}
          style={{
            padding: '10px 12px',
            borderRadius: '8px',
            border: 'none',
            background: userGroup === 'staff' ? '#ffffff' : 'transparent',
            color: userGroup === 'staff' ? '#0f172a' : '#475569',
            fontWeight: userGroup === 'staff' ? 700 : 500,
            fontSize: '0.85rem',
            cursor: 'pointer',
            boxShadow: userGroup === 'staff' ? '0 1px 3px rgba(15, 23, 42, 0.08)' : 'none',
            transition: 'all 150ms ease',
          }}
        >
          Cán bộ & Phòng ban
        </button>
      </div>

      {/* Dòng mô tả dưới tab */}
      <div
        style={{
          textAlign: 'center',
          fontSize: '0.78rem',
          color: '#64748b',
          marginBottom: '18px',
        }}
      >
        {userGroup === 'student'
          ? 'Đăng nhập bằng Email dành cho sinh viên'
          : 'Đăng nhập bằng tài khoản (Account) dành cho cán bộ và phòng ban'}
      </div>

      {/* Form nhập Email (Sinh viên) hoặc Account (Cán bộ & Phòng ban) + Mật khẩu */}
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
        {/* Input Email hoặc Account */}
        <div style={{ position: 'relative' }}>
          {userGroup === 'student' ? (
            <Mail
              size={16}
              style={{
                position: 'absolute',
                left: '14px',
                top: '50%',
                transform: 'translateY(-50%)',
                color: '#94a3b8',
                pointerEvents: 'none',
              }}
            />
          ) : (
            <User
              size={16}
              style={{
                position: 'absolute',
                left: '14px',
                top: '50%',
                transform: 'translateY(-50%)',
                color: '#94a3b8',
                pointerEvents: 'none',
              }}
            />
          )}
          <input
            type={userGroup === 'student' ? 'email' : 'text'}
            placeholder={
              userGroup === 'student'
                ? 'Nhập địa chỉ Email sinh viên'
                : 'Nhập tên tài khoản (Account)'
            }
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            style={{
              width: '100%',
              padding: '12px 14px 12px 40px',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              background: '#f1f5f9',
              fontSize: '0.86rem',
              color: '#0f172a',
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />
        </div>

        {/* Input Mật khẩu */}
        <div style={{ position: 'relative' }}>
          <Lock
            size={16}
            style={{
              position: 'absolute',
              left: '14px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: '#94a3b8',
              pointerEvents: 'none',
            }}
          />
          <input
            type={showPass ? 'text' : 'password'}
            placeholder="Mật khẩu"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            style={{
              width: '100%',
              padding: '12px 40px 12px 40px',
              borderRadius: '8px',
              border: '1px solid #e2e8f0',
              background: '#f1f5f9',
              fontSize: '0.86rem',
              color: '#0f172a',
              outline: 'none',
              boxSizing: 'border-box',
            }}
          />
          <button
            type="button"
            onClick={() => setShowPass((v) => !v)}
            tabIndex={-1}
            style={{
              position: 'absolute',
              right: '12px',
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'none',
              border: 'none',
              color: '#64748b',
              cursor: 'pointer',
              padding: '4px',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        </div>

        {/* Ghi nhớ đăng nhập & Quên mật khẩu */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: '2px',
            marginBottom: '4px',
          }}
        >
          <label
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '0.8rem',
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
                width: '15px',
                height: '15px',
                accentColor: '#0f514c',
                cursor: 'pointer',
              }}
            />
            <span>Ghi nhớ đăng nhập</span>
          </label>

          <button
            type="button"
            onClick={() =>
              setError('Vui lòng liên hệ Quản trị viên để được cấp lại mật khẩu.')
            }
            style={{
              background: 'none',
              border: 'none',
              padding: 0,
              fontSize: '0.8rem',
              fontWeight: 600,
              color: '#0f766e',
              cursor: 'pointer',
            }}
          >
            Quên mật khẩu?
          </button>
        </div>

        {error && (
          <div
            style={{
              padding: '10px 14px',
              borderRadius: '8px',
              background: '#fef2f2',
              border: '1px solid #fecaca',
              color: '#dc2626',
              fontSize: '0.82rem',
              lineHeight: 1.4,
            }}
          >
            {error}
          </div>
        )}

        {/* Nút Đăng nhập */}
        <button
          type="submit"
          disabled={loading}
          style={{
            width: '100%',
            padding: '12.5px 16px',
            borderRadius: '8px',
            border: 'none',
            background: '#0d534d',
            color: '#ffffff',
            fontSize: '0.9rem',
            fontWeight: 700,
            cursor: loading ? 'not-allowed' : 'pointer',
            opacity: loading ? 0.75 : 1,
            transition: 'background 150ms',
            marginTop: '2px',
          }}
        >
          {loading ? 'Đang kiểm tra...' : 'Đăng nhập'}
        </button>

        {/* Nút Đăng nhập với Google dành cho Sinh viên */}
        {userGroup === 'student' && (
          <button
            type="button"
            onClick={handleOpenGoogleModal}
            style={{
              width: '100%',
              padding: '11px 16px',
              borderRadius: '8px',
              border: '1px solid #cbd5e1',
              background: '#ffffff',
              color: '#1e293b',
              fontSize: '0.86rem',
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '10px',
              transition: 'background 150ms, border-color 150ms',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = '#f8fafc';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = '#ffffff';
            }}
          >
            <svg width="18" height="18" viewBox="0 0 24 24">
              <path
                fill="#4285F4"
                d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17Z"
              />
              <path
                fill="#34A853"
                d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.11-6.72-4.96H1.29v3.14C3.26 21.3 7.31 24 12 24Z"
              />
              <path
                fill="#FBBC05"
                d="M5.28 14.24c-.24-.72-.38-1.49-.38-2.24s.14-1.52.38-2.24V6.62H1.29C.47 8.24 0 10.06 0 12s.47 3.76 1.29 5.38l3.99-3.14Z"
              />
              <path
                fill="#EA4335"
                d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.7 1.29 6.62l3.99 3.14c.95-2.85 3.6-4.96 6.72-4.96Z"
              />
            </svg>
            <span>Đăng nhập với Google</span>
          </button>
        )}
      </form>

      {/* ── MODAL CHỌN TÀI KHOẢN GOOGLE ĐÃ ĐƯỢC HỆ THỐNG CUNG CẤP ── */}
      {googleModalOpen && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            zIndex: 1200,
            background: 'rgba(15, 23, 42, 0.55)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '16px',
          }}
          onClick={() => setGoogleModalOpen(false)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              width: '100%',
              maxWidth: '420px',
              background: '#ffffff',
              borderRadius: '12px',
              boxShadow: '0 20px 45px rgba(15, 23, 42, 0.25)',
              overflow: 'hidden',
              border: '1px solid #e2e8f0',
            }}
          >
            {/* Header Google Picker */}
            <div
              style={{
                padding: '18px 22px',
                borderBottom: '1px solid #f1f5f9',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <svg width="20" height="20" viewBox="0 0 24 24">
                  <path
                    fill="#4285F4"
                    d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17Z"
                  />
                  <path
                    fill="#34A853"
                    d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.11-6.72-4.96H1.29v3.14C3.26 21.3 7.31 24 12 24Z"
                  />
                  <path
                    fill="#FBBC05"
                    d="M5.28 14.24c-.24-.72-.38-1.49-.38-2.24s.14-1.52.38-2.24V6.62H1.29C.47 8.24 0 10.06 0 12s.47 3.76 1.29 5.38l3.99-3.14Z"
                  />
                  <path
                    fill="#EA4335"
                    d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.7 1.29 6.62l3.99 3.14c.95-2.85 3.6-4.96 6.72-4.96Z"
                  />
                </svg>
                <span style={{ fontWeight: 700, fontSize: '0.95rem', color: '#0f172a' }}>
                  Đăng nhập bằng Google
                </span>
              </div>
              <button
                type="button"
                onClick={() => setGoogleModalOpen(false)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: '#64748b',
                  cursor: 'pointer',
                  padding: '4px',
                  display: 'flex',
                }}
              >
                <X size={18} />
              </button>
            </div>

            <div style={{ padding: '18px 22px' }}>
              <p style={{ fontSize: '0.83rem', color: '#475569', margin: '0 0 14px' }}>
                Chọn tài khoản Google sinh viên đã được hệ thống cung cấp:
              </p>

              {/* Danh sách tài khoản Google sinh viên (USER) có sẵn trong DB */}
              <div
                style={{
                  maxHeight: '260px',
                  overflowY: 'auto',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                }}
              >
                {loadingGoogleAccounts ? (
                  <div style={{ padding: '20px', textAlign: 'center', fontSize: '0.84rem', color: '#64748b' }}>
                    Đang tải danh sách tài khoản Google...
                  </div>
                ) : googleAccounts.length > 0 ? (
                  googleAccounts.map((acc) => {
                    const cleanName = String(acc.fullName || acc.email).replace(/\s*\((Manager|Admin)\)/gi, '');
                    return (
                      <button
                        key={acc.email}
                        type="button"
                        onClick={() => handleSelectGoogleAccount(acc.email)}
                        style={{
                          width: '100%',
                          padding: '10px 12px',
                          borderRadius: '8px',
                          border: '1px solid #e2e8f0',
                          background: '#ffffff',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          gap: '10px',
                          cursor: 'pointer',
                          textAlign: 'left',
                          transition: 'background 120ms, border-color 120ms',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.background = '#f8fafc';
                          e.currentTarget.style.borderColor = '#cbd5e1';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = '#ffffff';
                          e.currentTarget.style.borderColor = '#e2e8f0';
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', minWidth: 0 }}>
                          <div
                            style={{
                              width: '34px',
                              height: '34px',
                              borderRadius: '50%',
                              background: '#0f514c',
                              color: '#ffffff',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              fontWeight: 700,
                              fontSize: '0.82rem',
                              flexShrink: 0,
                            }}
                          >
                            {cleanName.charAt(0).toUpperCase()}
                          </div>
                          <div style={{ minWidth: 0 }}>
                            <div
                              style={{
                                fontSize: '0.85rem',
                                fontWeight: 700,
                                color: '#0f172a',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                            >
                              {cleanName}
                            </div>
                            <div
                              style={{
                                fontSize: '0.76rem',
                                color: '#64748b',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis',
                              }}
                            >
                              {acc.email}
                            </div>
                          </div>
                        </div>
                        <span
                          style={{
                            fontSize: '0.68rem',
                            fontWeight: 700,
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: '#f1f5f9',
                            color: '#334155',
                            flexShrink: 0,
                          }}
                        >
                          {acc.role}
                        </span>
                      </button>
                    );
                  })
                ) : (
                  <div style={{ padding: '14px', textAlign: 'center', fontSize: '0.8rem', color: '#94a3b8' }}>
                    Chưa có tài khoản Google sinh viên nào trong hệ thống.
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
