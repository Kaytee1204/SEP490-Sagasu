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
import AdminLayout from '@/components/layouts/admin/AdminLayout';
import MainLayout from '@/components/layouts/MainLayout';
import AdminItemsPage from '@/features/admin/pages/AdminItemsPage';
import AdminReportsPage from '@/features/admin/pages/AdminReportsPage';
import AdminUserDetailsPage from '@/features/admin/pages/AdminUserDetailsPage';
import AccountDashboardPage from '@/features/account/pages/AccountDashboardPage';
import CategoryManagementPage from '@/features/category/pages/CategoryManagementPage';
import CheckerReceiptsPage from '@/features/checker/pages/CheckerReceiptsPage';
import ReceiptFormPage from '@/features/checker/pages/ReceiptFormPage';
import ReceiptDetailPage from '@/features/checker/pages/ReceiptDetailPage';
import ReceiptQueuePage from '@/features/approver/pages/ReceiptQueuePage';
import ReviewReceiptPage from '@/features/approver/pages/ReviewReceiptPage';

function ProtectedRoute({ children, adminOnly = false, managerOnly = false, checkerOnly = false, approverOnly = false }) {
  const { isAuthenticated, isAdmin, isManager, isChecker, isApprover } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/dang-nhap" replace />;
  }

  // Chỉ ADMIN và MANAGER mới được phép vào trang Dashboard (/admin)
  if (adminOnly && !isAdmin) {
    return <Navigate to="/" replace />;
  }

  if (managerOnly && !isManager) return <Navigate to="/admin" replace />;
  if (checkerOnly && !isChecker) return <Navigate to={isAdmin ? '/admin' : '/'} replace />;
  if (approverOnly && !isApprover) return <Navigate to={isAdmin ? '/admin' : '/'} replace />;

  return children;
}

function RootHomeRoute() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <HomePage /> : <LandingLoginPage />;
}

function AppRoutes() {
  const { isAuthenticated, isAdmin, isChecker, isApprover } = useAuth();
  const postLoginRedirect = isChecker ? '/checker/receipts' : isApprover ? '/approver/receipts' : isAdmin ? '/admin' : '/';

  return (
    <Routes>
      {/* ── AUTH ROUTES (Dành cho tất cả các Role) ── */}
      <Route
        path="/dang-nhap"
        element={isAuthenticated ? <Navigate to={postLoginRedirect} replace /> : <AuthPage />}
      />
      <Route
        path="/dang-ky"
        element={isAuthenticated ? <Navigate to={postLoginRedirect} replace /> : <AuthPage />}
      />
      <Route path="/login" element={<Navigate to="/dang-nhap" replace />} />
      <Route path="/register" element={<Navigate to="/dang-ky" replace />} />

      {/* ── CLIENT ROUTES (Navbar + Footer tự động từ MainLayout) ── */}
      <Route element={<MainLayout />}>
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

      <Route path="/checker" element={<ProtectedRoute checkerOnly><AdminLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/checker/receipts" replace />} />
        <Route path="receipts" element={<CheckerReceiptsPage />} />
        <Route path="receipts/new" element={<ReceiptFormPage />} />
        <Route path="receipts/:id" element={<ReceiptDetailPage />} />
        <Route path="receipts/:id/edit" element={<ReceiptFormPage />} />
      </Route>
      <Route path="/approver" element={<ProtectedRoute approverOnly><AdminLayout /></ProtectedRoute>}>
        <Route index element={<Navigate to="/approver/receipts" replace />} />
        <Route path="receipts" element={<ReceiptQueuePage />} />
        <Route path="receipts/:id" element={<ReviewReceiptPage />} />
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
