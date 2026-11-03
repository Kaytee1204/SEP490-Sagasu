import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getChat } from '../api/chatApi';

export default function ChatPage() {
  const { conversationId } = useParams();
  const [chat, setChat] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    setChat(null); setError('');
    if (!/^[1-9]\d*$/.test(conversationId || '')) { setError('Mã cuộc trò chuyện không hợp lệ.'); return; }
    getChat(conversationId).then(data => { if (!cancelled) setChat(data); })
      .catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [conversationId]);
  return <main className="container" style={{ padding: '32px 16px', minHeight: '60vh' }}>
    <h1>Tin nhắn</h1>{error && <p role="alert">{error}</p>}
    {!chat && !error && <p>Đang tải cuộc trò chuyện…</p>}
    {chat && <section><h2>{chat.otherName || 'Thành viên'}</h2>
      <p><Link to={`/chi-tiet/${chat.postId}`}>{chat.postTitle}</Link></p>
      <Link to={`/bai-dang/${chat.postId}/claims/${chat.claimId}`}>Xem hồ sơ claim</Link>
      {!chat.canSend && <p role="status">{chat.readOnlyReason}</p>}
      <p>Chưa có tin nhắn.</p></section>}
  </main>;
}
