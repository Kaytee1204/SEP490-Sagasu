import '@/features/workspace/Workspace.css';
import { useState, useRef, useEffect, useCallback } from 'react';
import { useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { readPostDraft, savePostDraft, deletePostDraft } from '../api/postDraftStore';
import StepImagesUpload from '../components/post/StepImagesUpload';
import StepDetailsForm from '../components/post/StepDetailsForm';
import LivePostPreview from '../components/post/LivePostPreview';
import PostTypeSelector from '../components/post/PostTypeSelector';
import PostSubmitResultDialog from '../components/post/PostSubmitResultDialog';
import { getPostCategories, createLostPost, createFoundPost } from '../api/postApi';
import useImageUploads from '@/hooks/useImageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';


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
  const changingType = useRef(false);
  const restoreOnTypeSwitch = useRef(location.state?.resumePostDraft === true);
  const [categories, setCategories] = useState(null);
  const [categoryError, setCategoryError] = useState('');
  const [categoryAttempt, setCategoryAttempt] = useState(0);
  const [images, setImages] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [switchingType, setSwitchingType] = useState(false);
  const { prepare, processing } = useImageUploads();
  const [created, setCreated] = useState(null);
  const [submitError, setSubmitError] = useState('');
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
  const replaceImages = useCallback(files => {
    const restored = files.map(file => ({ file, preview: URL.createObjectURL(file) }));
    previews.current.forEach(url => URL.revokeObjectURL(url));
    previews.current = restored.map(image => image.preview);
    setImages(restored);
  }, []);
  const restoreSavedDraft = useCallback(async (draft, isActive = () => true) => {
    let files = (draft.files || []).filter(file => file instanceof Blob);
    try {
      const prepared = files.length ? await prepare(files) : [];
      if (!prepared || !isActive()) return;
      files = prepared;
      setError('');
    } catch (problem) {
      if (!isActive()) return;
      // Preserve invalid attachments so the user can remove them without losing the draft.
      setError(`Đã khôi phục nội dung bản nháp. ${problem.message}`);
    }
    setFormData(current => Object.fromEntries(Object.entries(current).map(([key, value]) => [key, typeof draft.formData?.[key] === 'string' ? draft.formData[key] : value])));
    replaceImages(files);
    setRequestId(draft.requestId || crypto.randomUUID());
    setAttempted(Boolean(draft.attempted));
    if (draft.categories) setCategories(current => current || draft.categories);
    setPendingDraft(null);
  }, [prepare, replaceImages]);
  useEffect(() => {
    let active = true;
    readPostDraft(draftKey).then(async draft => {
      if (!active) return;
      if (draft) {
        if (restoreOnTypeSwitch.current) await restoreSavedDraft(draft, () => active);
        else setPendingDraft(draft);
      }
      if (!active) return;
      setDraftLoaded(true);
      setDraftStatus(draft ? 'Có bản nháp đã lưu trên trình duyệt này.' : 'Nội dung và ảnh sẽ tự động lưu trên trình duyệt này.');
    }).catch(() => {
      if (active) { setDraftLoaded(true); setDraftStatus('Không truy cập được bộ nhớ. Bản nháp chưa được bảo vệ khi tải lại trang.'); }
    });
    return () => { active = false; };
  }, [draftKey, restoreSavedDraft]);
  useEffect(() => {
    if (!location.state?.resumePostDraft) return;
    // Consume this one-time switch hint; a later reload should still offer draft recovery.
    const state = { ...location.state };
    delete state.resumePostDraft;
    navigate({ pathname: location.pathname, search: location.search, hash: location.hash }, {
      replace: true, state: Object.keys(state).length ? state : null,
    });
  }, [location.pathname, location.search, location.hash, location.state, navigate]);
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
  async function restoreDraft() {
    await restoreSavedDraft(pendingDraft);
  }
  async function discardDraft() {
    try {
      await deletePostDraft(draftKey);
      setPendingDraft(null);
      setDraftStatus('Đã bỏ bản nháp. Bạn có thể bắt đầu bài mới.');
    } catch { throw new Error('Không xóa được bản nháp. Vui lòng thử lại.'); }
  }
  async function selectPostType(type) {
    if (type === (isFound ? 'found' : 'lost') || changingType.current || inFlight.current || processing
        || !draftLoaded || pendingDraft || attempted || completed.current) return;
    changingType.current = true;
    setSwitchingType(true);
    setError('');
    try {
      if (images.length || Object.values(formData).some(Boolean)) await savePostDraft(draftKey, draftValue());
      else await deletePostDraft(draftKey);
      navigate(type === 'found' ? '/dang-tin-nhat-duoc' : '/dang-tin', { state: { resumePostDraft: true } });
    } catch {
      setError('Chưa lưu được bản nháp để chuyển loại tin. Vui lòng thử lại.');
    } finally {
      changingType.current = false;
      setSwitchingType(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    getPostCategories().then(data => {
      if (!cancelled) { setCategories(data); setCategoryError(''); }
    }).catch(err => { if (!cancelled) setCategoryError(err.message || 'Không tải được danh mục.'); });
    return () => { cancelled = true; };
  }, [categoryAttempt]);

  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), []);

  async function upload(event) {
    const files = Array.from(event.target.files || []);
    event.target.value = '';
    if (!files.length || submitting || processing || switchingType) return;
    if (files.length + images.length > 5) return setError('Chỉ được chọn tối đa 5 ảnh.');
    setError('');
    try {
      const prepared = await prepare(files);
      if (!prepared) return;
      const added = prepared.map(file => ({ file, preview: URL.createObjectURL(file) }));
      previews.current.push(...added.map(image => image.preview));
      setImages(current => [...current, ...added]);
    } catch (problem) {
      setError(problem.message);
    }
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
    if (inFlight.current || processing || changingType.current || completed.current) return;
    if (!navigator.onLine) return setSubmitError('Đang mất mạng. Hãy kết nối lại rồi bấm Đăng tin.');
    const validation = attempted ? '' : validateDetails();
    if (validation) return setSubmitError(validation);
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    setSubmitError('');
    try {
      const prepared = images.length ? await prepare(images.map(image => image.file)) : [];
      if (!prepared) return;
      // Persist the request key before sending; a reload can retry the same operation safely.
      try { await savePostDraft(draftKey, { ...draftValue(), files: prepared, attempted: true }); }
      catch { throw new Error('Không lưu được bản nháp trên máy nên chưa gửi bài. Vui lòng kiểm tra bộ nhớ trình duyệt rồi thử lại.'); }
      setAttempted(true);
      if (prepared.some((file, index) => file !== images[index].file)) replaceImages(prepared);
      const post = await (isFound ? createFoundPost : createLostPost)({
        requestId, title: formData.itemName.trim(), description: formData.description.trim(),
        categoryId: Number(formData.categoryId), locationText: formData.location.trim(),
        eventAt: `${formData.date}T${formData.time}:00+07:00`,
      }, prepared);
      completed.current = true;
      setCreated(post);
      try { await deletePostDraft(draftKey); } catch { /* Retained request ID makes retry safe even if cleanup fails. */ }
      previews.current.forEach(url => URL.revokeObjectURL(url));
      previews.current = [];
      setImages([]);
    } catch (err) {
      if (err.status >= 400 && err.status < 500 && ![408, 429].includes(err.status)) setAttempted(false);
      setSubmitError(err.message || 'Không gửi được bài đăng. Vui lòng thử lại.');
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  if (!['USER', 'APPROVER', 'MANAGER', 'ADMIN'].includes(user?.role)) return <main className="container" style={{ paddingBlock: 60 }}>
    <h1>{isFound ? 'Đăng tin nhặt được' : 'Đăng tin mất đồ'}</h1><p>Vui lòng đăng nhập bằng tài khoản hợp lệ để đăng bài.</p>
  </main>;

  const review = { ...formData, images: images.map(image => image.preview), categoryName: categories?.find(c => String(c.id) === formData.categoryId)?.name };
  return <main className="post-create-page post-create-compact">
    <div className="container post-create-container">

      <h1 className="post-create-title">{isFound ? 'Đăng tin nhặt được' : 'Đăng tin mất đồ'}</h1>
      {!pendingDraft && draftStatus.startsWith("Không") && <p className="claim-alert" role="alert">{draftStatus}</p>}
      {!online && <p className="claim-alert" role="status">Đang ngoại tuyến. Bạn vẫn có thể soạn và lưu nháp trên máy; kết nối lại để đăng tin.</p>}
      {pendingDraft && <RestoreDraftDialog attempted={pendingDraft.attempted} onRestore={restoreDraft} onDiscard={discardDraft} />}
      {(created || submitError) && <PostSubmitResultDialog post={created} error={submitError} onClose={() => {
        if (created) navigate(`/chi-tiet/${created.id}`, { replace: true });
        else setSubmitError('');
      }} />}

      <div className="card post-create-card">
        {categoryError && <div role="alert"><p>{categoryError}</p><button type="button" className="btn btn-ghost" onClick={() => setCategoryAttempt(n => n + 1)}>Tải lại danh mục</button></div>}
        {!categories && !categoryError && <p role="status">Đang tải danh mục…</p>}
        {categories?.length === 0 && <p role="alert">Chưa có danh mục đang hoạt động để đăng bài.</p>}
        {error && <p role="alert" style={{ color: 'var(--status-lost)', marginBottom: 20 }}>{error}</p>}
        <ImageProcessingStatus active={processing} />
        <form onSubmit={event => { event.preventDefault(); submit(); }}>
          <fieldset disabled={submitting || processing || switchingType || Boolean(created) || !draftLoaded || Boolean(pendingDraft) || attempted} className="post-compose-fields">
            <PostTypeSelector postType={isFound ? 'found' : 'lost'} onSelectType={selectPostType} />
            <div className="post-create-details"><StepDetailsForm formData={formData} postType={isFound ? 'found' : 'lost'} onChange={setFormData} categories={categories || []} />
              <StepImagesUpload compact images={review.images} imageNames={images.map(image => image.file.name)} fileInputRef={fileInputRef} onUpload={upload} onRemove={remove} disabled={submitting || processing || switchingType} />
            </div>
            <aside className="post-create-live"><LivePostPreview formData={review} postType={isFound ? 'found' : 'lost'} authorName={user?.fullName || user?.username} /></aside>
          </fieldset>
          <div className="post-create-actions"><span>Không đưa thông tin bí mật vào nội dung công khai.</span>
          <button type="submit" className="btn btn-primary" disabled={submitting || processing || switchingType || Boolean(created) || !draftLoaded || Boolean(pendingDraft) || !online || (!attempted && !categories?.length)}>{processing ? 'Đang xử lý ảnh…' : switchingType ? 'Đang chuyển…' : submitting ? 'Đang đăng…' : attempted ? 'Thử đăng lại' : 'Đăng tin'}</button></div>
        </form>
      </div>
    </div>
  </main>;
}

function RestoreDraftDialog({ attempted, onRestore, onDiscard }) {
  const dialog = useRef(null);
  const [busy, setBusy] = useState(false);
  const [restoring, setRestoring] = useState(false);
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
      <button type="button" className="btn btn-ghost" disabled={busy || restoring} onClick={async () => {
        setBusy(true);
        try { await onDiscard(); } catch (err) { setError(err.message); } finally { setBusy(false); }
      }}>{busy ? 'Đang bỏ bản nháp…' : 'Bỏ bản nháp'}</button>
      <button type="button" className="btn btn-primary" autoFocus disabled={busy || restoring} onClick={async () => {
        setRestoring(true); setError('');
        try { await onRestore(); } catch (problem) { setError(problem.message); } finally { setRestoring(false); }
      }}>{restoring ? 'Đang khôi phục ảnh…' : 'Tiếp tục viết'}</button>
    </div>
  </dialog>;
}
