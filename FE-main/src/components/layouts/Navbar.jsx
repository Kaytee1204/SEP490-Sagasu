import { useState, useRef, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Bell, Plus, LogOut, User, LayoutDashboard, ChevronDown, Menu, X, Home, Search, ShieldCheck } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import SagasuLogo from '@/assets/Sagasu_logo_color.svg';
import ChatBadgeLink from '@/features/chat/components/ChatBadgeLink';

function AvatarDropdown() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  const { user, isAdmin, isApprover, logout } = useAuth();

  // Close on outside click
  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleLogout = async () => {
    setOpen(false);
    await logout();
    navigate('/dang-nhap');
  };

  const rawName = user?.fullName || user?.name || user?.email || 'Người dùng';
  const displayName = String(rawName).replace(/\s*\((manager|admin|user|approver)\)\s*$/i, '').trim();
  const displayEmail = user?.email || '';
  const displayOrg = user?.organizationName || user?.unitName || 'Trường Đại học FPT';

  const roleLabels = {
    ADMIN: 'Quản trị viên',
    MANAGER: 'Quản lý tổ chức',
    OFFICER: 'Cán bộ tiếp nhận',
    USER: 'Thành viên',
  };
  const roleName = user?.role ? (roleLabels[String(user.role).toUpperCase()] || user.role) : 'Thành viên';

  const avatarSrc = user?.avatarUrl || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName)}&backgroundColor=1e6b6b&textColor=ffffff`;

  const menuItems = [
    { icon: User, label: 'Hồ sơ cá nhân', to: '/ho-so' },
    { icon: Bell, label: 'Bài đăng của tôi', to: '/bai-dang-cua-toi' },
    { icon: ShieldCheck, label: 'Claim của tôi', to: '/claim-cua-toi' },
    { icon: Bell, label: 'Tin nhắn', to: '/tin-nhan' },
  ];

  if (isAdmin) {
    menuItems.push({ icon: LayoutDashboard, label: 'Trang quản trị & Quản lý', to: '/admin' });
  }
  if (isApprover) {
    menuItems.push({ icon: LayoutDashboard, label: 'Phiếu tiếp nhận', to: '/approver/receipts' });
  }

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      {/* Trigger */}
      <button
        id="nav-avatar-btn"
        onClick={() => setOpen(!open)}
        style={{
          display: 'flex', alignItems: 'center', gap: '6px',
          background: 'none', border: '1.5px solid var(--border-strong)',
          borderRadius: '999px', padding: '3px 10px 3px 3px',
          cursor: 'pointer', transition: 'all 200ms',
          color: 'var(--text-primary)',
        }}
        onMouseEnter={e => e.currentTarget.style.borderColor = 'var(--accent)'}
        onMouseLeave={e => e.currentTarget.style.borderColor = 'var(--border-strong)'}
        aria-label="Tài khoản"
        aria-expanded={open}
      >
        <img
          src={avatarSrc}
          alt={displayName}
          style={{ width: 30, height: 30, borderRadius: '50%', flexShrink: 0, objectFit: 'cover' }}
        />
        <div className="hidden sm:flex flex-col items-start leading-[1.15]">
          <span style={{ fontSize: '0.78rem', fontWeight: 700, color: 'var(--text-primary)' }}>{displayName}</span>
          <span style={{ fontSize: '0.65rem', fontWeight: 600, color: 'var(--accent)', textTransform: 'uppercase' }}>
            {user?.role || 'USER'}
          </span>
        </div>
        <ChevronDown
          size={13}
          style={{
            transition: 'transform 200ms cubic-bezier(0.34,1.56,0.64,1)',
            transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
            color: 'var(--text-muted)',
          }}
        />
      </button>

      {/* Dropdown panel */}
      {open && (
        <div
          id="nav-avatar-dropdown"
          style={{
            position: 'absolute', top: 'calc(100% + 8px)', right: 0,
            width: '270px', maxWidth: 'calc(100vw - 24px)', background: 'var(--bg-surface)',
            border: '1px solid var(--border-strong)',
            borderRadius: 'var(--radius-xl)',
            boxShadow: 'var(--shadow-xl)',
            zIndex: 1000,
            animation: 'fadeInUp 180ms cubic-bezier(0.34,1.56,0.64,1)',
            overflow: 'hidden',
          }}
        >
          {/* User brief */}
          <div style={{
            padding: '14px 16px',
            background: 'var(--bg-canvas)',
            borderBottom: '1px solid var(--border)',
          }}>
            <div style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)' }}>
              {displayName}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px', wordBreak: 'break-all' }}>
              {displayEmail}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginTop: '6px' }}>
              <span style={{
                fontSize: '0.68rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.08em',
                background: 'var(--status-found-bg)', color: 'var(--status-found)',
                border: '1px solid rgba(16,185,129,0.2)',
                padding: '1px 7px', borderRadius: '4px',
              }}>
                {roleName}
              </span>
              <span style={{ fontSize: '0.7rem', color: 'var(--text-muted)' }}>{displayOrg}</span>
            </div>
          </div>

          {/* Menu links */}
          <div style={{ padding: '8px' }}>
            {menuItems.map((item) => {
              const Icon = item.icon;
              return (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={() => setOpen(false)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '10px',
                    padding: '9px 10px',
                    borderRadius: 'var(--radius-md)',
                    color: 'var(--text-secondary)',
                    textDecoration: 'none', fontSize: '0.875rem',
                    transition: 'all 150ms',
                  }}
                  onMouseEnter={e => { e.currentTarget.style.background = 'var(--bg-canvas)'; e.currentTarget.style.color = 'var(--text-primary)'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = 'var(--text-secondary)'; }}
                >
                  <Icon size={15} strokeWidth={1.8} />
                  {item.label}
                </Link>
              );
            })}
          </div>

          {/* Logout */}
          <div style={{ padding: '8px', borderTop: '1px solid var(--border)' }}>
            <button
              id="nav-logout-btn"
              onClick={handleLogout}
              style={{
                display: 'flex', alignItems: 'center', gap: '10px',
                padding: '9px 10px', width: '100%',
                borderRadius: 'var(--radius-md)',
                color: '#e03e2d', background: 'none', border: 'none',
                fontSize: '0.875rem', cursor: 'pointer',
                transition: 'all 150ms', fontFamily: 'var(--font-body)',
                textAlign: 'left',
              }}
              onMouseEnter={e => { e.currentTarget.style.background = 'rgba(224,62,45,0.08)'; }}
              onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; }}
            >
              <LogOut size={15} strokeWidth={1.8} />
              Đăng xuất an toàn
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
  const { isAuthenticated, user, isAdmin, isApprover, logout } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const isActive = (path) => location.pathname === path;
  const isHomePage = location.pathname === '/';

  // Close mobile drawer on navigation
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const handleMobileLogout = async () => {
    setMobileMenuOpen(false);
    await logout();
    navigate('/dang-nhap');
  };

  return (
    <>
      <nav className="navbar" style={isHomePage ? { position: 'relative', top: 'auto' } : {}}>
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
                <button className="nav-icon-btn hidden sm:flex" id="nav-notifications" aria-label="Thông báo">
                  <Bell size={18} />
                  <span className="nav-badge">1</span>
                </button>

                <Link to="/dang-tin" className="btn btn-accent btn-sm hidden xs:inline-flex" id="nav-post-btn">
                  <Plus size={15} />
                  <span className="hidden sm:inline">Đăng tin</span>
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
                  <span>Đăng tin tìm / nhặt đồ</span>
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
                    to="/bai-dang-cua-toi"
                    onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    <Bell size={16} className="text-slate-400" />
                    <span>Bài đăng của tôi</span>
                  </Link>
                  <Link to="/claim-cua-toi" onClick={() => setMobileMenuOpen(false)}
                    className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm font-medium text-slate-700 hover:bg-slate-50">
                    <ShieldCheck size={16} className="text-slate-400" /><span>Claim của tôi</span>
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
                      <span>Phiếu tiếp nhận</span>
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
                  <span>Đăng xuất an toàn</span>
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
