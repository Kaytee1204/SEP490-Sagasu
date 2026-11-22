import { useEffect, useState } from 'react';
import {
  AlertTriangle,
  CheckCircle2,
  Edit3,
  FileWarning,
  Filter,
  Layers,
  Plus,
  Power,
  RefreshCw,
  Search,
  ShieldAlert,
  Trash2,
  X,
} from 'lucide-react';
import adminGovernanceApi from '../api/adminGovernanceApi';
import ConfirmActionPopup from '@/components/common/ConfirmActionPopup';

const TARGET_GROUPS = [
  { value: 'ALL', label: 'Tất cả nhóm', badgeClass: 'bg-slate-100 text-slate-700 border-slate-200' },
  { value: 'POST', label: 'Bài đăng (POST)', badgeClass: 'bg-teal-50 text-teal-700 border-teal-200' },
  { value: 'CLAIM', label: 'Yêu cầu nhận đồ (CLAIM)', badgeClass: 'bg-amber-50 text-amber-800 border-amber-200' },
  { value: 'CLAIM_APPEAL', label: 'Tranh chấp', badgeClass: 'bg-sky-50 text-sky-700 border-sky-200' },
  { value: 'HANDOVER_ISSUE', label: 'Sự cố bàn giao (HANDOVER_ISSUE)', badgeClass: 'bg-rose-50 text-rose-700 border-rose-200' },
];

const GROUP_MAP = Object.fromEntries(TARGET_GROUPS.map((g) => [g.value, g]));

function suggestReasonCode(group, label) {
  if (!label || !label.trim()) return '';
  const slug = label
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  if (!slug) return '';
  const prefix = `${group || 'POST'}_`;
  const full = slug.startsWith(prefix) ? slug : `${prefix}${slug}`;
  return full.slice(0, 64).replace(/_+$/g, '');
}

