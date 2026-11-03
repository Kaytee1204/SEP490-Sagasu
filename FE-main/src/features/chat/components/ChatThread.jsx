import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useChat } from '../ChatProvider';
import { getMessages, sendMessage } from '../api/chatApi';

const merge = (old, incoming) => [...new Map([...old, ...incoming].map(row => [row.id, row])).values()].sort((a, b) => a.sequence - b.sequence);

export default function ChatThread({ chat }) {
  const { user } = useAuth();
  const { revision, refresh } = useChat();
  const [rows, setRows] = useState([]);
  const [before, setBefore] = useState(null);
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [olderBusy, setOlderBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const viewport = useRef(null);
  const pinBottom = useRef(true);
  const anchor = useRef(null);
  const retry = useRef(null);
  const synchronizer = useRef(null);
  useEffect(() => {
    let disposed = false, running = false, pending = false, initialized = false, sequence = 0;
    async function sync() {
      pending = true;
      if (running) return;
      running = true;
      try {
        while (pending && !disposed) {
          pending = false;
          let more;
          do {
            const page = await getMessages(chat.id, initialized ? { afterSequence: String(sequence) } : {});
            if (disposed) return;
            setRows(old => merge(old, page.items));
            sequence = Math.max(sequence, ...page.items.map(row => row.sequence));
            more = initialized && page.hasMore;
            if (!initialized) { setBefore(page.nextBefore); initialized = true; }
            setLoaded(true);
          } while (more && !disposed);
        }
      } catch (err) { if (!disposed) setError(err.message); }
      finally { running = false; }
    }
    synchronizer.current = sync; sync();
    return () => { disposed = true; synchronizer.current = null; };
  }, [chat.id]);
  useEffect(() => { synchronizer.current?.(); }, [revision]);
  useLayoutEffect(() => {
    const element = viewport.current;
    if (!element) return;
    if (anchor.current !== null) { element.scrollTop += element.scrollHeight - anchor.current; anchor.current = null; }
    else if (pinBottom.current) element.scrollTop = element.scrollHeight;
  }, [rows]);
  async function older() {
    if (!before || olderBusy) return;
    setOlderBusy(true); setError('');
    try {
      const page = await getMessages(chat.id, { beforeSequence: String(before) });
      anchor.current = viewport.current.scrollHeight;
      setRows(old => merge(old, page.items)); setBefore(page.nextBefore);
    } catch (err) { setError(err.message); }
    finally { setOlderBusy(false); }
  }
  async function send(event) {
    event?.preventDefault();
    const text = body.trim();
    if (busy || !text || !chat.canSend) return;
    const request = retry.current?.body === text ? retry.current : { clientMessageId: crypto.randomUUID(), body: text };
    retry.current = request; setBusy(true); setError('');
    try {
      const message = await sendMessage(chat.id, request);
      pinBottom.current = true; setRows(old => merge(old, [message]));
      setBody(''); retry.current = null; refresh();
    } catch (err) { setError(err.message); refresh(); }
    finally { setBusy(false); }
  }
  return <>
    <div className="chat-messages" ref={viewport} onScroll={() => {
      const element = viewport.current; pinBottom.current = element.scrollHeight - element.clientHeight - element.scrollTop < 60;
    }} aria-label="Tin nhắn trong cuộc trò chuyện">
      {before && <button type="button" className="chat-older" onClick={older} disabled={olderBusy}>{olderBusy ? 'Đang tải…' : 'Xem tin cũ hơn'}</button>}
      {!rows.length && <p className="chat-empty">{loaded ? 'Chưa có tin nhắn.' : 'Đang tải tin nhắn…'}</p>}
      {rows.map(row => <article key={row.id} data-sequence={row.sequence} className={`chat-bubble ${String(row.senderId) === String(user.id) ? 'mine' : ''}`}>
        {row.body && <p>{row.body}</p>}
        <small>{new Date(row.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}</small>
      </article>)}
    </div>
    {error && <p role="alert" className="chat-error">{error}</p>}
    {chat.canSend ? <form className="chat-composer" onSubmit={send}>
      <label className="sr-only" htmlFor="chat-message">Nội dung tin nhắn</label>
      <textarea id="chat-message" maxLength={4000} disabled={busy} placeholder="Nhập tin nhắn…" value={body} onChange={event => setBody(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); send(); }
      }} />
      <button className="btn btn-accent" type="submit" disabled={busy || !body.trim()}>{busy ? 'Đang gửi…' : 'Gửi'}</button>
    </form> : <p role="status" className="chat-readonly">{chat.readOnlyReason}</p>}
  </>;
}
