import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  AlertTriangle,
  ArrowUpRight,
  BarChart3,
  CheckCircle2,
  ClipboardList,
  FileWarning,
  Layers,
  Package,
  RefreshCw,
  Shield,
  ShieldAlert,
  Tags,
  TrendingUp,
  Users,
  Warehouse,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import adminGovernanceApi from '../api/adminGovernanceApi';

function formatShortTime(value) {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      dateStyle: 'short',
      timeStyle: 'short',
    });
  } catch {
    return String(value);
  }
}

export default function AdminOverviewPage() {
  const { user, isManager } = useAuth();
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
  const dailyTrends = Array.isArray(overview?.dailyTrends) ? overview.dailyTrends : [];
  const topCategories = Array.isArray(overview?.topCategories) ? overview.topCategories : [];
  const recentPosts = Array.isArray(overview?.recentPosts) ? overview.recentPosts : [];
  const pendingReports = Array.isArray(overview?.pendingReports) ? overview.pendingReports : [];
  const recentAuditLogs = Array.isArray(overview?.recentAuditLogs) ? overview.recentAuditLogs : [];

  const maxTrendValue = Math.max(
    1,
    ...dailyTrends.map((d) => Math.max(Number(d.postsCount || 0), Number(d.claimsCount || 0), Number(d.reportsCount || 0)))
  );

  const maxCategoryPosts = Math.max(
    1,
    ...topCategories.map((c) => Number(c.totalPosts || 0))
  );

  const openReportsTotal = Number(reports.open || 0) + Number(reports.inReview || 0);

  return (
    <div className="space-y-6 p-4 text-slate-900 lg:p-7">
      {/* Top Banner */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200 bg-gradient-to-r from-slate-900 via-slate-800 to-teal-900 p-6 text-white shadow-md">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full bg-teal-400/15 px-3 py-1 text-xs font-bold uppercase tracking-wider text-teal-300 border border-teal-400/30">
            <span className="h-2 w-2 rounded-full bg-teal-400 animate-pulse" />
            Trung tâm Điều hành Sagasu ({user?.role || 'ADMIN'})
          </div>
          <h1 className="mt-2 text-2xl font-extrabold tracking-tight">
            Bảng điều khiển Quản trị Hệ thống
          </h1>
          <p className="mt-1 text-sm text-slate-300">
            Tổng hợp số liệu thời gian thực về Tin đăng, Yêu cầu nhận đồ, Kho văn phòng, Báo cáo vi phạm và Nhật ký bảo mật.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={handleRefresh}
            className="inline-flex items-center gap-2 rounded-xl border border-white/15 bg-white/10 px-4 py-2.5 text-sm font-semibold text-white backdrop-blur-xs hover:bg-white/20"
          >
            <RefreshCw size={15} className={loading ? 'animate-spin' : ''} />
            <span>Đồng bộ số liệu</span>
          </button>
          <Link
            to="/admin/audit-logs"
            className="inline-flex items-center gap-2 rounded-xl bg-teal-500 px-4 py-2.5 text-sm font-bold text-slate-950 shadow-sm hover:bg-teal-400"
          >
            <Shield size={16} />
            <span>Nhật ký Audit Log</span>
          </Link>
        </div>
      </div>

      {/* Quick Shortcuts Bar */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        <Link
          to="/admin/users"
          className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
        >
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-teal-50 text-teal-700">
              <Users size={18} />
            </div>
            <div>
              <div className="text-xs font-bold text-slate-800">Tài khoản</div>
              <div className="text-[11px] text-slate-400">{accounts.total ?? 0} thành viên</div>
            </div>
          </div>
          <ArrowUpRight size={15} className="text-slate-300 group-hover:text-teal-600" />
        </Link>

        <Link
          to="/admin/report-reasons"
          className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
        >
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-rose-50 text-rose-700">
              <FileWarning size={18} />
            </div>
            <div>
              <div className="text-xs font-bold text-slate-800">Lý do báo cáo</div>
              <div className="text-[11px] text-slate-400">{reports.activeReasonsCount ?? 0} đang bật</div>
            </div>
          </div>
          <ArrowUpRight size={15} className="text-slate-300 group-hover:text-teal-600" />
        </Link>

        <Link
          to="/admin/audit-logs"
          className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
        >
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-50 text-indigo-700">
              <Activity size={18} />
            </div>
            <div>
              <div className="text-xs font-bold text-slate-800">Audit Logs</div>
              <div className="text-[11px] text-slate-400">{audit.todayEvents ?? 0} hôm nay</div>
            </div>
          </div>
          <ArrowUpRight size={15} className="text-slate-300 group-hover:text-teal-600" />
        </Link>

        {isManager && (
          <>
            <Link
              to="/admin/items"
              className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
            >
              <div className="flex items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-amber-50 text-amber-700">
                  <Package size={18} />
                </div>
                <div>
                  <div className="text-xs font-bold text-slate-800">Tin đăng</div>
                  <div className="text-[11px] text-slate-400">{posts.total ?? 0} bài đăng</div>
                </div>
              </div>
              <ArrowUpRight size={15} className="text-slate-300 group-hover:text-teal-600" />
            </Link>

            <Link
              to="/admin/reports"
              className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
            >
              <div className="flex items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-orange-50 text-orange-700">
                  <AlertTriangle size={18} />
                </div>
                <div>
                  <div className="text-xs font-bold text-slate-800">Điều tra</div>
                  <div className="text-[11px] text-slate-400">{openReportsTotal} chờ xử lý</div>
                </div>
              </div>
              <ArrowUpRight size={15} className="text-slate-300 group-hover:text-teal-600" />
            </Link>

            <Link
              to="/admin/categories"
              className="group flex items-center justify-between rounded-xl border border-slate-200 bg-white p-3.5 shadow-2xs transition-all hover:border-teal-500 hover:shadow-sm"
            >
              <div className="flex items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-emerald-50 text-emerald-700">
                  <Tags size={18} />
                </div>
                <div>
                  <div className="text-xs font-bold text-slate-800">Danh mục</div>
                  <div className="text-[11px] text-slate-400">{office.activeCategories ?? 0} hoạt động</div>
                </div>
              </div>
              <ArrowUpRight size={15} className="text-slate-300 group-hover:text-teal-600" />
            </Link>
          </>
        )}
      </div>

      {error && (
        <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm font-medium text-rose-700">
          {error}
        </div>
      )}

      {/* Main 6 KPI Cards */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        {/* Card 1: Posts */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Tổng bài đăng hệ thống
              </div>
              <div className="mt-1 text-3xl font-extrabold text-slate-900">{posts.total ?? 0}</div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-50 text-teal-700">
              <Package size={24} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3 text-xs">
            <div className="rounded-lg bg-rose-50/70 p-2 text-center">
              <div className="text-slate-500">Thất lạc (LOST)</div>
              <div className="mt-0.5 text-sm font-extrabold text-rose-700">{posts.lost ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/70 p-2 text-center">
              <div className="text-slate-500">Nhặt được (FOUND)</div>
              <div className="mt-0.5 text-sm font-extrabold text-teal-700">{posts.found ?? 0}</div>
            </div>
            <div className="rounded-lg bg-emerald-50/70 p-2 text-center">
              <div className="text-slate-500">Đã trao trả</div>
              <div className="mt-0.5 text-sm font-extrabold text-emerald-700">{posts.returned ?? 0}</div>
            </div>
          </div>
        </div>

        {/* Card 2: Claims */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Hồ sơ Yêu cầu nhận đồ (Claims)
              </div>
              <div className="mt-1 text-3xl font-extrabold text-slate-900">{claims.total ?? 0}</div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-700">
              <ClipboardList size={24} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3 text-xs">
            <div className="rounded-lg bg-amber-50/80 p-2 text-center">
              <div className="text-slate-500">Chờ duyệt</div>
              <div className="mt-0.5 text-sm font-extrabold text-amber-700">
                {(claims.pending ?? 0) + (claims.needsInfo ?? 0)}
              </div>
            </div>
            <div className="rounded-lg bg-sky-50/80 p-2 text-center">
              <div className="text-slate-500">Đã duyệt</div>
              <div className="mt-0.5 text-sm font-extrabold text-sky-700">{claims.approved ?? 0}</div>
            </div>
            <div className="rounded-lg bg-emerald-50/80 p-2 text-center">
              <div className="text-slate-500">Hoàn tất trả</div>
              <div className="mt-0.5 text-sm font-extrabold text-emerald-700">{claims.completed ?? 0}</div>
            </div>
          </div>
        </div>

        {/* Card 3: Reports */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Báo cáo Vi phạm & Tranh chấp
              </div>
              <div className="mt-1 text-3xl font-extrabold text-slate-900">{reports.total ?? 0}</div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-rose-50 text-rose-700">
              <ShieldAlert size={24} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3 text-xs">
            <div className="rounded-lg bg-rose-50/80 p-2 text-center">
              <div className="text-slate-500">Đang mở / Xử lý</div>
              <div className="mt-0.5 text-sm font-extrabold text-rose-700">{openReportsTotal}</div>
            </div>
            <div className="rounded-lg bg-amber-50/80 p-2 text-center">
              <div className="text-slate-500">Sự cố bàn giao</div>
              <div className="mt-0.5 text-sm font-extrabold text-amber-800">{reports.handoverIssues ?? 0}</div>
            </div>
            <div className="rounded-lg bg-emerald-50/80 p-2 text-center">
              <div className="text-slate-500">Đã giải quyết</div>
              <div className="mt-0.5 text-sm font-extrabold text-emerald-700">{reports.resolved ?? 0}</div>
            </div>
          </div>
        </div>

        {/* Card 4: Office Storage */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Lưu kho Văn phòng & Chuyển giao
              </div>
              <div className="mt-1 text-3xl font-extrabold text-slate-900">{office.inStorage ?? 0}</div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-amber-50 text-amber-700">
              <Warehouse size={24} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3 text-xs">
            <div className="rounded-lg bg-slate-50 p-2 text-center">
              <div className="text-slate-500">Tổng biên nhận</div>
              <div className="mt-0.5 text-sm font-extrabold text-slate-800">{office.totalReceipts ?? 0}</div>
            </div>
            <div className="rounded-lg bg-amber-50/80 p-2 text-center">
              <div className="text-slate-500">Chờ chuyển giao</div>
              <div className="mt-0.5 text-sm font-extrabold text-amber-700">{office.pendingTransfers ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/80 p-2 text-center">
              <div className="text-slate-500">Vị trí kệ kho</div>
              <div className="mt-0.5 text-sm font-extrabold text-teal-700">{office.activeLocations ?? 0}</div>
            </div>
          </div>
        </div>

        {/* Card 5: Accounts */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Tài khoản Thành viên
              </div>
              <div className="mt-1 text-3xl font-extrabold text-slate-900">{accounts.total ?? 0}</div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-50 text-emerald-700">
              <Users size={24} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-4 gap-1.5 border-t border-slate-100 pt-3 text-[11px]">
            <div className="rounded-lg bg-slate-50 p-1.5 text-center">
              <div className="text-slate-500">USER</div>
              <div className="font-extrabold text-slate-800">{byRole.USER ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/70 p-1.5 text-center">
              <div className="text-slate-500">STAFF</div>
              <div className="font-extrabold text-teal-700">{byRole.APPROVER ?? 0}</div>
            </div>
            <div className="rounded-lg bg-sky-50/70 p-1.5 text-center">
              <div className="text-slate-500">MANAGER</div>
              <div className="font-extrabold text-sky-700">{byRole.MANAGER ?? 0}</div>
            </div>
            <div className="rounded-lg bg-purple-50/70 p-1.5 text-center">
              <div className="text-slate-500">ADMIN</div>
              <div className="font-extrabold text-purple-700">{byRole.ADMIN ?? 0}</div>
            </div>
          </div>
        </div>

        {/* Card 6: Audit & Governance */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-bold uppercase tracking-wider text-slate-400">
                Nhật ký Audit & Quản trị
              </div>
              <div className="mt-1 text-3xl font-extrabold text-slate-900">{audit.totalEvents ?? 0}</div>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-purple-50 text-purple-700">
              <Activity size={24} />
            </div>
          </div>
          <div className="mt-4 grid grid-cols-3 gap-2 border-t border-slate-100 pt-3 text-xs">
            <div className="rounded-lg bg-indigo-50/80 p-2 text-center">
              <div className="text-slate-500">Hôm nay</div>
              <div className="mt-0.5 text-sm font-extrabold text-indigo-700">{audit.todayEvents ?? 0}</div>
            </div>
            <div className="rounded-lg bg-rose-50/80 p-2 text-center">
              <div className="text-slate-500">Sai mật khẩu</div>
              <div className="mt-0.5 text-sm font-extrabold text-rose-700">{audit.failedLogins ?? 0}</div>
            </div>
            <div className="rounded-lg bg-teal-50/80 p-2 text-center">
              <div className="text-slate-500">Lý do báo cáo</div>
              <div className="mt-0.5 text-sm font-extrabold text-teal-700">
                {reports.activeReasonsCount ?? 0}/{reports.totalReasonsCount ?? 0}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Charts Section: 7-Day Activity Trend & Top Categories */}
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
        {/* 7-Day Trend Chart */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs xl:col-span-7">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-4">
            <div className="flex items-center gap-2.5">
              <TrendingUp size={19} className="text-teal-700" />
              <div>
                <h2 className="text-base font-bold text-slate-900">Biểu đồ Hoạt động 7 ngày gần nhất</h2>
                <p className="text-xs text-slate-500">So sánh lượng bài đăng mới, yêu cầu nhận đồ và báo cáo phát sinh theo ngày</p>
              </div>
            </div>
            <div className="flex items-center gap-4 text-xs font-semibold">
              <span className="inline-flex items-center gap-1.5 text-teal-700">
                <span className="h-2.5 w-2.5 rounded-xs bg-teal-600" /> Tin đăng
              </span>
              <span className="inline-flex items-center gap-1.5 text-indigo-700">
                <span className="h-2.5 w-2.5 rounded-xs bg-indigo-500" /> Claims
              </span>
              <span className="inline-flex items-center gap-1.5 text-rose-700">
                <span className="h-2.5 w-2.5 rounded-xs bg-rose-500" /> Báo cáo
              </span>
            </div>
          </div>

          <div className="mt-5 grid grid-cols-7 gap-3 pt-2">
            {dailyTrends.map((day) => {
              const pHeight = Math.max(8, Math.round((Number(day.postsCount || 0) / maxTrendValue) * 120));
              const cHeight = Math.max(8, Math.round((Number(day.claimsCount || 0) / maxTrendValue) * 120));
              const rHeight = Math.max(8, Math.round((Number(day.reportsCount || 0) / maxTrendValue) * 120));
              return (
                <div key={day.date} className="flex flex-col items-center">
                  <div className="flex h-36 w-full items-end justify-center gap-1.5 rounded-xl bg-slate-50/70 px-1.5 pb-2 pt-4">
                    <div
                      style={{ height: `${pHeight}px` }}
                      title={`Tin đăng: ${day.postsCount}`}
                      className="w-2.5 rounded-t-md bg-teal-600 transition-all"
                    />
                    <div
                      style={{ height: `${cHeight}px` }}
                      title={`Claims: ${day.claimsCount}`}
                      className="w-2.5 rounded-t-md bg-indigo-500 transition-all"
                    />
                    <div
                      style={{ height: `${rHeight}px` }}
                      title={`Báo cáo: ${day.reportsCount}`}
                      className="w-2.5 rounded-t-md bg-rose-500 transition-all"
                    />
                  </div>
                  <div className="mt-2 text-xs font-bold text-slate-700">{day.label}</div>
                  <div className="text-[11px] text-slate-400">
                    {day.postsCount}/{day.claimsCount}/{day.reportsCount}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Top Categories Breakdown */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs xl:col-span-5">
          <div className="flex items-center justify-between border-b border-slate-100 pb-4">
            <div className="flex items-center gap-2.5">
              <BarChart3 size={19} className="text-teal-700" />
              <div>
                <h2 className="text-base font-bold text-slate-900">Phân bổ Tin đăng theo Danh mục</h2>
                <p className="text-xs text-slate-500">Top danh mục có số lượng đồ thất lạc & nhặt được cao nhất</p>
              </div>
            </div>
            {isManager && (
              <Link to="/admin/categories" className="text-xs font-semibold text-teal-700 hover:underline">
                Quản lý →
              </Link>
            )}
          </div>

          <div className="mt-4 space-y-3.5">
            {topCategories.length === 0 ? (
              <div className="py-10 text-center text-xs text-slate-400">Chưa có dữ liệu danh mục</div>
            ) : (
              topCategories.map((cat) => {
                const pct = Math.min(100, Math.round((Number(cat.totalPosts || 0) / maxCategoryPosts) * 100));
                return (
                  <div key={cat.id} className="space-y-1.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-bold text-slate-800">{cat.name}</span>
                      <span className="text-slate-500">
                        <strong className="text-slate-900">{cat.totalPosts}</strong> bài ({cat.lostCount} mất · {cat.foundCount} nhặt)
                      </span>
                    </div>
                    <div className="h-2.5 w-full overflow-hidden rounded-full bg-slate-100">
                      <div
                        style={{ width: `${Math.max(4, pct)}%` }}
                        className="h-full rounded-full bg-gradient-to-r from-teal-600 to-emerald-500"
                      />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* Bottom 3 Live Feeds: Recent Audit Logs, Pending Reports, Recent Posts */}
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
        {/* Recent Audit Logs */}
        <div className="rounded-2xl border border-slate-200 bg-white shadow-xs xl:col-span-5">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div className="flex items-center gap-2">
              <Shield size={17} className="text-indigo-600" />
              <h2 className="text-sm font-bold text-slate-900">Nhật ký Hệ thống gần đây (Audit Logs)</h2>
            </div>
            <Link to="/admin/audit-logs" className="text-xs font-semibold text-teal-700 hover:underline">
              Xem tất cả →
            </Link>
          </div>
          <div className="divide-y divide-slate-100">
            {recentAuditLogs.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400">Chưa có bản ghi nhật ký nào.</div>
            ) : (
              recentAuditLogs.slice(0, 6).map((log) => (
                <div key={log.uid} className="flex items-start justify-between gap-3 px-5 py-3 text-xs">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span
                        className={`rounded px-1.5 py-0.5 font-mono text-[10px] font-bold ${
                          log.source === 'SECURITY'
                            ? 'bg-indigo-50 text-indigo-700'
                            : log.source === 'CLAIM'
                              ? 'bg-teal-50 text-teal-700'
                              : 'bg-amber-50 text-amber-800'
                        }`}
                      >
                        {log.source}
                      </span>
                      <span className="font-mono font-semibold text-slate-700">{log.eventType}</span>
                    </div>
                    <div className="mt-1 truncate font-medium text-slate-900" title={log.summary}>
                      {log.summary}
                    </div>
                    <div className="mt-0.5 text-[11px] text-slate-400">
                      {log.actorName} ({log.actorRole}) · {formatShortTime(log.createdAt)}
                    </div>
                  </div>
                  <span
                    className={`mt-1 h-2 w-2 shrink-0 rounded-full ${
                      log.success ? 'bg-emerald-500' : 'bg-rose-500'
                    }`}
                    title={log.success ? 'Thành công' : 'Cảnh báo / Thất bại'}
                  />
                </div>
              ))
            )}
          </div>
        </div>

        {/* Pending Reports */}
        <div className="rounded-2xl border border-slate-200 bg-white shadow-xs xl:col-span-4">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div className="flex items-center gap-2">
              <AlertTriangle size={17} className="text-rose-600" />
              <h2 className="text-sm font-bold text-slate-900">
                Báo cáo chờ xử lý ({openReportsTotal})
              </h2>
            </div>
            {isManager && (
              <Link to="/admin/reports" className="text-xs font-semibold text-teal-700 hover:underline">
                Hàng đợi →
              </Link>
            )}
          </div>
          <div className="divide-y divide-slate-100">
            {pendingReports.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-500">
                <CheckCircle2 size={22} className="mx-auto mb-1.5 text-emerald-500" />
                Tất cả báo cáo vi phạm đã được giải quyết.
              </div>
            ) : (
              pendingReports.map((rep) => (
                <div key={rep.id} className="px-5 py-3 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                        rep.kind === 'HANDOVER_ISSUE'
                          ? 'bg-rose-50 text-rose-700 border border-rose-200'
                          : 'bg-amber-50 text-amber-800 border border-amber-200'
                      }`}
                    >
                      {rep.kind === 'HANDOVER_ISSUE' ? 'SỰ CỐ BÀN GIAO' : rep.targetType} #{rep.targetId}
                    </span>
                    <span className="font-mono text-[11px] text-slate-400">#{rep.id}</span>
                  </div>
                  <div className="mt-1 font-bold text-slate-900">{rep.reasonLabel}</div>
                  {rep.description && (
                    <div className="mt-0.5 truncate text-slate-500" title={rep.description}>
                      {rep.description}
                    </div>
                  )}
                  <div className="mt-1 flex items-center justify-between text-[11px] text-slate-400">
                    <span>Người gửi: {rep.reporterName}</span>
                    {isManager && (
                      <Link
                        to={`/admin/reports/${rep.id}`}
                        className="font-semibold text-teal-700 hover:underline"
                      >
                        Xử lý →
                      </Link>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Recent Posts */}
        <div className="rounded-2xl border border-slate-200 bg-white shadow-xs xl:col-span-3">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div className="flex items-center gap-2">
              <Layers size={17} className="text-teal-700" />
              <h2 className="text-sm font-bold text-slate-900">Tin đăng mới nhất</h2>
            </div>
            {isManager && (
              <Link to="/admin/items" className="text-xs font-semibold text-teal-700 hover:underline">
                Tất cả →
              </Link>
            )}
          </div>
          <div className="divide-y divide-slate-100">
            {recentPosts.length === 0 ? (
              <div className="p-8 text-center text-xs text-slate-400">Chưa có bài đăng nào.</div>
            ) : (
              recentPosts.map((p) => (
                <div key={p.id} className="px-5 py-3 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                        p.postType === 'FOUND'
                          ? 'bg-teal-50 text-teal-700'
                          : 'bg-rose-50 text-rose-700'
                      }`}
                    >
                      {p.postType === 'FOUND' ? 'NHẶT ĐƯỢC' : 'THẤT LẠC'}
                    </span>
                    <span className="text-[11px] text-slate-400">{p.categoryName}</span>
                  </div>
                  <div className="mt-1 truncate font-bold text-slate-900" title={p.title}>
                    {p.title}
                  </div>
                  <div className="mt-0.5 truncate text-[11px] text-slate-500">
                    {p.authorName} · {formatShortTime(p.createdAt)}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
