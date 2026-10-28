import { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import { CheckCircle, Bell, UploadCloud, Shield, FileText, Clock, Star } from 'lucide-react';
import ProfileHeader from '../components/ProfileHeader';
import ProfileStatCard from '../components/ProfileStatCard';
import ProfileSidebarCard from '../components/ProfileSidebarCard';
import PostsTab from '../components/PostsTab';
import ActivityTab from '../components/ActivityTab';
import SecurityTab from '../components/SecurityTab';

const ACTIVITY_LOG = [
  { icon: CheckCircle, color: 'var(--status-found)', label: 'Xác minh quyền sở hữu thành công', detail: 'Ví Pedro — Chủ nhân đã nhận đồ', time: '2 ngày trước' },
  { icon: Bell, color: 'var(--accent)', label: 'AI so khớp mới', detail: 'iPhone 15 Pro có 3 kết quả khớp mới', time: '4 ngày trước' },
  { icon: UploadCloud, color: '#6366f1', label: 'Đăng tin thất lạc', detail: 'Điện thoại iPhone 15 Pro xanh', time: '6 ngày trước' },
  { icon: Shield, color: 'var(--accent)', label: 'Xác thực OTP kích hoạt', detail: 'Tài khoản được bảo mật 2 lớp', time: '10 ngày trước' },
];

const TABS = [
  { key: 'posts', label: 'Tin đăng của tôi' },
  { key: 'activity', label: 'Lịch sử hoạt động' },
  { key: 'security', label: 'Bảo mật & Quyền riêng tư' },
];

export default function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [activeTab, setActiveTab] = useState('posts');
  const [editMode, setEditMode] = useState(false);

  const displayName = user?.fullName || user?.name || 'Người dùng FPT';
  const displayEmail = user?.email || 'user@fpt.edu.vn';
  const displayPhone = user?.phone || '0912 345 678';
  const displayJob = user?.role ? `Quyền: ${user.role}` : 'Thành viên';
  const displayAddress = user?.organizationName || user?.unitName || 'Đại học FPT Hà Nội';
  const avatarUrl = user?.avatarUrl || `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(displayName)}&backgroundColor=1e6b6b&textColor=ffffff`;

  const [formValues, setFormValues] = useState({
    name: displayName,
    email: displayEmail,
    phone: displayPhone,
    job: displayJob,
  });

  useEffect(() => {
    if (user) {
      setFormValues({
        name: user.fullName || user.name || '',
        email: user.email || '',
        phone: user.phone || '',
        job: user.role ? `Quyền: ${user.role}` : 'Thành viên',
      });
    }
  }, [user]);

  const profileUser = {
    name: displayName,
    email: displayEmail,
    phone: displayPhone,
    job: displayJob,
    address: displayAddress,
    joinDate: 'Tháng 9, 2023',
    verified: true,
    twoFA: true,
    avatar: avatarUrl,
    stats: [
      { label: 'Tin đã đăng', value: '3', sub: 'tổng cộng', icon: FileText },
      { label: 'Đã trao trả', value: '1', sub: 'thành công', icon: CheckCircle },
      { label: 'Đang xử lý', value: '1', sub: 'chờ xác minh', icon: Clock },
      { label: 'Điểm uy tín', value: '5.0', sub: '/ 5.0', icon: Star },
    ],
  };

  const handleSaveEdit = async () => {
    try {
      await updateUser({
        fullName: formValues.name,
        phone: formValues.phone,
        preferredContact: 'IN_APP',
      });
      setEditMode(false);
    } catch (e) {
      console.warn('Update profile error:', e);
      setEditMode(false);
    }
  };

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      {/* ── PAGE HERO / HEADER ── */}
      <ProfileHeader
        user={profileUser}
        editMode={editMode}
        onToggleEdit={() => setEditMode(!editMode)}
      />

      {/* ── MAIN CONTENT ── */}
      <div style={{ background: 'var(--bg-canvas)', flex: 1 }}>
        <div className="container" style={{ paddingTop: 0 }}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: '1fr 320px',
            gap: '24px',
            marginTop: '-40px',
            alignItems: 'flex-start',
          }}>
            {/* ── LEFT: Main card ── */}
            <div>
              {/* Stats grid */}
              <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '12px',
                marginBottom: '24px',
              }}>
                {profileUser.stats.map((s, i) => <ProfileStatCard key={i} stat={s} index={i} />)}
              </div>

              {/* Tabs + content card */}
              <div style={{
                background: 'var(--bg-surface)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius-2xl)',
                overflow: 'hidden',
                boxShadow: 'var(--shadow-sm)',
              }}>
                {/* Tab bar */}
                <div style={{
                  display: 'flex',
                  borderBottom: '1px solid var(--border)',
                  background: 'var(--bg-canvas)',
                  padding: '0 24px',
                  gap: '4px',
                }}>
                  {TABS.map(tab => (
                    <button
                      key={tab.key}
                      id={`tab-profile-${tab.key}`}
                      type="button"
                      onClick={() => setActiveTab(tab.key)}
                      style={{
                        padding: '14px 16px',
                        background: 'none', border: 'none',
                        borderBottom: `2px solid ${activeTab === tab.key ? 'var(--accent)' : 'transparent'}`,
                        color: activeTab === tab.key ? 'var(--accent)' : 'var(--text-secondary)',
                        fontSize: '0.875rem', fontWeight: activeTab === tab.key ? 700 : 500,
                        cursor: 'pointer', fontFamily: 'var(--font-body)',
                        transition: 'all 150ms', whiteSpace: 'nowrap',
                        marginBottom: '-1px',
                      }}
                    >
                      {tab.label}
                    </button>
                  ))}
                </div>

                {/* Tab content */}
                <div style={{ padding: '24px' }}>
                  {activeTab === 'posts' && <PostsTab />}
                  {activeTab === 'activity' && <ActivityTab activityLog={ACTIVITY_LOG} />}
                  {activeTab === 'security' && <SecurityTab />}
                </div>
              </div>
            </div>

            {/* ── RIGHT: Sidebar ── */}
            <ProfileSidebarCard
              user={profileUser}
              editMode={editMode}
              onStartEdit={() => setEditMode(true)}
              formValues={formValues}
              onChangeForm={setFormValues}
              onSaveEdit={handleSaveEdit}
              onCancelEdit={() => setEditMode(false)}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
