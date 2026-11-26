import '@/styles/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, ClipboardCheck, Flag, Package, UserRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import useDeadlineCountdown from '@/hooks/useDeadlineCountdown';
import { claimDate } from '@/features/claims/utils/claimUtils';
import { getChat } from '../api/chatApi';
import { useChat } from '@/features/chat/hooks/useChat';
import ChatList from '../components/ChatList';
import ChatThread from '../components/ChatThread';
import ChatItemPanel from '../components/ChatItemPanel';
import { getHandover } from '@/features/handover/api/handoverApi';
import ReportHandoverIssueDialog from '@/features/handover/components/ReportHandoverIssueDialog';
import HandoverIssueDeadline from '@/features/handover/components/HandoverIssueDeadline';
import { handoverIssueDeadline } from '@/features/handover/utils/issueForm';
import '../styles/Chat.css';

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
  const { revision } = useChat();
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
    {chat && <ChatConversation key={chat.claimId} chat={chat} />}
  </section>;
}

function ChatConversation({ chat }) {
  const { revision, online, refresh } = useChat();
  const [itemOpen, setItemOpen] = useState(false);
  const itemButton = useRef(null);
  const closeItem = () => { setItemOpen(false); itemButton.current?.focus(); };
  const [handover, setHandover] = useState(null);
  const [reportHandover, setReportHandover] = useState(null);
  const [submittedReportId, setSubmittedReportId] = useState(null);
  useEffect(() => {
    let cancelled = false;
    getHandover(chat.claimId).then(data => { if (!cancelled) setHandover(data); })
      .catch(() => { if (!cancelled) setHandover(null); });
    return () => { cancelled = true; };
  }, [chat.claimId, revision]);
  const state = handover?.appealWindow?.state;
  const deadline = handover?.appealWindow?.deadline;
  const timeLeft = useDeadlineCountdown(state === 'OPEN' ? deadline : null);
  const issueDeadline = handover?.issue ? null : handoverIssueDeadline(handover);
  const issueTimeLeft = useDeadlineCountdown(issueDeadline, { showDays: true });
  const canReportIssue = handover?.canReportIssue && (!issueDeadline || Boolean(issueTimeLeft));
  useEffect(() => {
    if (state !== 'OPEN' && state !== 'UNDER_REVIEW') return;
    const remaining = Date.parse(deadline) - Date.now();
    const timer = state === 'OPEN' && remaining > 0 ? window.setTimeout(refresh, remaining + 100) : undefined;
    const interval = window.setInterval(refresh, 30000);
    return () => { window.clearTimeout(timer); window.clearInterval(interval); };
  }, [state, deadline, refresh]);
  const pauseMessage = state === 'OPEN' && (timeLeft
    ? `Còn ${timeLeft} để các hồ sơ khác gửi tranh chấp. Chat chỉ xem trong thời gian này. Hết hạn lúc ${claimDate(deadline)}.`
    : 'Đang cập nhật quyền gửi tin nhắn.');
  return <>
    <header className="chat-header">
      <Link className="chat-back" to="/tin-nhan"><ArrowLeft size={16} aria-hidden="true" />Danh sách trò chuyện</Link>
      <div className="chat-header-main">
        <span className="chat-peer-avatar" aria-hidden="true"><UserRound size={23} /></span>
        <div className="chat-peer-info"><h2 title={chat.otherName}>{chat.otherName || 'Thành viên'}</h2>
          <p title={chat.postTitle}>{chat.postTitle}</p>
          {!online && <small role="status" className="chat-muted">Đang kết nối lại…</small>}</div>
        {canReportIssue && <button className="chat-header-action chat-header-report" type="button" onClick={() => setReportHandover(handover)}>
          <Flag size={16} aria-hidden="true" />Báo sự cố trao trả
        </button>}
      </div>
      <div className="chat-header-actions">
        <button ref={itemButton} type="button" className="chat-header-action" aria-expanded={itemOpen} aria-controls={itemOpen ? 'chat-item-panel' : undefined}
          onClick={() => setItemOpen(value => !value)}><Package size={17} aria-hidden="true" />{itemOpen ? 'Ẩn thông tin món đồ' : 'Xem thông tin món đồ'}</button>
        <Link className="chat-header-action" to={`/bai-dang/${chat.postId}/claims/${chat.claimId}#handover`}><ClipboardCheck size={17} aria-hidden="true" />Theo dõi nhận đồ</Link>
      </div>
    </header>
    <div className={`chat-conversation-body ${itemOpen ? 'with-item' : ''}`}>
      <div className="chat-thread-area">
        {issueDeadline && <div className="chat-issue-actions">
          <HandoverIssueDeadline deadline={issueDeadline} timeLeft={issueTimeLeft} />
        </div>}
        {submittedReportId && <p role="status" className="chat-issue-actions">
          Đã gửi báo cáo <Link to={`/bao-cao/${submittedReportId}`}>#{submittedReportId}</Link>
        </p>}
        <ChatThread chat={chat} pauseMessage={pauseMessage} />
        {reportHandover && <ReportHandoverIssueDialog claim={{ id: chat.claimId, postTitle: chat.postTitle }}
          handover={reportHandover} onClose={() => setReportHandover(null)}
          onReported={data => {
            setHandover(data);
            setSubmittedReportId(data.submittedReportId);
            setReportHandover(null);
          }} />}
      </div>
      {itemOpen && <ChatItemPanel chat={chat} onClose={closeItem} />}
    </div>
  </>;
}
