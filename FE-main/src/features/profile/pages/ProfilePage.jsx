import { useState, useEffect, useCallback } from 'react';
import { useAuth } from '@/context/AuthContext';
import { getMyPosts } from '@/features/items/api/postApi';
import { FileText, CheckCircle, Award } from 'lucide-react';
import ProfileHeader from '../components/ProfileHeader';
import ProfileStatCard from '../components/ProfileStatCard';
import ProfileSidebarCard from '../components/ProfileSidebarCard';
import PostsTab from '../components/PostsTab';
import SecurityTab from '../components/SecurityTab';

const TABS = [
  { key: 'posts', label: 'Tin đăng của tôi' },
  { key: 'security', label: 'Bảo mật & Quyền riêng tư' },
];

export default function ProfilePage() {
  const { user, isPureAdmin, refreshProfile, updateUser } = useAuth();
  const [activeTab, setActiveTab] = useState('posts');
  const [editMode, setEditMode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);
  const [postCounts, setPostCounts] = useState({ total: 0, visible: 0 });

  const loadAccountDataFromDb = useCallback(async () => {
    await refreshProfile();
    try {
      const [allRes, visibleRes] = await Promise.allSettled([
        getMyPosts(1, 'ALL'),
        getMyPosts(1, 'VISIBLE'),
      ]);
      const total =
        allRes.status === 'fulfilled' && typeof allRes.value?.totalElements === 'number'
          ? allRes.value.totalElements
          : 0;
      const visible =
        visibleRes.status === 'fulfilled' && typeof visibleRes.value?.totalElements === 'number'
          ? visibleRes.value.totalElements
          : 0;
      setPostCounts({ total, visible });
    } catch {
      setPostCounts({ total: 0, visible: 0 });
    }
  }, [refreshProfile]);

  useEffect(() => {
    loadAccountDataFromDb();
  }, [loadAccountDataFromDb]);

  const displayName = user?.fullName || user?.name || user?.email || 'Người dùng';
  const displayEmail = user?.email || 'Chưa cập nhật';
  const displayPhone = user?.phone ? user.phone : 'Chưa cập nhật';
  const displayRole = user?.role ? String(user.role).toUpperCase() : 'USER';
  const displayStatus = user?.status ? String(user.status).toUpperCase() : 'ACTIVE';
  const securityLabel =
    displayRole === 'ADMIN' || displayRole === 'MANAGER'
      ? 'Tài khoản Quản trị hệ thống'
      : 'Bảo mật xác thực OTP qua Email';

  const avatarUrl =
    user?.avatarUrl ||
    `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName)}&backgroundColor=1e6b6b&textColor=ffffff`;

  const [formValues, setFormValues] = useState({
    email: displayEmail,
    phone: user?.phone || '',
    role: displayRole,
    newPassword: '',
    confirmPassword: '',
  });

  useEffect(() => {
    if (user) {
      setFormValues({
        email: user.email || '',
        phone: user.phone || '',
        role: user.role ? String(user.role).toUpperCase() : 'USER',
        newPassword: '',
        confirmPassword: '',
      });
    }
  }, [user]);

  const profileUser = {
    name: displayName,
    email: displayEmail,
    phone: displayPhone,
    role: displayRole,
    status: displayStatus,
    securityLabel,
    avatar: avatarUrl,
    stats: [
      {
        label: 'Số lượng bài đăng',
        value: String(postCounts.total),
        sub: 'Tổng số tin đã đăng của bạn',
        icon: FileText,
      },
      {
        label: 'Bài đăng đang hiển thị',
        value: String(postCounts.visible),
        sub: 'Đang hiển thị trên hệ thống',
        icon: CheckCircle,
      },
      {
        label: 'Vai trò (Role)',
        value: displayRole,
        sub: displayStatus === 'ACTIVE' ? 'Trạng thái: Đang hoạt động' : `Trạng thái: ${displayStatus}`,
        icon: Award,
      },
    ],
  };

  const handleSaveEdit = async () => {
    setFeedback(null);

    if (formValues.newPassword) {
      if (formValues.newPassword.length < 3) {
        setFeedback({ type: 'error', message: 'Mật khẩu mới phải từ 3 ký tự trở lên' });
        return;
      }
      if (formValues.newPassword !== formValues.confirmPassword) {
        setFeedback({ type: 'error', message: 'Mật khẩu xác nhận không khớp' });
        return;
      }
    }

    setSaving(true);
    try {
      const payload = {
        phone: formValues.phone.trim(),
      };
      if (formValues.newPassword) {
        payload.newPassword = formValues.newPassword;
      }
      if (isPureAdmin && formValues.role) {
        payload.role = formValues.role;
      }

      await updateUser(payload);
      await loadAccountDataFromDb();
      setFormValues((prev) => ({ ...prev, newPassword: '', confirmPassword: '' }));
      setEditMode(false);
      setFeedback({ type: 'success', message: 'Đã cập nhật thông tin cá nhân thành công!' });
    } catch (e) {
      setFeedback({
        type: 'error',
        message: e?.message || 'Cập nhật hồ sơ thất bại',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', background: 'var(--bg-canvas)' }}>
      {/* ── PAGE HERO / HEADER ── */}
      <ProfileHeader
        user={profileUser}
        editMode={editMode}
        onToggleEdit={() => {
          setFeedback(null);
          setEditMode(!editMode);
        }}
      />

      {/* ── MAIN CONTENT ── */}
      <div style={{ flex: 1, padding: '24px 0 48px' }}>
        <div className="container">
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr 330px',
              gap: '24px',
              alignItems: 'flex-start',
            }}
          >
            {/* ── LEFT: Main column ── */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Stats grid */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, 1fr)',
                  gap: '16px',
                }}
              >
                {profileUser.stats.map((s, i) => (
                  <ProfileStatCard key={i} stat={s} index={i} />
                ))}
              </div>

              {/* Tabs + content card */}
              <div
                style={{
                  background: 'var(--bg-surface)',
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  overflow: 'hidden',
                  boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
                }}
              >
                {/* Tab bar */}
                <div
                  style={{
                    display: 'flex',
                    borderBottom: '1px solid #e2e8f0',
                    background: '#f8fafc',
                    padding: '0 20px',
                    gap: '8px',
                  }}
                >
                  {TABS.map((tab) => (
                    <button
                      key={tab.key}
                      id={`tab-profile-${tab.key}`}
                      type="button"
                      onClick={() => setActiveTab(tab.key)}
                      style={{
                        padding: '13px 14px',
                        background: 'none',
                        border: 'none',
                        borderBottom: `2px solid ${activeTab === tab.key ? 'var(--accent)' : 'transparent'}`,
                        color: activeTab === tab.key ? 'var(--accent)' : 'var(--text-secondary)',
                        fontSize: '0.85rem',
                        fontWeight: activeTab === tab.key ? 700 : 500,
                        cursor: 'pointer',
                        fontFamily: 'var(--font-body)',
                        transition: 'color 150ms ease, border-color 150ms ease',
                        whiteSpace: 'nowrap',
                        marginBottom: '-1px',
                      }}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>

                {/* Tab content */}
                <div style={{ padding: '22px' }}>
                  {activeTab === 'posts' && <PostsTab />}
                  {activeTab === 'security' && <SecurityTab />}
                </div>
              </div>
            </div>

            {/* ── RIGHT: Sidebar ── */}
            <ProfileSidebarCard
              user={profileUser}
              isPureAdmin={isPureAdmin}
              editMode={editMode}
              onStartEdit={() => {
                setFeedback(null);
                setEditMode(true);
              }}
              formValues={formValues}
              onChangeForm={setFormValues}
              onSaveEdit={handleSaveEdit}
              onCancelEdit={() => {
                setFeedback(null);
                setEditMode(false);
              }}
              saving={saving}
              feedback={feedback}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
