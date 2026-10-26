import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import SagasuLogoColor from '@/assets/Sagasu_logo_color.svg';
import AuthLeftPanel from '../components/AuthLeftPanel';
import LoginForm from '../components/LoginForm';
import RegisterForm from '../components/RegisterForm';

export default function AuthPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const isRegisterRoute = location.pathname.includes('dang-ky') || location.pathname.includes('register');
  const [activeTab, setActiveTab] = useState(isRegisterRoute ? 'register' : 'login');

  useEffect(() => {
    const isRegister = location.pathname.includes('dang-ky') || location.pathname.includes('register');
    setActiveTab(isRegister ? 'register' : 'login');
  }, [location.pathname]);

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    if (tab === 'register' && !location.pathname.includes('dang-ky')) {
      navigate('/dang-ky', { replace: true });
    } else if (tab === 'login' && !location.pathname.includes('dang-nhap')) {
      navigate('/dang-nhap', { replace: true });
    }
  };

  return (
    <div className="auth-page">
      {/* ── LEFT PANEL ── */}
      <AuthLeftPanel />

      {/* ── RIGHT PANEL ── */}
      <div className="auth-panel-right">
        {/* Mobile top bar */}
        <div className="md:hidden flex items-center justify-between mb-6 pb-4 border-b border-[var(--border)]">
          <Link to="/" className="inline-block" aria-label="Sagasu">
            <img src={SagasuLogoColor} alt="Sagasu" className="h-10 w-auto object-contain" />
          </Link>
        </div>

        <div className="auth-form-container animate-scaleIn">
          {/* Header */}
          <div className="mb-6">
            <h2 className="font-[var(--font-display)] text-2xl font-extrabold text-[var(--text-primary)] tracking-tight mb-2 whitespace-nowrap">
              {activeTab === 'login' ? 'Đăng nhập tài khoản' : 'Tạo tài khoản mới'}
            </h2>
            <p className="text-sm text-[var(--text-secondary)] m-0 mb-5">
              {activeTab === 'login'
                ? 'Chào mừng bạn quay lại hệ thống tìm kiếm & hỗ trợ thất lạc'
                : 'Tham gia mạng lưới tìm kiếm và bàn giao tài sản thất lạc'}
            </p>

            {/* Tab switcher - segmented control with sliding pill */}
            <div className="auth-tabs">
              <div
                className="auth-tab-indicator"
                style={{
                  width: 'calc(50% - 4px)',
                  transform: activeTab === 'login' ? 'translateX(0)' : 'translateX(100%)',
                }}
              />
              <button
                type="button"
                className={`auth-tab ${activeTab === 'login' ? 'active' : ''}`}
                id="tab-login"
                onClick={() => handleTabChange('login')}
              >
                Đăng nhập
              </button>
              <button
                type="button"
                className={`auth-tab ${activeTab === 'register' ? 'active' : ''}`}
                id="tab-register"
                onClick={() => handleTabChange('register')}
              >
                Đăng ký mới
              </button>
            </div>
          </div>

          {/* Form content with smooth key transition */}
          <div key={activeTab} className="auth-form-fade">
            {activeTab === 'login' ? (
              <LoginForm onSwitch={() => handleTabChange('register')} />
            ) : (
              <RegisterForm onSwitch={() => handleTabChange('login')} />
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
