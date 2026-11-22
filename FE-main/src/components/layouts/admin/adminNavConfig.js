// src/components/layouts/admin/adminNavConfig.js
import { LayoutDashboard, Package, AlertTriangle, Users, Tags, ClipboardList, Warehouse, FileCheck, FileWarning, Shield, Newspaper } from 'lucide-react';
import { reportReviewNavItems, reportReviewRoot } from '@/features/report-review/routes';

export const ADMIN_NAV_SECTIONS = [
  {
    title: 'Tiếp nhận & trao trả',
    // DaiHV integration: physical receipt of items transferred from existing posts.
    items: [
      {
        label: 'Xét duyệt và trao trả tài sản',
        path: '/approver/office-claims',
        icon: FileCheck,
        badgeKey: 'pendingOfficeClaims',
        badgeTone: 'danger',
        roles: ['APPROVER'],
        wrapLabel: true,
      },
      {
        label: 'Tiếp nhận tài sản từ bài đăng',
        path: '/approver/transfers',
        icon: Package,
        badgeKey: 'pendingTransfers',
        badgeTone: 'danger',
        roles: ['APPROVER'],
        wrapLabel: true,
      },
      {
        label: 'Phiếu tiếp nhận tài sản',
        path: '/approver/receipts',
        icon: ClipboardList,
        badgeKey: 'draftReceipts',
        badgeTone: 'warning',
        roles: ['APPROVER'],
        wrapLabel: true,
      },
      {
        label: 'Tài sản lưu kho',
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
        label: 'Quản lý bài đăng',
        path: '/admin/items',
        icon: Package,
        badgeKey: 'pendingPosts',
        badgeTone: 'warning',
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
