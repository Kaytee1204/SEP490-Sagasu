import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getChat } from '../api/chatApi';
import { useAuth } from '@/context/AuthContext';
import ChatList from '../components/ChatList';
import '../Chat.css';

export default function ChatPage() {
  const { conversationId } = useParams();
  const { user } = useAuth();
  return <ChatWorkspace key={user?.id} conversationId={conversationId} />;
}

function ChatWorkspace({ conversationId }) {
  const [chat, setChat] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    setChat(null); setError('');
    if (!conversationId) return;
    if (!/^[1-9]\d*$/.test(conversationId || '')) { setError('Mã cuộc trò chuyện không hợp lệ.'); return; }
    getChat(conversationId).then(data => { if (!cancelled) setChat(data); })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [conversationId]);
  return <main className={`container chat-shell ${conversationId ? 'has-selection' : ''}`}>
    <ChatList activeId={conversationId} /><section className="chat-panel">
    {error && <p role="alert" className="chat-error">{error}</p>}
    {!chat && !error && <p className="chat-empty">{conversationId ? 'Đang tải cuộc trò chuyện…' : 'Chọn một cuộc trò chuyện để xem tin nhắn.'}</p>}
    {chat && <><header className="chat-header"><Link className="chat-back" to="/tin-nhan">← Danh sách</Link><h2>{chat.otherName || 'Thành viên'}</h2>
      <p><Link to={`/chi-tiet/${chat.postId}`}>{chat.postTitle}</Link></p>
      <Link to={`/bai-dang/${chat.postId}/claims/${chat.claimId}`}>Xem hồ sơ claim</Link>
      </header>{!chat.canSend && <p role="status" className="chat-readonly">{chat.readOnlyReason}</p>}
      <p className="chat-empty">Chưa có tin nhắn.</p></>}
    </section>
  </main>;
}
