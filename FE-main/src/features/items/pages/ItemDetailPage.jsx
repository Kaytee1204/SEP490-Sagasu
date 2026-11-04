import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ChevronRight, UserRound, MapPin, SearchCheck, ArrowRight } from 'lucide-react';
import OfficeReceiptBadge from '../components/OfficeReceiptBadge';
import { getPostDetail } from '../api/postApi';
import { getLatestClaimOnPost } from '@/features/claims/api/claimApi';
import { claimDetailPath, canSubmitPostClaim, postClaimType } from '@/features/claims/claimUtils';
import ItemGallery from '../components/detail/ItemGallery';
import ItemInfoSection from '../components/detail/ItemInfoSection';
import ItemCharacteristics from '../components/detail/ItemCharacteristics';
import PrivateIdentifiersEditor from '../components/detail/PrivateIdentifiersEditor';
import OfficeHandoverAction from '../components/detail/OfficeHandoverAction';
import SmartMatchModal from '../components/detail/SmartMatchModal';
import { useAuth } from '@/context/AuthContext';
import './ItemDetailPage.css';
import OpenChatButton from '@/features/chat/components/OpenChatButton';

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
  const { user } = useAuth();
  const { id } = useParams();
  const [attempt, setAttempt] = useState(0);
  return <PostDetail key={`${user?.id}:${id}:${attempt}`} id={id} onRetry={() => setAttempt(value => value + 1)} />;
}

