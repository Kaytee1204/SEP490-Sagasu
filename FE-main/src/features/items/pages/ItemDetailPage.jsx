import { postClaimsPath } from '@/features/claims/claimUtils';
import '@/features/workspace/Workspace.css';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, ChevronRight, CircleCheck, ClipboardList, MapPin, Pencil, SearchCheck, UserRound } from 'lucide-react';
import OfficeReceiptBadge from '../components/OfficeReceiptBadge';
import { getPostDetail } from '../api/postApi';
import { EditPostDialog } from './EditPostPage';
import { getLatestClaimOnPost } from '@/features/claims/api/claimApi';
import { claimDetailPath, canSubmitPostClaim, postClaimType } from '@/features/claims/claimUtils';
import ItemGallery from '../components/detail/ItemGallery';
import ItemInfoSection from '../components/detail/ItemInfoSection';
import ItemCharacteristics from '../components/detail/ItemCharacteristics';
import OfficeHandoverAction from '../components/detail/OfficeHandoverAction';
import SmartMatchModal from '../components/detail/SmartMatchModal';
import { useAuth } from '@/context/AuthContext';
import './ItemDetailPage.css';
import OpenChatButton from '@/features/chat/components/OpenChatButton';
import ReportButton from '@/features/reports/ReportButton';

