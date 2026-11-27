import { useState, useEffect, useEffectEvent } from 'react';
import { Link, useNavigate, useOutletContext, useSearchParams } from 'react-router-dom';
import {
  MapPin,
  ChevronRight,
  ArrowRight,
  CheckCircle2,
  GraduationCap,
  Phone,
  Pencil,
  KeyRound,
  FileText,
  Bookmark,
  Send,
  Flag,
  MessageCircle,
  Megaphone,
  Mail,
  User,
  Lock,
  Eye,
  EyeOff,
  Check,
  AlertCircle,
  X,
  ShieldCheck,
  HeartHandshake,
  Sparkles,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import campusBanner from '@/assets/fpt_campus_banner.jpg';
import sagasuLogoColor from '@/assets/Sagasu_logo_color.svg';
import './ProfilePage.css';

const ROLE_LABELS = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý tổ chức',
  APPROVER: 'Staff',
  USER: 'Thành viên',
};

const QUICK_ACCESS_ITEMS = [
  {
    key: 'posts',
    label: 'Bài đăng của tôi',
    to: '/bai-dang-cua-toi',
    icon: FileText,
  },
  {
    key: 'saved',
    label: 'Bài đăng đã lưu',
    to: '/bai-dang-da-luu',
    icon: Bookmark,
  },
  {
    key: 'claims',
    label: 'Yêu cầu nhận đồ',
    to: '/claim-cua-toi',
    icon: Send,
  },
  {
    key: 'reports',
    label: 'Báo cáo của tôi',
    to: '/bao-cao-cua-toi',
    icon: Flag,
  },
  {
    key: 'messages',
    label: 'Tin nhắn',
    to: '/tin-nhan',
    icon: MessageCircle,
  },
];

const COMMUNITY_RULES = [
  {
    icon: ShieldCheck,
    title: 'Trung thực & minh bạch thông tin',
    desc: 'Mô tả chính xác đồ vật bị thất lạc hoặc nhặt được, đăng kèm hình ảnh thực tế và không mạo danh chủ sở hữu.',
  },
  {
    icon: HeartHandshake,
    title: 'Giao trả văn minh, an toàn',
    desc: 'Ưu tiên xác minh trực tiếp tại khuôn viên trường hoặc gửi về Văn phòng Đồ thất lạc để đảm bảo quyền lợi cho cả hai bên.',
  },
  {
    icon: Sparkles,
    title: 'Tôn trọng cộng đồng sinh viên',
    desc: 'Giữ thái độ lịch sự khi nhắn tin trao đổi, không đăng nội dung spam và báo cáo ngay các hành vi đáng ngờ.',
  },
];

