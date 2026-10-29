import { useState, useRef, useEffect } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Bell, Plus, LogOut, User, LayoutDashboard, ChevronDown } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import SagasuLogo from '@/assets/Sagasu_logo_color.svg';

function AvatarDropdown() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  const { user, isAdmin, logout } = useAuth();

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

  const displayName = user?.fullName || user?.name || user?.email || 'Người dùng';
  const displayEmail = user?.email || '';
  const displayOrg = user?.organizationName || user?.unitName || 'Trường Đại học FPT';

  const roleLabels = {
    ADMIN: 'Quản trị viên',
    MANAGER: 'Quản lý tổ chức',
    OFFICER: 'Cán bộ tiếp nhận',
    CHECKER: 'Cán bộ kiểm duyệt',
    USER: 'Thành viên',
  };
  const roleName = user?.role ? (roleLabels[String(user.role).toUpperCase()] || user.role) : 'Thành viên';

  const initials = displayName
    .split(' ')
    .filter(Boolean)
    .slice(-2)
    .map((w) => w[0].toUpperCase())
    .join('');

  const avatarSrc = user?.avatarUrl || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName)}&backgroundColor=1e6b6b&textColor=ffffff`;

  const menuItems = [
    { icon: User, label: 'Hồ sơ cá nhân', to: '/ho-so' },
    { icon: Bell, label: 'Bài đăng của tôi', to: '/bai-dang-cua-toi' },
  ];

  if (isAdmin) {
    menuItems.push({ icon: LayoutDashboard, label: 'Trang quản trị & Quản lý', to: '/admin' });
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
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', lineHeight: 1.15 }}>
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
          style={{
            position: 'absolute', top: 'calc(100% + 10px)', right: 0,
            width: '250px',
            background: 'white',
            border: '1px solid var(--border)',
            borderRadius: 'var(--radius-xl)',
            boxShadow: '0 16px 48px rgba(0,0,0,0.12), 0 4px 16px rgba(0,0,0,0.06)',
            overflow: 'hidden',
            zIndex: 200,
            animation: 'scaleIn 150ms cubic-bezier(0.34,1.56,0.64,1)',
          }}
        >
          {/* User info header */}
          <div style={{ padding: '16px 16px 12px', borderBottom: '1px solid var(--border)' }}>
            <div style={{ fontWeight: 700, fontSize: '0.9rem', color: 'var(--text-primary)', marginBottom: '2px' }}>
              {displayName}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '4px' }}>
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
  const { isAuthenticated } = useAuth();
  const isActive = (path) => location.pathname === path;
  const isHomePage = location.pathname === '/';

  return (
    <nav className="navbar" style={isHomePage ? { position: 'relative', top: 'auto' } : {}}>
      <div className="container navbar-inner">
        {/* Logo Sagasu SVG */}
        <Link to="/" className="flex items-center gap-2 flex-shrink-0 group" aria-label="Sagasu">
          <img
            src={SagasuLogo}
            alt="Sagasu"
            className="h-14 sm:h-[58px] w-auto object-contain transition-transform duration-200 group-hover:scale-105"
          />
        </Link>

        {/* Nav Links */}
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
              <button className="nav-icon-btn" id="nav-notifications" aria-label="Thông báo">
                <Bell size={18} />
                <span className="nav-badge">1</span>
              </button>

              <Link to="/dang-tin" className="btn btn-accent btn-sm" id="nav-post-btn">
                <Plus size={15} />
                Đăng tin
              </Link>
              <AvatarDropdown />
            </>
          ) : (
            <>
              <Link to="/dang-nhap" className="btn btn-ghost btn-sm" id="nav-login">
                Đăng nhập
              </Link>
              <Link to="/dang-nhap" className="btn btn-accent btn-sm" id="nav-register">
                Truy cập hệ thống
              </Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
