import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import HomePage from '@/features/home/pages/HomePage';
import LandingLoginPage from '@/features/home/pages/LandingLoginPage';
import AuthPage from '@/features/auth/pages/AuthPage';
import ProfilePage from '@/features/profile/pages/ProfilePage';
import PostItemPage from '@/features/items/pages/PostItemPage';
import SearchPage from '@/features/search/pages/SearchPage';
import ItemDetailPage from '@/features/items/pages/ItemDetailPage';
import SmartMatchPage from '@/features/smart-match/pages/SmartMatchPage';
import MyPostsPage from '@/features/profile/pages/MyPostsPage';
import EditPostPage from '@/features/items/pages/EditPostPage';
import ClaimItemPage from '@/features/items/pages/ClaimItemPage';
import MyClaimsPage, { PostClaimsPage } from '@/features/claims/pages/MyClaimsPage';
import ClaimDetailPage, { LegacyClaimRedirect } from '@/features/claims/pages/ClaimDetailPage';
import AdminLayout from '@/components/layouts/admin/AdminLayout';
import MainLayout from '@/components/layouts/MainLayout';
import AdminItemsPage from '@/features/admin/pages/AdminItemsPage';
import AdminReportsPage from '@/features/admin/pages/AdminReportsPage';
import AdminUserDetailsPage from '@/features/admin/pages/AdminUserDetailsPage';
import AccountDashboardPage from '@/features/account/pages/AccountDashboardPage';
import CategoryManagementPage from '@/features/category/pages/CategoryManagementPage';
import ReceiptQueuePage from '@/features/approver/pages/ReceiptQueuePage';
import IntakeFormPage from '@/features/approver/pages/IntakeFormPage';
import IntakeDetailPage from '@/features/approver/pages/IntakeDetailPage';

function ProtectedRoute({ children, adminOnly = false, managerOnly = false, approverOnly = false }) {
  const { isAuthenticated, isAdmin, isManager, isApprover } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/dang-nhap" replace />;
  }

  // Chỉ ADMIN và MANAGER mới được phép vào trang Dashboard (/admin)
  if (adminOnly && !isAdmin) {
    return <Navigate to="/" replace />;
  }

  if (managerOnly && !isManager) return <Navigate to="/admin" replace />;
  if (approverOnly && !isApprover) return <Navigate to={isAdmin ? '/admin' : '/'} replace />;

  return children;
}

function RootHomeRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <HomePage /> : <LandingLoginPage />;
}

function AppRoutes() {
  const { isAuthenticated, isAdmin, isApprover } = useAuth();
  const postLoginRedirect = isApprover ? '/approver/receipts' : isAdmin ? '/admin' : '/';

  return (
    <Routes>
      {/* ── AUTH ROUTES (Dành cho tất cả các Role) ── */}
      <Route
        path="/dang-nhap"
        element={isAuthenticated ? <Navigate to={postLoginRedirect} replace /> : <AuthPage defaultMode="login" />}
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
        <Route path="/claim-cua-toi" element={<ProtectedRoute><MyClaimsPage /></ProtectedRoute>} />
        <Route path="/claims/:id" element={<ProtectedRoute><LegacyClaimRedirect /></ProtectedRoute>} />
        <Route path="/bai-dang/:postId/claims/:claimId" element={<ProtectedRoute><ClaimDetailPage /></ProtectedRoute>} />
        <Route path="/bai-dang/:id/claims" element={<ProtectedRoute><PostClaimsPage /></ProtectedRoute>} />
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
        <Route
          path="/ho-so"
          element={
            <ProtectedRoute>
              <ProfilePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/tin-dang-cua-toi"
          element={
            <ProtectedRoute>
              <MyPostsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/bai-dang-cua-toi"
          element={
            <ProtectedRoute>
              <MyPostsPage />
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
        <Route index element={<AccountDashboardPage />} />
        <Route path="users" element={<AccountDashboardPage />} />
        <Route path="users/:id" element={<AdminUserDetailsPage />} />
        <Route path="items" element={<AdminItemsPage />} />
        <Route path="reports" element={<AdminReportsPage />} />
        <Route path="categories" element={<ProtectedRoute managerOnly><CategoryManagementPage /></ProtectedRoute>} />
      </Route>

      
      <Route path="/approver" element={<ProtectedRoute approverOnly><AdminLayout /></ProtectedRoute>}>
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
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  );
}
