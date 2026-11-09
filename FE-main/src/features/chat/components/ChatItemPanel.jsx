import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import { getPostDetail } from '@/features/items/api/postApi';
import { getHandover } from '@/features/handover/handoverApi';
import { claimDate, claimDetailPath, evidenceUrl } from '@/features/claims/claimUtils';
import { useChat } from '../ChatProvider';

const PROGRESS = { NOT_RECORDED: 'Chưa ghi nhận giao đồ', WAITING_CONFIRMATION: 'Chờ người nhận xác nhận', COMPLETED: 'Đã nhận đồ', DISPUTED: 'Có sự cố — chờ Manager xử lý' };
const POST_STATUS = { OPEN: 'Đang chờ nhận', RESERVED: 'Đang xử lý nhận đồ', RETURNED: 'Đã trao trả', CLOSED: 'Đã đóng' };

export default function ChatItemPanel({ chat, onClose }) {
  const { revision } = useChat();
  const closeButton = useRef(null);
  const [post, setPost] = useState(null);
  const [handover, setHandover] = useState(null);
  const [postError, setPostError] = useState('');
  const [handoverError, setHandoverError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { closeButton.current?.focus(); }, []);
  useEffect(() => {
    let cancelled = false;
    getPostDetail(chat.postId).then(value => { if (!cancelled) { setPost(value); setPostError(''); } })
      .catch(error => { if (!cancelled) setPostError(error.message || 'Không tải được bài đăng.'); });
    getHandover(chat.claimId).then(value => { if (!cancelled) { setHandover(value); setHandoverError(''); } })
      .catch(error => { if (!cancelled) setHandoverError(error.message || 'Không tải được tiến trình nhận đồ.'); });
    return () => { cancelled = true; };
  }, [chat.postId, chat.claimId, revision, attempt]);
  const cover = evidenceUrl(post?.images?.[0]?.url);
  return <aside id="chat-item-panel" className="chat-item-panel" aria-label="Thông tin món đồ"
    onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); onClose(); } }}>
    <header><h3>Thông tin món đồ</h3><button ref={closeButton} type="button" aria-label="Đóng thông tin món đồ" onClick={onClose}><X size={20} /></button></header>
    <div className="chat-item-content">
      {cover && <img className="chat-item-cover" src={cover} alt={post.title} />}
      <h4>{post?.title || chat.postTitle}</h4>
      {postError && <p role="alert" className="chat-error">{postError}</p>}
      {!post && !postError && <p role="status">Đang tải thông tin món đồ…</p>}
      {post && <><span className="chat-item-badge">{POST_STATUS[post.status] || post.status}</span>
        <dl><dt>Danh mục</dt><dd>{post.categoryName}</dd><dt>Địa điểm</dt><dd>{post.locationText || 'Chưa cung cấp'}</dd>
          {post.eventAt && <><dt>Thời điểm {post.postType === 'FOUND' ? 'nhặt được' : 'thất lạc'}</dt><dd>{claimDate(post.eventAt)}</dd></>}</dl>
        {post.description && <details><summary>Mô tả món đồ</summary><p className="claim-prose">{post.description}</p></details>}
      </>}
      <Link className="btn btn-ghost" to={`/chi-tiet/${chat.postId}`}>Xem bài đăng đầy đủ</Link>
      <section className="chat-item-progress"><h4>Tiến trình nhận đồ</h4>
        {handoverError ? <p role="alert">{handoverError}</p> : handover ? <>
          <p className="chat-item-badge">{PROGRESS[handover.status] || handover.status}</p>
          {handover.recordedAt && <p>Đã ghi nhận giao: {claimDate(handover.recordedAt)}</p>}
          {handover.confirmedAt && <p>Đã xác nhận nhận: {claimDate(handover.confirmedAt)}</p>}
          {handover.status === 'DISPUTED' && <p>Hai bên vẫn có thể trao đổi; bàn giao đang chờ xử lý sự cố.</p>}
        </> : <p role="status">Đang tải tiến trình…</p>}
        <Link className="btn btn-primary" to={`${claimDetailPath(chat.postId, chat.claimId)}#handover`}>Mở hồ sơ nhận đồ</Link>
      </section>
      {(postError || handoverError) && <button type="button" className="btn btn-ghost" onClick={() => setAttempt(value => value + 1)}>Thử tải lại</button>}
    </div>
  </aside>;
}
