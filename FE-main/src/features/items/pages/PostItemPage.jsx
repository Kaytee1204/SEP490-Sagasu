import '@/features/workspace/Workspace.css';
import { useState, useRef, useEffect } from 'react';
import { Link, useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { readPostDraft, savePostDraft, deletePostDraft } from '../api/postDraftStore';
import StepImagesUpload from '../components/post/StepImagesUpload';
import StepDetailsForm from '../components/post/StepDetailsForm';
import LivePostPreview from '../components/post/LivePostPreview';
import PostTypeSelector from '../components/post/PostTypeSelector';
import { getPostCategories, createLostPost, createFoundPost } from '../api/postApi';


export default function PostItemPage() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const location = useLocation();
  const type = params.get('type') === 'found' || location.state?.type === 'found' || location.pathname === '/dang-tin-nhat-duoc' ? 'found' : 'lost';
  return <PostComposer key={`${user?.id}:${type}`} />;
}

function PostComposer() {
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const fileInputRef = useRef(null);
  const previews = useRef([]);
  const inFlight = useRef(false);
  const [categories, setCategories] = useState(null);
  const [categoryError, setCategoryError] = useState('');
  const [categoryAttempt, setCategoryAttempt] = useState(0);
  const [images, setImages] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(null);
  const [formData, setFormData] = useState({ itemName: '', description: '', categoryId: '', date: '', time: '', location: '' });
  const isFound = params.get('type') === 'found' || location.state?.type === 'found' || location.pathname === '/dang-tin-nhat-duoc';

  const draftKey = `sagasu-post-draft-${user?.id}-${isFound ? 'found' : 'lost'}`;
  const [draftLoaded, setDraftLoaded] = useState(false);
  const [pendingDraft, setPendingDraft] = useState(null);
  const [draftStatus, setDraftStatus] = useState('Đang kiểm tra bản nháp…');
  const [online, setOnline] = useState(navigator.onLine);
  const [requestId, setRequestId] = useState(() => crypto.randomUUID());
  const [attempted, setAttempted] = useState(false);
  const completed = useRef(false);
  const saveRevision = useRef(0);
  useEffect(() => {
    let active = true;
    readPostDraft(draftKey).then(draft => {
      if (!active) return;
      if (draft) setPendingDraft(draft);
      setDraftLoaded(true);
      setDraftStatus(draft ? 'Có bản nháp đã lưu trên trình duyệt này.' : 'Nội dung và ảnh sẽ tự động lưu trên trình duyệt này.');
    }).catch(() => {
      if (active) { setDraftLoaded(true); setDraftStatus('Không truy cập được bộ nhớ. Bản nháp chưa được bảo vệ khi tải lại trang.'); }
    });
    return () => { active = false; };
  }, [draftKey]);
  useEffect(() => {
    const reconnect = () => { setOnline(true); setCategoryAttempt(value => value + 1); };
    const disconnect = () => setOnline(false);
    window.addEventListener('online', reconnect);
    window.addEventListener('offline', disconnect);
    return () => { window.removeEventListener('online', reconnect); window.removeEventListener('offline', disconnect); };
  }, []);
  const draftValue = () => ({ formData, files: images.map(image => image.file), requestId, attempted, categories, savedAt: Date.now() });
  useEffect(() => {
    if (!draftLoaded || pendingDraft || created || completed.current) return;
    if (!images.length && !Object.values(formData).some(Boolean)) {
      deletePostDraft(draftKey).catch(() => setDraftStatus('Không cập nhật được bản nháp trên máy.'));
      return;
    }
    const revision = ++saveRevision.current;
    setDraftStatus('Đang lưu…');
    const value = { formData, files: images.map(image => image.file), requestId, attempted, categories, savedAt: Date.now() };
    savePostDraft(draftKey, value).then(() => {
      if (saveRevision.current === revision && !completed.current) setDraftStatus(`Đã lưu nháp lúc ${new Date(value.savedAt).toLocaleTimeString('vi-VN')}`);
    }).catch(() => {
      if (saveRevision.current === revision) setDraftStatus('Không lưu được bản nháp. Vui lòng giữ trang mở và thử lại khi bộ nhớ khả dụng.');
    });
  }, [draftKey, draftLoaded, pendingDraft, formData, images, requestId, attempted, categories, created]);
  function restoreDraft() {
    const draft = pendingDraft;
    setFormData(current => Object.fromEntries(Object.entries(current).map(([key, value]) => [key, typeof draft.formData?.[key] === 'string' ? draft.formData[key] : value])));
    const restored = (draft.files || []).filter(file => file instanceof Blob).map(file => ({ file, preview: URL.createObjectURL(file) }));
    previews.current.push(...restored.map(image => image.preview));
    setImages(restored);
    setRequestId(draft.requestId || crypto.randomUUID());
    setAttempted(Boolean(draft.attempted));
    if (!categories && draft.categories) setCategories(draft.categories);
    setPendingDraft(null);
  }
  async function discardDraft() {
    try {
      await deletePostDraft(draftKey);
      setPendingDraft(null);
      setDraftStatus('Đã bỏ bản nháp. Bạn có thể bắt đầu bài mới.');
    } catch { throw new Error('Không xóa được bản nháp. Vui lòng thử lại.'); }
  }

  useEffect(() => {
    let cancelled = false;
    getPostCategories().then(data => {
      if (!cancelled) { setCategories(data); setCategoryError(''); }
    }).catch(err => { if (!cancelled) setCategoryError(err.message || 'Không tải được danh mục.'); });
    return () => { cancelled = true; };
  }, [categoryAttempt]);

  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), []);

  function upload(event) {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (!files.length) return;
    if (files.length + images.length > 5) return setError('Chỉ được chọn tối đa 5 ảnh.');
    if (files.some(file => !['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024)) {
      return setError('Ảnh phải là JPG hoặc PNG, tối đa 5 MB/ảnh.');
    }
    const added = files.map(file => ({ file, preview: URL.createObjectURL(file) }));
    previews.current.push(...added.map(image => image.preview));
    setImages(current => [...current, ...added]);
    setError('');
  }

  function remove(index, event) {
    event.stopPropagation();
    URL.revokeObjectURL(images[index].preview);
    previews.current = previews.current.filter(url => url !== images[index].preview);
    setImages(current => current.filter((_, i) => i !== index));
  }

  function validateDetails() {
    if (!formData.itemName.trim() || !formData.description.trim() || !formData.location.trim()
      || !formData.categoryId || !formData.date || !formData.time) return 'Vui lòng nhập đầy đủ thông tin.';
    const timestamp = new Date(`${formData.date}T${formData.time}:00+07:00`).getTime();
    if (!Number.isFinite(timestamp) || timestamp > Date.now()) return 'Thời gian xảy ra không được ở tương lai (giờ Việt Nam).';
    if (!categories?.some(category => String(category.id) === formData.categoryId)) return 'Vui lòng chọn danh mục hợp lệ.';
    return '';
  }

  async function submit() {
    if (inFlight.current) return;
    if (!navigator.onLine) return setError("Đang mất mạng. Hãy kết nối lại rồi bấm Đăng tin.");
    const validation = attempted ? '' : validateDetails();
    if (validation) return setError(validation);
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      // Persist the request key before sending; a reload can retry the same operation safely.
      try { await savePostDraft(draftKey, { ...draftValue(), attempted: true }); }
      catch { throw new Error('Không lưu được bản nháp trên máy nên chưa gửi bài. Vui lòng kiểm tra bộ nhớ trình duyệt rồi thử lại.'); }
      setAttempted(true);
      const post = await (isFound ? createFoundPost : createLostPost)({
        requestId, title: formData.itemName.trim(), description: formData.description.trim(),
        categoryId: Number(formData.categoryId), locationText: formData.location.trim(),
        eventAt: `${formData.date}T${formData.time}:00+07:00`,
      }, images.map(image => image.file));
      completed.current = true;
      setCreated(post);
      try { await deletePostDraft(draftKey); } catch { /* Retained request ID makes retry safe even if cleanup fails. */ }
      previews.current.forEach(url => URL.revokeObjectURL(url));
      previews.current = [];
      setImages([]);
    } catch (err) {
      if (err.status >= 400 && err.status < 500 && ![408, 429].includes(err.status)) setAttempted(false);
      setError(err.message || 'Không gửi được bài đăng. Vui lòng thử lại.');
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  if (!['USER', 'APPROVER', 'MANAGER', 'ADMIN'].includes(user?.role)) return <main className="container" style={{ paddingBlock: 60 }}>
    <h1>{isFound ? 'Đăng tin nhặt được' : 'Đăng tin mất đồ'}</h1><p>Vui lòng đăng nhập bằng tài khoản hợp lệ để đăng bài.</p>
  </main>;
  if (created) return <main className="container-sm" style={{ paddingBlock: 60 }}>
    <h1 className="text-display-md">Đăng bài thành công #{created.id}</h1>
    <p role="status" style={{ marginBlock: 20 }}>{created.moderationStatus === 'VISIBLE'
      ? 'Bài đăng đã hiển thị trong danh sách.' : 'Bài đăng đang chờ Approver duyệt và chưa hiển thị công khai.'}</p>
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
      <Link className="btn btn-primary" to={`/chi-tiet/${created.id}`}>Xem bài đăng</Link>
      <Link className="btn btn-ghost" to="/tim-kiem">Về danh sách</Link>
    </div>
  </main>;

  const review = { ...formData, images: images.map(image => image.preview), categoryName: categories?.find(c => String(c.id) === formData.categoryId)?.name };
  return <main className="post-create-page post-create-compact">
    <div className="container post-create-container">

      <h1 className="post-create-title">{isFound ? 'Đăng tin nhặt được' : 'Đăng tin mất đồ'}</h1>
      {!pendingDraft && draftStatus.startsWith("Không") && <p className="claim-alert" role="alert">{draftStatus}</p>}
      {!online && <p className="claim-alert" role="status">Đang ngoại tuyến. Bạn vẫn có thể soạn và lưu nháp trên máy; kết nối lại để đăng tin.</p>}
      {pendingDraft && <RestoreDraftDialog attempted={pendingDraft.attempted} onRestore={restoreDraft} onDiscard={discardDraft} />}

      {attempted && <p className="claim-notice">Đã gửi yêu cầu đăng bài. Nếu chưa nhận được kết quả, bấm “Thử đăng lại” để kiểm tra cùng yêu cầu. Nội dung được giữ nguyên để tránh đăng trùng.</p>}
      <div className="card post-create-card">
        {categoryError && <div role="alert"><p>{categoryError}</p><button type="button" className="btn btn-ghost" onClick={() => setCategoryAttempt(n => n + 1)}>Tải lại danh mục</button></div>}
        {!categories && !categoryError && <p role="status">Đang tải danh mục…</p>}
        {categories?.length === 0 && <p role="alert">Chưa có danh mục đang hoạt động để đăng bài.</p>}
        {error && <p role="alert" style={{ color: 'var(--status-lost)', marginBottom: 20 }}>{error}</p>}
        <form onSubmit={event => { event.preventDefault(); submit(); }}>
          <fieldset disabled={submitting || !draftLoaded || Boolean(pendingDraft) || attempted} className="post-compose-fields">
            <PostTypeSelector postType={isFound ? 'found' : 'lost'}
              onSelectType={type => navigate(type === 'found' ? '/dang-tin-nhat-duoc' : '/dang-tin', { state: null })} />
            <div className="post-create-details"><StepDetailsForm formData={formData} postType={isFound ? 'found' : 'lost'} onChange={setFormData} categories={categories || []} />
              <StepImagesUpload compact images={review.images} fileInputRef={fileInputRef} onUpload={upload} onRemove={remove} />
            </div>
            <aside className="post-create-live"><LivePostPreview formData={review} postType={isFound ? 'found' : 'lost'} authorName={user?.fullName || user?.username} /></aside>
          </fieldset>
          <div className="post-create-actions"><span>Không đưa thông tin bí mật vào nội dung công khai.</span>
          <button type="submit" className="btn btn-primary" disabled={submitting || !draftLoaded || Boolean(pendingDraft) || !online || (!attempted && !categories?.length)}>{submitting ? 'Đang đăng…' : attempted ? 'Thử đăng lại' : 'Đăng tin'}</button></div>
        </form>
      </div>
    </div>
  </main>;
}

function RestoreDraftDialog({ attempted, onRestore, onDiscard }) {
  const dialog = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    const element = dialog.current;
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    element.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      previousFocus?.focus();
    };
  }, []);
  return <dialog ref={dialog} className="post-draft-dialog" aria-labelledby="post-draft-title"
    aria-describedby="post-draft-description" onCancel={event => event.preventDefault()}>
    <h2 id="post-draft-title">Tiếp tục bài đang soạn?</h2>
    <p id="post-draft-description">Nội dung và ảnh của bạn đã được lưu. Bạn muốn tiếp tục viết hay bỏ bản nháp để tạo bài mới?</p>
    {attempted && <p className="claim-notice">Bài này đã được gửi nhưng chưa xác nhận kết quả. Hãy tiếp tục và thử lại để kiểm tra, tránh tạo bài mới trùng nội dung.</p>}
    {error && <p role="alert">{error}</p>}
    <div className="post-draft-actions">
      <button type="button" className="btn btn-ghost" disabled={busy} onClick={async () => {
        setBusy(true);
        try { await onDiscard(); } catch (err) { setError(err.message); } finally { setBusy(false); }
      }}>{busy ? 'Đang bỏ bản nháp…' : 'Bỏ bản nháp'}</button>
      <button type="button" className="btn btn-primary" autoFocus disabled={busy} onClick={onRestore}>Tiếp tục viết</button>
    </div>
  </dialog>;
}
