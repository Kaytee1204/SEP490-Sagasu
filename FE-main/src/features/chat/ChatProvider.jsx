import { createContext, useContext, useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useAuth } from '@/context/AuthContext';
import apiClient from '@/services/apiClient';
import { getUnreadCount, getChatLimits } from './api/chatApi';

const ChatContext = createContext({ revision: 0, online: false, unread: 0, refresh: () => {} });
export const useChat = () => useContext(ChatContext);

export default function ChatProvider({ children }) {
  const { user, token } = useAuth();
  const [revision, setRevision] = useState(0);
  const [online, setOnline] = useState(false);
  const [unread, setUnread] = useState(0);
  const [limits, setLimits] = useState({ messageMaxLength: 4000, attachmentMaxCount: 5, imageMaxBytes: 5242880, pdfMaxBytes: 10485760, totalMaxBytes: 26214400 });
  useEffect(() => {
    let disposed = false;
    if (user && token) getChatLimits().then(value => { if (!disposed) setLimits(value); }).catch(() => {});
    return () => { disposed = true; };
  }, [user?.id, token]);
  function refresh() { setRevision(value => value + 1); }
  useEffect(() => {
    let disposed = false;
    if (!user || !token) { setUnread(0); return; }
    getUnreadCount().then(value => { if (!disposed) setUnread(value); }).catch(() => {});
    return () => { disposed = true; };
  }, [user?.id, token, revision]);
  useEffect(() => {
    if (!user || !token) return;
    let expiration;
    try { expiration = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000; }
    catch { return; }
    if (!Number.isFinite(expiration) || expiration <= Date.now()) return;
    const url = new URL(apiClient.getBaseUrl(), window.location.origin);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = `${url.pathname.replace(/\/api\/?$/, '').replace(/\/$/, '')}/ws/chat`;
    url.search = ''; url.hash = '';
    let disposed = false;
    const client = new Client({
      brokerURL: import.meta.env.VITE_CHAT_WS_URL || url.href,
      connectHeaders: { Authorization: `Bearer ${token}` }, reconnectDelay: 3000,
      heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
      onConnect: () => {
        if (disposed) return;
        setOnline(true); refresh();
        client.subscribe('/user/queue/chat-events', () => { if (!disposed) refresh(); });
      },
      onWebSocketClose: () => { if (!disposed) setOnline(false); },
      onStompError: () => { if (!disposed) setOnline(false); },
    });
    window.addEventListener('focus', refresh);
    client.activate();
    const timer = setTimeout(() => { setOnline(false); client.deactivate(); }, Math.min(expiration - Date.now(), 2147483647));
    return () => {
      disposed = true; clearTimeout(timer); window.removeEventListener('focus', refresh);
      setOnline(false); client.deactivate();
    };
  }, [user?.id, token]);
  return <ChatContext.Provider value={{ revision, online, unread, limits, refresh }}>{children}</ChatContext.Provider>;
}
