import { useCallback, useEffect, useRef, useState } from 'react';
import { BookmarkCheck, CircleAlert, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useLocation } from 'react-router-dom';
import { getSavedPostIds, savePost, unsavePost } from '../api/savedPostApi';
import { SavedPostsContext } from './SavedPostsContext';
import '../styles/SavedPosts.css';

export default function SavedPostsProvider({ children }) {
  const { token } = useAuth();
  return <SavedPostsSession key={token || 'guest'} token={token}>{children}</SavedPostsSession>;
}

function SavedPostsSession({ token, children }) {
  const { pathname } = useLocation();
  const [ids, setIds] = useState(new Set());
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [pending, setPending] = useState(new Set());
  const [version, setVersion] = useState(0);
  const [notice, setNotice] = useState(null);
  const busy = useRef(new Set());
  const revision = useRef(0);
  const request = useRef(0);
  const knownIds = useRef(new Set());

  const reload = useCallback(async () => {
    if (!token || busy.current.size) return;
    const sequence = ++request.current;
    const startedAt = revision.current;
    try {
      const result = await getSavedPostIds(token);
      if (sequence !== request.current || startedAt !== revision.current) return;
      const updated = new Set(result);
      const changed = updated.size !== knownIds.current.size || [...updated].some(id => !knownIds.current.has(id));
      knownIds.current = updated;
      setIds(updated);
      if (changed) setVersion(current => current + 1);
      setReady(true);
      setError('');
    } catch {
      if (sequence === request.current) setError('Chưa tải được trạng thái lưu. Bấm để thử lại.');
    }
  }, [token]);

  useEffect(() => {
    const initialLoad = window.setTimeout(reload, 0);
    window.addEventListener('focus', reload);
    return () => { window.clearTimeout(initialLoad); window.removeEventListener('focus', reload); };
  }, [reload, pathname]);

  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(null), notice.error ? 7000 : 3500);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const toggle = async postId => {
    if (!token || !ready || error || busy.current.has(postId)) return;
    const wasSaved = ids.has(postId);
    busy.current.add(postId);
    ++revision.current;
    setPending(new Set(busy.current));
    try {
      await (wasSaved ? unsavePost(postId) : savePost(postId));
      if (wasSaved) knownIds.current.delete(postId); else knownIds.current.add(postId);
      setIds(current => {
        const updated = new Set(current);
        if (wasSaved) updated.delete(postId); else updated.add(postId);
        return updated;
      });
      setVersion(current => current + 1);
      setNotice({ text: wasSaved ? 'Đã bỏ lưu bài đăng' : 'Đã lưu bài đăng', error: false });
    } catch (err) {
      setNotice({ text: err.message || 'Chưa lưu được thay đổi. Vui lòng thử lại.', error: true });
    } finally {
      busy.current.delete(postId);
      ++revision.current;
      setPending(new Set(busy.current));
    }
  };

  return <SavedPostsContext.Provider value={{ ids, ready, error, pending, version, toggle, reload }}>
    {children}
    {notice && <div className={`saved-post-notice${notice.error ? ' is-error' : ''}`} role={notice.error ? 'alert' : 'status'}>
      {notice.error ? <CircleAlert size={19} aria-hidden="true" /> : <BookmarkCheck size={19} aria-hidden="true" />}
      <span>{notice.text}</span>
      <button type="button" aria-label="Đóng thông báo" onClick={() => setNotice(null)}><X size={17} /></button>
    </div>}
  </SavedPostsContext.Provider>;
}
