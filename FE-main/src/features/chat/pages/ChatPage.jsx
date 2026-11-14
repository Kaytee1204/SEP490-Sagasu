import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ClipboardCheck, Package, UserRound } from 'lucide-react';
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
    {chat && <><header className="chat-header">
      <Link className="chat-back" to="/tin-nhan"><ArrowLeft size={16} aria-hidden="true" />Danh sách trò chuyện</Link>
      <div className="chat-header-main">
        <span className="chat-peer-avatar" aria-hidden="true"><UserRound size={23} /></span>
        <div className="chat-peer-info"><h2 title={chat.otherName}>{chat.otherName || 'Thành viên'}</h2>
          <p title={chat.postTitle}>{chat.postTitle}</p>
          {!online && <small role="status" className="chat-muted">Đang kết nối lại…</small>}</div>
        <div className="chat-header-report"><ReportButton type="USER" id={chat.otherAccountId} targetLabel={chat.otherName} label="Báo cáo tài khoản"/></div>
      </div>
      <div className="chat-header-actions">
        <button ref={itemButton} type="button" className="chat-header-action" aria-expanded={itemOpen} aria-controls={itemOpen ? 'chat-item-panel' : undefined}
          onClick={() => setItemOpen(value => !value)}><Package size={17} aria-hidden="true" />{itemOpen ? 'Ẩn thông tin món đồ' : 'Xem thông tin món đồ'}</button>
        <Link className="chat-header-action" to={`/bai-dang/${chat.postId}/claims/${chat.claimId}#handover`}><ClipboardCheck size={17} aria-hidden="true" />Theo dõi nhận đồ</Link>
      </div>
    </header><div className={`chat-conversation-body ${itemOpen ? 'with-item' : ''}`}>
      <div className="chat-thread-area"><ChatThread chat={chat} /></div>
      {itemOpen && <ChatItemPanel key={`${chat.id}:${chat.claimId}`} chat={chat} onClose={closeItem} />}
    </div></>}
  </section>;
}