function PostDetail({ id, onRetry }) {
  const { isAdmin, isManager, isApprover } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [result, setResult] = useState(null);
  const [activeImage, setActiveImage] = useState(0);
  const [showSmartMatch, setShowSmartMatch] = useState(false);
  const validId = /^[1-9]\d*$/.test(id);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document.documentElement.scrollTop = 0;
    document.body.scrollTop = 0;
  }, [id]);

  const handleGoBack = () => {
    if (location.state?.from) {
      navigate(location.state.from);
    } else if (window.history.length > 1) {
      navigate(-1);
    } else {
      let fallback = '/tim-kiem';
      try {
        fallback = sessionStorage.getItem('sagasu_search_last_url') || '/tim-kiem';
      } catch { /* ignore */ }
      navigate(fallback);
    }
  };

  useEffect(() => {
    let cancelled = false;
    if (!validId) return;
    getPostDetail(id)
      .then(async post => {
        const myClaim = post.postType === 'FOUND' && !post.canEditOwnPost
          ? await getLatestClaimOnPost(id).catch(() => null) : null;
        if (!cancelled) setResult({ id, post, myClaim });
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
        <button type="button" className="post-detail-back-btn" onClick={handleGoBack}>
          <ArrowLeft size={15} aria-hidden="true" />
          <span>Quay lại danh sách</span>
        </button>
        <Link to="/">Trang chủ</Link><ChevronRight size={14} />
        <span>{post.title}</span>
      </nav>
      <div className="container post-detail-grid">
        <ItemGallery images={images} title={item.title} type={item.type}
          activeImage={activeImage} onSelectImage={setActiveImage} />
        <section className="post-detail-info" aria-label="Thông tin bài đăng">
          <ItemInfoSection item={item} />
          <div className="flex flex-wrap items-center gap-3">
            <p className="flex items-center gap-2 text-sm text-slate-600">
              <UserRound size={17} aria-hidden="true" />Người đăng: <strong className="text-slate-800">{post.authorName || 'Chưa cập nhật tên'}</strong>
            </p>
            <OfficeReceiptBadge receipt={post.officeReceipt} />
          </div>

          {/* Action & Smart Match Buttons */}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', marginTop: '14px', marginBottom: '14px' }}>
            {(post.canEditOwnPost || isAdmin || isManager) && (
            <button
              type="button"
              onClick={() => setShowSmartMatch(true)}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '9.5px 18px',
                borderRadius: '8px',
                border: '1px solid #0d9488',
                background: '#f0fdfa',
                color: '#0f766e',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: 'pointer',
                boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04)',
                transition: 'background 150ms',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = '#e6fffa')}
              onMouseLeave={(e) => (e.currentTarget.style.background = '#f0fdfa')}
            >
              <SearchCheck size={18} />
              <span>Tìm đồ vật trùng khớp</span>
            </button>
            )}

            {!post.canEditOwnPost && (current.myClaim || canSubmitPostClaim(post)) && (
              <Link
                to={current.myClaim ? claimDetailPath(post.id, current.myClaim.id) : `/xac-minh/${post.id}`}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '9.5px 18px',
                  borderRadius: '8px',
                  border: '1px solid #2563eb',
                  background: '#2563eb',
                  color: '#ffffff',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  textDecoration: 'none',
                  boxShadow: '0 1px 2px rgba(0, 0, 0, 0.04)',
                }}
              >
                <span>{current.myClaim ? 'Xem claim đã gửi' : postClaimType(post) === 'OFFICE' ? 'Gửi yêu cầu nhận đồ cho phòng ban' : 'Xác minh nhận đồ (Claim)'}</span>
                <ArrowRight size={15} />
              </Link>
            )}
          </div>

          {post.postType === 'FOUND' && (post.canEditOwnPost || (isApprover && post.officeReceipt)) && <Link className="btn btn-accent" to={`/bai-dang/${post.id}/claims`}>Xem claim nhận đồ trên bài này</Link>}
          <OfficeHandoverAction post={post} onTransferred={updated => setResult(current => ({ ...current, post: updated }))} />
          {post.postType === 'FOUND' && post.custodyMode === 'P2P' && !post.officeReceipt
            && post.moderationStatus === 'VISIBLE'
            && post.status === 'RESERVED' && (post.canEditOwnPost || current.myClaim?.status === 'APPROVED')
            && <OpenChatButton postId={post.id} />}

          {post.officeReceipt && <section className="space-y-3 rounded-2xl border border-blue-200 bg-blue-50/60 p-5" aria-label="Thông tin nhà trường tiếp nhận">
            <h2 className="flex items-center gap-2 font-semibold text-blue-950"><MapPin size={19} />{post.officeReceipt.status === 'RETURNED' ? 'Tài sản đã được trao trả' : 'Tài sản đang được nhà trường lưu giữ'}</h2>
            <dl className="space-y-3 text-sm leading-6"><div><dt className="text-slate-600">{post.officeReceipt.status === 'RETURNED' ? 'Nơi lưu trữ trước khi trao trả' : 'Nơi lưu trữ'}</dt><dd className="font-semibold text-slate-900">{post.officeReceipt.storageLocationName || 'Liên hệ văn phòng tiếp nhận'}</dd></div><div><dt className="text-slate-600">Cán bộ tiếp nhận</dt><dd className="font-semibold text-slate-900">{post.officeReceipt.receivedBy || 'Chưa cập nhật tên'}</dd></div><div><dt className="text-slate-600">Thời điểm tiếp nhận</dt><dd className="font-semibold text-slate-900">{new Date(post.officeReceipt.receivedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</dd></div></dl>
          </section>}
          {post.canEditOwnPost && <Link className="btn btn-ghost" to="/tin-dang-cua-toi">Quản lý bài đăng của tôi</Link>}
          {post.moderationStatus === 'PENDING' && <p role="status">Bài đăng đang chờ Approver duyệt. Chỉ bạn xem được nội dung này.</p>}
          <div><strong>Trạng thái: </strong>{STATUS_LABELS[post.status] || post.status}</div>
          {post.postType === 'FOUND' && !post.officeReceipt && <div>
            <strong>Hình thức trao trả: </strong>
            {post.custodyMode === 'OFFICE'
              ? 'Đã gửi yêu cầu giao cho bên tiếp nhận; chưa xác nhận đã bàn giao đồ.'
              : 'Người nhặt chọn tự giữ để trao trả.'}
          </div>}
          <ItemCharacteristics description={post.description} />
          {post.canEditOwnPost && <PrivateIdentifiersEditor key={post.id} postId={post.id} />}
        </section>
      </div>

      {/* Smart Match Modal */}
      <SmartMatchModal
        isOpen={showSmartMatch}
        onClose={() => setShowSmartMatch(false)}
        post={post}
      />
    </main>
  );
}
