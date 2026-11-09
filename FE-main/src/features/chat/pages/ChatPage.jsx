import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getChat } from '../api/chatApi';
import { useChat } from '../ChatProvider';
import ChatList from '../components/ChatList';
import ChatThread from '../components/ChatThread';
import ChatItemPanel from '../components/ChatItemPanel';
import '../Chat.css';
import ReportButton from '@/features/reports/ReportButton';

export default function ChatPage() {
  const { conversationId } = useParams();
  const { user } = useAuth();
  return <main key={user?.id} className={`container chat-shell ${conversationId ? 'has-selection' : ''}`}>
    <ChatList activeId={conversationId} />
    {conversationId ? <ChatPanel key={`${user?.id}:${conversationId}`} id={conversationId} />
      : <section className="chat-panel"><p className="chat-empty">Chọn một cuộc trò chuyện để xem tin nhắn.</p></section>}
  </main>;
}

function ChatPanel({ id }) {
  const { revision, online } = useChat();
  const [chat, setChat] = useState(null);
  const [itemOpen, setItemOpen] = useState(false);
  const itemButton = useRef(null);
  const closeItem = () => { setItemOpen(false); itemButton.current?.focus(); };
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    if (!/^[1-9]\d*$/.test(id)) { setError('Mã cuộc trò chuyện không hợp lệ.'); return; }
    getChat(id).then(data => { if (!cancelled) { setChat(data); setError(''); } })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [id, revision]);
  return <section className="chat-panel">
    {error && <p className="chat-error" role="alert">{error}</p>}
    {!chat && !error && <p className="chat-empty">Đang tải cuộc trò chuyện…</p>}
    {chat && <><header className="chat-header"><Link className="chat-back" to="/tin-nhan">← Danh sách</Link>
      <h2>{chat.otherName || 'Thành viên'}</h2>
      <details className="workflow-menu"><summary aria-label="Thao tác cuộc trò chuyện">⋯</summary><div><ReportButton type="USER" id={chat.otherAccountId} targetLabel={chat.otherName} label="Báo cáo tài khoản"/></div></details>
      <button ref={itemButton} type="button" className="chat-item-toggle" aria-expanded={itemOpen} aria-controls="chat-item-panel"
        onClick={() => setItemOpen(value => !value)}>{chat.postTitle} · {itemOpen ? 'Ẩn thông tin' : 'Xem thông tin món đồ'}</button>
      <div className="chat-row-title"><Link to={`/bai-dang/${chat.postId}/claims/${chat.claimId}#handover`}>Tiến trình nhận đồ</Link>
        {!online && <small role="status" className="chat-muted">Đang kết nối lại…</small>}</div>
    </header><div className={`chat-conversation-body ${itemOpen ? 'with-item' : ''}`}>
      <div className="chat-thread-area"><ChatThread chat={chat} /></div>
      {itemOpen && <ChatItemPanel key={`${chat.id}:${chat.claimId}`} chat={chat} onClose={closeItem} />}
    </div></>}
  </section>;
}
