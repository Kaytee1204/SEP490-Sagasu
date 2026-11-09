// src/components/layouts/admin/AdminHeader.jsx
import { useLocation, useNavigate } from 'react-router-dom';
import { Menu, RefreshCw } from 'lucide-react';
import { useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import NotificationBell from '@/features/notifications/components/NotificationBell';

export default function AdminHeader({ onMenuClick, pendingReportsCount = 0 }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { isApprover } = useAuth();
  const [isRefreshing, setIsRefreshing] = useState(false);

  const triggerRefresh = () => {
    setIsRefreshing(true);
    navigate(0);
  };

  const getBreadcrumb = () => {
    // DaiHV: office claim processing has moved to the Approver workspace.
    if (location.pathname.startsWith('/approver/office-claims')) return 'Yêu cầu nhận đồ';
    if (location.pathname.endsWith('/transfers')) return 'Yêu cầu tiếp nhận đồ thất lạc';
    if (location.pathname.startsWith('/admin/storage') || location.pathname.startsWith('/approver/storage')) return 'Kho đồ thất lạc';
    if (location.pathname.startsWith('/approver/receipts/')) return 'Xem & xét duyệt phiếu';
    if (location.pathname.startsWith('/approver')) return 'Hàng đợi tiếp nhận';
    if (location.pathname.startsWith('/admin/categories')) return 'Quản lý danh mục';
    if (location.pathname.startsWith('/admin/items')) return 'Tin đăng đồ thất lạc';
    if (location.pathname.startsWith('/admin/reports')) return 'Báo cáo & Vi phạm';
    if (location.pathname.startsWith('/admin/users/')) return 'Chi tiết tài khoản';
    if (location.pathname.startsWith('/admin/users')) return 'Tài khoản thành viên';
    return 'Bảng điều khiển';
  };

  return (
    <header className="h-14 bg-white border-b border-black/5 px-4 lg:px-7 flex items-center justify-between sticky top-0 z-20">
      <div className="flex items-center gap-3">
        <button type="button" onClick={onMenuClick} className="lg:hidden p-1.5 -ml-2 text-zinc-500 hover:bg-zinc-100 rounded-md">
          <Menu size={20} />
        </button>
        <div className="flex items-center gap-2 text-[13px]">
          <span className="text-zinc-600 font-medium">Quản lý</span>
          <span className="text-zinc-300">/</span>
          <span className="font-semibold text-zinc-900">{getBreadcrumb()}</span>
        </div>
      </div>
      <div className="flex items-center gap-2.5">
        {isApprover ? (
          <span className="hidden shrink-0 rounded-md bg-teal-50 px-2.5 py-1 text-sm font-semibold text-teal-800 sm:inline">Người xét duyệt</span>
        ) : (
          <button type="button" onClick={triggerRefresh} className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border border-zinc-200 bg-white text-zinc-600 text-[13px] font-medium hover:bg-zinc-50 transition-colors">
            <RefreshCw size={13} className={isRefreshing ? 'animate-spin' : ''} />
            <span className="hidden sm:inline">Đồng bộ</span>
          </button>
        )}
        <NotificationBell variant="admin" />
      </div>
    </header>
  );
}
