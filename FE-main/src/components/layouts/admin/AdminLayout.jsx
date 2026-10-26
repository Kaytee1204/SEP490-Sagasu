// src/components/layouts/admin/AdminLayout.jsx
import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import AdminSidebar from './AdminSidebar';
import AdminHeader from './AdminHeader';
import { INITIAL_REPORTS } from '@/data/mockData';

export default function AdminLayout() {
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const [reports] = useState(INITIAL_REPORTS);
  const pendingReportsCount = reports.filter((r) => r.status === 'pending').length;

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
