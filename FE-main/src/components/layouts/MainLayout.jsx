// src/components/layouts/MainLayout.jsx
import { Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Navbar from './Navbar';
import Footer from './Footer';

export default function MainLayout() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  // Khi chưa đăng nhập ở trang chủ (/), hiển thị toàn màn hình khung đăng nhập (không hiện Navbar/Footer)
  if (!isAuthenticated && location.pathname === '/') {
    return <Outlet />;
  }

  return (
    <div className="min-h-screen flex flex-col bg-[var(--bg-canvas)] text-[var(--text-primary)]">
      <Navbar />
      <main className="flex-1 flex flex-col">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}
