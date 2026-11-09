import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import { Bell, X, Mail, Globe } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import apiClient from '@/services/apiClient';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  updateNotificationPreferences,
} from './api/notificationApi';

const DEFAULT_PREFERENCES = {
  inAppEnabled: true,
  emailEnabled: true,
  browserPushEnabled: false,
};

const NotificationContext = createContext({
  items: [],
  unreadCount: 0,
  loading: false,
  preferences: DEFAULT_PREFERENCES,
  savingPrefs: false,
  fetchList: async () => {},
  markRead: async () => {},
  markAllRead: async () => {},
  savePreferences: async () => {},
});

export const useNotifications = () => useContext(NotificationContext);

export default function NotificationProvider({ children }) {
  const { user, token, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [items, setItems] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [preferences, setPreferences] = useState(DEFAULT_PREFERENCES);
  const [savingPrefs, setSavingPrefs] = useState(false);
  const [toasts, setToasts] = useState([]);

  const prefsRef = useRef(DEFAULT_PREFERENCES);
  const locationRef = useRef(location.pathname);

  useEffect(() => {
    prefsRef.current = preferences;
  }, [preferences]);

  useEffect(() => {
    locationRef.current = location.pathname;
  }, [location.pathname]);

  const dismissToast = useCallback((toastId) => {
    setToasts((prev) => prev.filter((t) => t.toastId !== toastId));
  }, []);

  const pushToast = useCallback(
    (notif) => {
      const toastId = `${notif.id || Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
      setToasts((prev) => [{ ...notif, toastId }, ...prev].slice(0, 3));
      setTimeout(() => {
        dismissToast(toastId);
      }, 4500);
    },
    [dismissToast]
  );

  const syncUnreadAndPrefs = useCallback(async () => {
    if (!isAuthenticated || !user || !token) {
      setUnreadCount(0);
      setItems([]);
      return;
    }
    try {
      const data = await getUnreadNotificationCount();
      if (data) {
        setUnreadCount(typeof data.unreadCount === 'number' ? data.unreadCount : 0);
        if (data.preferences) {
          setPreferences(data.preferences);
        }
      }
    } catch {
      // Ignore transient network errors
    }
  }, [isAuthenticated, user, token]);

  const fetchList = useCallback(
    async ({ unreadOnly = false, page = 1, size = 15 } = {}) => {
      if (!isAuthenticated || !user || !token) return null;
      setLoading(true);
      try {
        const data = await getNotifications({ unreadOnly, page, size });
        if (data) {
          setItems(Array.isArray(data.content) ? data.content : []);
          if (typeof data.unreadCount === 'number') {
            setUnreadCount(data.unreadCount);
          }
          if (data.preferences) {
            setPreferences(data.preferences);
          }
        }
        return data;
      } catch {
        return null;
      } finally {
        setLoading(false);
      }
    },
    [isAuthenticated, user, token]
  );

  const markRead = useCallback(
    async (id) => {
      if (!id) return;
      setItems((prev) =>
        prev.map((item) =>
          item.id === id ? { ...item, read: true, readAt: new Date().toISOString() } : item
        )
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
      try {
        const res = await markNotificationAsRead(id);
        if (res && typeof res.unreadCount === 'number') {
          setUnreadCount(res.unreadCount);
        }
      } catch {
        syncUnreadAndPrefs();
      }
    },
    [syncUnreadAndPrefs]
  );

  const markAllRead = useCallback(async () => {
    setItems((prev) =>
      prev.map((item) => ({ ...item, read: true, readAt: item.readAt || new Date().toISOString() }))
    );
    setUnreadCount(0);
    try {
      await markAllNotificationsAsRead();
    } catch {
      syncUnreadAndPrefs();
    }
  }, [syncUnreadAndPrefs]);

  const savePreferences = useCallback(
    async (patch) => {
      const next = { ...prefsRef.current, ...patch };
      if (patch.browserPushEnabled === true && typeof window !== 'undefined' && 'Notification' in window) {
        if (window.Notification.permission === 'default') {
          try {
            const perm = await window.Notification.requestPermission();
            if (perm !== 'granted') {
              next.browserPushEnabled = false;
            }
          } catch {
            next.browserPushEnabled = false;
          }
        } else if (window.Notification.permission === 'denied') {
          next.browserPushEnabled = false;
        }
      }

      setPreferences(next);
      setSavingPrefs(true);
      try {
        const updated = await updateNotificationPreferences(next);
        if (updated) {
          setPreferences(updated);
        }
        return updated;
      } finally {
        setSavingPrefs(false);
      }
    },
    []
  );

  useEffect(() => {
    syncUnreadAndPrefs();
  }, [syncUnreadAndPrefs]);

  useEffect(() => {
    if (!isAuthenticated || !user || !token) return;

    let expiration;
    try {
      expiration =
        JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000;
    } catch {
      return;
    }
    if (!Number.isFinite(expiration) || expiration <= Date.now()) return;

    const url = new URL(apiClient.getBaseUrl(), window.location.origin);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = `${url.pathname.replace(/\/api\/?$/, '').replace(/\/$/, '')}/ws/chat`;
    url.search = '';
    url.hash = '';

    let disposed = false;
    const client = new Client({
      brokerURL: import.meta.env.VITE_CHAT_WS_URL || url.href,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        if (disposed) return;
        syncUnreadAndPrefs();
        client.subscribe('/user/queue/notifications', (frame) => {
          if (disposed) return;
          try {
            const incoming = JSON.parse(frame.body);
            if (!incoming || !incoming.id) return;

            setItems((prev) => {
              if (prev.some((x) => x.id === incoming.id)) return prev;
              return [incoming, ...prev].slice(0, 30);
            });
            setUnreadCount((prev) => prev + 1);

            const isAlreadyOnChat =
              incoming.type === 'CHAT_MESSAGE' &&
              locationRef.current.startsWith('/tin-nhan');

            if (prefsRef.current.inAppEnabled && !isAlreadyOnChat) {
              pushToast(incoming);
            }

            if (
              prefsRef.current.browserPushEnabled &&
              typeof window !== 'undefined' &&
              'Notification' in window &&
              window.Notification.permission === 'granted'
            ) {
              try {
                const browserNotif = new window.Notification(incoming.title || 'Thông báo Sagasu', {
                  body: incoming.content || '',
                  tag: `sagasu-notif-${incoming.id}`,
                });
                browserNotif.onclick = () => {
                  window.focus();
                  if (incoming.targetUrl) {
                    navigate(incoming.targetUrl);
                  }
                  browserNotif.close();
                };
              } catch {
                // Ignore browser notification errors
              }
            }
          } catch {
            syncUnreadAndPrefs();
          }
        });
      },
    });

    const handleFocus = () => syncUnreadAndPrefs();
    window.addEventListener('focus', handleFocus);
    client.activate();

    return () => {
      disposed = true;
      window.removeEventListener('focus', handleFocus);
      client.deactivate();
    };
  }, [isAuthenticated, user?.id, token, syncUnreadAndPrefs, pushToast, navigate]);

  const handleToastClick = (toast) => {
    dismissToast(toast.toastId);
    if (toast.id && !toast.read) {
      markRead(toast.id);
    }
    if (toast.targetUrl) {
      navigate(toast.targetUrl);
    }
  };

  return (
    <NotificationContext.Provider
      value={{
        items,
        unreadCount,
        loading,
        preferences,
        savingPrefs,
        fetchList,
        markRead,
        markAllRead,
        savePreferences,
      }}
    >
      {children}

      {/* Floating Real-time Toasts */}
      {toasts.length > 0 && (
        <div
          className="fixed top-20 right-4 z-[9999] flex flex-col gap-2.5 w-[340px] max-w-[calc(100vw-2rem)] pointer-events-none"
          aria-live="polite"
        >
          {toasts.map((toast) => (
            <div
              key={toast.toastId}
              onClick={() => handleToastClick(toast)}
              className="pointer-events-auto cursor-pointer bg-white/95 backdrop-blur-md border border-indigo-100 rounded-2xl shadow-xl p-3.5 flex items-start gap-3 transition-all hover:border-indigo-300 hover:shadow-2xl"
            >
              <div className="w-9 h-9 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0 mt-0.5">
                <Bell size={18} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1.5 mb-0.5">
                  <span className="inline-flex items-center gap-1 text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded bg-indigo-50 text-indigo-700">
                    {toast.channel === 'BOTH' ? (
                      <>
                        <Mail size={10} /> Web + Email
                      </>
                    ) : (
                      <>
                        <Globe size={10} /> Trong Web
                      </>
                    )}
                  </span>
                  <span className="text-[11px] text-slate-400">Vừa xong</span>
                </div>
                <p className="text-[13px] font-bold text-slate-900 line-clamp-1">{toast.title}</p>
                <p className="text-[12px] text-slate-600 line-clamp-2 mt-0.5 leading-relaxed">
                  {toast.content}
                </p>
              </div>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  dismissToast(toast.toastId);
                }}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-100 shrink-0"
                aria-label="Đóng thông báo"
              >
                <X size={14} />
              </button>
            </div>
          ))}
        </div>
      )}
    </NotificationContext.Provider>
  );
}
