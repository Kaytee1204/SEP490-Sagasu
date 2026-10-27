import { useState, useRef, useEffect } from 'react';
import { Link, useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import StepIndicator from '../components/post/StepIndicator';
import StepImagesUpload from '../components/post/StepImagesUpload';
import StepDetailsForm from '../components/post/StepDetailsForm';
import StepReview from '../components/post/StepReview';
import PostTypeSelector from '../components/post/PostTypeSelector';
import { getPostCategories, createLostPost, createFoundPost } from '../api/postApi';

const STEPS = ['Hình ảnh', 'Thông tin', 'Xác nhận'];

export default function PostItemPage() {
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const fileInputRef = useRef(null);
  const detailsRef = useRef(null);
  const previews = useRef([]);
  const inFlight = useRef(false);
  const [step, setStep] = useState(0);
  const [categories, setCategories] = useState(null);
  const [categoryError, setCategoryError] = useState('');
  const [categoryAttempt, setCategoryAttempt] = useState(0);
  const [images, setImages] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState(null);
  const [formData, setFormData] = useState({ itemName: '', description: '', categoryId: '', date: '', time: '', location: '', custodyMode: 'P2P', privateIdentifiers: '' });
  const isFound = params.get('type') === 'found' || location.state?.type === 'found' || location.pathname === '/dang-tin-nhat-duoc';

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

  function next() {
    if (step === 1) {
      if (!detailsRef.current.reportValidity()) return;
      const validation = validateDetails();
      if (validation) return setError(validation);
    }
    setError('');
    setStep(value => value + 1);
  }

  async function submit() {
    if (inFlight.current) return;
    const validation = validateDetails();
    if (validation) return setError(validation);
    inFlight.current = true;
    setSubmitting(true);
    setError('');
    try {
      const post = await (isFound ? createFoundPost : createLostPost)({
        title: formData.itemName.trim(), description: formData.description.trim(),
        categoryId: Number(formData.categoryId), locationText: formData.location.trim(),
        privateIdentifiers: formData.privateIdentifiers.trim(),
        ...(isFound ? { custodyMode: formData.custodyMode } : {}),
        eventAt: `${formData.date}T${formData.time}:00+07:00`,
      }, images.map(image => image.file));
      setCreated(post);
      previews.current.forEach(url => URL.revokeObjectURL(url));
      previews.current = [];
      setImages([]);
    } catch (err) {
      setError(err.message || 'Không gửi được bài đăng. Vui lòng thử lại.');
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }

  if (!['USER', 'EXTERNAL_USER', 'CHECKER'].includes(user?.role)) return <main className="container" style={{ paddingBlock: 60 }}>
    <h1>{isFound ? 'Đăng tin nhặt được' : 'Đăng tin mất đồ'}</h1><p>Chức năng dành cho tài khoản nội bộ, người ngoài trường và Checker.</p>
  </main>;
  if (created) return <main className="container-sm" style={{ paddingBlock: 60 }}>
    <h1 className="text-display-md">Đăng bài thành công #{created.id}</h1>
    <p role="status" style={{ marginBlock: 20 }}>{created.moderationStatus === 'VISIBLE'
      ? 'Bài đăng đã hiển thị trong danh sách.' : 'Bài đăng đang chờ Approver duyệt và chưa hiển thị công khai.'}</p>
    {isFound && formData.custodyMode === 'OFFICE' && <p>Hãy mang đồ đến văn phòng để Checker xác nhận tiếp nhận.</p>}
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
      <Link className="btn btn-primary" to={`/chi-tiet/${created.id}`}>Xem bài đăng</Link>
      <Link className="btn btn-ghost" to="/tim-kiem">Về danh sách</Link>
    </div>
  </main>;

  const review = { ...formData, images: images.map(image => image.preview), categoryName: categories?.find(c => String(c.id) === formData.categoryId)?.name };
  return <main style={{ background: 'var(--bg-canvas)', minHeight: '70vh', padding: '48px 0 80px' }}>
    <div className="container-sm">
      <h1 className="text-display-lg" style={{ textAlign: 'center' }}>{isFound ? 'Đăng tin nhặt được' : 'Đăng tin mất đồ'}</h1>
      <p style={{ textAlign: 'center', marginBlock: 16 }}>Chia sẻ thông tin món đồ để cộng đồng hỗ trợ tìm kiếm và trao trả. Thời gian sử dụng giờ Việt Nam.</p>
      <StepIndicator currentStep={step} steps={STEPS} />
      <div className="card" style={{ padding: 'clamp(16px, 4vw, 40px)' }}>
        {categoryError && <div role="alert"><p>{categoryError}</p><button type="button" className="btn btn-ghost" onClick={() => setCategoryAttempt(n => n + 1)}>Tải lại danh mục</button></div>}
        {!categories && !categoryError && <p role="status">Đang tải danh mục…</p>}
        {categories?.length === 0 && <p role="alert">Chưa có danh mục đang hoạt động để đăng bài.</p>}
        {error && <p role="alert" style={{ color: 'var(--status-lost)', marginBottom: 20 }}>{error}</p>}
        <fieldset disabled={submitting} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
          {step === 0 && <PostTypeSelector postType={isFound ? 'found' : 'lost'}
            onSelectType={type => navigate(type === 'found' ? '/dang-tin-nhat-duoc' : '/dang-tin', { state: null })} />}
          {step === 0 && <StepImagesUpload images={review.images} fileInputRef={fileInputRef} onUpload={upload} onRemove={remove} />}
          {step === 1 && <form ref={detailsRef} onSubmit={e => { e.preventDefault(); next(); }}>
            <StepDetailsForm formData={formData} postType={isFound ? 'found' : 'lost'} onChange={setFormData} categories={categories || []} />
          </form>}
          {step === 2 && <StepReview formData={review} postType={isFound ? 'found' : 'lost'} requiresReview={user?.role !== 'USER'} />}
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, justifyContent: 'space-between', marginTop: 32 }}>
            {step > 0 ? <button type="button" className="btn btn-ghost" onClick={() => { setStep(s => s - 1); setError(''); }}>Quay lại</button> : <span />}
            {step < 2 ? <button type="button" className="btn btn-primary" disabled={!categories?.length || Boolean(categoryError)} onClick={next}>Tiếp tục</button>
              : <button type="button" className="btn btn-primary" onClick={submit}>{submitting ? 'Đang đăng…' : 'Đăng bài'}</button>}
          </div>
        </fieldset>
      </div>
    </div>
  </main>;
}
