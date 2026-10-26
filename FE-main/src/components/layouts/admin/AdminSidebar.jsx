// src/components/layouts/admin/AdminSidebar.jsx
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ArrowUpRight, LogOut, X } from 'lucide-react';
import { ADMIN_NAV_SECTIONS } from './adminNavConfig';
import { useAuth } from '@/context/AuthContext';

export default function AdminSidebar({ pendingReportsCount = 0, isOpen = false, onClose = () => {} }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const isItemActive = (path, exact) => {
    if (exact) return location.pathname === path;
    return location.pathname.startsWith(path);
  };

  const handleLogout = async () => {
    await logout();
    navigate('/dang-nhap');
  };

  const displayName = user?.fullName || user?.email || 'Quản trị viên';
  const displayRole = user?.role || 'ADMIN';
  const avatarSrc = `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName)}&backgroundColor=1e6b6b&textColor=ffffff`;

  const sidebarContent = (
    <div className="flex flex-col h-full bg-[#1c2836] text-[#f8fafc] border-r border-white/10 select-none">
      {/* Brand Header */}
      <div className="p-4 px-5 border-b border-white/10 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-teal-500 to-teal-700 flex items-center justify-center text-white font-extrabold text-base shadow-md shadow-teal-500/20">
            S
          </div>
          <div>
            <div className="font-extrabold text-base tracking-tight text-white leading-tight">Sagasu</div>
            <div className="inline-flex items-center gap-1.5 text-[11px] font-semibold text-teal-300 tracking-wider">
              <span className="w-1.5 h-1.5 rounded-full bg-teal-400 animate-pulse" />
              {displayRole} Dashboard
            </div>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="lg:hidden p-1.5 rounded-md text-slate-400 hover:text-white hover:bg-white/10 transition-colors"
        >
          <X size={18} />
        </button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto p-3 space-y-6">
        {ADMIN_NAV_SECTIONS.map((section) => (
          <div key={section.title} className="space-y-1">
            <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-3 mb-1">
              {section.title}
            </div>
            {section.items.map((item) => {
              const active = isItemActive(item.path, item.exact);
              const Icon = item.icon;
              const hasBadge = item.badgeKey === 'pendingReports' && pendingReportsCount > 0;
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  onClick={onClose}
                  className={`flex items-center justify-between gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-all ${
                    active
                      ? 'bg-teal-500/15 text-teal-300 font-semibold border border-teal-500/30'
                      : 'text-slate-300 hover:text-white hover:bg-white/5 border border-transparent'
                  }`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <Icon size={17} className={active ? 'text-teal-400' : 'text-slate-400'} />
                    <span className="truncate">{item.label}</span>
                  </div>
                  {hasBadge && (
                    <span className="text-[11px] font-bold bg-rose-500 text-white px-2 py-0.5 rounded-full min-w-5 text-center">
                      {pendingReportsCount}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* Bottom Profile */}
      <div className="p-3 border-t border-white/10 bg-black/20 space-y-2">
        <Link
          to="/"
          className="flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium text-slate-400 hover:text-white bg-white/5 hover:bg-white/10 border border-white/5 transition-all"
        >
          <span>Về trang người dùng</span>
          <ArrowUpRight size={14} />
        </Link>
        <div className="flex items-center gap-3 px-2 py-1.5">
          <img
            src={avatarSrc}
            alt={displayName}
            className="w-8 h-8 rounded-full border border-teal-400 object-cover"
          />
          <div className="flex-1 min-w-0">
            <div className="text-xs font-semibold text-white truncate">{displayName}</div>
            <div className="text-[11px] text-teal-300 font-semibold">{displayRole}</div>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="p-1.5 rounded-md text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
            title="Đăng xuất"
          >
            <LogOut size={16} />
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <>
      <aside className="hidden lg:block w-64 h-screen sticky top-0 shrink-0 z-30">{sidebarContent}</aside>
      {isOpen && (
        <div className="lg:hidden fixed inset-0 z-50 flex">
          <div className="fixed inset-0 bg-black/60 backdrop-blur-sm" onClick={onClose} />
          <div className="relative w-72 max-w-[85vw] h-full shadow-2xl z-10">{sidebarContent}</div>
        </div>
      )}
    </>
  );
}
