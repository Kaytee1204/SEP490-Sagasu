import { useState, useMemo } from 'react';
import { USERS_LIST } from '../pages/mockData';

export function useAdminUsers() {
  const [users, setUsers] = useState(USERS_LIST);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');

  // Modal for actions that require a reason (Ban, Delete)
  const [actionModal, setActionModal] = useState({ isOpen: false, type: '', payload: null, title: '', placeholder: '' });
  const [actionReason, setActionReason] = useState('');

  // Modal for Create/Update User
  const [crudModal, setCrudModal] = useState({ isOpen: false, mode: 'create', data: null });
  const [formData, setFormData] = useState({ name: '', email: '', role: 'Thành viên', trustScore: 100, status: 'Hoạt động' });

  const [toastMessage, setToastMessage] = useState(null);

  const showToast = (msg) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const executeAction = () => {
    if (!actionReason.trim()) {
      showToast('Vui lòng nhập lý do thực hiện');
      return;
    }

    const { type, payload } = actionModal;

    if (type === 'BAN_USER') {
      setUsers(prev => prev.map(u => u.id === payload ? { ...u, status: 'Đã tạm khóa' } : u));
      showToast(`Đã khóa tài khoản ${payload}`);
    } else if (type === 'UNBAN_USER') {
      setUsers(prev => prev.map(u => u.id === payload ? { ...u, status: 'Hoạt động' } : u));
      showToast(`Đã mở khóa tài khoản ${payload}`);
    } else if (type === 'DELETE_USER') {
      setUsers(prev => prev.filter(u => u.id !== payload));
      showToast(`Đã xóa tài khoản ${payload}`);
    }

    setActionModal({ isOpen: false, type: '', payload: null, title: '', placeholder: '' });
    setActionReason('');
  };

  const openCrudModal = (mode, user = null) => {
    if (mode === 'edit' && user) {
      setFormData({ name: user.name, email: user.email, role: user.role, trustScore: user.trustScore, status: user.status });
      setCrudModal({ isOpen: true, mode: 'edit', data: user });
    } else {
      setFormData({ name: '', email: '', role: 'Thành viên', trustScore: 100, status: 'Hoạt động' });
      setCrudModal({ isOpen: true, mode: 'create', data: null });
    }
  };

  const submitCrud = (e) => {
    e.preventDefault();
    if (crudModal.mode === 'create') {
      const newUser = {
        id: `USR-${Math.floor(Math.random() * 900) + 100}`,
        ...formData,
        returnsCount: 0,
        verified: false,
      };
      setUsers([newUser, ...users]);
      showToast(`Đã tạo tài khoản: ${newUser.name}`);
    } else {
      setUsers(prev => prev.map(u => u.id === crudModal.data.id ? { ...u, ...formData } : u));
      showToast(`Đã cập nhật tài khoản ${crudModal.data.id}`);
    }
    setCrudModal({ isOpen: false, mode: 'create', data: null });
  };

  const filteredUsers = useMemo(() => {
    return users.filter(u => {
      const matchesSearch = u.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
        u.id.toLowerCase().includes(searchQuery.toLowerCase());

      const matchesStatus = statusFilter === 'all' ||
        (statusFilter === 'active' && u.status === 'Hoạt động') ||
        (statusFilter === 'banned' && u.status === 'Đã tạm khóa') ||
        (statusFilter === 'warning' && (u.role === 'Cảnh báo' || u.trustScore < 50));

      return matchesSearch && matchesStatus;
    });
  }, [users, searchQuery, statusFilter]);

  const activeCount = users.filter(u => u.status === 'Hoạt động').length;
  const bannedCount = users.filter(u => u.status === 'Đã tạm khóa').length;
  const warningCount = users.filter(u => u.role === 'Cảnh báo' || u.trustScore < 50).length;

  return {
    users,
    filteredUsers,
    searchQuery,
    setSearchQuery,
    statusFilter,
    setStatusFilter,
    actionModal,
    setActionModal,
    actionReason,
    setActionReason,
    crudModal,
    setCrudModal,
    formData,
    setFormData,
    toastMessage,
    executeAction,
    openCrudModal,
    submitCrud,
    counts: {
      total: users.length,
      active: activeCount,
      banned: bannedCount,
      warning: warningCount,
    }
  };
}
