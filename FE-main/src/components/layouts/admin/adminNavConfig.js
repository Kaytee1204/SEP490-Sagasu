// src/components/layouts/admin/adminNavConfig.js
import { LayoutDashboard, Package, AlertTriangle, Users, Tags, ClipboardList } from 'lucide-react';

export const ADMIN_NAV_SECTIONS = [
  {
    title: 'Tiếp nhận & xét duyệt',
    items: [{ label: 'Hàng đợi tiếp nhận', path: '/approver/receipts', icon: ClipboardList, roles: ['APPROVER'] }],
  },
  {
    title: 'Tuần tra & ghi nhận',
    items: [{ label: 'Phiếu của tôi', path: '/checker/receipts', icon: ClipboardList, roles: ['CHECKER'] }],
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
      {
        label: 'Tin đăng',
        path: '/admin/items',
        icon: Package,
        roles: ['ADMIN', 'MANAGER'],
      },
      {
        label: 'Báo cáo vi phạm',
        path: '/admin/reports',
        icon: AlertTriangle,
        roles: ['ADMIN', 'MANAGER'],
        badgeKey: 'pendingReports',
      },
      {
        label: 'Tài khoản thành viên',
        path: '/admin/users',
        icon: Users,
        roles: ['ADMIN', 'MANAGER'],
      },
    ],
  },
];
