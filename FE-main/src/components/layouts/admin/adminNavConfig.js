// src/components/layouts/admin/adminNavConfig.js
import { LayoutDashboard, Package, AlertTriangle, Users, Tags, ClipboardList, Warehouse, FileCheck, FileWarning, Shield, Newspaper } from 'lucide-react';
import { reportReviewNavItems, reportReviewRoot } from '@/features/report-review/routes';

export const ADMIN_NAV_SECTIONS = [
  {
    title: 'Tiếp nhận & trao trả',
    // DaiHV integration: physical receipt of items transferred from existing posts.
    items: [
      {
        label: 'Yêu cầu nhận đồ',
        path: '/approver/office-claims',
        icon: FileCheck,
        badgeKey: 'pendingOfficeClaims',
        badgeTone: 'danger',
        roles: ['APPROVER'],
      },
      {
        label: 'Yêu cầu tiếp nhận đồ thất lạc',
        path: '/approver/transfers',
        icon: Package,
        badgeKey: 'pendingTransfers',
        badgeTone: 'danger',
        roles: ['APPROVER'],
        wrapLabel: true,
      },
      {
        label: 'Hồ sơ tiếp nhận',
        path: '/approver/receipts',
        icon: ClipboardList,
        badgeKey: 'draftReceipts',
        badgeTone: 'warning',
        roles: ['APPROVER'],
      },
      {
        label: 'Đồ đang lưu kho',
        path: '/approver/storage',
        icon: Warehouse,
        badgeKey: 'storedItems',
        badgeTone: 'neutral',
        roles: ['APPROVER'],
      },
      {
        label: 'Tin tức hệ thống',
        path: '/approver/news',
        icon: Newspaper,
        badgeKey: 'newsArticles',
        badgeTone: 'neutral',
        roles: ['APPROVER'],
      },
    ],
  },
  {
    title: 'Tổng quan',
    items: [
      {
        label: 'Bảng điều khiển',
        path: '/admin',
        exact: true,
        icon: LayoutDashboard,
        roles: ['ADMIN', 'MANAGER'],
      },
    ],
  },
  {
    title: 'Quản lý dữ liệu',
    items: [
      { label: 'Danh mục', path: '/admin/categories', icon: Tags, roles: ['MANAGER'] },
      { label: 'Lý do báo cáo', path: '/admin/report-reasons', icon: FileWarning, roles: ['ADMIN', 'MANAGER'] },
      {
        label: 'Kho & vị trí lưu trữ',
        path: '/admin/storage',
        icon: Warehouse,
        badgeKey: 'storedItems',
        badgeTone: 'neutral',
        roles: ['MANAGER'],
      },
      {
        label: 'Theo dõi chuyển giao',
        path: '/admin/transfers',
        icon: Package,
        badgeKey: 'pendingTransfers',
        badgeTone: 'warning',
        roles: ['MANAGER'],
      },
      {
        label: 'Bài đăng',
        path: '/admin/items',
        icon: Package,
        badgeKey: 'pendingPosts',
        badgeTone: 'warning',
        roles: ['MANAGER'],
      },
      {
        label: 'Tin tức hệ thống',
        path: '/admin/news',
        icon: Newspaper,
        badgeKey: 'newsArticles',
        badgeTone: 'neutral',
        roles: ['MANAGER'],
      },
      {
        // DaiHV: the report investigation queue belongs to Manager.
        label: 'Báo cáo & phản ánh',
        path: reportReviewRoot,
        children: reportReviewNavItems,
        icon: AlertTriangle,
        badgeKey: 'pendingReports',
        badgeTone: 'danger',
        roles: ['MANAGER'],
      },
      {
        label: 'Tài khoản thành viên',
        path: '/admin/users',
        icon: Users,
        roles: ['ADMIN', 'MANAGER'],
      },
      {
        label: 'Nhật ký hệ thống (Audit)',
        path: '/admin/audit-logs',
        icon: Shield,
        roles: ['ADMIN', 'MANAGER'],
      },
    ],
  },
];