export default function ProfilePage() {
  const { user, isPureAdmin, refreshProfile, updateUser, logout } = useAuth();
  const navigate = useNavigate();
  const outletCtx = useOutletContext() || {};
  const accountBadges = outletCtx.accountBadges || {};
  const [searchParams, setSearchParams] = useSearchParams();
  const wantsPassword = searchParams.get('action') === 'password';

  // Modal state: null | 'profile' | 'password' | 'community'
  const [manualModal, setManualModal] = useState(null);
  const activeModal = manualModal ?? (wantsPassword ? 'password' : null);

  // Edit profile form state
  const [phoneInput, setPhoneInput] = useState(user?.phone || '');
  const [roleInput, setRoleInput] = useState(user?.role ? String(user.role).toUpperCase() : 'USER');
  const [savingInfo, setSavingInfo] = useState(false);
  const [infoFeedback, setInfoFeedback] = useState(null);

  // Change password form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordFeedback, setPasswordFeedback] = useState(null);

  const loadAccountDataOnOpen = useEffectEvent(async () => {
    await refreshProfile();
  });

  useEffect(() => {
    loadAccountDataOnOpen().catch(() => {
      // Keep current cached user state if refresh fails transiently
    });
  }, [user?.id]);

  // Close open modal on Escape key
  useEffect(() => {
    if (!activeModal) return;
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        setManualModal(null);
        if (wantsPassword) setSearchParams({}, { replace: true });
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeModal, wantsPassword, setSearchParams]);

  const rawName = user?.fullName || user?.name || user?.email || 'Người dùng';
  const displayName = String(rawName).replace(/\s*\((manager|admin|user|approver|staff)\)\s*$/i, '').trim();
  const displayEmail = user?.email || 'Chưa cập nhật';
  const displayPhone = user?.phone ? String(user.phone).trim() : '';
  const displayRole = user?.role ? String(user.role).toUpperCase() : 'USER';
  const displayRoleLabel = ROLE_LABELS[displayRole] || displayRole;
  const displayOrg = user?.organizationName || user?.unitName || 'Trường học';

  const openProfileModal = () => {
    setInfoFeedback(null);
    setPasswordFeedback(null);
    setPhoneInput(user?.phone || '');
    setRoleInput(displayRole);
    setManualModal('profile');
  };

  const openPasswordModal = () => {
    setInfoFeedback(null);
    setPasswordFeedback(null);
    setManualModal('password');
  };

  const openCommunityModal = () => {
    setManualModal('community');
  };

  const closeModal = () => {
    setManualModal(null);
    setInfoFeedback(null);
    setPasswordFeedback(null);
    if (wantsPassword) {
      setSearchParams({}, { replace: true });
    }
  };

  const handleSaveInfo = async (event) => {
    event.preventDefault();
    setInfoFeedback(null);

    const trimmedPhone = (phoneInput || '').trim().replace(/\s+/g, '');
    if (trimmedPhone && !/^(0|\+84)(3[2-9]|5[2689]|7[06-9]|8[1-9]|9[0-9])\d{7}$/.test(trimmedPhone)) {
      setInfoFeedback({
        type: 'error',
        message: 'Số điện thoại không đúng định dạng Việt Nam (VD: 0912345678 hoặc +84912345678).',
      });
      return;
    }

    setSavingInfo(true);
    try {
      const payload = { phone: trimmedPhone };
      if (isPureAdmin && roleInput) {
        payload.role = roleInput;
      }
      await updateUser(payload);
      await refreshProfile();
      setInfoFeedback({ type: 'success', message: 'Đã cập nhật thông tin hồ sơ thành công!' });
    } catch (err) {
      setInfoFeedback({ type: 'error', message: err?.message || 'Cập nhật thông tin thất bại.' });
    } finally {
      setSavingInfo(false);
    }
  };

  // Password validation rules
  const trimmedCurrent = currentPassword.trim();
  const trimmedNew = newPassword.trim();
  const passwordRules = [
    { key: 'len', label: '8–16 ký tự', ok: trimmedNew.length >= 8 && trimmedNew.length <= 16 },
    { key: 'special', label: 'Ít nhất 1 ký tự đặc biệt', ok: /[^a-zA-Z0-9\s]/.test(trimmedNew) },
    { key: 'space', label: 'Không khoảng trắng', ok: trimmedNew.length > 0 && !/\s/.test(newPassword) },
    {
      key: 'diff',
      label: 'Khác mật khẩu hiện tại',
      ok: trimmedNew.length > 0 && trimmedCurrent.length > 0 && trimmedNew !== trimmedCurrent,
    },
    { key: 'match', label: 'Xác nhận trùng khớp', ok: trimmedNew.length > 0 && newPassword === confirmPassword },
  ];

  const handleChangePassword = async (event) => {
    event.preventDefault();
    setPasswordFeedback(null);

    if (!trimmedCurrent) {
      setPasswordFeedback({ type: 'error', message: 'Vui lòng nhập mật khẩu hiện tại.' });
      return;
    }
    if (!trimmedNew) {
      setPasswordFeedback({ type: 'error', message: 'Vui lòng nhập mật khẩu mới.' });
      return;
    }
    if (
      trimmedNew.length < 8 ||
      trimmedNew.length > 16 ||
      /\s/.test(newPassword) ||
      !/[^a-zA-Z0-9\s]/.test(trimmedNew)
    ) {
      setPasswordFeedback({
        type: 'error',
        message: 'Mật khẩu mới phải dài 8–16 ký tự, có ít nhất 1 ký tự đặc biệt và không chứa khoảng trắng.',
      });
      return;
    }
    if (trimmedNew === trimmedCurrent) {
      setPasswordFeedback({ type: 'error', message: 'Mật khẩu mới phải khác mật khẩu hiện tại.' });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordFeedback({ type: 'error', message: 'Mật khẩu xác nhận không trùng khớp.' });
      return;
    }

    setSavingPassword(true);
    try {
      await updateUser({
        currentPassword,
        newPassword: trimmedNew,
      });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      await logout();
      navigate('/dang-nhap', {
        replace: true,
        state: {
          notice: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại bằng mật khẩu mới.',
        },
      });
    } catch (err) {
      setPasswordFeedback({ type: 'error', message: err?.message || 'Đổi mật khẩu thất bại.' });
    } finally {
      setSavingPassword(false);
    }
  };

  return (
    <main className="profile-hub-page">
      <div className="profile-hub-container">
        {/* 1. Welcome Hero Banner */}
        <section
          className="profile-welcome-hero"
          style={{ '--campus-banner-url': `url(${campusBanner})` }}
          aria-label="Chào mừng đến với Sagasu"
        >
          <div className="profile-welcome-content">
            <div className="profile-welcome-campus">
              <MapPin size={15} strokeWidth={2.2} aria-hidden="true" />
              <span>{displayOrg}</span>
              <ChevronRight size={14} aria-hidden="true" />
            </div>

            <h1>
              Chào mừng bạn
              <br />
              đến với Sagasu!
            </h1>

            <p>
              Kết nối, chia sẻ và tìm lại những món đồ quan trọng cùng cộng đồng sinh viên.
            </p>
          </div>
        </section>

        {/* 2. User Identity Card */}
        <section className="profile-user-card" aria-label="Thông tin hồ sơ cá nhân">
          <div className="profile-user-main">
            {user?.avatarUrl ? (
              <img className="profile-user-avatar-img" src={user.avatarUrl} alt={displayName} />
            ) : (
              <div className="profile-user-avatar-placeholder" aria-hidden="true">
                <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <circle cx="32" cy="32" r="32" fill="#d9e4f8" />
                  <circle cx="32" cy="24.5" r="10.5" fill="#7c98d4" />
                  <path
                    d="M14 55.5C16.8 45.2 23.6 39.5 32 39.5C40.4 39.5 47.2 45.2 50 55.5C45.1 60.7 38.9 64 32 64C25.1 64 18.9 60.7 14 55.5Z"
                    fill="#7c98d4"
                  />
                </svg>
              </div>
            )}

            <div className="profile-user-meta">
              <h2>{displayName}</h2>
              <p className="profile-user-email">{displayEmail}</p>
              <div className="profile-user-badges">
                <span className="profile-verified-badge">
                  <CheckCircle2 size={14} strokeWidth={2.3} aria-hidden="true" />
                  {displayRoleLabel}
                </span>

                <span className="profile-meta-divider" aria-hidden="true" />

                <span className="profile-meta-item">
                  <GraduationCap size={15} aria-hidden="true" />
                  {displayOrg}
                </span>

                {displayPhone && (
                  <>
                    <span className="profile-meta-divider" aria-hidden="true" />
                    <span className="profile-meta-item">
                      <Phone size={14} aria-hidden="true" />
                      {displayPhone}
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>

          <div className="profile-user-actions">
            <button type="button" className="profile-pill-btn" onClick={openProfileModal}>
              <Pencil size={14} aria-hidden="true" />
              <span>Chỉnh sửa hồ sơ</span>
            </button>
            <button type="button" className="profile-pill-btn is-subtle" onClick={openPasswordModal}>
              <KeyRound size={14} aria-hidden="true" />
              <span>Đổi mật khẩu</span>
            </button>
          </div>
        </section>

        {/* 3. Truy cập nhanh */}
        <section className="profile-quick-section" aria-labelledby="profile-quick-heading">
          <div className="profile-section-head">
            <h3 id="profile-quick-heading">
              <span className="profile-section-accent" aria-hidden="true" />
              Truy cập nhanh
            </h3>
            <Link to="/tim-kiem" className="profile-view-all">
              <span>Xem tất cả</span>
              <ArrowRight size={15} aria-hidden="true" />
            </Link>
          </div>

          <div className="profile-quick-grid">
            {QUICK_ACCESS_ITEMS.map((item) => {
              const Icon = item.icon;
              const badge = accountBadges[item.key];
              const badgeText = badge?.count > 0 ? (badge.count > 99 ? '99+' : String(badge.count)) : '';
              return (
                <Link key={item.key} to={item.to} className="profile-quick-card" title={badge?.title}>
                  <span className="profile-quick-left">
                    <Icon size={20} strokeWidth={1.85} aria-hidden="true" />
                    <span>{item.label}</span>
                  </span>
                  <span className="profile-quick-right">
                    {badgeText && (
                      <span className={`profile-quick-badge is-${badge.tone}`} aria-label={badge.title}>
                        {badgeText}
                      </span>
                    )}
                    <ChevronRight size={17} className="profile-quick-arrow" aria-hidden="true" />
                  </span>
                </Link>
              );
            })}
          </div>
        </section>

        {/* 4. Community Callout Banner */}
        <section className="profile-community-banner" aria-label="Cộng đồng Sagasu">
          <div className="profile-community-left">
            <span className="profile-community-icon" aria-hidden="true">
              <Megaphone size={22} strokeWidth={2} />
            </span>
            <div className="profile-community-text">
              <strong>Cùng xây dựng cộng đồng Sagasu văn minh hơn!</strong>
              <p>Hãy tuân thủ quy định và lan tỏa tinh thần giúp đỡ lẫn nhau nhé.</p>
            </div>
          </div>

          <button type="button" className="profile-community-btn" onClick={openCommunityModal}>
            <span>Tìm hiểu thêm</span>
            <ArrowRight size={15} aria-hidden="true" />
          </button>

          <img
            src={sagasuLogoColor}
            alt=""
            aria-hidden="true"
            className="profile-community-watermark"
          />
        </section>
      </div>

      {/* Modal: Chỉnh sửa hồ sơ & Đổi mật khẩu */}
      {(activeModal === 'profile' || activeModal === 'password') && (
        <div
          className="profile-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="profile-modal-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeModal();
          }}
        >
          <div className="profile-modal-card">
            <header className="profile-modal-header">
              <div>
                <h2 id="profile-modal-title">
                  {activeModal === 'profile' ? 'Chỉnh sửa hồ sơ cá nhân' : 'Đổi mật khẩu'}
                </h2>
                <p>
                  {activeModal === 'profile'
                    ? 'Cập nhật thông tin liên hệ gắn với tài khoản Sagasu của bạn.'
                    : 'Đặt mật khẩu mới mạnh hơn và khác với mật khẩu hiện tại.'}
                </p>
              </div>
              <button
                type="button"
                className="profile-modal-close"
                onClick={closeModal}
                aria-label="Đóng hộp thoại"
              >
                <X size={18} />
              </button>
            </header>

            <div className="profile-modal-tabs" role="tablist">
              <button
                type="button"
                role="tab"
                aria-selected={activeModal === 'profile'}
                className={`profile-modal-tab ${activeModal === 'profile' ? 'is-active' : ''}`}
                onClick={openProfileModal}
              >
                <User size={15} aria-hidden="true" />
                Thông tin cá nhân
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={activeModal === 'password'}
                className={`profile-modal-tab ${activeModal === 'password' ? 'is-active' : ''}`}
                onClick={openPasswordModal}
              >
                <KeyRound size={15} aria-hidden="true" />
                Đổi mật khẩu
              </button>
            </div>

            <div className="profile-modal-body">
              {activeModal === 'profile' ? (
                <form className="profile-modal-form" onSubmit={handleSaveInfo}>
                  {infoFeedback?.message && (
                    <div className={`profile-feedback is-${infoFeedback.type}`} role="alert">
                      {infoFeedback.type === 'error' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
                      <span>{infoFeedback.message}</span>
                    </div>
                  )}

                  <div className="profile-form-field">
                    <label htmlFor="modal-fullname">Họ và tên</label>
                    <div className="profile-input-box is-disabled">
                      <User size={15} className="profile-input-ico" aria-hidden="true" />
                      <input id="modal-fullname" type="text" value={displayName} disabled readOnly />
                    </div>
                  </div>

                  <div className="profile-form-field">
                    <label htmlFor="modal-email">Email liên hệ</label>
                    <div className="profile-input-box is-disabled">
                      <Mail size={15} className="profile-input-ico" aria-hidden="true" />
                      <input id="modal-email" type="email" value={displayEmail} disabled readOnly />
                    </div>
                  </div>

                  <div className="profile-form-field">
                    <label htmlFor="modal-phone">Số điện thoại</label>
                    <div className="profile-input-box">
                      <Phone size={15} className="profile-input-ico" aria-hidden="true" />
                      <input
                        id="modal-phone"
                        type="tel"
                        placeholder="Nhập số điện thoại (VD: 0912345678)"
                        value={phoneInput}
                        onChange={(e) => setPhoneInput(e.target.value)}
                      />
                    </div>
                  </div>

                  {isPureAdmin && (
                    <div className="profile-form-field">
                      <label htmlFor="modal-role">Vai trò tài khoản</label>
                      <div className="profile-input-box">
                        <select
                          id="modal-role"
                          value={roleInput}
                          onChange={(e) => setRoleInput(e.target.value)}
                        >
                          <option value="ADMIN">Quản trị viên (ADMIN)</option>
                          <option value="MANAGER">Quản lý tổ chức (MANAGER)</option>
                          <option value="APPROVER">Staff (STAFF)</option>
                          <option value="USER">Thành viên (USER)</option>
                        </select>
                      </div>
                    </div>
                  )}

                  <div className="profile-modal-actions">
                    <button
                      type="button"
                      className="profile-btn-secondary"
                      onClick={closeModal}
                      disabled={savingInfo}
                    >
                      Đóng
                    </button>
                    <button type="submit" className="profile-btn-primary" disabled={savingInfo}>
                      {savingInfo ? 'Đang lưu...' : 'Lưu thay đổi'}
                    </button>
                  </div>
                </form>
              ) : (
                <form className="profile-modal-form" onSubmit={handleChangePassword}>
                  {passwordFeedback?.message && (
                    <div className={`profile-feedback is-${passwordFeedback.type}`} role="alert">
                      {passwordFeedback.type === 'error' ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
                      <span>{passwordFeedback.message}</span>
                    </div>
                  )}

                  <div className="profile-form-field">
                    <label htmlFor="modal-current-password">Mật khẩu hiện tại</label>
                    <div className="profile-input-box">
                      <Lock size={15} className="profile-input-ico" aria-hidden="true" />
                      <input
                        id="modal-current-password"
                        type={showCurrent ? 'text' : 'password'}
                        autoComplete="current-password"
                        placeholder="Nhập mật khẩu hiện tại"
                        value={currentPassword}
                        onChange={(e) => setCurrentPassword(e.target.value)}
                        autoFocus
                      />
                      <button
                        type="button"
                        className="profile-eye-btn"
                        onClick={() => setShowCurrent((v) => !v)}
                        aria-label={showCurrent ? 'Ẩn mật khẩu hiện tại' : 'Hiện mật khẩu hiện tại'}
                      >
                        {showCurrent ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <div className="profile-form-field">
                    <label htmlFor="modal-new-password">Mật khẩu mới</label>
                    <div className="profile-input-box">
                      <KeyRound size={15} className="profile-input-ico" aria-hidden="true" />
                      <input
                        id="modal-new-password"
                        type={showNew ? 'text' : 'password'}
                        autoComplete="new-password"
                        placeholder="8–16 ký tự, có ký tự đặc biệt"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                      />
                      <button
                        type="button"
                        className="profile-eye-btn"
                        onClick={() => setShowNew((v) => !v)}
                        aria-label={showNew ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'}
                      >
                        {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <div className="profile-form-field">
                    <label htmlFor="modal-confirm-password">Xác nhận mật khẩu mới</label>
                    <div className="profile-input-box">
                      <KeyRound size={15} className="profile-input-ico" aria-hidden="true" />
                      <input
                        id="modal-confirm-password"
                        type={showConfirm ? 'text' : 'password'}
                        autoComplete="new-password"
                        placeholder="Nhập lại mật khẩu mới"
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                      />
                      <button
                        type="button"
                        className="profile-eye-btn"
                        onClick={() => setShowConfirm((v) => !v)}
                        aria-label={showConfirm ? 'Ẩn mật khẩu xác nhận' : 'Hiện mật khẩu xác nhận'}
                      >
                        {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                      </button>
                    </div>
                  </div>

                  <ul className="profile-pwd-rules" aria-label="Yêu cầu mật khẩu">
                    {passwordRules.map((rule) => (
                      <li key={rule.key} className={rule.ok ? 'is-ok' : ''}>
                        <span className="profile-rule-dot" aria-hidden="true">
                          {rule.ok && <Check size={10} strokeWidth={3} />}
                        </span>
                        {rule.label}
                      </li>
                    ))}
                  </ul>

                  <div className="profile-modal-actions">
                    <button
                      type="button"
                      className="profile-btn-secondary"
                      onClick={closeModal}
                      disabled={savingPassword}
                    >
                      Đóng
                    </button>
                    <button type="submit" className="profile-btn-primary" disabled={savingPassword}>
                      {savingPassword ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Modal: Quy định cộng đồng */}
      {activeModal === 'community' && (
        <div
          className="profile-modal-backdrop"
          role="dialog"
          aria-modal="true"
          aria-labelledby="community-modal-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) closeModal();
          }}
        >
          <div className="profile-modal-card">
            <header className="profile-modal-header">
              <div>
                <h2 id="community-modal-title">Quy định cộng đồng Sagasu</h2>
                <p>Cùng nhau giữ gìn môi trường tìm đồ thất lạc an toàn, tin cậy và văn minh.</p>
              </div>
              <button
                type="button"
                className="profile-modal-close"
                onClick={closeModal}
                aria-label="Đóng hộp thoại"
              >
                <X size={18} />
              </button>
            </header>

            <div className="profile-modal-body">
              <div className="profile-community-rules-list">
                {COMMUNITY_RULES.map((rule) => {
                  const Icon = rule.icon;
                  return (
                    <div key={rule.title} className="profile-community-rule-item">
                      <span className="profile-community-rule-icon">
                        <Icon size={18} aria-hidden="true" />
                      </span>
                      <div>
                        <h4>{rule.title}</h4>
                        <p>{rule.desc}</p>
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="profile-modal-actions">
                <button type="button" className="profile-btn-primary" onClick={closeModal}>
                  Đã hiểu
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
