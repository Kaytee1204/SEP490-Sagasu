import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getChat } from '../api/chatApi';
import { useChat } from '../ChatProvider';
import ChatList from '../components/ChatList';
import ChatThread from '../components/ChatThread';
import '../Chat.css';

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
      <Link to={`/chi-tiet/${chat.postId}`}>{chat.postTitle}</Link>
      <div className="chat-row-title"><Link to={`/bai-dang/${chat.postId}/claims/${chat.claimId}`}>Xem hồ sơ claim</Link>
        <small className="chat-muted">{online ? 'Đã kết nối' : 'Đang kết nối lại…'}</small></div>
    </header><ChatThread chat={chat} /></>}
  </section>;
}
