// src/components/layouts/admin/AdminHeader.jsx
import { useLocation, useNavigate } from 'react-router-dom';
import { Menu, RefreshCw, Bell, CheckCircle2 } from 'lucide-react';
import { useState } from 'react';

export default function AdminHeader({ onMenuClick, pendingReportsCount = 0 }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMsg, setToastMsg] = useState(null);

  const triggerRefresh = () => {
    setIsRefreshing(true);
    setToastMsg('Dữ liệu đã được đồng bộ mới nhất');
    setTimeout(() => setIsRefreshing(false), 600);
    setTimeout(() => setToastMsg(null), 3000);
  };

  const getBreadcrumb = () => {
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
          <span className="text-zinc-400 font-medium">Portal</span>
          <span className="text-zinc-300">/</span>
          <span className="font-semibold text-zinc-900">{getBreadcrumb()}</span>
        </div>
      </div>
      <div className="flex items-center gap-2.5">
        {toastMsg && (
          <div className="hidden sm:inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-700 bg-emerald-50 px-2.5 py-1 rounded-md border border-emerald-200">
            <CheckCircle2 size={13} /> {toastMsg}
          </div>
        )}
        <button type="button" onClick={triggerRefresh} className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md border border-zinc-200 bg-white text-zinc-600 text-[13px] font-medium hover:bg-zinc-50 transition-colors">
          <RefreshCw size={13} className={isRefreshing ? 'animate-spin' : ''} />
          <span className="hidden sm:inline">Đồng bộ</span>
        </button>
        <button type="button" onClick={() => navigate('/admin/reports')} className="w-8 h-8 rounded-md border border-zinc-200 bg-white flex items-center justify-center text-zinc-600 hover:bg-zinc-50 relative">
          <Bell size={15} />
          {pendingReportsCount > 0 && <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 rounded-full bg-rose-500" />}
        </button>
      </div>
    </header>
  );
}
