// src/components/layouts/MainLayout.jsx
import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Navbar from './Navbar';
import Footer from './Footer';
import ChatProvider from '@/features/chat/ChatProvider';

export default function MainLayout() {
  const { isAuthenticated, user } = useAuth();
  const location = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }, [location.pathname]);

  // Khi chưa đăng nhập ở trang chủ (/), hiển thị toàn màn hình khung đăng nhập (không hiện Navbar/Footer)
  if (!isAuthenticated && location.pathname === '/') {
    return <Outlet />;
  }

  return (
    <ChatProvider key={user?.id || 'anonymous'}><div className="min-h-screen flex flex-col bg-[var(--bg-canvas)] text-[var(--text-primary)]">
      <Navbar />
      <main className="flex-1 flex flex-col">
        <Outlet />
      </main>
      <Footer />
    </div></ChatProvider>
  );
}
