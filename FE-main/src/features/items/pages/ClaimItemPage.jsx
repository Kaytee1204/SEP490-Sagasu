import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import ClaimForm from '../components/claim/ClaimForm';
import ClaimSuccess from '../components/claim/ClaimSuccess';

const FOUND_ITEM = {
  id: '1',
  title: 'Ví da nam Pedro màu đen',
  location: 'Toà H1, Đại học Bách Khoa Hà Nội',
  finder: 'Thành viên uy tín'
};

export default function ClaimItemPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState({
    secretDetail: '',
    proofImage: null,
    contactInfo: '0912 345 678',
    meetingPref: ''
  });

  const handleImageUpload = (e) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      const url = URL.createObjectURL(file);
      setFormData({ ...formData, proofImage: url });
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setStep(3);
  };

  return (
    <div style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <main style={{ flex: 1, background: 'var(--bg-canvas)', padding: '60px 0 100px' }}>
        <div className="container-sm">
          {/* ── BREADCRUMB ── */}
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="btn btn-ghost"
            style={{ marginBottom: '24px', paddingLeft: 0, color: 'var(--text-secondary)' }}
          >
            <ArrowLeft size={16} /> Quay lại chi tiết đồ vật
          </button>

          {/* ── HEADER ── */}
          <div style={{ marginBottom: '40px' }}>
            <h1 className="text-display-md" style={{ marginBottom: '8px' }}>Xác minh quyền sở hữu</h1>
            <p className="text-body" style={{ color: 'var(--text-secondary)' }}>
              Để bảo vệ tài sản, bạn cần cung cấp bằng chứng chứng minh bạn là chủ nhân thực sự của món đồ
              <strong style={{ color: 'var(--text-primary)' }}> {FOUND_ITEM.title}</strong>.
            </p>
          </div>

          {step < 3 ? (
            <ClaimForm
              item={FOUND_ITEM}
              formData={formData}
              onChange={setFormData}
              onUpload={handleImageUpload}
              onSubmit={handleSubmit}
            />
          ) : (
            <ClaimSuccess />
          )}
        </div>
      </main>
    </div>
  );
}