const STATUS_LABELS = {
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
  const [editOpen, setEditOpen] = useState(false);
  const [editNotice, setEditNotice] = useState('');
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
        const myClaim = post.postType === 'FOUND' && !post.isOwner && !post.canEditOwnPost
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
  const canUseSmartMatch = post.postType === 'LOST' && (post.canEditOwnPost || isAdmin || isManager);
  const isOwnPost = post.isOwner || post.canEditOwnPost;
  const isFinished = ['RETURNED', 'CLOSED'].includes(post.status);
  const canViewClaims = post.postType === 'FOUND' && (isOwnPost || (isApprover && post.officeReceipt));
  const canChat = post.postType === 'FOUND' && post.custodyMode === 'P2P' && !post.officeReceipt
    && post.moderationStatus === 'VISIBLE' && post.status === 'RESERVED'
    && (post.canEditOwnPost || current.myClaim?.status === 'APPROVED');
  const openStatusLabel = post.postType === 'LOST' ? 'Đang tìm đồ' : 'Đang chờ chủ sở hữu';
  const statusLabel = post.status === 'OPEN'
    ? openStatusLabel
    : STATUS_LABELS[post.status] || post.status;

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
        <div className="post-detail-media">
          <ItemGallery images={images} title={item.title}
            activeImage={activeImage} onSelectImage={setActiveImage} />
          <ItemCharacteristics description={post.description} />
        </div>
        <section className="post-detail-info" aria-label="Thông tin bài đăng">
          <div className="post-detail-card post-detail-summary">
            <ItemInfoSection item={item} />
            <div className="post-detail-author">
              <span className="post-detail-author-icon"><UserRound size={19} aria-hidden="true" /></span>
              <div><span>Người đăng</span><strong>{post.authorName || 'Chưa cập nhật tên'}</strong></div>
              <OfficeReceiptBadge receipt={post.officeReceipt} />
            </div>

            <div className="post-detail-state" data-status={post.status}>
              <span>{post.status === 'RETURNED' && <CircleCheck size={17} aria-hidden="true" />}{statusLabel}</span>
              {isFinished && <p>{post.status === 'RETURNED' ? 'Món đồ đã được trao trả.' : 'Bài đăng đã ngừng nhận yêu cầu mới.'}</p>}
              {post.postType === 'FOUND' && !post.officeReceipt && !isFinished && <p>
                {post.custodyMode === 'OFFICE'
                  ? 'Đang chờ văn phòng xác nhận tiếp nhận đồ.'
                  : 'Người nhặt đang giữ đồ và trực tiếp trao trả.'}
              </p>}
            </div>

            {post.moderationStatus === 'PENDING' && <p className="post-detail-notice" role="status">Bài đăng đang chờ Approver duyệt. Chỉ bạn xem được nội dung này.</p>}
            {editNotice && <p className="post-detail-notice" role="status">{editNotice}</p>}

            <div className="post-detail-actions">
              {canUseSmartMatch && (
                <button type="button" className="btn btn-accent" onClick={() => setShowSmartMatch(true)}>
                  <SearchCheck size={18} aria-hidden="true" />
                  <span>Tìm đồ vật trùng khớp</span>
                </button>
              )}

              {!isOwnPost && post.officeReceipt && post.moderationStatus === 'VISIBLE' && post.status === 'OPEN' && (
                <Link to={`/van-phong/xac-minh/${post.id}`} className="btn btn-accent">
                  <span>Xác minh nhận đồ tại văn phòng</span>
                  <ArrowRight size={17} aria-hidden="true" />
                </Link>
              )}

              {!isOwnPost && (current.myClaim || canSubmitPostClaim(post)) && !post.officeReceipt && (
                <Link
                  to={current.myClaim ? claimDetailPath(post.id, current.myClaim.id) : `/xac-minh/${post.id}`}
                  className="btn btn-accent"
                >
                  <span>{current.myClaim ? 'Theo dõi yêu cầu của tôi' : postClaimType(post) === 'OFFICE' ? 'Gửi yêu cầu nhận đồ cho phòng ban' : 'Đây là đồ của tôi'}</span>
                  <ArrowRight size={17} aria-hidden="true" />
                </Link>
              )}
              {canViewClaims && <Link className={`btn ${isFinished ? 'btn-ghost' : 'btn-accent'}`}
                to={post.officeReceipt && isApprover ? '/approver/office-claims' : postClaimsPath(post.id)}>
                <ClipboardList size={18} aria-hidden="true" />{isFinished ? 'Xem yêu cầu nhận đồ' : 'Xét duyệt yêu cầu nhận đồ'}
              </Link>}
              {canChat && <OpenChatButton postId={post.id} className="btn btn-ghost" />}
            </div>

            {isOwnPost && <div className="post-detail-manage-actions">
              {post.canEditOwnPost ? <button type="button" className="btn btn-ghost" onClick={() => setEditOpen(true)}>
                <Pencil size={16} aria-hidden="true" />Sửa bài đăng
              </button> : <Link className="btn btn-ghost" to="/tin-dang-cua-toi">Quản lý bài đăng</Link>}
              {post.postType === 'FOUND' && <div className="post-detail-office-action">
                <OfficeHandoverAction post={post} onTransferred={updated => setResult(current => ({ ...current, post: updated }))} />
              </div>}
            </div>}
          </div>

          {post.officeReceipt && <section className="post-detail-card post-detail-office" aria-label="Thông tin văn phòng tiếp nhận">
            <h2><MapPin size={19} aria-hidden="true" />{post.officeReceipt.status === 'RETURNED' ? 'Văn phòng đã trao trả đồ' : 'Đồ đang được văn phòng lưu giữ'}</h2>
            <dl>
              <div><dt>{post.officeReceipt.status === 'RETURNED' ? 'Nơi lưu trữ trước khi trao trả' : 'Nơi lưu trữ'}</dt><dd>{post.officeReceipt.storageLocationName || 'Liên hệ văn phòng tiếp nhận'}</dd></div>
              <div><dt>Cán bộ tiếp nhận</dt><dd>{post.officeReceipt.receivedBy || 'Chưa cập nhật tên'}</dd></div>
              <div><dt>Thời điểm văn phòng nhận đồ</dt><dd>{new Date(post.officeReceipt.receivedAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</dd></div>
            </dl>
          </section>}

          {!isOwnPost && <div className="post-detail-report"><ReportButton type="POST" id={post.id} targetLabel={post.title} label="Báo cáo bài đăng" /></div>}

          {editOpen && <EditPostDialog id={post.id} onClose={() => setEditOpen(false)} onSaved={() => {
            setEditOpen(false); setEditNotice('Đã lưu thay đổi.');
            getPostDetail(post.id).then(updated => {setResult(current => ({...current, post:updated}));setActiveImage(0);})
              .catch(() => setEditNotice('Đã lưu bài. Hãy tải lại trang để xem nội dung mới.'));
          }} />}
        </section>
      </div>

      {/* Smart Match Modal */}
      {canUseSmartMatch && <SmartMatchModal
        isOpen={showSmartMatch}
        onClose={() => setShowSmartMatch(false)}
        post={post}
      />}
    </main>
  );
}