export default function ReportReasonsManagementPage() {
  const [reasons, setReasons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  const [targetGroup, setTargetGroup] = useState('ALL');
  const [activeFilter, setActiveFilter] = useState('');
  const [keyword, setKeyword] = useState('');

  const [toast, setToast] = useState(null);
  const [modalState, setModalState] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    window.clearTimeout(window.__reportReasonToastTimer);
    window.__reportReasonToastTimer = window.setTimeout(() => setToast(null), 3500);
  };

  useEffect(() => {
    let active = true;
    adminGovernanceApi
      .listReportReasons({ targetGroup, active: activeFilter, keyword })
      .then((list) => {
        if (!active) return;
        setReasons(list);
        setError('');
        setLoading(false);
      })
      .catch((err) => {
        if (!active) return;
        setError(err.message || 'Không thể tải danh sách lý do báo cáo.');
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [targetGroup, activeFilter, keyword, reloadTick]);

  const triggerReload = () => {
    setLoading(true);
    setReloadTick((v) => v + 1);
  };

  const handleGroupChange = (group) => {
    setLoading(true);
    setTargetGroup(group);
  };

  const handleActiveFilterChange = (val) => {
    setLoading(true);
    setActiveFilter(val);
  };

  const handleKeywordChange = (val) => {
    setLoading(true);
    setKeyword(val);
  };

  const openCreateModal = () => {
    const defaultGroup = targetGroup !== 'ALL' ? targetGroup : 'POST';
    setModalState({
      mode: 'create',
      code: '',
      targetGroup: defaultGroup,
      label: '',
      descriptionRequired: false,
      active: true,
      sortOrder: '',
      formError: '',
    });
  };

  const openEditModal = (item) => {
    setModalState({
      mode: 'edit',
      code: item.code,
      targetGroup: item.targetGroup,
      label: item.label,
      descriptionRequired: Boolean(item.descriptionRequired),
      active: Boolean(item.active),
      sortOrder: String(item.sortOrder ?? 1),
      formError: '',
    });
  };

  const handleSaveModal = async (e) => {
    e.preventDefault();
    if (!modalState) return;
    const trimmedLabel = modalState.label.trim();
    if (!trimmedLabel) {
      setModalState((prev) => ({ ...prev, formError: 'Vui lòng nhập tên hiển thị của lý do báo cáo.' }));
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        code: modalState.code.trim() || null,
        targetGroup: modalState.targetGroup,
        label: trimmedLabel,
        descriptionRequired: Boolean(modalState.descriptionRequired),
        active: Boolean(modalState.active),
        sortOrder: modalState.sortOrder !== '' ? Number(modalState.sortOrder) : null,
      };
      if (modalState.mode === 'create') {
        await adminGovernanceApi.createReportReason(payload);
        showToast('Đã tạo lý do báo cáo mới thành công.');
      } else {
        await adminGovernanceApi.updateReportReason(modalState.code, payload);
        showToast(`Đã cập nhật lý do báo cáo ${modalState.code}.`);
      }
      setModalState(null);
      triggerReload();
    } catch (err) {
      setModalState((prev) => ({
        ...prev,
        formError: err.message || 'Không thể lưu lý do báo cáo.',
      }));
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleStatus = async (item) => {
    try {
      const nextActive = !item.active;
      await adminGovernanceApi.toggleReportReasonStatus(item.code, nextActive);
      showToast(
        nextActive
          ? `Đã bật hoạt động lý do ${item.code}.`
          : `Đã tạm tắt lý do ${item.code}.`
      );
      triggerReload();
    } catch (err) {
      showToast(err.message || 'Không thể cập nhật trạng thái.', 'error');
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setSubmitting(true);
    try {
      const res = await adminGovernanceApi.deleteReportReason(deleteTarget.code);
      showToast(res?.message || `Đã xử lý xóa lý do ${deleteTarget.code}.`);
      setDeleteTarget(null);
      triggerReload();
    } catch (err) {
      showToast(err.message || 'Không thể xóa lý do báo cáo.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const totalCount = reasons.length;
  const activeCount = reasons.filter((r) => r.active).length;
  const requiredDescCount = reasons.filter((r) => r.descriptionRequired).length;
  const totalUsageCount = reasons.reduce((acc, r) => acc + Number(r.usageCount || 0), 0);

  return (
    <div className="space-y-6 p-4 text-slate-900 lg:p-7">
      {toast && (
        <div
          role="status"
          className={`fixed right-6 top-16 z-50 flex items-center gap-2.5 rounded-xl px-4 py-3 text-sm font-medium text-white shadow-lg ${
            toast.type === 'error' ? 'bg-rose-700' : 'bg-teal-800'
          }`}
        >
          {toast.type === 'error' ? <AlertTriangle size={17} /> : <CheckCircle2 size={17} />}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Quản lý Lý do Báo cáo & Khiếu nại
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Cấu hình danh mục lý do báo cáo vi phạm (Bài đăng, Người dùng, Claim, Tin nhắn) và Sự cố bàn giao (`report_reason`).
          </p>
        </div>

        <div className="flex items-center gap-2.5">
          <button
            type="button"
            onClick={triggerReload}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
            <span>Làm mới</span>
          </button>
          <button
            type="button"
            onClick={openCreateModal}
            className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-teal-800"
          >
            <Plus size={17} />
            <span>Thêm lý do báo cáo</span>
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
            <Layers size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Tổng lý do hiển thị</div>
            <div className="text-2xl font-extrabold text-slate-900">{totalCount}</div>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
            <CheckCircle2 size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Đang hoạt động</div>
            <div className="text-2xl font-extrabold text-emerald-700">{activeCount}</div>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-50 text-amber-700">
            <FileWarning size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Bắt buộc nhập mô tả</div>
            <div className="text-2xl font-extrabold text-amber-700">{requiredDescCount}</div>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
            <ShieldAlert size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Tổng lượt báo cáo đã dùng</div>
            <div className="text-2xl font-extrabold text-rose-700">{totalUsageCount}</div>
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex flex-wrap items-center gap-2">
          {TARGET_GROUPS.map((g) => {
            const active = targetGroup === g.value;
            return (
              <button
                key={g.value}
                type="button"
                onClick={() => handleGroupChange(g.value)}
                className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
                  active
                    ? 'bg-teal-700 text-white shadow-sm'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {g.label}
              </button>
            );
          })}
        </div>

        <div className="flex flex-wrap items-center gap-3 pt-1">
          <div className="relative min-w-[240px] flex-1">
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => handleKeywordChange(e.target.value)}
              placeholder="Tìm theo mã code (VD: POST_SPAM) hoặc tên hiển thị..."
              className="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-sm text-slate-800 focus:border-teal-600 focus:outline-none"
            />
          </div>

          <div className="flex items-center gap-2">
            <Filter size={15} className="text-slate-400" />
            <select
              value={activeFilter}
              onChange={(e) => handleActiveFilterChange(e.target.value)}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-teal-600 focus:outline-none"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="true">Đang hoạt động (Active)</option>
              <option value="false">Ngừng hoạt động (Inactive)</option>
            </select>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        {loading ? (
          <div className="space-y-3 p-6">
            {[1, 2, 3, 4, 5].map((i) => (
              <div key={i} className="h-11 animate-pulse rounded-lg bg-slate-100" />
            ))}
          </div>
        ) : error ? (
          <div className="p-10 text-center">
            <p className="font-medium text-rose-600">{error}</p>
            <button
              type="button"
              onClick={triggerReload}
              className="mt-3 rounded-lg bg-teal-700 px-4 py-2 text-xs font-semibold text-white"
            >
              Thử lại
            </button>
          </div>
        ) : reasons.length === 0 ? (
          <div className="p-12 text-center">
            <ShieldAlert size={36} className="mx-auto text-slate-300" />
            <p className="mt-2 text-sm font-medium text-slate-600">
              Không tìm thấy lý do báo cáo nào phù hợp với bộ lọc.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/80 text-xs font-bold uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3.5">Mã lý do (Code)</th>
                  <th className="px-4 py-3.5">Nhóm đối tượng</th>
                  <th className="px-4 py-3.5">Tên hiển thị</th>
                  <th className="px-4 py-3.5 text-center">Mô tả chi tiết</th>
                  <th className="px-4 py-3.5 text-center">Thứ tự</th>
                  <th className="px-4 py-3.5 text-center">Lượt báo cáo</th>
                  <th className="px-4 py-3.5 text-center">Trạng thái</th>
                  <th className="px-4 py-3.5 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {reasons.map((item) => {
                  const groupInfo = GROUP_MAP[item.targetGroup] || TARGET_GROUPS[0];
                  return (
                    <tr key={item.code} className="hover:bg-slate-50/80 transition-colors">
                      <td className="px-4 py-3.5 font-mono text-xs font-bold text-slate-800">
                        {item.code}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className={`inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold ${groupInfo.badgeClass}`}>
                          {item.targetGroup}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 font-medium text-slate-900">
                        {item.label}
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        {item.descriptionRequired ? (
                          <span className="inline-flex items-center rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-800 border border-amber-200">
                            Bắt buộc
                          </span>
                        ) : (
                          <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                            Tùy chọn
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-center font-semibold text-slate-700">
                        {item.sortOrder}
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <span className="inline-flex min-w-8 items-center justify-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-700">
                          {item.usageCount || 0}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                            item.active
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-slate-100 text-slate-500 border border-slate-200'
                          }`}
                        >
                          <span className={`h-1.5 w-1.5 rounded-full ${item.active ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                          {item.active ? 'Hoạt động' : 'Đã tắt'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <div className="inline-flex items-center gap-1.5">
                          <button
                            type="button"
                            onClick={() => openEditModal(item)}
                            title="Chỉnh sửa"
                            className="rounded-lg border border-slate-200 p-1.5 text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                          >
                            <Edit3 size={15} />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleToggleStatus(item)}
                            title={item.active ? 'Tạm tắt lý do' : 'Kích hoạt lý do'}
                            className={`rounded-lg border p-1.5 ${
                              item.active
                                ? 'border-amber-200 text-amber-700 hover:bg-amber-50'
                                : 'border-emerald-200 text-emerald-700 hover:bg-emerald-50'
                            }`}
                          >
                            <Power size={15} />
                          </button>
                          <button
                            type="button"
                            onClick={() => setDeleteTarget(item)}
                            title="Xóa lý do"
                            className="rounded-lg border border-rose-200 p-1.5 text-rose-600 hover:bg-rose-50"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Create / Edit Modal */}
      {modalState && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
          onClick={() => !submitting && setModalState(null)}
        >
          <div
            className="w-full max-w-lg overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
              <h2 className="text-lg font-bold text-slate-900">
                {modalState.mode === 'create' ? 'Thêm lý do báo cáo mới' : `Chỉnh sửa lý do: ${modalState.code}`}
              </h2>
              <button
                type="button"
                onClick={() => !submitting && setModalState(null)}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveModal} className="space-y-4 p-6">
              {modalState.formError && (
                <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-xs font-medium text-rose-700">
                  {modalState.formError}
                </div>
              )}

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-slate-600">
                    Nhóm đối tượng *
                  </label>
                  <select
                    value={modalState.targetGroup}
                    onChange={(e) => {
                      const nextGroup = e.target.value;
                      setModalState((prev) => ({
                        ...prev,
                        targetGroup: nextGroup,
                        code:
                          prev.mode === 'create' && (!prev.code || prev.code.startsWith(`${prev.targetGroup}_`))
                            ? suggestReasonCode(nextGroup, prev.label)
                            : prev.code,
                      }));
                    }}
                    className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 focus:border-teal-600 focus:outline-none"
                  >
                    {TARGET_GROUPS.filter((g) => g.value !== 'ALL').map((g) => (
                      <option key={g.value} value={g.value}>
                        {g.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-slate-600">
                    Thứ tự hiển thị
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="999"
                    value={modalState.sortOrder}
                    onChange={(e) => setModalState((prev) => ({ ...prev, sortOrder: e.target.value }))}
                    placeholder="Tự động tăng nếu để trống"
                    className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800 focus:border-teal-600 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-slate-600">
                  Tên hiển thị lý do báo cáo *
                </label>
                <input
                  type="text"
                  required
                  maxLength={200}
                  value={modalState.label}
                  onChange={(e) => {
                    const nextLabel = e.target.value;
                    setModalState((prev) => ({
                      ...prev,
                      label: nextLabel,
                      code:
                        prev.mode === 'create'
                          ? suggestReasonCode(prev.targetGroup, nextLabel)
                          : prev.code,
                    }));
                  }}
                  placeholder="VD: Thông tin sai lệch hoặc nghi ngờ giả mạo"
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-800 focus:border-teal-600 focus:outline-none"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-bold uppercase tracking-wider text-slate-600">
                  Mã định danh (Code)
                </label>
                <input
                  type="text"
                  disabled={modalState.mode === 'edit'}
                  maxLength={64}
                  value={modalState.code}
                  onChange={(e) => setModalState((prev) => ({ ...prev, code: e.target.value.toUpperCase() }))}
                  placeholder="Tự động sinh từ nhóm & tên lý do"
                  className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 font-mono text-sm text-slate-800 focus:border-teal-600 focus:outline-none disabled:opacity-70"
                />
                <p className="mt-1 text-xs text-slate-400">
                  Chỉ gồm chữ in hoa, chữ số và dấu gạch dưới (VD: POST_FALSE_INFO).
                </p>
              </div>

              <div className="space-y-2.5 rounded-xl border border-slate-200 bg-slate-50 p-3.5">
                <label className="flex cursor-pointer items-center gap-2.5 text-sm font-medium text-slate-800">
                  <input
                    type="checkbox"
                    checked={modalState.descriptionRequired}
                    onChange={(e) => setModalState((prev) => ({ ...prev, descriptionRequired: e.target.checked }))}
                    className="h-4 w-4 rounded accent-teal-700"
                  />
                  <span>Bắt buộc người báo cáo nhập mô tả chi tiết</span>
                </label>

                <label className="flex cursor-pointer items-center gap-2.5 text-sm font-medium text-slate-800">
                  <input
                    type="checkbox"
                    checked={modalState.active}
                    onChange={(e) => setModalState((prev) => ({ ...prev, active: e.target.checked }))}
                    className="h-4 w-4 rounded accent-teal-700"
                  />
                  <span>Kích hoạt hiển thị cho người dùng chọn ngay</span>
                </label>
              </div>

              <div className="flex items-center justify-end gap-2.5 border-t border-slate-100 pt-4">
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => setModalState(null)}
                  className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-slate-50"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="rounded-lg bg-teal-700 px-5 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
                >
                  {submitting ? 'Đang lưu...' : modalState.mode === 'create' ? 'Tạo lý do' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      <ConfirmActionPopup
        open={Boolean(deleteTarget)}
        variant={deleteTarget?.usageCount > 0 ? 'warning' : 'danger'}
        title="Xác nhận xóa lý do báo cáo?"
        description={
          deleteTarget
            ? deleteTarget.usageCount > 0
              ? `Lý do "${deleteTarget.label}" (${deleteTarget.code}) đang được tham chiếu bởi ${deleteTarget.usageCount} báo cáo. Hệ thống sẽ chuyển sang trạng thái Ngừng hoạt động để bảo toàn lịch sử điều tra.`
              : `Lý do "${deleteTarget.label}" (${deleteTarget.code}) chưa có báo cáo nào sử dụng và sẽ được xóa vĩnh viễn khỏi cơ sở dữ liệu.`
            : ''
        }
        confirmLabel={
          submitting
            ? 'Đang xử lý…'
            : deleteTarget?.usageCount > 0
              ? 'Ngừng hoạt động'
              : 'Xóa vĩnh viễn'
        }
        cancelLabel="Hủy bỏ"
        busy={submitting}
        onConfirm={handleConfirmDelete}
        onClose={() => !submitting && setDeleteTarget(null)}
      />
    </div>
  );
}
