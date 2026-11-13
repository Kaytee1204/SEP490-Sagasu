import { useEffect, useId, useRef } from 'react';
import { AlertCircle, Check, X } from 'lucide-react';
import './PostSubmitResultDialog.css';

export default function PostSubmitResultDialog({ post, error, onClose }) {
  const dialog = useRef(null);
  const titleId = useId();
  const descriptionId = useId();
  const success = Boolean(post);

  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);

  const message = success
    ? post.moderationStatus === 'VISIBLE'
      ? 'Tin đăng của bạn đã được hiển thị.'
      : 'Tin đăng đã được gửi và đang chờ xét duyệt.'
    : error;

  return <dialog ref={dialog} className={`post-submit-dialog ${success ? 'post-submit-success' : 'post-submit-failure'}`}
    role="alertdialog" aria-labelledby={titleId} aria-describedby={descriptionId}
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <button type="button" className="post-submit-close" aria-label="Đóng thông báo" onClick={onClose}><X size={20} /></button>
    <div className="post-submit-icon" aria-hidden="true">{success ? <Check size={30} /> : <AlertCircle size={30} />}</div>
    <h2 id={titleId}>{success ? 'Đăng tin thành công' : 'Đăng tin thất bại'}</h2>
    <p id={descriptionId}>{message}</p>
    <button type="button" className="btn btn-primary" autoFocus onClick={onClose}>{success ? 'Xem bài đăng' : 'Đóng'}</button>
  </dialog>;
}
