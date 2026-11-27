import { BrowserRouter, Routes, Route, Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import HomePage from '@/features/home/pages/HomePage';
import LandingLoginPage from '@/features/home/pages/LandingLoginPage';
import AuthPage from '@/features/auth/pages/AuthPage';
import ProfilePage from '@/features/profile/pages/ProfilePage';
import PostItemPage from '@/features/items/pages/PostItemPage';
import SearchPage from '@/features/search/pages/SearchPage';
import ItemDetailPage from '@/features/items/pages/ItemDetailPage';
import SmartMatchPage from '@/features/smart-match/pages/SmartMatchPage';
import MyPostsPage from '@/features/items/pages/MyPostsPage';
import EditPostPage from '@/features/items/pages/EditPostPage';
import ClaimItemPage from '@/features/items/pages/ClaimItemPage';
import MyClaimsPage from '@/features/claims/pages/MyClaimsPage';
import PostClaimsPage from '@/features/claims/pages/PostClaimsPage';
import ClaimDetailPage from '@/features/claims/pages/ClaimDetailPage';
import LegacyClaimRedirect from '@/features/claims/components/LegacyClaimRedirect';
import AdminLayout from '@/components/layouts/admin/AdminLayout';
import MainLayout from '@/components/layouts/MainLayout';
import AccountSidebarLayout from '@/components/layouts/AccountSidebarLayout';
import PostAdministration from '@/features/office/pages/PostAdministration';
import StaffPostDetail from '@/features/office/pages/StaffPostDetail';
import StoragePage from '@/features/office/pages/StoragePage';
import OfficeClaimList from '@/features/office/pages/OfficeClaimList';
import OfficeClaimForm from '@/features/office/pages/OfficeClaimForm';
import OfficeClaimDetail from '@/features/office/pages/OfficeClaimDetail';
import { counterConfirmationReturn } from '@/features/office/officeRoutes';
// DaiHV integration: office custody requests share the existing protected layouts.
import OfficeTransfers from '@/features/office/pages/OfficeTransfers';
import OfficeTransferDetailPage from '@/features/office/pages/OfficeTransferDetailPage';
import CounterIntakePage from '@/features/office/pages/CounterIntakePage';
import StorageLocationsPage from '@/features/office/pages/StorageLocationsPage';
import ReportReviewDetailPage from '@/features/report-review/pages/ReportReviewDetailPage';
import AdminReportsPage from '@/features/admin/pages/AdminReportsPage';
import AdminOverviewPage from '@/features/admin/pages/AdminOverviewPage';
import ReportReasonsManagementPage from '@/features/admin/pages/ReportReasonsManagementPage';
import AdminAuditLogsPage from '@/features/admin/pages/AdminAuditLogsPage';
import AdminUserDetailsPage from '@/features/admin/pages/AdminUserDetailsPage';
import AccountDashboardPage from '@/features/account/pages/AccountDashboardPage';
import CategoryManagementPage from '@/features/category/pages/CategoryManagementPage';
import ReceiptQueuePage from '@/features/approver/pages/ReceiptQueuePage';
import IntakeFormPage from '@/features/approver/pages/IntakeFormPage';
import IntakeDetailPage from '@/features/approver/pages/IntakeDetailPage';
import ChatPage from '@/features/chat/pages/ChatPage';
import MyReportsPage from '@/features/reports/pages/MyReportsPage';
import ReportDetailPage from '@/features/reports/pages/ReportDetailPage';
import { reportsPath } from '@/features/reports/utils/reportUtils';
import NotificationProvider from '@/features/notifications/NotificationProvider';
import SavedPostsPage from '@/features/items/pages/SavedPostsPage';
import SavedPostsProvider from '@/features/items/context/SavedPostsProvider';

function ProtectedRoute({ children, adminOnly = false, pureAdminOnly = false, managerOnly = false, approverOnly = false }) {
  const { isAuthenticated, isAdmin, isPureAdmin, isManager, isApprover } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // A counter QR may be scanned before the recipient signs in.
    const officeReceipt = counterConfirmationReturn(location.pathname + location.hash);
    return <Navigate to="/dang-nhap" replace state={officeReceipt ? { officeReceipt } : undefined} />;
  }

  // Chỉ ADMIN và MANAGER mới được phép vào khu vực quản trị (/admin)
  if (adminOnly && !isAdmin) {
    return <Navigate to="/" replace />;
  }

  if (pureAdminOnly && !isPureAdmin) {
    return <Navigate to={isManager ? '/admin/items' : '/'} replace />;
  }

  if (managerOnly && !isManager) return <Navigate to="/admin" replace />;
  if (approverOnly && !isApprover) return <Navigate to={isAdmin ? '/admin' : '/'} replace />;

  return children;
}

function RootHomeRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <HomePage /> : <LandingLoginPage />;
}

function ReportWorkspaceRedirect({ results = false }) {
  const [params] = useSearchParams();
  const finished = results || params.get('tab') === 'results';
  return <Navigate to={`${reportsPath}?tab=related${finished ? '&status=FINISHED' : ''}`} replace />;
}

function AppRoutes() {
  const { isAuthenticated, isAdmin, isManager, isApprover } = useAuth();
  const postLoginRedirect = isApprover ? '/approver/receipts' : isManager ? '/admin/items' : isAdmin ? '/admin' : '/';
  const location = useLocation();
  const officeReceipt = counterConfirmationReturn(location.state?.officeReceipt);

  return (
    <Routes>
      <Route path="/van-phong/lich-hen" element={<Navigate to="/van-phong/claims" replace />} />
      <Route path="/van-phong/hen-giao-do" element={<Navigate to="/van-phong/chuyen-giao" replace />} />
      {/* ── AUTH ROUTES (Dành cho tất cả các Role) ── */}
      <Route
        path="/dang-nhap"
        element={isAuthenticated ? <Navigate to={officeReceipt || postLoginRedirect} replace /> : <AuthPage defaultMode="login" />}
      />
      <Route path="/login" element={<Navigate to="/dang-nhap" replace />} />
      <Route
        path="/quen-mat-khau"
        element={isAuthenticated ? <Navigate to={postLoginRedirect} replace /> : <AuthPage defaultMode="forgot_email" />}
      />
      <Route path="/forgot-password" element={<Navigate to="/quen-mat-khau" replace />} />
      <Route path="/dang-ky" element={<Navigate to="/dang-nhap" replace />} />
      <Route path="/register" element={<Navigate to="/dang-nhap" replace />} />

      {/* ── CLIENT ROUTES (Navbar + Footer tự động từ MainLayout) ── */}
      <Route element={<MainLayout />}>
        {/* ── ACCOUNT WORKSPACE ROUTES (Sidebar trái: Hồ sơ, Bài đăng, Đã lưu, Yêu cầu nhận đồ, Báo cáo, Tin nhắn) ── */}
        <Route element={<ProtectedRoute><AccountSidebarLayout /></ProtectedRoute>}>
          <Route path="/ho-so" element={<ProfilePage />} />
          <Route path="/tin-dang-cua-toi" element={<MyPostsPage />} />
          <Route path="/bai-dang-cua-toi" element={<MyPostsPage />} />
          <Route path="/bai-dang-da-luu" element={<SavedPostsPage />} />
          <Route path="/claim-cua-toi" element={<MyClaimsPage />} />
          <Route path="/claims/:id" element={<LegacyClaimRedirect />} />
          <Route path="/bai-dang/:postId/claims/:claimId" element={<ClaimDetailPage />} />
          <Route path="/bai-dang/:id/claims" element={<PostClaimsPage />} />
          <Route path="/van-phong/claims" element={<OfficeClaimList mine />} />
          <Route path="/van-phong/claims/:id" element={<OfficeClaimDetail mine />} />
          <Route path="/van-phong/chuyen-giao" element={<OfficeTransfers mine />} />
          <Route path="/van-phong/chuyen-giao/:id" element={<OfficeTransferDetailPage mine />} />
          <Route path="/bao-cao-cua-toi" element={<MyReportsPage />} />
          <Route path="/bao-cao-trao-doi" element={<ReportWorkspaceRedirect />} />
          <Route path="/ket-qua-bao-cao" element={<ReportWorkspaceRedirect results />} />
          <Route path="/bao-cao/:id" element={<ReportDetailPage />} />
          <Route path="/tin-nhan" element={<ChatPage />} />
          <Route path="/tin-nhan/:conversationId" element={<ChatPage />} />
        </Route>

        <Route path="/van-phong/xac-minh/:id" element={<ProtectedRoute><OfficeClaimForm /></ProtectedRoute>} />
        <Route path="/sua-tin/:id" element={<ProtectedRoute><EditPostPage /></ProtectedRoute>} />
        <Route path="/" element={<RootHomeRoute />} />
        <Route path="/tim-kiem" element={<ProtectedRoute><SearchPage /></ProtectedRoute>} />
        <Route path="/chi-tiet/:id" element={<ProtectedRoute><ItemDetailPage /></ProtectedRoute>} />

        <Route
          path="/dang-tin"
          element={
            <ProtectedRoute>
              <PostItemPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/dang-tin-nhat-duoc"
          element={
            <ProtectedRoute>
              <PostItemPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/smart-match"
          element={
            <ProtectedRoute>
              <SmartMatchPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/xac-minh/:id"
          element={
            <ProtectedRoute>
              <ClaimItemPage />
            </ProtectedRoute>
          }
        />
      </Route>

      {/* ── DASHBOARD ROUTES (CHỈ ADMIN VÀ MANAGER ĐƯỢC TRUY CẬP) ── */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute adminOnly>
            <AdminLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<ProtectedRoute pureAdminOnly><AdminOverviewPage /></ProtectedRoute>} />
        <Route path="dashboard" element={<ProtectedRoute pureAdminOnly><AdminOverviewPage /></ProtectedRoute>} />
        <Route path="users" element={<AccountDashboardPage />} />
        <Route path="users/:id" element={<AdminUserDetailsPage />} />
        <Route path="report-reasons" element={<ProtectedRoute pureAdminOnly><ReportReasonsManagementPage /></ProtectedRoute>} />
        <Route path="audit-logs" element={<ProtectedRoute pureAdminOnly><AdminAuditLogsPage /></ProtectedRoute>} />
        <Route path="items" element={<ProtectedRoute managerOnly><PostAdministration /></ProtectedRoute>} />
        <Route path="items/:id" element={<ProtectedRoute managerOnly><StaffPostDetail /></ProtectedRoute>} />
        <Route path="storage" element={<ProtectedRoute managerOnly><StoragePage /></ProtectedRoute>} />
        <Route path="storage/locations" element={<ProtectedRoute managerOnly><StorageLocationsPage /></ProtectedRoute>} />
        <Route path="transfers" element={<ProtectedRoute managerOnly><OfficeTransfers /></ProtectedRoute>} />
        <Route path="transfers/:id" element={<ProtectedRoute managerOnly><OfficeTransferDetailPage /></ProtectedRoute>} />
        <Route path="reports" element={<ProtectedRoute managerOnly><AdminReportsPage /></ProtectedRoute>} />
        <Route path="reports/vi-pham" element={<ProtectedRoute managerOnly><AdminReportsPage kind="GENERAL" /></ProtectedRoute>} />
        <Route path="reports/su-co-ban-giao" element={<ProtectedRoute managerOnly><AdminReportsPage kind="HANDOVER_ISSUE" /></ProtectedRoute>} />
        <Route path="reports/khang-cao" element={<ProtectedRoute managerOnly><AdminReportsPage kind="CLAIM_APPEAL" /></ProtectedRoute>} />
        <Route path="reports/:id" element={<ProtectedRoute managerOnly><ReportReviewDetailPage /></ProtectedRoute>} />
        <Route path="categories" element={<ProtectedRoute managerOnly><CategoryManagementPage /></ProtectedRoute>} />
      </Route>

      
      <Route path="/approver" element={<ProtectedRoute approverOnly><AdminLayout /></ProtectedRoute>}>
        <Route path="storage" element={<StoragePage />} />
        {/* DaiHV: Approver owns counter claims and the office return workflow. */}
        <Route path="transfers" element={<OfficeTransfers />} />
        <Route path="transfers/new" element={<CounterIntakePage />} />
        <Route path="transfers/:id" element={<OfficeTransferDetailPage />} />
        <Route path="office-claims" element={<OfficeClaimList key="groups" />} />
        <Route path="office-claims/post/:postId" element={<OfficeClaimList key="post-claims" />} />
        <Route path="office-claims/new" element={<OfficeClaimForm counter />} />
        <Route path="office-claims/:id" element={<OfficeClaimDetail />} />
        <Route index element={<Navigate to="/approver/receipts" replace />} />
        <Route path="receipts" element={<ReceiptQueuePage />} />
        <Route path="receipts/new" element={<IntakeFormPage />} />
        <Route path="receipts/:id/edit" element={<IntakeFormPage />} />
        <Route path="receipts/:id" element={<IntakeDetailPage />} />
      </Route>
      <Route path="/dashboard" element={<Navigate to={postLoginRedirect} replace />} />
      <Route path="/quan-tri" element={<Navigate to="/admin" replace />} />

      {/* Fallback */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <NotificationProvider>
          <SavedPostsProvider>
          <AppRoutes />
          </SavedPostsProvider>
        </NotificationProvider>
      </BrowserRouter>
    </AuthProvider>
  );
}
