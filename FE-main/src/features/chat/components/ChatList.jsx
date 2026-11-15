import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronDown, History, LockKeyhole, MessageCircle, RotateCcw, UserRound } from 'lucide-react';
import { getChats } from '../api/chatApi';
import { useChat } from '../ChatProvider';

export default function ChatList({ activeId }) {
  const { revision } = useChat();
  return <aside className="chat-list" aria-label="Danh sách trò chuyện"><h1>Tin nhắn</h1>
    <ConversationGroup view="ACTIVE" activeId={activeId} revision={revision} />
    <ConversationGroup view="HISTORY" activeId={activeId} revision={revision} />
  </aside>;
}

function ConversationGroup({ view, activeId, revision }) {
  const [snapshot, setSnapshot] = useState({ key: null, items: [], cursor: null, error: '' });
  const [attempt, setAttempt] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const moreInFlight = useRef(false);
  const requestKey = `${view}:${activeId || ''}:${revision}:${attempt}`;
  const busy = loadingMore || snapshot.key !== requestKey;
  const error = snapshot.key === requestKey ? snapshot.error : '';
  const history = view === 'HISTORY';
  const Icon = history ? History : MessageCircle;
  const Group = history ? 'details' : 'section';

  useEffect(() => {
    let cancelled = false;
    getChats(null, view).then(page => {
      if (!cancelled) setSnapshot({ key: requestKey, items: page.items, cursor: page.nextCursor, error: '' });
    }).catch(problem => {
      if (!cancelled) setSnapshot(previous => ({ ...previous, key: requestKey, error: problem.message || 'Chưa tải được danh sách trò chuyện.' }));
    });
    return () => { cancelled = true; };
  }, [view, requestKey]);

  async function more() {
    if (busy || !snapshot.cursor || moreInFlight.current) return;
    moreInFlight.current = true;
    setLoadingMore(true);
    try {
      const page = await getChats(snapshot.cursor, view);
      setSnapshot(previous => {
        if (previous.key !== requestKey) return previous;
        const existing = new Set(previous.items.map(row => row.id));
        return { ...previous, items: [...previous.items, ...page.items.filter(row => !existing.has(row.id))], cursor: page.nextCursor, error: '' };
      });
    } catch (problem) {
      setSnapshot(previous => previous.key === requestKey ? { ...previous, error: problem.message || 'Chưa tải được cuộc trò chuyện tiếp theo.' } : previous);
    } finally { moreInFlight.current = false; setLoadingMore(false); }
  }

  if (history && !busy && !error && !snapshot.items.length) return null;
  return <Group className={`chat-list-group${history ? ' is-history' : ''}`} aria-label={history ? 'Lịch sử trò chuyện' : 'Cuộc trò chuyện đang trao đổi'} aria-busy={busy}>
    {history ? <summary className="chat-history-toggle"><Icon size={15} aria-hidden="true" /><span>Lịch sử</span>
      <ChevronDown size={17} className="chat-history-chevron" aria-hidden="true" />
    </summary> : <h2><Icon size={15} aria-hidden="true" />Đang trao đổi</h2>}
    {error && <div className="chat-list-error" role="alert"><p>{error}</p>
      <button type="button" onClick={() => setAttempt(value => value + 1)}><RotateCcw size={14} aria-hidden="true" />Thử lại</button>
    </div>}
    {!snapshot.items.length && !error && <p className="chat-list-empty" role="status">{busy ? 'Đang tải…' : 'Chưa có cuộc trò chuyện đang hoạt động.'}</p>}
    <div className="chat-list-cards">{snapshot.items.map(row => <ConversationCard key={row.id} row={row} activeId={activeId} />)}</div>
    {snapshot.cursor && <button type="button" className="chat-list-more" disabled={busy} onClick={more}>
      {busy ? 'Đang tải…' : history ? 'Xem thêm lịch sử' : 'Tải thêm cuộc trò chuyện'}
    </button>}
  </Group>;
}

function ConversationCard({ row, activeId }) {
  const active = String(row.id) === String(activeId);
  const date = new Date(row.lastActivityAt);
  return <Link to={`/tin-nhan/${row.id}`} aria-current={active ? 'page' : undefined}
    className={`chat-list-row${active ? ' active' : ''}${row.canSend ? '' : ' is-readonly'}${row.unreadCount > 0 ? ' has-unread' : ''}`}>
    <span className="chat-list-avatar" aria-hidden="true"><UserRound size={20} /></span>
    <span className="chat-list-content">
      <span className="chat-row-title"><strong title={row.otherName}>{row.otherName || 'Thành viên'}</strong>
        <time dateTime={row.lastActivityAt} title={date.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}>
          {date.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}
        </time>
      </span>
      <span className="chat-list-post" title={row.postTitle}>{row.postTitle}</span>
      <span className="chat-list-preview-row"><span className="chat-preview">{row.preview}</span>
        {row.unreadCount > 0 && <span className="chat-unread" aria-label={`${row.unreadCount} tin nhắn chưa đọc`}>{row.unreadCount > 99 ? '99+' : row.unreadCount}</span>}
      </span>
      {!row.canSend && <span className="chat-list-readonly-label"><LockKeyhole size={11} aria-hidden="true" />Chỉ xem lịch sử</span>}
    </span>
  </Link>;
}
