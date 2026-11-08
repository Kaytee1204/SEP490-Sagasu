import '@/features/workspace/Workspace.css';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getChat } from '../api/chatApi';
import { useChat } from '../ChatProvider';
import ChatList from '../components/ChatList';
import ChatThread from '../components/ChatThread';
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
      <Link to={`/chi-tiet/${chat.postId}`}>{chat.postTitle}</Link>
      <div className="chat-row-title"><Link to={`/bai-dang/${chat.postId}/claims/${chat.claimId}#handover`}>Tiến trình nhận đồ</Link>
        {!online && <small role="status" className="chat-muted">Đang kết nối lại…</small>}</div>
    </header><ChatThread chat={chat} /></>}
  </section>;
}
