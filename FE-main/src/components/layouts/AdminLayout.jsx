import { Link, useNavigate, useLocation, Outlet } from 'react-router-dom';
import { LayoutDashboard, Package, Users, ExternalLink, LogOut, AlertTriangle, RefreshCw, Bell, ArrowUpRight, CheckCircle2 } from 'lucide-react';
import { useState } from 'react';
import { INITIAL_REPORTS, CURRENT_USER } from '@/features/admin/pages/mockData';

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [reports] = useState(INITIAL_REPORTS);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMsg, setToastMsg] = useState(null);

  const pendingReportsCount = reports.filter(r => r.status === 'pending').length;

  const triggerRefresh = () => {
    setIsRefreshing(true);
    setToastMsg('Dữ liệu đã được đồng bộ mới nhất');
    setTimeout(() => setIsRefreshing(false), 600);
    setTimeout(() => setToastMsg(null), 3000);
  };

  const isActive = (path) => {
    if (path === '/admin' && location.pathname === '/admin') return true;
    if (path !== '/admin' && location.pathname.startsWith(path)) return true;
    return false;
  };

  const getBreadcrumb = () => {
    if (location.pathname.startsWith('/admin/items')) return 'Tin đăng đồ thất lạc';
    if (location.pathname.startsWith('/admin/reports')) return 'Báo cáo & Vi phạm';
    if (location.pathname.startsWith('/admin/users/')) return 'Chi tiết tài khoản';
    if (location.pathname.startsWith('/admin/users')) return 'Tài khoản thành viên';
    return 'Bảng điều khiển';
  };

  return (
    <div style={{ display: 'flex', minHeight: '100dvh', background: '#f8fafc', color: 'var(--text-primary)', fontFamily: 'var(--font-body)' }}>
      {/* ── SIDEBAR (Soft Slate-800: Modern, comfortable, not pitch black, not glaring white) ── */}
      <aside style={{
        width: '250px', background: '#1c2836', color: '#f8fafc',
        display: 'flex', flexDirection: 'column', flexShrink: 0,
        borderRight: '1px solid rgba(255,255,255,0.08)',
        position: 'sticky', top: 0, height: '100dvh', zIndex: 30
      }}>
        <style>{`
          .admin-sidebar-nav-link {
            display: flex;
            align-items: center;
            gap: 10px;
            text-decoration: none;
            padding: 9px 12px;
            border-radius: 6px;
            font-size: 0.85rem;
            font-weight: 500;
            color: #94a3b8;
            border: 1px solid transparent;
            transition: all 120ms ease;
          }
          .admin-sidebar-nav-link:hover {
            color: #ffffff;
            background: rgba(255, 255, 255, 0.07);
          }
          .admin-sidebar-nav-link.active {
            background: rgba(45, 212, 191, 0.14);
            color: #2dd4bf;
            border-color: rgba(45, 212, 191, 0.25);
            font-weight: 600;
          }
          .admin-sidebar-nav-link.active svg {
            color: #2dd4bf;
          }
          .admin-sidebar-ext:hover {
            color: #ffffff !important;
            background: rgba(255, 255, 255, 0.08) !important;
            border-color: rgba(45, 212, 191, 0.3) !important;
          }
          .admin-sidebar-logout:hover {
            background: rgba(239, 68, 68, 0.15) !important;
          }
        `}</style>

        {/* Brand Header */}
        <div style={{ padding: '20px 18px', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{
            width: '34px', height: '34px', borderRadius: '8px',
            background: 'linear-gradient(135deg, var(--teal-500), var(--teal-700))',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: 'white', fontWeight: 800, fontSize: '1rem',
            boxShadow: '0 2px 8px rgba(45, 212, 191, 0.25)'
          }}>
            T
          </div>
          <div>
            <div style={{ fontFamily: 'var(--font-display)', fontWeight: 800, fontSize: '1.05rem', letterSpacing: '-0.02em', color: '#ffffff', lineHeight: 1.2 }}>
              TimDo
            </div>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '5px', fontSize: '0.65rem', fontWeight: 600, color: 'var(--teal-300)', letterSpacing: '0.04em' }}>
              <span style={{ width: 5, height: 5, borderRadius: '50%', background: 'var(--teal-400)', display: 'inline-block' }} />
              Admin Console
            </div>
          </div>
        </div>

        {/* Navigation List */}
        <nav style={{ padding: '16px 10px', display: 'flex', flexDirection: 'column', gap: '4px', flex: 1, overflowY: 'auto' }}>
          <div style={{ fontSize: '0.65rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.08em', padding: '6px 12px 2px' }}>
            Tổng quan
          </div>

          <Link
            to="/admin"
            className={`admin-sidebar-nav-link ${isActive('/admin') ? 'active' : ''}`}
          >
            <LayoutDashboard size={16} />
            <span>Bảng điều khiển</span>
          </Link>

          <div style={{ fontSize: '0.65rem', fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.08em', padding: '14px 12px 2px' }}>
            Quản lý dữ liệu
          </div>

          <Link
            to="/admin/items"
            className={`admin-sidebar-nav-link ${isActive('/admin/items') ? 'active' : ''}`}
          >
            <Package size={16} />
            <span style={{ flex: 1 }}>Tin đăng</span>
          </Link>

          <Link
            to="/admin/reports"
            className={`admin-sidebar-nav-link ${isActive('/admin/reports') ? 'active' : ''}`}
            style={{ justifyContent: 'space-between' }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <AlertTriangle size={16} />
              <span>Báo cáo vi phạm</span>
            </div>
            {pendingReportsCount > 0 && (
              <span style={{
                fontSize: '0.65rem', fontWeight: 700,
                background: '#ef4444', color: 'white',
                padding: '1px 6px', borderRadius: '10px', minWidth: '18px', textAlign: 'center'
              }}>
                {pendingReportsCount}
              </span>
            )}
          </Link>

          <Link
            to="/admin/users"
            className={`admin-sidebar-nav-link ${isActive('/admin/users') ? 'active' : ''}`}
          >
            <Users size={16} />
            <span>Tài khoản thành viên</span>
          </Link>
        </nav>

        {/* Bottom Sidebar: External Link & Admin Profile */}
        <div style={{ padding: '12px 14px', borderTop: '1px solid rgba(255,255,255,0.08)', display: 'flex', flexDirection: 'column', gap: '8px', background: 'rgba(0,0,0,0.12)' }}>
          <Link
            to="/"
            className="admin-sidebar-ext"
            style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '8px 10px', borderRadius: '6px',
              color: '#94a3b8', fontSize: '0.775rem', fontWeight: 500,
              textDecoration: 'none', background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.06)',
              transition: 'all 120ms ease'
            }}
          >
            <span>Về giao diện người dùng</span>
            <ArrowUpRight size={13} />
          </Link>

          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '6px 4px' }}>
            <img
              src={CURRENT_USER.avatar}
              alt={CURRENT_USER.name}
              style={{ width: '32px', height: '32px', borderRadius: '50%', border: '1.5px solid var(--teal-400)', objectFit: 'cover' }}
            />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'white', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {CURRENT_USER.name}
              </div>
              <div style={{ fontSize: '0.675rem', color: '#64748b' }}>Quản trị viên</div>
            </div>
            <button
              type="button"
              onClick={() => navigate('/dang-nhap')}
              className="admin-sidebar-logout"
              style={{ background: 'none', border: 'none', color: '#f87171', cursor: 'pointer', padding: '6px', borderRadius: '6px', display: 'flex', alignItems: 'center', transition: 'all 120ms ease' }}
              title="Đăng xuất"
            >
              <LogOut size={15} />
            </button>
          </div>
        </div>
      </aside>

      {/* ── MAIN CONTENT WORKSPACE ── */}
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0, overflowY: 'auto' }}>
        {/* Sleek Top Bar (Single global source of truth, no duplicate title headers) */}
        <header style={{
          height: '56px', background: 'white', borderBottom: '1px solid rgba(0,0,0,0.06)',
          padding: '0 28px', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          position: 'sticky', top: 0, zIndex: 20
        }}>
          {/* Breadcrumb path */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.825rem' }}>
            <span style={{ color: 'var(--text-muted)', fontWeight: 500 }}>Portal</span>
            <span style={{ color: 'var(--zinc-300)' }}>/</span>
            <span style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{getBreadcrumb()}</span>
          </div>

          {/* Quick Global Actions */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            {toastMsg && (
              <div style={{
                display: 'inline-flex', alignItems: 'center', gap: '6px',
                fontSize: '0.75rem', fontWeight: 600, color: '#059669',
                background: '#ecfdf5', padding: '4px 10px', borderRadius: '6px',
                border: '1px solid #a7f3d0'
              }}>
                <CheckCircle2 size={13} /> {toastMsg}
              </div>
            )}

            <button
              type="button"
              onClick={triggerRefresh}
              style={{
                display: 'inline-flex', alignItems: 'center', gap: '6px',
                padding: '6px 10px', borderRadius: '6px',
                border: '1px solid var(--border)', background: 'white',
                color: 'var(--text-secondary)', fontSize: '0.775rem', fontWeight: 500,
                cursor: 'pointer', transition: 'all 120ms'
              }}
              title="Đồng bộ dữ liệu"
            >
              <RefreshCw size={13} style={{ transform: isRefreshing ? 'rotate(180deg)' : 'none', transition: 'transform 500ms' }} />
              <span>Đồng bộ</span>
            </button>

            <button
              type="button"
              onClick={() => navigate('/admin/reports')}
              style={{
                width: '32px', height: '32px', borderRadius: '6px',
                border: '1px solid var(--border)', background: 'white',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: 'var(--text-secondary)', cursor: 'pointer', position: 'relative'
              }}
              title="Thông báo vi phạm"
            >
              <Bell size={15} />
              {pendingReportsCount > 0 && (
                <span style={{ position: 'absolute', top: '5px', right: '5px', width: '6px', height: '6px', borderRadius: '50%', background: '#ef4444' }} />
              )}
            </button>
          </div>
        </header>

        {/* Child Pages Outlet */}
        <div style={{ flex: 1 }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}

