// src/components/layouts/admin/adminNavConfig.js
import { LayoutDashboard, Package, AlertTriangle, Users, Tags, ClipboardList, Warehouse, FileCheck } from 'lucide-react';

export const ADMIN_NAV_SECTIONS = [
  {
    title: 'Tiếp nhận đồ',
    items: [{ label: 'Hồ sơ tiếp nhận', path: '/approver/receipts', icon: ClipboardList, roles: ['APPROVER'] },
      { label: 'Đồ đang lưu kho', path: '/approver/storage', icon: Warehouse, roles: ['APPROVER'] }],
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
      { label: 'Kho & vị trí lưu trữ', path: '/admin/storage', icon: Warehouse, roles: ['MANAGER'] },
      { label: 'Yêu cầu nhận đồ', path: '/admin/office-claims', icon: FileCheck, roles: ['MANAGER'] },
      {
        label: 'Tin đăng',
        path: '/admin/items',
        icon: Package,
        roles: ['MANAGER'],
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
