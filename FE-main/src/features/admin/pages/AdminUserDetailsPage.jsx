import React from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { ArrowLeft, CheckCircle2 } from 'lucide-react';
import { useAdminUserDetails } from '../hooks/useAdminUserDetails';
import UserDetailsHeader from '../components/users/UserDetailsHeader';
import UserTabsContent from '../components/users/UserTabsContent';

export default function AdminUserDetailsPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const {
    user,
    viewTab,
    setViewTab,
    toastMessage,
    handleSendWarning,
    handleToggleBan
  } = useAdminUserDetails(id);

  if (!user) {
    return (
      <div style={{ padding: '48px', textAlign: 'center' }}>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Đang tải dữ liệu hoặc không tìm thấy thành viên...</p>
        <button onClick={() => navigate('/admin/users')} style={{ marginTop: '12px', padding: '8px 16px', borderRadius: '6px', border: '1px solid var(--border)', background: 'white', cursor: 'pointer' }}>
          Quay lại danh sách
        </button>
      </div>
    );
  }

  return (
    <div style={{ padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Toast Notification */}
      {toastMessage && (
        <div style={{
          position: 'fixed', top: '70px', right: '28px', zIndex: 100,
          background: '#090d12', color: 'white', padding: '10px 16px',
          borderRadius: '8px', fontSize: '0.85rem', fontWeight: 500,
          display: 'flex', alignItems: 'center', gap: '8px',
          boxShadow: '0 8px 24px rgba(0,0,0,0.2)'
        }}>
          <CheckCircle2 size={16} style={{ color: '#2dd4bf' }} />
          <span>{toastMessage}</span>
        </div>
      )}

      {/* ── BREADCRUMB & BACK LINK ── */}
      <div>
        <Link
          to="/admin/users"
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '6px',
            color: 'var(--text-muted)', textDecoration: 'none', fontSize: '0.825rem',
            fontWeight: 500, marginBottom: '14px'
          }}
        >
          <ArrowLeft size={14} /> Quay lại danh sách tài khoản
        </Link>

        <UserDetailsHeader 
          user={user} 
          onSendWarning={handleSendWarning} 
          onToggleBan={handleToggleBan} 
        />
      </div>

      <UserTabsContent 
        viewTab={viewTab} 
        setViewTab={setViewTab} 
        user={user} 
      />
    </div>
  );
}
