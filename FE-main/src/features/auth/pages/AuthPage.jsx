import React from 'react';
import { Link } from 'react-router-dom';
import SagasuLogoColor from '@/assets/Sagasu_logo_color.svg';
import AuthLeftPanel from '../components/AuthLeftPanel';
import LoginForm from '../components/LoginForm';

export default function AuthPage({ defaultMode = 'login' }) {
  return (
    <div className="auth-page" style={{ gridTemplateColumns: '1.45fr 1fr', minHeight: '100dvh' }}>
      {/* ── LEFT PANEL (Giữ nguyên) ── */}
      <AuthLeftPanel />

      {/* ── RIGHT PANEL ── */}
      <div
        className="auth-panel-right"
        style={{
          background: '#f8fafc',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '32px 28px',
        }}
      >
        {/* Mobile top bar */}
        <div className="md:hidden flex items-center justify-between mb-6 pb-4 border-b border-[var(--border)] w-full">
          <Link to="/" className="inline-block" aria-label="Sagasu">
            <img src={SagasuLogoColor} alt="Sagasu" className="h-10 w-auto object-contain" />
          </Link>
        </div>

        <div style={{ width: '100%', maxWidth: '410px' }}>
          <LoginForm initialMode={defaultMode} />
        </div>
      </div>
    </div>
  );
}
