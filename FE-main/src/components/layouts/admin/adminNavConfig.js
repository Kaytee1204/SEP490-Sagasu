// src/components/layouts/admin/adminNavConfig.js
import { LayoutDashboard, Package, AlertTriangle, Users, Tags, ClipboardList, Warehouse, FileCheck } from 'lucide-react';

export const ADMIN_NAV_SECTIONS = [
  {
    title: 'Tiếp nhận & trao trả',
    // DaiHV integration: physical receipt of items transferred from existing posts.
    items: [{ label: 'Yêu cầu nhận đồ', path: '/approver/office-claims', icon: FileCheck, roles: ['APPROVER'] },
      { label: 'Yêu cầu tiếp nhận đồ thất lạc', path: '/approver/transfers', icon: Package, roles: ['APPROVER'], wrapLabel: true },
      { label: 'Hồ sơ tiếp nhận', path: '/approver/receipts', icon: ClipboardList, roles: ['APPROVER'] },
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
      { label: 'Theo dõi chuyển giao', path: '/admin/transfers', icon: Package, roles: ['MANAGER'] },
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
