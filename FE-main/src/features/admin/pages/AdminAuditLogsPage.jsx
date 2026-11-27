import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  Calendar,
  CheckCircle2,
  ClipboardCheck,
  Eye,
  FileSearch,
  RefreshCw,
  Search,
  Shield,
  ShieldAlert,
  UserCheck,
  X,
} from 'lucide-react';
import Pagination from '@/components/common/Pagination';
import adminGovernanceApi from '../api/adminGovernanceApi';

const SOURCE_TABS = [
  { value: 'ALL', label: 'Tất cả nhật ký' },
  { value: 'SECURITY', label: 'Bảo mật & Đăng nhập' },
  { value: 'CLAIM', label: 'Xét duyệt Claim' },
  { value: 'REPORT', label: 'Điều tra Báo cáo' },
];

const SOURCE_BADGES = {
  SECURITY: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  CLAIM: 'bg-teal-50 text-teal-700 border-teal-200',
  REPORT: 'bg-amber-50 text-amber-800 border-amber-200',
};

const ROLE_BADGES = {
  ADMIN: 'bg-purple-50 text-purple-700 border-purple-200',
  MANAGER: 'bg-sky-50 text-sky-700 border-sky-200',
  APPROVER: 'bg-teal-50 text-teal-700 border-teal-200',
  USER: 'bg-slate-100 text-slate-700 border-slate-200',
};

function formatDateTime(value) {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      dateStyle: 'short',
      timeStyle: 'medium',
    });
  } catch {
    return String(value);
  }
}

