import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useChat } from '../ChatProvider';
import { getMessages, sendMessage, sendAttachments, markChatRead } from '../api/chatApi';
import ChatAttachments from './ChatAttachments';

const merge = (old, incoming) => [...new Map([...old, ...incoming].map(row => [row.id, row])).values()].sort((a, b) => a.sequence - b.sequence);

export default function ChatThread({ chat }) {
  const { user } = useAuth();
  const { revision, refresh, limits } = useChat();
  const [rows, setRows] = useState([]);
  const [before, setBefore] = useState(null);
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [olderBusy, setOlderBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [files, setFiles] = useState([]);
  const viewport = useRef(null);
  const pinBottom = useRef(true);
  const anchor = useRef(null);
  const retry = useRef(null);
  const synchronizer = useRef(null);
  const syncedThrough = useRef(0);
  const requestedRead = useRef(chat.lastReadSequence);
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
            syncedThrough.current = sequence;
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
  useEffect(() => {
    requestedRead.current = Math.max(requestedRead.current, chat.lastReadSequence);
    const element = viewport.current;
    if (!element) return;
    let timer, disposed = false;
    function scheduleRead() {
      clearTimeout(timer);
      timer = setTimeout(async () => {
        if (document.visibilityState !== 'visible' || !document.hasFocus() || document.querySelector('dialog[open]')) return;
        const bounds = element.getBoundingClientRect();
        if (!bounds.height) return;
        const visibleTop = Math.max(bounds.top, 0);
        const visibleBottom = Math.min(bounds.bottom, window.innerHeight);
        if (visibleBottom <= visibleTop) return;
        let visible = requestedRead.current;
        for (const bubble of element.querySelectorAll('[data-sequence]')) {
          const box = bubble.getBoundingClientRect();
          const sequence = Number(bubble.dataset.sequence);
          if (bubble.dataset.sender !== String(user.id) && box.bottom <= visibleBottom && box.bottom > visibleTop
              && sequence <= syncedThrough.current) visible = Math.max(visible, sequence);
        }
        if (visible <= requestedRead.current) return;
        requestedRead.current = visible;
        try {
          const saved = await markChatRead(chat.id, visible);
          if (!disposed) { requestedRead.current = Math.max(requestedRead.current, saved); refresh(); }
        } catch {
          if (requestedRead.current === visible) requestedRead.current = chat.lastReadSequence;
        }
      }, 300);
    }
    element.addEventListener('scroll', scheduleRead);
    document.addEventListener('visibilitychange', scheduleRead);
    document.addEventListener('focusin', scheduleRead);
    window.addEventListener('focus', scheduleRead);
    window.addEventListener('scroll', scheduleRead, { passive: true });
    window.addEventListener('resize', scheduleRead);
    scheduleRead();
    return () => {
      disposed = true; clearTimeout(timer); element.removeEventListener('scroll', scheduleRead);
      document.removeEventListener('visibilitychange', scheduleRead); document.removeEventListener('focusin', scheduleRead);
      window.removeEventListener('focus', scheduleRead);
      window.removeEventListener('scroll', scheduleRead); window.removeEventListener('resize', scheduleRead);
    };
  }, [rows, chat.id, chat.lastReadSequence, user.id]);
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
    if (busy || (!text && !files.length) || !chat.canSend) return;
    const request = retry.current?.body === (text || null) && retry.current?.files === files ? retry.current
      : { clientMessageId: crypto.randomUUID(), body: text || null, files };
    retry.current = request; setBusy(true); setError('');
    try {
      const payload = { body: request.body, clientMessageId: request.clientMessageId };
      const message = files.length ? await sendAttachments(chat.id, payload, files) : await sendMessage(chat.id, payload);
      pinBottom.current = true; setRows(old => merge(old, [message]));
      setBody(''); setFiles([]); retry.current = null; refresh();
    } catch (err) { setError(err.message); refresh(); }
    finally { setBusy(false); }
  }
  return <>
    <div className="chat-messages" ref={viewport} onScroll={() => {
      const element = viewport.current; pinBottom.current = element.scrollHeight - element.clientHeight - element.scrollTop < 60;
    }} aria-label="Tin nhắn trong cuộc trò chuyện">
      {before && <button type="button" className="chat-older" onClick={older} disabled={olderBusy}>{olderBusy ? 'Đang tải…' : 'Xem tin cũ hơn'}</button>}
      {!rows.length && <p className="chat-empty">{loaded ? 'Chưa có tin nhắn.' : 'Đang tải tin nhắn…'}</p>}
      {rows.map(row => <article key={row.id} data-sequence={row.sequence} data-sender={row.senderId} className={`chat-bubble ${String(row.senderId) === String(user.id) ? 'mine' : ''}`}>
        {row.body && <p>{row.body}</p>}
        {!!row.attachments?.length && <ChatAttachments chatId={chat.id} message={row} />}
        <small>{new Date(row.createdAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}</small>
        {String(row.senderId) === String(user.id) && row.sequence <= chat.otherReadSequence && <small>Đã xem</small>}
      </article>)}
    </div>
    {error && <p role="alert" className="chat-error">{error}</p>}
    {chat.canSend ? <><div className="chat-selected-files">{files.map((file, index) => <span key={`${file.name}:${index}`}>{file.name}
      <button type="button" disabled={busy} aria-label={`Bỏ tệp ${file.name}`} onClick={() => { setFiles(old => old.filter((_, n) => n !== index)); retry.current = null; }}>×</button></span>)}</div>
    <form className="chat-composer" onSubmit={send}>
      <label className="sr-only" htmlFor="chat-message">Nội dung tin nhắn</label>
      <label className="chat-attach-button" title={`Tối đa ${limits.attachmentMaxCount} tệp JPG/PNG/PDF`}>
        <span>Đính kèm</span><input type="file" multiple accept=".jpg,.jpeg,.png,.pdf" disabled={busy} onChange={event => {
          const picked = [...event.target.files]; event.target.value = '';
          const next = [...files, ...picked];
          const valid = next.length <= limits.attachmentMaxCount && next.every(file => {
            const name = file.name.toLowerCase();
            return file.size > 0 && (/\.(jpe?g|png)$/.test(name) ? file.size <= limits.imageMaxBytes : name.endsWith('.pdf') && file.size <= limits.pdfMaxBytes);
          }) && next.reduce((sum, file) => sum + file.size, 0) <= limits.totalMaxBytes;
          if (!valid) { setError(`Tối đa ${limits.attachmentMaxCount} tệp; ảnh ${limits.imageMaxBytes / 1048576} MB, PDF ${limits.pdfMaxBytes / 1048576} MB, tổng ${limits.totalMaxBytes / 1048576} MB.`); return; }
          setFiles(next); retry.current = null; setError('');
        }} />
      </label>
      <textarea id="chat-message" maxLength={limits.messageMaxLength} disabled={busy} placeholder="Nhập tin nhắn…" value={body} onChange={event => setBody(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); send(); }
      }} />
      <button className="btn btn-accent" type="submit" disabled={busy || (!body.trim() && !files.length)}>{busy ? 'Đang gửi…' : 'Gửi'}</button>
    </form></> : <p role="status" className="chat-readonly">{chat.readOnlyReason}</p>}
  </>;
}
