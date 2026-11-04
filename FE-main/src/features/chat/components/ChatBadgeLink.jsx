import { Link } from 'react-router-dom';
import { MessageCircle } from 'lucide-react';
import { useChat } from '../ChatProvider';

export default function ChatBadgeLink() {
  const { unread } = useChat();
  return <Link to="/tin-nhan" className="nav-icon-btn" aria-label={`Tin nhắn${unread ? `: ${unread} tin chưa đọc` : ''}`}>
    <MessageCircle size={20} />{unread > 0 && <span className="nav-badge">{unread > 99 ? '99+' : unread}</span>}
  </Link>;
}
