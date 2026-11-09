import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  BellOff,
  Calendar,
  CheckCheck,
  CheckCircle2,
  ClipboardList,
  Globe,
  Mail,
  MessageCircle,
  MonitorSmartphone,
  Settings,
  ShieldAlert,
} from 'lucide-react';
import { useNotifications } from '../NotificationProvider';

function formatRelativeTime(isoString) {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return '';
  const diffSec = Math.floor((Date.now() - date.getTime()) / 1000);
  if (diffSec < 60) return 'Vừa xong';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin} phút trước`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour} giờ trước`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay} ngày trước`;
  return date.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

function getTypeStyle(type = '') {
  if (type === 'CHAT_MESSAGE') {
    return { Icon: MessageCircle, bg: 'bg-sky-50 text-sky-600' };
  }
  if (type.includes('APPOINTMENT')) {
    return { Icon: Calendar, bg: 'bg-teal-50 text-teal-600' };
  }
  if (type.includes('APPROVED') || type.includes('COMPLETED') || type.includes('VISIBLE') || type.includes('RECEIVED')) {
    return { Icon: CheckCircle2, bg: 'bg-emerald-50 text-emerald-600' };
  }
  if (type.includes('REJECTED') || type.includes('DISPUTED') || type.includes('HIDDEN') || type.includes('REPORT')) {
    return { Icon: ShieldAlert, bg: 'bg-rose-50 text-rose-600' };
  }
  return { Icon: ClipboardList, bg: 'bg-indigo-50 text-indigo-600' };
}

export default function NotificationBell({ variant = 'navbar' }) {
  const navigate = useNavigate();
  const {
    items,
    unreadCount,
    loading,
    preferences,
    savingPrefs,
    fetchList,
    markRead,
    markAllRead,
    savePreferences,
  } = useNotifications();

  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState('all'); // 'all' | 'unread' | 'settings'
  const containerRef = useRef(null);

  useEffect(() => {
    const handleOutside = (e) => {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handleOutside);
    return () => document.removeEventListener('mousedown', handleOutside);
  }, []);

  useEffect(() => {
    if (open && tab !== 'settings') {
      fetchList({ unreadOnly: tab === 'unread', page: 1, size: 15 });
    }
  }, [open, tab, fetchList]);

  const handleItemClick = async (notif) => {
    if (!notif.read && notif.id) {
      await markRead(notif.id);
    }
    setOpen(false);
    if (notif.targetUrl) {
      navigate(notif.targetUrl);
    }
  };

  const displayedItems = tab === 'unread' ? items.filter((i) => !i.read) : items;

  const isMutedAll = !preferences.inAppEnabled && !preferences.emailEnabled;

  return (
    <div className="relative" ref={containerRef}>
      {variant === 'admin' ? (
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="w-8 h-8 rounded-md border border-zinc-200 bg-white flex items-center justify-center text-zinc-600 hover:bg-zinc-50 relative cursor-pointer"
          aria-label="Thông báo"
          title="Thông báo hệ thống"
        >
          {isMutedAll ? <BellOff size={15} className="text-zinc-400" /> : <Bell size={15} />}
          {unreadCount > 0 && (
            <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] px-1 rounded-full bg-rose-500 text-white text-[10px] font-bold flex items-center justify-center">
              {unreadCount > 99 ? '99+' : unreadCount}
            </span>
          )}
        </button>
      ) : (
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="nav-icon-btn flex relative cursor-pointer"
          id="nav-notifications"
          aria-label="Thông báo"
          title="Thông báo"
        >
          {isMutedAll ? <BellOff size={18} className="text-slate-400" /> : <Bell size={18} />}
          {unreadCount > 0 && (
            <span className="nav-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>
          )}
        </button>
      )}

      {open && (
        <div className="absolute right-0 mt-2.5 w-[350px] sm:w-[400px] max-w-[calc(100vw-1.5rem)] bg-white rounded-2xl shadow-2xl border border-slate-200/90 z-50 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
          {/* Header */}
          <div className="px-4 py-3.5 border-b border-slate-100 flex items-center justify-between bg-slate-50/60">
            <div className="flex items-center gap-2">
              <span className="text-[15px] font-bold text-slate-900">Thông báo</span>
              {unreadCount > 0 && (
                <span className="px-2 py-0.5 text-[11px] font-bold rounded-full bg-indigo-100 text-indigo-700">
                  {unreadCount} mới
                </span>
              )}
            </div>
            <div className="flex items-center gap-1.5">
              {unreadCount > 0 && tab !== 'settings' && (
                <button
                  type="button"
                  onClick={markAllRead}
                  className="inline-flex items-center gap-1 text-[12px] font-semibold text-indigo-600 hover:text-indigo-700 px-2 py-1 rounded-lg hover:bg-indigo-50 transition-colors cursor-pointer"
                  title="Đánh dấu tất cả là đã đọc"
                >
                  <CheckCheck size={14} />
                  <span>Đọc tất cả</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => setTab((prev) => (prev === 'settings' ? 'all' : 'settings'))}
                className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
                  tab === 'settings'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-slate-500 hover:bg-slate-100 hover:text-slate-800'
                }`}
                title="Cài đặt bật/tắt thông báo"
                aria-label="Cài đặt thông báo"
              >
                <Settings size={16} />
              </button>
            </div>
          </div>

          {/* Filter Tabs */}
          <div className="px-4 py-2 border-b border-slate-100 flex items-center gap-1.5 bg-white">
            <button
              type="button"
              onClick={() => setTab('all')}
              className={`px-3 py-1.5 rounded-lg text-[12px] font-semibold transition-colors cursor-pointer ${
                tab === 'all'
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              Tất cả
            </button>
            <button
              type="button"
              onClick={() => setTab('unread')}
              className={`px-3 py-1.5 rounded-lg text-[12px] font-semibold transition-colors cursor-pointer ${
                tab === 'unread'
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              Chưa đọc {unreadCount > 0 ? `(${unreadCount})` : ''}
            </button>
            <button
              type="button"
              onClick={() => setTab('settings')}
              className={`ml-auto px-3 py-1.5 rounded-lg text-[12px] font-semibold transition-colors cursor-pointer ${
                tab === 'settings'
                  ? 'bg-indigo-600 text-white'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              Chủ động bật/tắt
            </button>
          </div>

          {/* Body */}
          {tab === 'settings' ? (
            <div className="p-4 space-y-3.5 max-h-[420px] overflow-y-auto">
              <div className="text-[12px] text-slate-500 leading-relaxed bg-slate-50 p-3 rounded-xl border border-slate-100">
                Bạn có thể chủ động bật hoặc tắt từng kênh thông báo tùy theo nhu cầu khi đang dùng web hoặc khi không mở web:
              </div>

              {/* Toggle 1: In-App */}
              <label className="flex items-start justify-between gap-3 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition-colors cursor-pointer">
                <div className="flex items-start gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0 mt-0.5">
                    <Globe size={16} />
                  </div>
                  <div>
                    <div className="text-[13px] font-bold text-slate-900">
                      Thông báo trong Web (In-App)
                    </div>
                    <p className="text-[11.5px] text-slate-500 mt-0.5 leading-relaxed">
                      Hiển thị tại chuông & popup góc màn hình cho tin nhắn chat, cập nhật trạng thái bài đăng, hồ sơ nhận đồ.
                    </p>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={Boolean(preferences.inAppEnabled)}
                  disabled={savingPrefs}
                  onChange={(e) => savePreferences({ inAppEnabled: e.target.checked })}
                  className="mt-1 h-4 w-4 accent-indigo-600 rounded cursor-pointer shrink-0"
                />
              </label>

              {/* Toggle 2: Email */}
              <label className="flex items-start justify-between gap-3 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition-colors cursor-pointer">
                <div className="flex items-start gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0 mt-0.5">
                    <Mail size={16} />
                  </div>
                  <div>
                    <div className="text-[13px] font-bold text-slate-900">
                      Thông báo qua Email (Khi không mở Web)
                    </div>
                    <p className="text-[11.5px] text-slate-500 mt-0.5 leading-relaxed">
                      Gửi đồng thời qua Email cho các sự kiện quan trọng: có yêu cầu nhận đồ mới, duyệt/từ chối hồ sơ, biên bản bàn giao P2P, lịch hẹn văn phòng.
                    </p>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={Boolean(preferences.emailEnabled)}
                  disabled={savingPrefs}
                  onChange={(e) => savePreferences({ emailEnabled: e.target.checked })}
                  className="mt-1 h-4 w-4 accent-indigo-600 rounded cursor-pointer shrink-0"
                />
              </label>

              {/* Toggle 3: Browser Push */}
              <label className="flex items-start justify-between gap-3 p-3 rounded-xl border border-slate-200/80 hover:border-indigo-200 transition-colors cursor-pointer">
                <div className="flex items-start gap-2.5">
                  <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center shrink-0 mt-0.5">
                    <MonitorSmartphone size={16} />
                  </div>
                  <div>
                    <div className="text-[13px] font-bold text-slate-900">
                      Thông báo đẩy trình duyệt (Desktop Push)
                    </div>
                    <p className="text-[11.5px] text-slate-500 mt-0.5 leading-relaxed">
                      Bật thông báo góc màn hình máy tính/điện thoại ngay cả khi bạn đang thu nhỏ hoặc chuyển sang tab khác.
                    </p>
                  </div>
                </div>
                <input
                  type="checkbox"
                  checked={Boolean(preferences.browserPushEnabled)}
                  disabled={savingPrefs}
                  onChange={(e) => savePreferences({ browserPushEnabled: e.target.checked })}
                  className="mt-1 h-4 w-4 accent-indigo-600 rounded cursor-pointer shrink-0"
                />
              </label>

              <div className="text-[11px] text-slate-400 px-1">
                * Lưu ý: Email mã xác thực bảo mật (OTP quên mật khẩu / đổi mật khẩu) luôn được gửi để bảo vệ tài khoản của bạn.
              </div>
            </div>
          ) : (
            <div className="max-h-[400px] overflow-y-auto divide-y divide-slate-100">
              {loading && displayedItems.length === 0 ? (
                <div className="py-10 text-center text-[13px] text-slate-400">
                  Đang tải thông báo...
                </div>
              ) : displayedItems.length === 0 ? (
                <div className="py-10 px-6 text-center">
                  <div className="w-11 h-11 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-2.5">
                    <Bell size={20} />
                  </div>
                  <p className="text-[13px] font-semibold text-slate-700">
                    {tab === 'unread' ? 'Không có thông báo chưa đọc' : 'Chưa có thông báo nào'}
                  </p>
                  <p className="text-[12px] text-slate-400 mt-1">
                    Các cập nhật về hồ sơ nhận đồ, tin nhắn và lịch hẹn sẽ hiển thị tại đây.
                  </p>
                </div>
              ) : (
                displayedItems.map((notif) => {
                  const { Icon, bg } = getTypeStyle(notif.type);
                  return (
                    <button
                      key={notif.id}
                      type="button"
                      onClick={() => handleItemClick(notif)}
                      className={`w-full text-left px-4 py-3 flex items-start gap-3 transition-colors hover:bg-slate-50 cursor-pointer ${
                        !notif.read ? 'bg-indigo-50/35' : 'bg-white'
                      }`}
                    >
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 mt-0.5 ${bg}`}
                      >
                        <Icon size={17} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-2 mb-0.5">
                          <span
                            className={`inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded ${
                              notif.channel === 'BOTH'
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-slate-100 text-slate-600'
                            }`}
                          >
                            {notif.channel === 'BOTH' ? 'Web + Email' : 'Trong Web'}
                          </span>
                          <span className="text-[11px] text-slate-400 shrink-0">
                            {formatRelativeTime(notif.createdAt)}
                          </span>
                        </div>
                        <p
                          className={`text-[13px] line-clamp-1 ${
                            !notif.read ? 'font-bold text-slate-900' : 'font-semibold text-slate-700'
                          }`}
                        >
                          {notif.title}
                        </p>
                        <p className="text-[12px] text-slate-600 line-clamp-2 mt-0.5 leading-relaxed">
                          {notif.content}
                        </p>
                      </div>
                      {!notif.read && (
                        <span
                          className="w-2.5 h-2.5 rounded-full bg-indigo-600 shrink-0 mt-2"
                          title="Chưa đọc"
                        />
                      )}
                    </button>
                  );
                })
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
