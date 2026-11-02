// src/features/account/hooks/useAccountDashboard.js
import { useState, useEffect, useCallback } from 'react';
import { accountApi } from '../api/accountApi';

export function useAccountDashboard() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(false);

  // Server-side pagination state
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [counts, setCounts] = useState({
    active: 0,
    suspended: 0,
    deleted: 0,
  });

  // Filter state
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [includeDeleted, setIncludeDeleted] = useState(true);

  // CRUD Modal state
  const [crudModal, setCrudModal] = useState({
    isOpen: false,
    mode: 'create',
    data: null,
  });
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    role: 'USER',
    status: 'ACTIVE',
  });
  const [submitting, setSubmitting] = useState(false);

  // Import Excel Modal state
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [importing, setImporting] = useState(false);

  // Toast feedback
  const [toast, setToast] = useState(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 4000);
  };

  const fetchAccounts = useCallback(async () => {
    setLoading(true);
    try {
      const pageData = await accountApi.getAccountsPage({
        page,
        size,
        keyword,
        role: roleFilter,
        status: statusFilter,
        includeDeleted: true,
      });
      setAccounts(pageData?.content || []);
      setTotalElements(pageData?.totalElements || 0);
      setTotalPages(Math.max(pageData?.totalPages || 1, 1));
      setCounts({
        active: pageData?.activeCount || 0,
        suspended: pageData?.suspendedCount || 0,
        deleted: pageData?.deletedCount || 0,
      });
    } catch (err) {
      showToast(err?.message || 'Không thể tải danh sách tài khoản', 'error');
    } finally {
      setLoading(false);
    }
  }, [page, size, keyword, roleFilter, statusFilter, includeDeleted]);

  useEffect(() => {
    fetchAccounts();
  }, [fetchAccounts]);

  const openCrudModal = (mode, account = null) => {
    if (mode === 'edit' && account) {
      setFormData({
        fullName: account.fullName || '',
        email: account.email || '',
        password: '',
        role: account.role || 'USER',
        status: account.status || 'ACTIVE',
      });
      setCrudModal({ isOpen: true, mode: 'edit', data: account });
    } else {
      setFormData({
        fullName: '',
        email: '',
        password: '',
        role: 'USER',
        status: 'ACTIVE',
      });
      setCrudModal({ isOpen: true, mode: 'create', data: null });
    }
  };

  const submitCrud = async (e) => {
    e.preventDefault();

    const trimmedName = (formData.fullName || '').trim();
    const trimmedEmail = (formData.email || '').trim();
    const trimmedPass = (formData.password || '').trim();

    if (trimmedName.length < 2 || trimmedName.length > 150) {
      showToast('Tên người dùng phải từ 2 đến 150 ký tự', 'error');
      return;
    }

    if (!trimmedEmail || /\s/.test(trimmedEmail)) {
      showToast('Email không được để trống hoặc chứa khoảng trắng', 'error');
      return;
    }

    if (trimmedEmail.toLowerCase() !== 'admin' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      showToast('Vui lòng nhập địa chỉ Email hợp lệ (VD: user01@gmail.com)', 'error');
      return;
    }

    if (trimmedPass) {
      if (trimmedPass.length < 8 || trimmedPass.length > 16 || /\s/.test(trimmedPass) || !/[^a-zA-Z0-9\s]/.test(trimmedPass)) {
        showToast('Mật khẩu phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng', 'error');
        return;
      }
    }

    setSubmitting(true);
    try {
      if (crudModal.mode === 'create') {
        const payload = {
          fullName: trimmedName,
          email: trimmedEmail,
          role: formData.role,
          status: formData.status,
        };
        if (trimmedPass) {
          payload.password = trimmedPass;
        }
        const created = await accountApi.createAccount(payload);
        showToast(
          created?.generatedPassword
            ? `Đã tạo tài khoản #${created.id} (${created.email}) - Mật khẩu: ${created.generatedPassword}`
            : `Đã tạo tài khoản #${created.id} (${created.email})`
        );
      } else {
        const payload = {
          fullName: trimmedName,
          email: trimmedEmail,
          role: formData.role,
          status: formData.status,
        };
        if (trimmedPass) {
          payload.password = trimmedPass;
        }
        const updated = await accountApi.updateAccount(crudModal.data.id, payload);
        showToast(`Đã cập nhật tài khoản #${updated.id} (${updated.email})`);
      }
      setCrudModal({ isOpen: false, mode: 'create', data: null });
      fetchAccounts();
    } catch (err) {
      showToast(err?.message || 'Thao tác lưu tài khoản thất bại', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleLock = async (account) => {
    const nextStatus = account.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    try {
      await accountApi.updateAccount(account.id, { status: nextStatus });
      showToast(
        nextStatus === 'SUSPENDED'
          ? `Đã tạm khóa tài khoản #${account.id} (${account.email})`
          : `Đã mở khóa tài khoản #${account.id} (${account.email})`
      );
      fetchAccounts();
    } catch (err) {
      showToast(err?.message || 'Không thể thay đổi trạng thái tài khoản', 'error');
    }
  };

  const handleSoftDelete = async (account) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa mềm tài khoản #${account.id} (${account.email})?`)) {
      return;
    }
    try {
      await accountApi.softDeleteAccount(account.id);
      showToast(`Đã xóa mềm tài khoản #${account.id} (${account.email})`);
      fetchAccounts();
    } catch (err) {
      showToast(err?.message || 'Xóa mềm tài khoản thất bại', 'error');
    }
  };

  const handleExcelImport = async (file) => {
    if (!file) return;
    setImporting(true);
    setImportResult(null);
    try {
      const result = await accountApi.importAccountsFromExcel(file);
      setImportResult(result);
      showToast(`Import hoàn tất: Thành công ${result.successCount}/${result.totalRows} dòng`);
      fetchAccounts();
    } catch (err) {
      showToast(err?.message || 'Lỗi khi import file Excel', 'error');
    } finally {
      setImporting(false);
    }
  };

  return {
    accounts,
    loading,
    page,
    setPage,
    size,
    setSize,
    totalElements,
    totalPages,
    counts,
    keyword,
    setKeyword: (val) => {
      setKeyword(val);
      setPage(1);
    },
    roleFilter,
    setRoleFilter: (val) => {
      setRoleFilter(val);
      setPage(1);
    },
    statusFilter,
    setStatusFilter: (val) => {
      setStatusFilter(val);
      setPage(1);
    },
    includeDeleted,
    setIncludeDeleted: (val) => {
      setIncludeDeleted(val);
      setPage(1);
    },
    crudModal,
    setCrudModal,
    formData,
    setFormData,
    submitting,
    openCrudModal,
    submitCrud,
    handleToggleLock,
    handleSoftDelete,
    importModalOpen,
    setImportModalOpen,
    importResult,
    setImportResult,
    importing,
    handleExcelImport,
    toast,
    refresh: fetchAccounts,
  };
}
