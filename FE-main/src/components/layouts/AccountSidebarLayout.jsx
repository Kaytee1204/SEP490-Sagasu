import { useContext, useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  User,
  FileText,
  Bookmark,
  Send,
  Flag,
  MessageCircle,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { SavedPostsContext } from '@/features/items/context/SavedPostsContext';
import { getMyPosts } from '@/features/items/api/postApi';
import { getMyClaims, getReceivedClaims } from '@/features/claims/api/claimApi';
import { getReportActionCounts } from '@/features/reports/api/reportApi';
import { getUnreadCount } from '@/features/chat/api/chatApi';
import './AccountSidebarLayout.css';

const STORAGE_KEY = 'sagasu_account_sidebar_collapsed';
const NO_REPORT_ACTIONS = { mine: 0, related: 0 };

const ACCOUNT_NAV_ITEMS = [
  {
    key: 'profile',
    label: 'Hồ sơ cá nhân',
    to: '/ho-so',
    icon: User,
    match: (path) => path === '/ho-so',
  },
  {
    key: 'posts',
    label: 'Bài đăng của tôi',
    to: '/bai-dang-cua-toi',
    icon: FileText,
    match: (path) => path === '/bai-dang-cua-toi' || path === '/tin-dang-cua-toi',
  },
  {
    key: 'saved',
    label: 'Bài đăng đã lưu',
    to: '/bai-dang-da-luu',
    icon: Bookmark,
    match: (path) => path === '/bai-dang-da-luu',
  },
  {
    key: 'claims',
    label: 'Yêu cầu nhận đồ',
    to: '/claim-cua-toi',
    icon: Send,
    match: (path) =>
      path === '/claim-cua-toi' ||
      /^\/bai-dang\/\d+\/claims/.test(path) ||
      path.startsWith('/van-phong/claims') ||
      path.startsWith('/van-phong/chuyen-giao'),
  },
  {
    key: 'reports',
    label: 'Báo cáo của tôi',
    to: '/bao-cao-cua-toi',
    icon: Flag,
    match: (path) =>
      path === '/bao-cao-cua-toi' ||
      path === '/bao-cao-trao-doi' ||
      path === '/ket-qua-bao-cao' ||
      path.startsWith('/bao-cao/'),
  },
  {
    key: 'messages',
    label: 'Tin nhắn',
    to: '/tin-nhan',
    icon: MessageCircle,
    match: (path) => path === '/tin-nhan' || path.startsWith('/tin-nhan/'),
  },
];

function formatBadgeCount(count) {
  if (!count || count <= 0) return '';
  return count > 99 ? '99+' : String(count);
}

function readTotal(settled) {
  if (settled.status !== 'fulfilled') return 0;
  const val = settled.value;
  if (typeof val === 'number') return val;
  return Number(val?.totalElements ?? 0) || 0;
}

export default function AccountSidebarLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const savedCtx = useContext(SavedPostsContext);
  const savedCount = savedCtx?.ids?.size ?? 0;

  const [collapsed, setCollapsed] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === '1';
    } catch {
      return false;
    }
  });

  const [counts, setCounts] = useState({
    postsTotal: 0,
    postsPending: 0,
    claimsReceivedPending: 0,
    claimsSentPending: 0,
    claimsPending: 0,
    reportActions: NO_REPORT_ACTIONS,
    unreadMessages: 0,
  });

  useEffect(() => {
    if (!user?.id) return;
    let active = true;
    let lastRequest = 0;

    const loadCounts = async () => {
      const request = ++lastRequest;
      const results = await Promise.allSettled([
        getMyPosts(1, 'ALL', 'ALL'),
        getMyPosts(1, 'PENDING', 'ALL'),
        getReceivedClaims(1, 'PENDING'),
        getMyClaims(1, 'PENDING'),
        getReportActionCounts(),
        getUnreadCount(),
      ]);

      if (!active || request !== lastRequest) return;

      const reportActions = results[4];
      const receivedPending = readTotal(results[2]);
      const sentPending = readTotal(results[3]);
      setCounts({
        postsTotal: readTotal(results[0]),
        postsPending: readTotal(results[1]),
        claimsReceivedPending: receivedPending,
        claimsSentPending: sentPending,
        claimsPending: receivedPending + sentPending,
        reportActions: reportActions.status === 'fulfilled' ? reportActions.value ?? NO_REPORT_ACTIONS : NO_REPORT_ACTIONS,
        unreadMessages: readTotal(results[5]),
      });
    };

    loadCounts();
    window.addEventListener('focus', loadCounts);
    window.addEventListener('sagasu-counts-changed', loadCounts);
    return () => {
      active = false;
      window.removeEventListener('focus', loadCounts);
      window.removeEventListener('sagasu-counts-changed', loadCounts);
    };
  }, [user?.id, location.pathname]);

  const reportsNeedingAction = counts.reportActions.mine + counts.reportActions.related;
  const accountBadges = {
    posts:
      counts.postsPending > 0
        ? {
            count: counts.postsPending,
            tone: 'warning',
            title: `${counts.postsPending} bài đăng chờ duyệt (tổng ${counts.postsTotal} bài)`,
          }
        : counts.postsTotal > 0
          ? {
              count: counts.postsTotal,
              tone: 'neutral',
              title: `${counts.postsTotal} bài đăng`,
            }
          : null,
    saved:
      savedCount > 0
        ? {
            count: savedCount,
            tone: 'neutral',
            title: `${savedCount} bài đăng đã lưu`,
          }
        : null,
    claims:
      counts.claimsPending > 0
        ? {
            count: counts.claimsPending,
            tone: 'warning',
            title: `${counts.claimsPending} yêu cầu đang chờ duyệt`,
          }
        : null,
    reports:
      reportsNeedingAction > 0
        ? {
            count: reportsNeedingAction,
            tone: 'danger',
            title: `${reportsNeedingAction} báo cáo cần bạn xử lý`,
          }
        : null,
    messages:
      counts.unreadMessages > 0
        ? {
            count: counts.unreadMessages,
            tone: 'danger',
            title: `${counts.unreadMessages} tin nhắn chưa đọc`,
          }
        : null,
  };

  const toggleSidebar = () => {
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

  const handleLogout = async (event) => {
    event?.preventDefault();
    event?.stopPropagation();
    await logout();
    navigate('/dang-nhap', { replace: true });
  };

  return (
    <div className="account-shell">
      <aside
        className={`account-sidebar ${collapsed ? 'is-collapsed' : ''}`}
        aria-label="Điều hướng tài khoản cá nhân"
      >
        <div className="account-sidebar-upper">
          <div className="account-sidebar-top">
            {!collapsed && <span className="account-sidebar-heading">Tài khoản</span>}
            <button
              type="button"
              className="account-sidebar-toggle"
              onClick={toggleSidebar}
              aria-label={collapsed ? 'Mở thanh điều hướng bên trái' : 'Thu gọn thanh điều hướng bên trái'}
              title={collapsed ? 'Mở thanh bên trái' : 'Thu gọn thanh bên trái'}
              aria-expanded={!collapsed}
            >
              {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
            </button>
          </div>

          <nav className="account-sidebar-nav">
            {ACCOUNT_NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              const active = item.match(location.pathname);
              const badge = accountBadges[item.key];
              const badgeText = badge ? formatBadgeCount(badge.count) : '';
              return (
                <NavLink
                  key={item.key}
                  to={item.to}
                  title={collapsed ? (badge ? `${item.label} (${badge.title})` : item.label) : badge?.title}
                  aria-label={badge ? `${item.label} (${badge.count})` : item.label}
                  className={`account-sidebar-link ${active ? 'is-active' : ''}`}
                >
                  <Icon size={19} strokeWidth={1.85} aria-hidden="true" />
                  {!collapsed && <span className="account-sidebar-label">{item.label}</span>}
                  {badgeText && (
                    <span
                      className={`account-sidebar-badge is-${badge.tone}`}
                      aria-hidden="true"
                    >
                      {badgeText}
                    </span>
                  )}
                </NavLink>
              );
            })}
          </nav>

          {!collapsed && <div className="account-sidebar-divider" aria-hidden="true" />}
        </div>

        <div className="account-sidebar-footer">
          <button
            type="button"
            className="account-sidebar-logout"
            onClick={handleLogout}
            title={collapsed ? 'Đăng xuất' : undefined}
            aria-label="Đăng xuất"
          >
            <LogOut size={19} strokeWidth={1.85} aria-hidden="true" />
            {!collapsed && <span>Đăng xuất</span>}
          </button>
        </div>
      </aside>

      <div className="account-shell-content">
        <Outlet
          context={{
            accountBadges,
            reportActions: counts.reportActions,
            claimCounts: {
              receivedPending: counts.claimsReceivedPending,
              sentPending: counts.claimsSentPending,
            },
          }}
        />
      </div>
    </div>
  );
}
