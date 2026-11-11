import { useState, useEffect, useEffectEvent } from 'react';
import { useAuth } from '@/context/AuthContext';
import ProfileSidebarCard from '../components/ProfileSidebarCard';
import SecurityTab from '../components/SecurityTab';
import './ProfilePage.css';

export default function ProfilePage() {
  const { user, isPureAdmin, refreshProfile, updateUser } = useAuth();
  const [editMode, setEditMode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [feedback, setFeedback] = useState(null);

  // Read the latest context callback without reloading when its reference changes.
  const loadAccountDataOnOpen = useEffectEvent(async () => {
    await refreshProfile();
  });

  useEffect(() => {
    let cancelled = false;
    loadAccountDataOnOpen().catch(() => {
      if (!cancelled) setFeedback({ type: 'error', message: 'Không tải được thông tin mới nhất. Vui lòng thử lại.' });
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
    <main className="profile-account-page">
      <div className="container profile-account-container">
        <header className="profile-account-heading">
          <img src={avatarUrl} alt="" />
          <div><h1>Hồ sơ cá nhân</h1><p>Quản lý thông tin và bảo mật tài khoản của bạn.</p></div>
        </header>
        <div className="profile-account-grid">
          <section aria-label="Thông tin cá nhân">
            <ProfileSidebarCard
              user={profileUser}
              showQuickActions={false}
              isPureAdmin={isPureAdmin}
              editMode={editMode}
              onStartEdit={() => { setFeedback(null); setEditMode(true); }}
              formValues={formValues}
              onChangeForm={setFormValues}
              onSaveEdit={handleSaveEdit}
              onCancelEdit={() => { setFeedback(null); setEditMode(false); }}
              saving={saving}
              feedback={feedback}
            />
          </section>
          <section className="profile-security-card" aria-labelledby="profile-security-title">
            <h2 id="profile-security-title">Bảo mật & Quyền riêng tư</h2>
            <SecurityTab />
          </section>
        </div>
      </div>
    </main>
  );
}
