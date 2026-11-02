// src/features/auth/components/LoginForm.jsx
import React, { useState } from 'react';
import { ShieldCheck, ArrowLeft, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';

export default function LoginForm() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(true);

  // OTP Step state (chỉ dành cho role USER)
  const [otpStep, setOtpStep] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [pendingEmail, setPendingEmail] = useState('');
  const [otpMessage, setOtpMessage] = useState('');

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { login, verifyOtp } = useAuth();
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
      return;
    }

    const role = result?.account?.role;
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
      setError('Vui lòng nhập đầy đủ Email và mật khẩu');
      return;
    }

    if (/\s/.test(trimmedInput)) {
      setError('Email không được chứa khoảng trắng');
      return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (trimmedInput.toLowerCase() !== 'admin' && !emailRegex.test(trimmedInput)) {
      setError('Vui lòng nhập địa chỉ Email hợp lệ (VD: example@gmail.com)');
      return;
    }

    setLoading(true);
    try {
      const result = await login(trimmedInput, password);
      handleLoginResult(result);
    } catch (err) {
      setError(err.message || 'Email hoặc mật khẩu không chính xác');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setError('');

    const cleanOtp = otpCode.trim();
    if (!/^[0-9]{6}$/.test(cleanOtp)) {
      setError('Vui lòng nhập đầy đủ mã OTP gồm đúng 6 chữ số đã gửi về email');
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

  const handleResendOtp = async () => {
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

  // Giao diện bước 2: Xác thực OTP (dành cho role USER)
  if (otpStep) {
    return (
      <div
        style={{
          width: '100%',
          background: '#ffffff',
          borderRadius: '16px',
          boxShadow: '0 10px 35px rgba(15, 23, 42, 0.07), 0 1px 4px rgba(15, 23, 42, 0.03)',
          border: '1px solid #f1f5f9',
          padding: '42px 36px',
          boxSizing: 'border-box',
        }}
      >
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

        <form onSubmit={handleVerifyOtp} style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div style={{ position: 'relative' }}>
            <label
              style={{
                position: 'absolute',
                top: '-9px',
                left: '12px',
                background: '#ffffff',
                padding: '0 6px',
                fontSize: '0.75rem',
                color: '#94a3b8',
                fontWeight: 500,
                pointerEvents: 'none',
              }}
            >
              Mã OTP (6 chữ số)
            </label>
            <input
              type="text"
              maxLength={6}
              placeholder="••••••"
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
              autoFocus
              style={{
                width: '100%',
                padding: '14px 16px',
                borderRadius: '6px',
                border: '1px solid #cbd5e1',
                background: '#ffffff',
                fontSize: '1.25rem',
                fontWeight: 700,
                letterSpacing: '0.35em',
                textAlign: 'center',
                color: '#1e293b',
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
              padding: '13px 16px',
              borderRadius: '8px',
              border: 'none',
              background: '#5848f4',
              color: '#ffffff',
              fontSize: '0.92rem',
              fontWeight: 600,
              cursor: loading ? 'not-allowed' : 'pointer',
              opacity: loading ? 0.7 : 1,
              boxShadow: '0 6px 16px rgba(88, 72, 244, 0.32)',
              transition: 'background 150ms, transform 150ms',
            }}
          >
            {loading ? 'Đang xác thực...' : 'Xác nhận'}
          </button>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
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
                padding: 0,
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

  return (
    <div
      style={{
        width: '100%',
        background: '#ffffff',
        borderRadius: '16px',
        boxShadow: '0 10px 35px rgba(15, 23, 42, 0.07), 0 1px 4px rgba(15, 23, 42, 0.03)',
        border: '1px solid #f1f5f9',
        padding: '44px 36px',
        boxSizing: 'border-box',
      }}
    >
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
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
        {/* Email Field (Notched border label) */}
        <div style={{ position: 'relative' }}>
          <label
            htmlFor="login-email"
            style={{
              position: 'absolute',
              top: '-9px',
              left: '12px',
              background: '#ffffff',
              padding: '0 6px',
              fontSize: '0.75rem',
              color: '#94a3b8',
              fontWeight: 500,
              pointerEvents: 'none',
            }}
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
            style={{
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
            }}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = '#5848f4';
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderColor = '#cbd5e1';
            }}
          />
        </div>

        {/* Password Field (Notched border label) */}
        <div style={{ position: 'relative' }}>
          <label
            htmlFor="login-password"
            style={{
              position: 'absolute',
              top: '-9px',
              left: '12px',
              background: '#ffffff',
              padding: '0 6px',
              fontSize: '0.75rem',
              color: '#94a3b8',
              fontWeight: 500,
              pointerEvents: 'none',
            }}
          >
            Mật khẩu
          </label>
          <input
            id="login-password"
            type="password"
            placeholder="•••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            style={{
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
            }}
            onFocus={(e) => {
              e.currentTarget.style.borderColor = '#5848f4';
            }}
            onBlur={(e) => {
              e.currentTarget.style.borderColor = '#cbd5e1';
            }}
          />
        </div>

        {/* Remember me */}
        <div style={{ display: 'flex', alignItems: 'center', marginTop: '-2px' }}>
          <label
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '10px',
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

        {/* Submit Button */}
        <button
          type="submit"
          disabled={loading}
          style={{
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
            marginTop: '4px',
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
