import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate, useSearchParams, useLocation } from 'react-router-dom';
import { CheckCircle, ArrowRight, ArrowLeft } from 'lucide-react';
import StepIndicator from '../components/post/StepIndicator';
import PostTypeSelector from '../components/post/PostTypeSelector';
import StepImagesUpload from '../components/post/StepImagesUpload';
import StepDetailsForm from '../components/post/StepDetailsForm';
import StepReview from '../components/post/StepReview';

const STEPS = ['Hình ảnh', 'Thông tin', 'Hoàn tất'];

export default function PostItemPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const fileInputRef = useRef(null);

  const isFoundParam =
    searchParams.get('type') === 'found' ||
    location.state?.type === 'found' ||
    location.pathname === '/dang-tin-nhat-duoc';

  const [postType, setPostType] = useState(isFoundParam ? 'found' : 'lost');
  const [step, setStep] = useState(0);
  const [formData, setFormData] = useState({
    images: [],
    itemName: '',
    description: '',
    date: '',
    time: '',
    location: '',
  });

  useEffect(() => {
    const isFound =
      searchParams.get('type') === 'found' ||
      location.state?.type === 'found' ||
      location.pathname === '/dang-tin-nhat-duoc';

    setPostType(isFound ? 'found' : 'lost');
  }, [searchParams, location.state, location.pathname]);

  const handleImageUpload = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      const newFiles = Array.from(e.target.files);
      const totalImages = formData.images.length + newFiles.length;
      if (totalImages > 5) {
        alert('Bạn chỉ được tải lên tối đa 5 ảnh.');
        return;
      }
      const newUrls = newFiles.map(file => URL.createObjectURL(file));
      setFormData({ ...formData, images: [...formData.images, ...newUrls] });
    }
  };

  const handleRemoveImage = (indexToRemove, e) => {
    e.stopPropagation();
    setFormData({
      ...formData,
      images: formData.images.filter((_, idx) => idx !== indexToRemove),
    });
  };

  const handleNext = () => {
    if (step < STEPS.length - 1) setStep(step + 1);
  };

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1, background: 'var(--bg-canvas)', padding: '60px 0 100px' }}>
        <div className="container-sm">
          {/* Header */}
          <div style={{ textAlign: 'center', marginBottom: '40px' }}>
            <h1 className="text-display-lg" style={{ marginBottom: '8px' }}>Đăng tin vật phẩm</h1>
            <p className="text-body" style={{ color: 'var(--text-secondary)' }}>
              Cung cấp thông tin chi tiết để hệ thống AI Smart Match có thể giúp bạn tìm kiếm nhanh chóng nhất.
            </p>
          </div>

          {/* Type selector */}
          <PostTypeSelector postType={postType} onSelectType={setPostType} />

          {/* Step Indicator */}
          <StepIndicator currentStep={step} steps={STEPS} />

          {/* Form Card */}
          <div className="card animate-fadeInUp" style={{ padding: '40px', boxShadow: 'var(--shadow-lg)' }}>
            {step === 0 && (
              <StepImagesUpload
                images={formData.images}
                fileInputRef={fileInputRef}
                onUpload={handleImageUpload}
                onRemove={handleRemoveImage}
              />
            )}

            {step === 1 && (
              <StepDetailsForm
                formData={formData}
                postType={postType}
                onChange={setFormData}
              />
            )}

            {step === 2 && (
              <StepReview
                formData={formData}
                postType={postType}
              />
            )}

            {/* Navigation Buttons */}
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '40px', paddingTop: '24px', borderTop: '1px solid var(--border)' }}>
              {step > 0 ? (
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => setStep(step - 1)}
                >
                  <ArrowLeft size={16} /> Quay lại
                </button>
              ) : <div />}

              {step < STEPS.length - 1 ? (
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={handleNext}
                  disabled={step === 0 && formData.images.length === 0}
                >
                  Tiếp tục <ArrowRight size={16} />
                </button>
              ) : (
                <div style={{ display: 'flex', gap: '12px' }}>
                  <Link
                    to="/tin-dang-cua-toi"
                    className="btn btn-ghost"
                    style={{ textDecoration: 'none', borderColor: 'var(--border-strong)', color: 'var(--text-primary)' }}
                  >
                    Chỉ đăng tin thường
                  </Link>
                  <Link
                    to="/smart-match"
                    className="btn btn-primary"
                    style={{ background: 'var(--status-found)', color: 'white', textDecoration: 'none' }}
                  >
                    <CheckCircle size={16} /> Đăng & Quét AI
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
