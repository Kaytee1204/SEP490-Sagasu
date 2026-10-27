import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { getPostDetail } from '../api/postApi';
import ItemGallery from '../components/detail/ItemGallery';
import ItemInfoSection from '../components/detail/ItemInfoSection';
import ItemCharacteristics from '../components/detail/ItemCharacteristics';
import PrivateIdentifiersEditor from '../components/detail/PrivateIdentifiersEditor';
import './ItemDetailPage.css';

const STATUS_LABELS = {
  OPEN: 'Đang tìm / chờ nhận',
  RESERVED: 'Đang xử lý nhận đồ',
  RETURNED: 'Đã trao trả',
  CLOSED: 'Đã đóng',
};

function imageUrl(value) {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

export default function ItemDetailPage() {
  const { id } = useParams();
  const [attempt, setAttempt] = useState(0);
  return <PostDetail key={`${id}:${attempt}`} id={id} onRetry={() => setAttempt(value => value + 1)} />;
}

function PostDetail({ id, onRetry }) {
  const [result, setResult] = useState(null);
  const [activeImage, setActiveImage] = useState(0);
  const validId = /^[1-9]\d*$/.test(id);

  useEffect(() => {
    let cancelled = false;
    if (!validId) return;
    getPostDetail(id)
      .then(post => {
        if (!cancelled) setResult({ id, post });
      })
      .catch(error => {
        if (!cancelled) setResult({ id, error: error.message || 'Không tải được bài đăng.' });
      });
    return () => { cancelled = true; };
  }, [id, validId]);

  const current = validId ? result : { error: 'Mã bài đăng không hợp lệ.' };
  if (!current || current.error) {
    return (
      <main className="container post-detail-message" aria-live="polite">
        <h1>{current?.error ? 'Không thể xem bài đăng' : 'Đang tải bài đăng…'}</h1>
        {current?.error && <>
          <p role="alert">{current.error}</p>
          <button className="btn btn-primary" onClick={onRetry}>Thử lại</button>
        </>}
        <Link to="/">Về trang chủ</Link>
      </main>
    );
  }

  const post = current.post;
  const event = new Date(post.eventAt);
  const item = {
    id: post.id,
    title: post.title,
    type: post.postType === 'LOST' ? 'lost' : 'found',
    category: post.categoryName,
    location: post.locationText,
    date: event.toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }),
    time: event.toLocaleTimeString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' }),
  };
  const images = (post.images || []).map(image => imageUrl(image.url)).filter(Boolean);

  return (
    <main className="post-detail-page">
      <nav className="container post-detail-breadcrumb" aria-label="Điều hướng">
        <Link to="/">Trang chủ</Link><ChevronRight size={14} />
        <span>{post.title}</span>
      </nav>
      <div className="container post-detail-grid">
        <ItemGallery images={images} title={item.title} type={item.type}
          activeImage={activeImage} onSelectImage={setActiveImage} />
        <section className="post-detail-info" aria-label="Thông tin bài đăng">
          <ItemInfoSection item={item} />
          {post.moderationStatus === 'PENDING' && <p role="status">Bài đăng đang chờ Approver duyệt. Chỉ bạn xem được nội dung này.</p>}
          <div><strong>Trạng thái: </strong>{STATUS_LABELS[post.status] || post.status}</div>
          {post.postType === 'FOUND' && <div>
            <strong>Hình thức trao trả: </strong>
            {post.custodyMode === 'OFFICE'
              ? 'Đã chọn bàn giao cho văn phòng; cần xác nhận tiếp nhận.'
              : 'Người nhặt chọn tự giữ để trao trả.'}
          </div>}
          <ItemCharacteristics description={post.description} />
          {post.canEditOwnPost && <PrivateIdentifiersEditor key={post.id} postId={post.id} />}
        </section>
      </div>
    </main>
  );
}
