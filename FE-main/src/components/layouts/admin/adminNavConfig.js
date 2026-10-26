// src/components/layouts/admin/adminNavConfig.js
import { LayoutDashboard, Package, AlertTriangle, Users } from 'lucide-react';

export const ADMIN_NAV_SECTIONS = [
  {
    title: 'Tổng quan',
    items: [
      {
        label: 'Bảng điều khiển',
        path: '/admin',
        exact: true,
        icon: LayoutDashboard,
      },
    ],
  },
  {
    title: 'Quản lý dữ liệu',
    items: [
      {
        label: 'Tin đăng',
        path: '/admin/items',
        icon: Package,
      },
      {
        label: 'Báo cáo vi phạm',
        path: '/admin/reports',
        icon: AlertTriangle,
        badgeKey: 'pendingReports',
      },
      {
        label: 'Tài khoản thành viên',
        path: '/admin/users',
        icon: Users,
      },
    ],
  },
];
