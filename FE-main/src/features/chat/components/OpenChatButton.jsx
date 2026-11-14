import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MessageCircle } from 'lucide-react';
import { openPostChat } from '../api/chatApi';

export default function OpenChatButton({ postId, label = 'Nhắn tin', className = 'btn btn-accent' }) {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function open() {
    if (busy) return;
    setBusy(true); setError('');
    try { const chat = await openPostChat(postId); navigate(`/tin-nhan/${chat.id}`); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <div><button type="button" className={className} disabled={busy} onClick={open}>
    <MessageCircle size={18} aria-hidden="true" />{busy ? 'Đang mở trò chuyện…' : label}</button>{error && <p role="alert">{error}</p>}</div>;
}
