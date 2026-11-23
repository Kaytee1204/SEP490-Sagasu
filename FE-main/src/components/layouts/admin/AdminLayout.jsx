// src/components/layouts/admin/AdminLayout.jsx
import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import AdminSidebar from './AdminSidebar';
import Navbar from '@/components/layouts/Navbar';
import ChatProvider from '@/features/chat/context/ChatProvider';
import { useAuth } from '@/context/AuthContext';
import { reviewApi } from '@/features/report-review/api';
import { officeApi } from '@/features/office/api/officeApi';
import { workflowApi } from '@/features/office/api/workflowApi';
import { approverApi } from '@/features/approver/api/approverApi';
import { newsApi } from '@/features/news/api/newsApi';
import './AdminSidebar.css';

function readTotal(settled) {
  if (settled?.status !== 'fulfilled') return 0;
  return Number(settled.value?.totalElements ?? 0) || 0;
}

export default function AdminLayout() {
  const { user } = useAuth();
  const location = useLocation();
  const [navBadges, setNavBadges] = useState({});

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }, [location.pathname]);

  useEffect(() => {
    let active = true;
    const role = user?.role ? String(user.role).toUpperCase() : '';

    const load = async () => {
      if (role === 'MANAGER') {
        const [reportsRes, postsRes, transfersRes, storedRes, newsRes] = await Promise.allSettled([
          reviewApi.list({ queue: 'READY' }),
          officeApi.posts({ page: 1, moderation: 'PENDING' }),
          workflowApi.transfers({ page: 1, status: 'PENDING', mine: false }),
          officeApi.stored({ page: 1 }),
          newsApi.listForApprover({ page: 1, size: 1 }),
        ]);
        if (!active) return;
        setNavBadges({
          pendingReports: readTotal(reportsRes),
          reportQueues: reportsRes.status === 'fulfilled' ? reportsRes.value?.countsByKind : undefined,
          pendingPosts: readTotal(postsRes),
          pendingTransfers: readTotal(transfersRes),
          storedItems: readTotal(storedRes),
          newsArticles: readTotal(newsRes),
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
    <ChatProvider key={user?.id || 'anonymous'}>
      <div className="min-h-screen flex flex-col bg-[#f8f9fc] text-[var(--text-primary)] font-sans">
        <Navbar />
        <div className="admin-shell flex-1">
          <AdminSidebar
            pendingReportsCount={pendingReportsCount}
            navBadges={navBadges}
          />
          <main className="admin-main">
            <Outlet />
          </main>
        </div>
      </div>
    </ChatProvider>
  );
}
