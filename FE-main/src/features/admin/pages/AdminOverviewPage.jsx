import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowUpRight,
  ClipboardList,
  FileWarning,
  Package,
  RefreshCw,
  Shield,
  ShieldAlert,
  Users,
  Warehouse,
} from 'lucide-react';
import adminGovernanceApi from '../api/adminGovernanceApi';

export default function AdminOverviewPage() {
  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    let active = true;
    adminGovernanceApi
      .getDashboardOverview()
      .then((data) => {
        if (!active) return;
        setOverview(data);
        setError('');
        setLoading(false);
      })
      .catch((err) => {
        if (!active) return;
        setError(err.message || 'Không thể tải dữ liệu Bảng điều khiển quản trị.');
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [reloadTick]);

  const handleRefresh = () => {
    setLoading(true);
    setReloadTick((v) => v + 1);
  };

  const accounts = overview?.accounts || {};
  const byRole = accounts.byRole || {};
  const posts = overview?.posts || {};
  const claims = overview?.claims || {};
  const reports = overview?.reports || {};
  const office = overview?.office || {};
  const audit = overview?.audit || {};

  const openReportsTotal = Number(reports.open || 0) + Number(reports.inReview || 0);

  return (
    <div className="space-y-6 p-4 text-slate-900 lg:p-7">
      {/* Simple Header */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-white px-6 py-5 shadow-2xs">
        <div>
          <h1 className="text-xl font-extrabold tracking-tight text-slate-900">
            Bảng điều khiển Quản trị Hệ thống
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Số liệu thống kê vận hành toàn hệ thống theo thời gian thực.
          </p>
        </div>

        <button
          type="button"
          onClick={handleRefresh}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100 cursor-pointer"
        >
          <RefreshCw size={15} className={loading ? 'animate-spin text-teal-700' : 'text-slate-500'} />
          <span>Làm mới số liệu</span>
        </button>
      </div>

      {error && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm font-medium text-rose-700">
          {error}
        </div>
      )}

      {/* 6 Visual Number KPI Cards */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {/* 1. Tài khoản Thành viên */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-2xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Tài khoản thành viên
              </div>
              <div className="mt-1.5 text-3xl font-extrabold text-slate-900">
                {accounts.total ?? 0}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700">
              <Users size={22} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-4 gap-2 border-t border-slate-100 pt-3.5 text-xs">
            <div className="rounded-lg bg-slate-50 py-2 text-center">
              <div className="text-[11px] text-slate-500">USER</div>
              <div className="mt-0.5 text-base font-extrabold text-slate-800">{byRole.USER ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/70 py-2 text-center">
              <div className="text-[11px] text-slate-500">STAFF</div>
              <div className="mt-0.5 text-base font-extrabold text-teal-700">{byRole.APPROVER ?? 0}</div>
            </div>
            <div className="rounded-lg bg-sky-50/70 py-2 text-center">
              <div className="text-[11px] text-slate-500">MANAGER</div>
              <div className="mt-0.5 text-base font-extrabold text-sky-700">{byRole.MANAGER ?? 0}</div>
            </div>
            <div className="rounded-lg bg-purple-50/70 py-2 text-center">
              <div className="text-[11px] text-slate-500">ADMIN</div>
              <div className="mt-0.5 text-base font-extrabold text-purple-700">{byRole.ADMIN ?? 0}</div>
            </div>
          </div>
        </div>

        {/* 2. Tổng bài đăng hệ thống */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-2xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Tổng bài đăng hệ thống
              </div>
              <div className="mt-1.5 text-3xl font-extrabold text-slate-900">
                {posts.total ?? 0}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
              <Package size={22} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3.5 text-xs">
            <div className="rounded-lg bg-rose-50/70 py-2 text-center">
              <div className="text-[11px] text-slate-500">Thất lạc (LOST)</div>
              <div className="mt-0.5 text-base font-extrabold text-rose-700">{posts.lost ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/70 py-2 text-center">
              <div className="text-[11px] text-slate-500">Nhặt được (FOUND)</div>
              <div className="mt-0.5 text-base font-extrabold text-teal-700">{posts.found ?? 0}</div>
            </div>
            <div className="rounded-lg bg-emerald-50/70 py-2 text-center">
              <div className="text-[11px] text-slate-500">Đã trao trả</div>
              <div className="mt-0.5 text-base font-extrabold text-emerald-700">{posts.returned ?? 0}</div>
            </div>
          </div>
        </div>

        {/* 3. Yêu cầu nhận đồ (Claims) */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-2xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Yêu cầu nhận đồ (Claims)
              </div>
              <div className="mt-1.5 text-3xl font-extrabold text-slate-900">
                {claims.total ?? 0}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-700">
              <ClipboardList size={22} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3.5 text-xs">
            <div className="rounded-lg bg-amber-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Chờ duyệt</div>
              <div className="mt-0.5 text-base font-extrabold text-amber-700">
                {(claims.pending ?? 0) + (claims.needsInfo ?? 0)}
              </div>
            </div>
            <div className="rounded-lg bg-sky-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Đã duyệt</div>
              <div className="mt-0.5 text-base font-extrabold text-sky-700">{claims.approved ?? 0}</div>
            </div>
            <div className="rounded-lg bg-emerald-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Hoàn tất trả</div>
              <div className="mt-0.5 text-base font-extrabold text-emerald-700">{claims.completed ?? 0}</div>
            </div>
          </div>
        </div>

        {/* 4. Lưu kho Văn phòng & Chuyển giao */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-2xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Tài sản lưu kho Văn phòng
              </div>
              <div className="mt-1.5 text-3xl font-extrabold text-slate-900">
                {office.inStorage ?? 0}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-amber-50 text-amber-700">
              <Warehouse size={22} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3.5 text-xs">
            <div className="rounded-lg bg-slate-50 py-2 text-center">
              <div className="text-[11px] text-slate-500">Tổng biên nhận</div>
              <div className="mt-0.5 text-base font-extrabold text-slate-800">{office.totalReceipts ?? 0}</div>
            </div>
            <div className="rounded-lg bg-amber-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Chờ chuyển giao</div>
              <div className="mt-0.5 text-base font-extrabold text-amber-700">{office.pendingTransfers ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Vị trí kệ kho</div>
              <div className="mt-0.5 text-base font-extrabold text-teal-700">{office.activeLocations ?? 0}</div>
            </div>
          </div>
        </div>

        {/* 5. Báo cáo Vi phạm & Tranh chấp */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-2xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Báo cáo &amp; Tranh chấp
              </div>
              <div className="mt-1.5 text-3xl font-extrabold text-slate-900">
                {reports.total ?? 0}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
              <ShieldAlert size={22} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3.5 text-xs">
            <div className="rounded-lg bg-rose-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Đang chờ xử lý</div>
              <div className="mt-0.5 text-base font-extrabold text-rose-700">{openReportsTotal}</div>
            </div>
            <div className="rounded-lg bg-amber-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Sự cố bàn giao</div>
              <div className="mt-0.5 text-base font-extrabold text-amber-800">{reports.handoverIssues ?? 0}</div>
            </div>
            <div className="rounded-lg bg-emerald-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Đã giải quyết</div>
              <div className="mt-0.5 text-base font-extrabold text-emerald-700">{reports.resolved ?? 0}</div>
            </div>
          </div>
        </div>

        {/* 6. Nhật ký Audit & Cấu hình */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-2xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Nhật ký Hệ thống (Audit)
              </div>
              <div className="mt-1.5 text-3xl font-extrabold text-slate-900">
                {audit.totalEvents ?? 0}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-purple-50 text-purple-700">
              <Activity size={22} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3.5 text-xs">
            <div className="rounded-lg bg-indigo-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Hôm nay</div>
              <div className="mt-0.5 text-base font-extrabold text-indigo-700">{audit.todayEvents ?? 0}</div>
            </div>
            <div className="rounded-lg bg-rose-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Sai mật khẩu</div>
              <div className="mt-0.5 text-base font-extrabold text-rose-700">{audit.failedLogins ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/80 py-2 text-center">
              <div className="text-[11px] text-slate-500">Lý do báo cáo</div>
              <div className="mt-0.5 text-base font-extrabold text-teal-700">
                {reports.activeReasonsCount ?? 0}/{reports.totalReasonsCount ?? 0}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Access Links for Admin */}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <Link
          to="/admin/users"
          className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
              <Users size={19} />
            </div>
            <div>
              <div className="text-sm font-bold text-slate-800">Quản lý Tài khoản</div>
              <div className="text-xs text-slate-500">
                {accounts.active ?? 0} đang hoạt động · {accounts.suspended ?? 0} đang khóa
              </div>
            </div>
          </div>
          <ArrowUpRight size={17} className="text-slate-300 group-hover:text-teal-600" />
        </Link>

        <Link
          to="/admin/report-reasons"
          className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-rose-50 text-rose-700">
              <FileWarning size={19} />
            </div>
            <div>
              <div className="text-sm font-bold text-slate-800">Cấu hình Lý do báo cáo</div>
              <div className="text-xs text-slate-500">
                {reports.activeReasonsCount ?? 0} lý do đang kích hoạt
              </div>
            </div>
          </div>
          <ArrowUpRight size={17} className="text-slate-300 group-hover:text-teal-600" />
        </Link>

        <Link
          to="/admin/audit-logs"
          className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-4 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-indigo-700">
              <Shield size={19} />
            </div>
            <div>
              <div className="text-sm font-bold text-slate-800">Tra cứu Nhật ký Audit</div>
              <div className="text-xs text-slate-500">
                {audit.todayEvents ?? 0} sự kiện ghi nhận hôm nay
              </div>
            </div>
          </div>
          <ArrowUpRight size={17} className="text-slate-300 group-hover:text-teal-600" />
        </Link>
      </div>
    </div>
  );
}
