import { useState, useEffect, useEffectEvent } from 'react';
import { useAuth } from '@/context/AuthContext';
import { getMyPosts } from '@/features/items/api/postApi';
import { FileText, CheckCircle, Award } from 'lucide-react';
import ProfileHeader from '../components/ProfileHeader';
import ProfileStatCard from '../components/ProfileStatCard';
import ProfileSidebarCard from '../components/ProfileSidebarCard';
import PostsTab from '../components/PostsTab';
import SecurityTab from '../components/SecurityTab';
import MyClaimsPage from '@/features/claims/pages/MyClaimsPage';

const TABS = [
  { key: 'posts', label: 'Tin đăng của tôi' },
  { key: 'claims', label: 'Claim của tôi' },
  { key: 'security', label: 'Bảo mật & Quyền riêng tư' },
];

async function loadPostCounts() {
  const [allRes, visibleRes] = await Promise.allSettled([
    getMyPosts(1, 'ALL'),
    getMyPosts(1, 'VISIBLE'),
  ]);
  return {
    total: allRes.status === 'fulfilled' ? (allRes.value?.totalElements ?? 0) : 0,
    visible: visibleRes.status === 'fulfilled' ? (visibleRes.value?.totalElements ?? 0) : 0,
  };
}

export default function ProfilePage() {
  const { user, isPureAdmin, refreshProfile, updateUser } = useAuth();
  const [activeTab, setActiveTab] = useState('posts');
  const [editMode, setEditMode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);
  const [postCounts, setPostCounts] = useState({ total: 0, visible: 0 });

  // Read the latest context callback without reloading when its reference changes.
  const loadAccountDataOnOpen = useEffectEvent(async () => {
    await refreshProfile();
    return loadPostCounts();
  });

  useEffect(() => {
    let cancelled = false;
    loadAccountDataOnOpen().then((counts) => {
      if (!cancelled) setPostCounts(counts);
    }).catch(() => {
      if (!cancelled) setPostCounts({ total: 0, visible: 0 });
    });
    return () => { cancelled = true; };
  }, [user?.id]);

  const displayName = String(user?.fullName || user?.name || user?.email || 'Người dùng').replace(/\s*\((Manager|Admin)\)/gi, '');
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

    const trimmedPhone = (formValues.phone || '').trim();
    if (trimmedPhone && !/^(0|\+84)[0-9]{9,10}$/.test(trimmedPhone)) {
      setFeedback({
        type: 'error',
        message: 'Số điện thoại không hợp lệ (phải gồm 10-11 chữ số, bắt đầu bằng 0 hoặc +84)',
      });
      return;
    }

    if (formValues.newPassword) {
      const pass = formValues.newPassword.trim();
      if (pass.length < 8 || pass.length > 16 || /\s/.test(pass) || !/[^a-zA-Z0-9\s]/.test(pass)) {
        setFeedback({
          type: 'error',
          message: 'Mật khẩu mới phải dài từ 8 đến 16 ký tự, chứa ít nhất 1 ký tự đặc biệt và không có khoảng trắng',
        });
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
        phone: trimmedPhone,
      };
      if (formValues.newPassword) {
        payload.newPassword = formValues.newPassword.trim();
      }
      if (isPureAdmin && formValues.role) {
        payload.role = formValues.role;
      }

      await updateUser(payload);
      await refreshProfile();
      setPostCounts(await loadPostCounts());
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
            className="grid grid-cols-1 lg:grid-cols-[1fr_330px] gap-6 items-start"
          >
            {/* ── LEFT: Main column ── */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
              {/* Stats grid */}
              <div
                className="grid grid-cols-1 sm:grid-cols-3 gap-4"
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
                  {activeTab === 'claims' && <MyClaimsPage embedded />}
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
