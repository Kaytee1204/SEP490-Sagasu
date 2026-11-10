// src/components/layouts/admin/AdminLayout.jsx
import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import AdminSidebar from './AdminSidebar';
import AdminHeader from './AdminHeader';
import { useAuth } from '@/context/AuthContext';
import { reviewApi } from '@/features/report-review/api';

export default function AdminLayout() {
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  // DaiHV integration: report badges reflect the real queue, only for the responsible role.
  const {user}=useAuth();const location=useLocation();
  const [pendingReportsCount,setPendingReportsCount]=useState(0);
  useEffect(()=>{
    let active=true;
    const load=()=>{if(user?.role==='MANAGER')reviewApi.list({status:'OPEN'}).then(r=>{if(active)setPendingReportsCount(r.totalElements);}).catch(()=>{if(active)setPendingReportsCount(0);});else setPendingReportsCount(0);};
    load();window.addEventListener('report-review-changed',load);
    return()=>{active=false;window.removeEventListener('report-review-changed',load);};
  },[user?.role,location.pathname]);

  return (
    <div className="flex min-h-screen bg-slate-50 text-slate-900 font-sans">
      <AdminSidebar
        pendingReportsCount={pendingReportsCount}
        isOpen={isMobileOpen}
        onClose={() => setIsMobileOpen(false)}
      />
      <main className="flex-1 flex flex-col min-w-0 h-screen overflow-hidden">
        <AdminHeader
          pendingReportsCount={pendingReportsCount}
          onMenuClick={() => setIsMobileOpen(true)}
        />
        <div className="flex-1 overflow-y-auto">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
