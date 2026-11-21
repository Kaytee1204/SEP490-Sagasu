import { useState, useRef, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  Bookmark,
  FileText,
  HandHelping,
  Flag,
  MessageCircle,
  KeyRound,
  Plus,
  LogOut,
  User,
  LayoutDashboard,
  ChevronDown,
  ChevronRight,
  Shield,
  LayoutGrid,
  ArrowRight,
  Menu,
  X,
  Home,
  Search,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useChat } from '@/features/chat/hooks/useChat';
import SagasuLogo from '@/assets/Sagasu_logo_color.svg';
import ChatBadgeLink from '@/features/chat/components/ChatBadgeLink';
import NotificationBell from '@/features/notifications/components/NotificationBell';

function getInitials(name) {
  if (!name) return 'U';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function AvatarDropdown() {
  const [open, setOpen] = useState(false);
  const [activitiesExpanded, setActivitiesExpanded] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  const { user, isAdmin, isManager, isApprover, logout } = useAuth();
  const { unread: chatUnread = 0 } = useChat() || {};

  // Close on outside click
  useEffect(() => {
    const handler = (e) => {
      if (ref.current && !ref.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleLogout = async () => {
    setOpen(false);
    await logout();
    navigate('/dang-nhap');
  };

  const rawName = user?.fullName || user?.name || user?.email || 'Người dùng';
  const displayName = String(rawName).replace(/\s*\((manager|admin|user|approver|staff)\)\s*$/i, '').trim();
  const displayEmail = user?.email || '';
  const displayOrg = user?.organizationName || user?.unitName || 'Trường Đại học Sakura';
  const initials = getInitials(displayName);

  const rawRole = user?.role ? String(user.role).toUpperCase() : 'USER';
  const shortRoleBadge = (rawRole === 'APPROVER' || rawRole === 'OFFICER') ? 'STAFF' : rawRole;

  return (
    <div ref={ref} className="relative">
      {/* Trigger */}
      <button
        id="nav-avatar-btn"
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2.5 bg-white border-1.5 border-teal-700/80 hover:border-teal-700 rounded-full py-1.5 pl-1.5 pr-3 shadow-xs transition-all cursor-pointer"
        style={{ backgroundColor: '#ffffff' }}
        aria-label="Tài khoản"
        aria-expanded={open}
      >
        {user?.avatarUrl ? (
          <img
            src={user.avatarUrl}
            alt={displayName}
            className="w-7 h-7 rounded-full object-cover flex-shrink-0"
          />
        ) : (
          <div className="w-7 h-7 rounded-full bg-[#1e6b6b] text-white font-bold flex items-center justify-center text-xs flex-shrink-0">
            {initials}
          </div>
        )}
        <div className="hidden sm:flex flex-col items-start leading-tight text-left">
          <span className="text-[13px] font-bold text-slate-900" style={{ color: '#0f172a' }}>
            {displayName}
          </span>
          <span className="text-[10px] font-bold text-teal-700 tracking-wider uppercase">
            {shortRoleBadge}
          </span>
        </div>
        <ChevronDown
          size={14}
          className={`text-slate-500 transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          id="nav-avatar-dropdown"
          className="absolute top-[calc(100%+8px)] right-0 w-[315px] max-w-[calc(100vw-24px)] bg-white border border-slate-200 rounded-2xl shadow-2xl z-[1000] overflow-hidden animate-in fade-in zoom-in-95 duration-150 font-sans"
          style={{ backgroundColor: '#ffffff' }}
        >
          {/* User brief header */}
          <div className="p-4 flex items-center gap-3 bg-slate-50/80 border-b border-slate-100">
            {user?.avatarUrl ? (
              <img
                src={user.avatarUrl}
                alt={displayName}
                className="w-12 h-12 rounded-full object-cover border border-slate-200 flex-shrink-0"
              />
            ) : (
              <div className="w-12 h-12 rounded-full bg-[#1e6b6b] text-white font-bold flex items-center justify-center text-base flex-shrink-0 shadow-xs">
                {initials}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="font-bold text-slate-900 text-base truncate" style={{ color: '#0f172a' }}>
                {displayName}
              </div>
              <div className="text-slate-500 text-[12.5px] truncate mt-0.5">
                {displayEmail}
              </div>
              <div className="flex items-center gap-2 mt-1.5">
                <span className="text-[10.5px] font-bold text-teal-800 bg-teal-50 uppercase tracking-wider px-2 py-0.5 rounded-full border border-teal-200">
                  {shortRoleBadge}
                </span>
                <span className="text-slate-500 text-[12px] truncate">
                  {displayOrg}
                </span>
              </div>
            </div>
          </div>

          {/* Section 1: Tài khoản (Giữ nguyên như cũ) */}
          <div className="p-2 border-b border-slate-100">
            <div className="text-[11.5px] font-bold text-slate-400 uppercase tracking-wider px-3 py-1.5">
              Tài khoản
            </div>
            <Link
              to="/ho-so"
              onClick={() => setOpen(false)}
              className="flex items-center justify-between px-3 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 hover:text-slate-900 transition-colors text-[14.5px] font-medium"
            >
              <div className="flex items-center gap-3">
                <User size={17} className="text-slate-400" />
                <span>Hồ sơ cá nhân</span>
              </div>
              <ChevronRight size={15} className="text-slate-400" />
            </Link>
            <Link
              to="/ho-so?action=password"
              onClick={() => setOpen(false)}
              className="flex items-center justify-between px-3 py-2.5 rounded-xl text-slate-700 hover:bg-slate-100 hover:text-slate-900 transition-colors text-[14.5px] font-medium"
            >
              <div className="flex items-center gap-3">
                <KeyRound size={17} className="text-slate-400" />
                <span>Đổi mật khẩu</span>
              </div>
              <ChevronRight size={15} className="text-slate-400" />
            </Link>
          </div>

          {/* Section 2: Hoạt động của tôi (Có mục tổng, ấn vào xổ ra các mục bài đăng) */}
          <div className="p-2 border-b border-slate-100">
            <button
              type="button"
              onClick={() => setActivitiesExpanded(prev => !prev)}
              className="w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-slate-800 hover:bg-slate-100 transition-colors text-[14.5px] font-semibold cursor-pointer"
            >
              <div className="flex items-center gap-3">
                <FileText size={17} className="text-teal-600" />
                <span>Hoạt động của tôi</span>
              </div>
              <div className="flex items-center gap-2">
                {chatUnread > 0 && !activitiesExpanded && (
                  <span className="bg-[#ef4444] text-white text-[11px] font-bold min-w-5 h-5 px-1.5 rounded-full flex items-center justify-center">
                    {chatUnread > 99 ? '99+' : chatUnread}
                  </span>
                )}
                <ChevronDown
                  size={16}
                  className={`text-slate-400 transition-transform duration-200 ${activitiesExpanded ? 'rotate-180 text-teal-700' : ''}`}
                />
              </div>
            </button>

            {activitiesExpanded && (
              <div className="mt-1 pl-4 pr-1 py-1 space-y-1 animate-in slide-in-from-top-1 duration-150 bg-slate-50/70 rounded-xl">
                <Link
                  to="/bai-dang-cua-toi"
                  onClick={() => setOpen(false)}
                  className="flex items-center justify-between px-3 py-2 rounded-lg text-slate-700 hover:bg-white hover:text-slate-900 hover:shadow-xs transition-all text-[14px] font-medium"
                >
                  <div className="flex items-center gap-2.5">
                    <FileText size={16} className="text-sky-500" />
                    <span>Bài đăng của tôi</span>
                  </div>
                  <ChevronRight size={14} className="text-slate-300" />
                </Link>
                <Link
                  to="/bai-dang-da-luu"
                  onClick={() => setOpen(false)}
                  className="flex items-center justify-between px-3 py-2 rounded-lg text-slate-700 hover:bg-white hover:text-slate-900 hover:shadow-xs transition-all text-[14px] font-medium"
                >
                  <div className="flex items-center gap-2.5">
                    <Bookmark size={16} className="text-sky-500" />
                    <span>Bài đăng đã lưu</span>
                  </div>
                  <ChevronRight size={14} className="text-slate-300" />
                </Link>
                <Link
                  to="/claim-cua-toi"
                  onClick={() => setOpen(false)}
                  className="flex items-center justify-between px-3 py-2 rounded-lg text-slate-700 hover:bg-white hover:text-slate-900 hover:shadow-xs transition-all text-[14px] font-medium"
                >
                  <div className="flex items-center gap-2.5">
                    <HandHelping size={16} className="text-sky-500" />
                    <span>Yêu cầu nhận đồ</span>
                  </div>
                  <ChevronRight size={14} className="text-slate-300" />
                </Link>
                <Link
                  to="/bao-cao-cua-toi"
                  onClick={() => setOpen(false)}
                  className="flex items-center justify-between px-3 py-2 rounded-lg text-slate-700 hover:bg-white hover:text-slate-900 hover:shadow-xs transition-all text-[14px] font-medium"
                >
                  <div className="flex items-center gap-2.5">
                    <Flag size={16} className="text-sky-500" />
                    <span>Báo cáo của tôi</span>
                  </div>
                  <ChevronRight size={14} className="text-slate-300" />
                </Link>
                <Link
                  to="/tin-nhan"
                  onClick={() => setOpen(false)}
                  className="flex items-center justify-between px-3 py-2 rounded-lg text-slate-700 hover:bg-white hover:text-slate-900 hover:shadow-xs transition-all text-[14px] font-medium"
                >
                  <div className="flex items-center gap-2.5">
                    <MessageCircle size={16} className="text-sky-500" />
                    <span>Tin nhắn</span>
                  </div>
                  {chatUnread > 0 ? (
                    <span className="bg-[#ef4444] text-white text-[11px] font-bold min-w-5 h-5 px-1.5 rounded-full flex items-center justify-center">
                      {chatUnread > 99 ? '99+' : chatUnread}
                    </span>
                  ) : (
                    <ChevronRight size={14} className="text-slate-300" />
                  )}
                </Link>
              </div>
            )}
          </div>

          {/* Section 3: Trang quản lý (Giữ nguyên thẻ như cũ) */}
          {(isApprover || isAdmin || isManager) && (
            <div className="p-2 border-b border-slate-100">
              <Link
                to={isApprover ? '/approver/receipts' : '/admin'}
                onClick={() => setOpen(false)}
                className="block p-3 rounded-xl bg-slate-50/90 border border-slate-200 hover:bg-teal-50/60 hover:border-teal-300 transition-all group"
              >
                <div className="flex items-center gap-1.5 text-slate-500 text-[12px] font-medium mb-1.5">
                  <Shield size={14} className="text-teal-700" />
                  <span>{isApprover ? 'Quản lý · chỉ dành cho staff' : 'Quản lý · chỉ dành cho quản trị'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2.5 font-bold text-slate-900 text-[14.5px]">
                    <LayoutGrid size={17} className="text-teal-800" />
                    <span>Trang quản lý</span>
                  </div>
                  <ArrowRight size={15} className="text-slate-400 group-hover:text-teal-700 group-hover:translate-x-0.5 transition-all" />
                </div>
              </Link>
            </div>
          )}

          {/* Section 4: Đăng xuất (Giữ nguyên như cũ) */}
          <div className="p-2">
            <button
              id="nav-logout-btn"
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-rose-600 hover:bg-rose-50 transition-colors text-[14.5px] font-medium text-left cursor-pointer"
            >
              <LogOut size={17} className="text-rose-500" />
              <span>Đăng xuất</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/* ── MAIN NAVBAR ── */
export default function Navbar() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isAuthenticated, isAdmin, isApprover, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [isHeaderVisible, setIsHeaderVisible] = useState(true);
  const lastScrollYRef = useRef(0);

  const keepHeaderVisible = location.pathname === '/tim-kiem';
  const isActive = (path) => location.pathname === path;

  // Close mobile drawer on navigation and ensure header is visible
  useEffect(() => {
    setMobileMenuOpen(false);
    setIsHeaderVisible(true);
    lastScrollYRef.current = window.scrollY || 0;
  }, [location.pathname]);

  // Show header immediately when scrolling up; hide when scrolling down past navbar height
  useEffect(() => {
    lastScrollYRef.current = window.scrollY || 0;

    const handleScroll = () => {
      const currentY = Math.max(0, window.scrollY || document.documentElement.scrollTop || 0);
      const delta = currentY - lastScrollYRef.current;

      if (currentY <= 48) {
        setIsHeaderVisible(true);
      } else if (delta < -3) {
        setIsHeaderVisible(true);
      } else if (delta > 8 && currentY > 72 && !mobileMenuOpen) {
        setIsHeaderVisible(false);
      }

      lastScrollYRef.current = currentY;
    };

    const handleWheel = (event) => {
      if (event.deltaY < -2) {
        setIsHeaderVisible(true);
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    window.addEventListener('wheel', handleWheel, { passive: true });
    return () => {
      window.removeEventListener('scroll', handleScroll);
      window.removeEventListener('wheel', handleWheel);
    };
  }, [mobileMenuOpen]);

  useEffect(() => {
    const visible = keepHeaderVisible || isHeaderVisible || mobileMenuOpen;
    document.documentElement.style.setProperty('--navbar-offset', visible ? '68px' : '0px');
  }, [keepHeaderVisible, isHeaderVisible, mobileMenuOpen]);

  const handleMobileLogout = async () => {
    setMobileMenuOpen(false);
    await logout();
    navigate('/dang-nhap');
  };

  return (
    <>
      <nav className={`navbar ${!keepHeaderVisible && !isHeaderVisible && !mobileMenuOpen ? 'navbar--hidden' : ''}`}>
        <div className="container navbar-inner">
          {/* Logo Sagasu SVG */}
          <Link to="/" className="flex items-center gap-2 flex-shrink-0 group" aria-label="Sagasu">
            <img
              src={SagasuLogo}
              alt="Sagasu"
              className="h-10 sm:h-12 md:h-14 sm:h-[58px] w-auto object-contain transition-transform duration-200 group-hover:scale-105"
            />
          </Link>

          {/* Nav Links (Desktop) */}
          <ul className="nav-links">
            <li>
              <Link to="/" className={`nav-link ${isActive('/') ? 'active' : ''}`}>
                Trang chủ
              </Link>
            </li>
            <li>
              <Link to="/tim-kiem" className={`nav-link ${isActive('/tim-kiem') ? 'active' : ''}`}>
                Tìm kiếm
              </Link>
            </li>
          </ul>

          {/* Actions */}
          <div className="nav-actions">
            {isAuthenticated ? (
              <>
                {/* Notifications */}
                <ChatBadgeLink />
                <NotificationBell variant="navbar" />

                <Link to="/dang-tin" className="btn btn-accent btn-sm hidden xs:inline-flex" id="nav-post-btn">
                  <Plus size={15} />
                  <span className="hidden sm:inline">Đăng bài</span>
                </Link>

                <AvatarDropdown />
              </>
            ) : (
              <>
                <Link to="/dang-nhap" className="btn btn-ghost btn-sm" id="nav-login">
                  Đăng nhập
                </Link>
                <Link to="/dang-nhap" className="btn btn-accent btn-sm hidden xs:inline-flex" id="nav-register">
                  Truy cập hệ thống
                </Link>
              </>
            )}

            {/* Mobile Hamburger Toggle Button (< 768px) */}
            <button
              type="button"
              className="md:hidden p-2 rounded-lg text-slate-700 hover:bg-slate-100 transition-colors cursor-pointer"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Menu điều hướng"
              aria-expanded={mobileMenuOpen}
            >
              {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
            </button>
          </div>
        </div>
      </nav>

      {/* ── MOBILE NAVIGATION DRAWER (< 768px) ── */}
      {mobileMenuOpen && (
        <div className="md:hidden fixed inset-0 z-50 flex">
          {/* Backdrop */}
          <div
            className="fixed inset-0 bg-black/50 backdrop-blur-xs transition-opacity"
            onClick={() => setMobileMenuOpen(false)}
          />

          {/* Drawer content */}
          <div className="relative ml-auto w-72 max-w-[85vw] h-full bg-white shadow-2xl z-10 flex flex-col justify-between p-5 border-l border-slate-200 overflow-y-auto animate-in slide-in-from-right duration-200">
            <div className="space-y-6">
              {/* Drawer Top */}
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <Link to="/" onClick={() => setMobileMenuOpen(false)} className="flex items-center gap-2">
                  <img src={SagasuLogo} alt="Sagasu" className="h-9 w-auto object-contain" />
                </Link>
                <button
                  type="button"
                  onClick={() => setMobileMenuOpen(false)}
                  className="p-1.5 rounded-lg text-slate-500 hover:bg-slate-100 transition-colors"
                >
                  <X size={20} />
                </button>
              </div>

              {/* Navigation Links */}
              <div className="space-y-1">
                <div className="text-[11px] font-bold text-slate-400 uppercase tracking-wider px-3 mb-2">
                  Menu chính
                </div>
                <Link
                  to="/"
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                    isActive('/') ? 'bg-teal-50 text-teal-700' : 'text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <Home size={18} className={isActive('/') ? 'text-teal-600' : 'text-slate-400'} />
                  <span>Trang chủ</span>
                </Link>
                <Link
                  to="/tim-kiem"
                  onClick={() => setMobileMenuOpen(false)}
                  className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                    isActive('/tim-kiem') ? 'bg-teal-50 text-teal-700' : 'text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  <Search size={18} className={isActive('/tim-kiem') ? 'text-teal-600' : 'text-slate-400'} />
                  <span>Tìm kiếm đồ thất lạc</span>
                </Link>
                <Link
                  to="/dang-tin"
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-semibold text-teal-700 bg-teal-50/70 hover:bg-teal-50 transition-colors"
                >
                  <Plus size={18} className="text-teal-600" />
                  <span>Đăng bài tìm / nhặt đồ</span>
                </Link>
              </div>

              {/* Authenticated Links */}
              {isAuthenticated && (
                <div className="space-y-1 pt-4 border-t border-slate-100">
                  <div className="text-[11px] font-bold text-slate-400 uppercase tracking-wider px-3 mb-2">
                    Tài khoản cá nhân
                  </div>
                  <Link
                    to="/ho-so"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <User size={16} className="text-slate-400" />
                    <span>Hồ sơ cá nhân</span>
                  </Link>
                  <Link
                    to="/ho-so?action=password"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <KeyRound size={16} className="text-slate-400" />
                    <span>Đổi mật khẩu</span>
                  </Link>
                  <Link
                    to="/bai-dang-cua-toi"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <FileText size={16} className="text-slate-400" />
                    <span>Bài đăng của tôi</span>
                  </Link>
                  <Link
                    to="/bai-dang-da-luu"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <Bookmark size={16} className="text-slate-400" />
                    <span>Bài đăng đã lưu</span>
                  </Link>
                  <Link
                    to="/claim-cua-toi"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <HandHelping size={16} className="text-slate-400" />
                    <span>Yêu cầu nhận đồ</span>
                  </Link>
                  <Link
                    to="/bao-cao-cua-toi"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <Flag size={16} className="text-slate-400" />
                    <span>Báo cáo của tôi</span>
                  </Link>
                  <Link
                    to="/tin-nhan"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <MessageCircle size={16} className="text-slate-400" />
                    <span>Tin nhắn</span>
                  </Link>
                  {isAdmin && (
                    <Link
                      to="/admin"
                      onClick={() => setMobileMenuOpen(false)}
                      className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-teal-700 bg-teal-50/50 hover:bg-teal-50"
                    >
                      <LayoutDashboard size={16} className="text-teal-600" />
                      <span>Trang Quản trị & Dashboard</span>
                    </Link>
                  )}
                  {isApprover && (
                    <Link
                      to="/approver/receipts"
                      onClick={() => setMobileMenuOpen(false)}
                      className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-teal-700 bg-teal-50/50 hover:bg-teal-50"
                    >
                      <LayoutDashboard size={16} className="text-teal-600" />
                      <span>Hồ sơ tiếp nhận</span>
                    </Link>
                  )}
                </div>
              )}
            </div>

            {/* Bottom Actions */}
            <div className="pt-4 border-t border-slate-100">
              {isAuthenticated ? (
                <button
                  type="button"
                  onClick={handleMobileLogout}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold text-rose-600 bg-rose-50 hover:bg-rose-100 transition-colors"
                >
                  <LogOut size={16} />
                  <span>Đăng xuất</span>
                </button>
              ) : (
                <Link
                  to="/dang-nhap"
                  onClick={() => setMobileMenuOpen(false)}
                  className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold text-white bg-teal-700 hover:bg-teal-800 transition-colors"
                >
                  <span>Đăng nhập hệ thống</span>
                </Link>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
