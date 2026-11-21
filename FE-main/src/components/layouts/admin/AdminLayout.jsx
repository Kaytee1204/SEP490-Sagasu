// src/components/layouts/admin/AdminLayout.jsx
import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import AdminSidebar from './AdminSidebar';
import AdminHeader from './AdminHeader';
import { useAuth } from '@/context/AuthContext';
import { reviewApi } from '@/features/report-review/api';
import { officeApi } from '@/features/office/api/officeApi';
import { workflowApi } from '@/features/office/api/workflowApi';
import { approverApi } from '@/features/approver/api/approverApi';
import { newsApi } from '@/features/news/api/newsApi';

function readTotal(settled) {
  if (settled?.status !== 'fulfilled') return 0;
  return Number(settled.value?.totalElements ?? 0) || 0;
}

export default function AdminLayout() {
  const [isMobileOpen, setIsMobileOpen] = useState(false);
  const { user } = useAuth();
  const location = useLocation();
  const [navBadges, setNavBadges] = useState({});

  useEffect(() => {
    let active = true;
    const role = user?.role ? String(user.role).toUpperCase() : '';

    const load = async () => {
      if (role === 'MANAGER') {
        const [reportsRes, postsRes, transfersRes, storedRes] = await Promise.allSettled([
          reviewApi.list({ queue: 'READY' }),
          officeApi.posts({ page: 1, moderation: 'PENDING' }),
          workflowApi.transfers({ page: 1, status: 'PENDING', mine: false }),
          officeApi.stored({ page: 1 }),
        ]);
        if (!active) return;
        setNavBadges({
          pendingReports: readTotal(reportsRes),
          reportQueues: reportsRes.status === 'fulfilled' ? reportsRes.value?.countsByKind : undefined,
          pendingPosts: readTotal(postsRes),
          pendingTransfers: readTotal(transfersRes),
          storedItems: readTotal(storedRes),
        });
      } else if (role === 'APPROVER') {
        const [claimsRes, transfersRes, receiptsRes, storedRes, newsRes] = await Promise.allSettled([
          officeApi.claims({ page: 1, status: 'PENDING' }, false),
          workflowApi.transfers({ page: 1, status: 'PENDING', mine: false }),
          approverApi.list({ page: 1, filter: 'DRAFT' }),
          officeApi.stored({ page: 1 }),
          newsApi.listForApprover({ page: 1, size: 1 }),
        ]);
        if (!active) return;
        setNavBadges({
          pendingOfficeClaims: readTotal(claimsRes),
          pendingTransfers: readTotal(transfersRes),
          draftReceipts: readTotal(receiptsRes),
          storedItems: readTotal(storedRes),
          newsArticles: readTotal(newsRes),
        });
      } else {
        if (active) setNavBadges({});
      }
    };

    load();
    window.addEventListener('report-review-changed', load);
    window.addEventListener('sagasu-counts-changed', load);
    window.addEventListener('focus', load);
    return () => {
      active = false;
      window.removeEventListener('report-review-changed', load);
      window.removeEventListener('sagasu-counts-changed', load);
      window.removeEventListener('focus', load);
    };
  }, [user?.role, location.pathname]);

  const pendingReportsCount = navBadges.pendingReports || 0;

  return (
    <div className="flex min-h-screen bg-slate-50 text-slate-900 font-sans">
      <AdminSidebar
        pendingReportsCount={pendingReportsCount}
        navBadges={navBadges}
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
