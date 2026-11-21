// src/components/layouts/admin/AdminSidebar.jsx
import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { LogOut, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { ADMIN_NAV_SECTIONS } from './adminNavConfig';
import { useAuth } from '@/context/AuthContext';
import './AdminSidebar.css';

const STORAGE_KEY = 'sagasu_admin_sidebar_collapsed';

function formatBadgeCount(count) {
  if (!count || count <= 0) return '';
  return count > 99 ? '99+' : String(count);
}

export default function AdminSidebar({ pendingReportsCount = 0, navBadges = {} }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === '1';
    } catch {
      return false;
    }
  });

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0');
      } catch {
        // Ignore storage errors
      }
      return next;
    });
  };

  const isItemActive = (path, exact) => {
    if (exact) return location.pathname === path;
    return location.pathname.startsWith(path);
  };

  const handleLogout = async (event) => {
    event?.preventDefault();
    event?.stopPropagation();
    await logout();
    navigate('/dang-nhap', { replace: true });
  };

  const rawRole = user?.role ? String(user.role).toUpperCase() : 'ADMIN';
  const roleHeading = rawRole === 'APPROVER' ? 'Staff' : rawRole === 'MANAGER' ? 'Quản lý' : 'Quản trị';

  const visibleSections = ADMIN_NAV_SECTIONS.filter((section) =>
    section.items.some((item) => !item.roles || item.roles.includes(user?.role)),
  );
  const showSectionHeadings = visibleSections.length > 1;

  return (
    <aside
      className={`admin-sidebar ${collapsed ? 'is-collapsed' : ''}`}
      aria-label={`Điều hướng ${roleHeading}`}
    >
      <div className="admin-sidebar-upper">
        {/* Top Heading + Collapse Toggle */}
        <div className="admin-sidebar-top">
          {!collapsed && <span className="admin-sidebar-role-heading">{roleHeading}</span>}
          <button
            type="button"
            onClick={toggleCollapsed}
            className="admin-sidebar-toggle"
            aria-label={collapsed ? 'Mở thanh điều hướng bên trái' : 'Thu gọn thanh điều hướng bên trái'}
            title={collapsed ? 'Mở thanh bên trái' : 'Thu gọn thanh bên trái'}
            aria-expanded={!collapsed}
          >
            {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
          </button>
        </div>

        {/* Role Navigation */}
        <nav className="admin-sidebar-nav" aria-label={`Điều hướng ${roleHeading}`}>
          {visibleSections.map((section, sectionIdx) => (
            <div key={section.title} className="admin-sidebar-section">
              {!collapsed && showSectionHeadings && (
                <div className="admin-sidebar-heading">{section.title}</div>
              )}
              {collapsed && sectionIdx > 0 && (
                <div className="admin-sidebar-section-sep" aria-hidden="true" />
              )}

              {section.items
                .filter((item) => !item.roles || item.roles.includes(user?.role))
                .map((item) => {
                  const active = isItemActive(item.path, item.exact);
                  const Icon = item.icon;
                  const badgeCount = item.badgeKey
                    ? (navBadges[item.badgeKey] ?? (item.badgeKey === 'pendingReports' ? pendingReportsCount : 0))
                    : 0;
                  const badgeText = formatBadgeCount(badgeCount);
                  const tone =
                    item.badgeTone === 'danger'
                      ? 'danger'
                      : item.badgeTone === 'warning'
                        ? 'warning'
                        : 'neutral';

                  return (
                    <Link
                      key={item.path}
                      to={item.path}
                      title={collapsed ? (badgeText ? `${item.label} (${badgeText})` : item.label) : undefined}
                      aria-label={badgeText ? `${item.label} (${badgeText})` : item.label}
                      className={`admin-sidebar-link ${active ? 'is-active' : ''}`}
                    >
                      <Icon size={19} strokeWidth={1.85} aria-hidden="true" />
                      {!collapsed && (
                        <span className={`admin-sidebar-label ${item.wrapLabel ? 'is-wrap' : ''}`}>
                          {item.label}
                        </span>
                      )}
                      {badgeText && (
                        <span className={`admin-sidebar-badge is-${tone}`} aria-hidden="true">
                          {badgeText}
                        </span>
                      )}
                    </Link>
                  );
                })}
            </div>
          ))}
        </nav>

        {!collapsed && <div className="admin-sidebar-divider" aria-hidden="true" />}
      </div>

      {/* Clean Profile-style Footer (Logout) */}
      <div className="admin-sidebar-footer">
        <button
          type="button"
          className="admin-sidebar-logout"
          onClick={handleLogout}
          title={collapsed ? 'Đăng xuất' : undefined}
          aria-label="Đăng xuất"
        >
          <LogOut size={19} strokeWidth={1.85} aria-hidden="true" />
          {!collapsed && <span>Đăng xuất</span>}
        </button>
      </div>
    </aside>
  );
}
