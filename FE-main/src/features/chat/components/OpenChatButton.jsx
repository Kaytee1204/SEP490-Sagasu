import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { openPostChat } from '../api/chatApi';

export default function OpenChatButton({ postId }) {
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
  return <div><button type="button" className="btn btn-accent" disabled={busy} onClick={open}>
    {busy ? 'Đang mở…' : 'Nhắn tin'}</button>{error && <p role="alert">{error}</p>}</div>;
}
