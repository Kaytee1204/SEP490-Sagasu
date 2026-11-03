import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getChats } from '../api/chatApi';

export default function ChatList({ activeId }) {
  const [rows, setRows] = useState([]);
  const [cursor, setCursor] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);
  useEffect(() => {
    let cancelled = false;
    getChats().then(page => { if (!cancelled) { setRows(page.items); setCursor(page.nextCursor); setError(''); } })
      .catch(err => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setBusy(false); });
    return () => { cancelled = true; };
  }, [activeId]);
  async function more() {
    if (busy || !cursor) return;
    setBusy(true); setError('');
    try {
      const page = await getChats(cursor);
      setRows(old => [...old, ...page.items.filter(item => !old.some(row => row.id === item.id))]);
      setCursor(page.nextCursor);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <aside className="chat-list" aria-label="Danh sách trò chuyện"><h1>Tin nhắn</h1>
    {error && <p role="alert">{error}</p>}
    {!rows.length && <p className="chat-muted">{busy ? 'Đang tải…' : 'Bạn chưa có cuộc trò chuyện nào. Chat sẽ xuất hiện khi claim được chấp thuận.'}</p>}
    {rows.map(row => <Link key={row.id} to={`/tin-nhan/${row.id}`} className={`chat-list-row ${String(row.id) === activeId ? 'active' : ''}`}>
      <div className="chat-row-title"><strong>{row.otherName || 'Thành viên'}</strong>{row.unreadCount > 0 && <span className="chat-unread">{row.unreadCount > 99 ? '99+' : row.unreadCount}</span>}</div>
      <span>{row.postTitle}</span><small className="chat-preview">{row.preview}</small>
      <small className="chat-muted">{new Date(row.lastActivityAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}{!row.canSend && ' · Chỉ đọc'}</small>
    </Link>)}
    {cursor && <button type="button" className="btn btn-ghost" disabled={busy} onClick={more}>{busy ? 'Đang tải…' : 'Xem thêm'}</button>}
  </aside>;
}