export default function AdminAuditLogsPage() {
  const [data, setData] = useState({
    content: [],
    page: 1,
    size: 15,
    totalElements: 0,
    totalPages: 1,
    summary: {},
    eventTypes: [],
  });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  const [page, setPage] = useState(1);
  const [source, setSource] = useState('ALL');
  const [eventType, setEventType] = useState('ALL');
  const [role, setRole] = useState('ALL');
  const [success, setSuccess] = useState('');
  const [keyword, setKeyword] = useState('');
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const [selectedLog, setSelectedLog] = useState(null);

  useEffect(() => {
    let active = true;
    adminGovernanceApi
      .getAuditLogs({
        page,
        size: 15,
        source,
        eventType,
        role,
        success,
        keyword,
        fromDate,
        toDate,
      })
      .then((res) => {
        if (!active) return;
        setData(res);
        setError('');
        setLoading(false);
      })
      .catch((err) => {
        if (!active) return;
        setError(err.message || 'Không thể tải dữ liệu nhật ký hệ thống.');
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, source, eventType, role, success, keyword, fromDate, toDate, reloadTick]);

  const triggerReload = () => {
    setLoading(true);
    setReloadTick((v) => v + 1);
  };

  const updateFilter = (setter, value) => {
    setLoading(true);
    setPage(1);
    setter(value);
  };

  const handlePageChange = (nextPage) => {
    setLoading(true);
    setPage(nextPage);
  };

  const resetFilters = () => {
    setLoading(true);
    setPage(1);
    setSource('ALL');
    setEventType('ALL');
    setRole('ALL');
    setSuccess('');
    setKeyword('');
    setFromDate('');
    setToDate('');
  };

  const summary = data.summary || {};
  const hasActiveFilters =
    source !== 'ALL' ||
    eventType !== 'ALL' ||
    role !== 'ALL' ||
    success !== '' ||
    keyword.trim() !== '' ||
    fromDate !== '' ||
    toDate !== '';

  return (
    <div className="space-y-6 p-4 text-slate-900 lg:p-7">
      {/* Page Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Nhật ký Hệ thống
          </h1>
        </div>

        <button
          type="button"
          onClick={triggerReload}
          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-sm font-medium text-slate-700 shadow-2xs hover:bg-slate-50"
        >
          <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
          <span>Làm mới</span>
        </button>
      </div>

      {/* KPI Summary Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
            <Activity size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Tổng sự kiện ghi nhận</div>
            <div className="text-2xl font-extrabold text-slate-900">{summary.totalAll ?? 0}</div>
            <div className="text-xs text-slate-500">{summary.todayCount ?? 0} sự kiện trong hôm nay</div>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-700">
            <Shield size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Bảo mật & Tài khoản</div>
            <div className="text-2xl font-extrabold text-indigo-700">{summary.securityCount ?? 0}</div>
            <div className="text-xs text-slate-500">Đăng nhập, OTP & Hồ sơ</div>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
            <ClipboardCheck size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Nghiệp vụ Claim & Báo cáo</div>
            <div className="text-2xl font-extrabold text-emerald-700">
              {(summary.claimCount ?? 0) + (summary.reportCount ?? 0)}
            </div>
            <div className="text-xs text-slate-500">
              {summary.claimCount ?? 0} Claim · {summary.reportCount ?? 0} Báo cáo
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
            <ShieldAlert size={21} />
          </div>
          <div>
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">Cảnh báo xác thực thất bại</div>
            <div className="text-2xl font-extrabold text-rose-700">{summary.failedSecurityCount ?? 0}</div>
            <div className="text-xs text-slate-500">Sai mật khẩu / Khóa tạm thời</div>
          </div>
        </div>
      </div>

      {/* Filter Panel */}
      <div className="space-y-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        {/* Source Tabs */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div className="flex flex-wrap items-center gap-2">
            {SOURCE_TABS.map((tab) => {
              const active = source === tab.value;
              return (
                <button
                  key={tab.value}
                  type="button"
                  onClick={() => updateFilter(setSource, tab.value)}
                  className={`rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
                    active
                      ? 'bg-teal-700 text-white shadow-sm'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>

          {hasActiveFilters && (
            <button
              type="button"
              onClick={resetFilters}
              className="text-xs font-semibold text-teal-700 underline hover:text-teal-800"
            >
              Đặt lại bộ lọc
            </button>
          )}
        </div>

        {/* Inputs Row */}
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-6">
          <div className="relative xl:col-span-2">
            <Search size={16} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={keyword}
              onChange={(e) => updateFilter(setKeyword, e.target.value)}
              placeholder="Tìm người thực hiện, email, mã sự kiện, IP, nội dung..."
              className="w-full rounded-lg border border-slate-200 py-2 pl-9 pr-3 text-sm text-slate-800 focus:border-teal-600 focus:outline-none"
            />
          </div>

          <div>
            <select
              value={eventType}
              onChange={(e) => updateFilter(setEventType, e.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-teal-600 focus:outline-none"
            >
              <option value="ALL">Mọi loại sự kiện</option>
              {(data.eventTypes || []).map((ev) => (
                <option key={ev} value={ev}>
                  {ev}
                </option>
              ))}
            </select>
          </div>

          <div>
            <select
              value={role}
              onChange={(e) => updateFilter(setRole, e.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-teal-600 focus:outline-none"
            >
              <option value="ALL">Mọi vai trò (Role)</option>
              <option value="ADMIN">ADMIN</option>
              <option value="MANAGER">MANAGER</option>
              <option value="APPROVER">STAFF</option>
              <option value="USER">USER</option>
            </select>
          </div>

          <div>
            <select
              value={success}
              onChange={(e) => updateFilter(setSuccess, e.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 focus:border-teal-600 focus:outline-none"
            >
              <option value="">Mọi kết quả</option>
              <option value="true">Thành công</option>
              <option value="false">Thất bại / Cảnh báo</option>
            </select>
          </div>

          <div className="flex items-center gap-1.5">
            <input
              type="date"
              value={fromDate}
              onChange={(e) => updateFilter(setFromDate, e.target.value)}
              title="Từ ngày"
              className="w-full rounded-lg border border-slate-200 px-2.5 py-2 text-xs text-slate-700 focus:border-teal-600 focus:outline-none"
            />
            <span className="text-slate-400">–</span>
            <input
              type="date"
              value={toDate}
              onChange={(e) => updateFilter(setToDate, e.target.value)}
              title="Đến ngày"
              className="w-full rounded-lg border border-slate-200 px-2.5 py-2 text-xs text-slate-700 focus:border-teal-600 focus:outline-none"
            />
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        {loading ? (
          <div className="space-y-3 p-6">
            {[1, 2, 3, 4, 5, 6].map((i) => (
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
        ) : (data.content || []).length === 0 ? (
          <div className="p-12 text-center">
            <FileSearch size={36} className="mx-auto text-slate-300" />
            <p className="mt-2 text-sm font-medium text-slate-600">
              Không có bản ghi nhật ký nào khớp với điều kiện tìm kiếm.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/80 text-xs font-bold uppercase tracking-wider text-slate-500">
                  <th className="px-4 py-3.5">Mã Log</th>
                  <th className="px-4 py-3.5">Thời gian</th>
                  <th className="px-4 py-3.5">Phân hệ</th>
                  <th className="px-4 py-3.5">Sự kiện</th>
                  <th className="px-4 py-3.5">Người thực hiện</th>
                  <th className="px-4 py-3.5 text-center">Kết quả</th>
                  <th className="px-4 py-3.5 text-right">Chi tiết</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {data.content.map((log) => (
                  <tr key={log.uid} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-4 py-3.5 font-mono text-xs font-bold text-slate-600">
                      {log.uid}
                    </td>
                    <td className="px-4 py-3.5 whitespace-nowrap text-xs text-slate-600">
                      {formatDateTime(log.createdAt)}
                    </td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-bold ${
                          SOURCE_BADGES[log.source] || 'bg-slate-100 text-slate-700 border-slate-200'
                        }`}
                      >
                        {log.source}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 font-mono text-xs font-semibold text-slate-800">
                      {log.eventType}
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="flex items-center gap-2">
                        <div>
                          <div className="font-semibold text-slate-900">
                            {String(log.actorName || '').replace(/\s*\((Manager|Admin|Approver|User|Staff)\)/gi, '').trim()}
                          </div>
                          <div className="text-xs text-slate-500">{log.actorEmail}</div>
                        </div>
                        <span
                          className={`rounded border px-1.5 py-0.5 text-[10px] font-bold ${
                            ROLE_BADGES[log.actorRole] || ROLE_BADGES.USER
                          }`}
                        >
                          {log.actorRole === 'APPROVER' ? 'STAFF' : log.actorRole}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-center">
                      {log.success ? (
                        <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
                          <CheckCircle2 size={12} /> Thành công
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-rose-50 px-2.5 py-0.5 text-xs font-semibold text-rose-700">
                          <AlertTriangle size={12} /> Thất bại
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-right">
                      <button
                        type="button"
                        onClick={() => setSelectedLog(log)}
                        className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                      >
                        <Eye size={13} />
                        <span>Xem</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Pagination Footer */}
      {!loading && !error && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-slate-500">
            Trang <strong>{data.page}</strong> / <strong>{data.totalPages}</strong> (Tổng <strong>{data.totalElements}</strong> bản ghi nhật ký)
          </p>
          <Pagination
            currentPage={data.page}
            totalPages={data.totalPages}
            onPageChange={handlePageChange}
          />
        </div>
      )}

      {/* Detail Modal */}
      {selectedLog && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-xs"
          onClick={() => setSelectedLog(null)}
        >
          <div
            className="w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-100 px-6 py-4">
              <div className="flex items-center gap-2.5">
                <span className="rounded-md bg-slate-100 px-2.5 py-1 font-mono text-xs font-bold text-slate-800">
                  {selectedLog.uid}
                </span>
                <h3 className="text-base font-bold text-slate-900">Chi tiết Bản ghi Nhật ký</h3>
              </div>
              <button
                type="button"
                onClick={() => setSelectedLog(null)}
                className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-4 p-6 text-sm">
              <div className="grid grid-cols-1 gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4 sm:grid-cols-2">
                <div>
                  <div className="text-xs font-semibold text-slate-400">Thời điểm ghi nhận</div>
                  <div className="mt-0.5 flex items-center gap-1.5 font-semibold text-slate-800">
                    <Calendar size={14} className="text-teal-700" />
                    {formatDateTime(selectedLog.createdAt)}
                  </div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-slate-400">Mã sự kiện & Phân hệ</div>
                  <div className="mt-0.5 font-mono text-xs font-bold text-slate-800">
                    {selectedLog.source} / {selectedLog.eventType}
                  </div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-slate-400">Người thực hiện</div>
                  <div className="mt-0.5 flex items-center gap-1.5 font-semibold text-slate-900">
                    <UserCheck size={14} className="text-teal-700" />
                    {String(selectedLog.actorName || '').replace(/\s*\((Manager|Admin|Approver|User|Staff)\)/gi, '').trim()} ({selectedLog.actorRole === 'APPROVER' ? 'STAFF' : selectedLog.actorRole})
                  </div>
                  <div className="text-xs text-slate-500">{selectedLog.actorEmail}</div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-slate-400">Địa chỉ IP & Môi trường</div>
                  <div className="mt-0.5 font-mono text-xs font-semibold text-slate-800">
                    IP: {selectedLog.ipAddress || '—'}
                  </div>
                  <div className="truncate text-xs text-slate-500" title={selectedLog.userAgent}>
                    {selectedLog.userAgent || '—'}
                  </div>
                </div>
              </div>

              <div>
                <div className="mb-1 text-xs font-bold uppercase tracking-wider text-slate-500">
                  Chi tiết
                </div>
                <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-lg border border-slate-200 bg-slate-900 p-3.5 font-mono text-xs text-teal-300">
                  {String(selectedLog.details || '{}').replace(/APPROVER/gi, 'STAFF')}
                </pre>
              </div>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 bg-slate-50 px-6 py-3.5">
              <div>
                {selectedLog.targetType === 'REPORT' && selectedLog.targetId && (
                  <Link
                    to={`/admin/reports/${selectedLog.targetId}`}
                    onClick={() => setSelectedLog(null)}
                    className="text-xs font-semibold text-teal-700 underline hover:text-teal-800"
                  >
                    Mở hồ sơ Báo cáo #{selectedLog.targetId} →
                  </Link>
                )}
              </div>
              <button
                type="button"
                onClick={() => setSelectedLog(null)}
                className="rounded-lg bg-slate-800 px-4 py-2 text-xs font-semibold text-white hover:bg-slate-900"
              >
                Đóng
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
