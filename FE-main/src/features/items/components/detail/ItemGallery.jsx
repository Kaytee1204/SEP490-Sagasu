import { useState } from 'react';
import ImageViewer from '@/components/common/ImageViewer';

function GalleryImage({ src, alt, style }) {
  const [failed, setFailed] = useState(false);
  if (failed || !src) {
    return <span role="img" aria-label={alt} style={{ ...style, display: 'grid', placeItems: 'center', background: 'var(--bg-canvas)', color: 'var(--text-muted)' }}>Ảnh chưa khả dụng</span>;
  }
  return <img src={src} alt={alt} style={style} onError={() => setFailed(true)} />;
}

export default function ItemGallery({ images, title, type, activeImage, onSelectImage }) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const previewImages = images.map((url, index) => ({ id: index, url, name: `Ảnh ${index + 1} của ${title}` }));
  function openPreview(index) {
    onSelectImage(index);
    setPreviewOpen(true);
  }
  return (
    <div style={{
      background: 'white', padding: '16px', borderRadius: 'var(--radius-2xl)',
      border: '1px solid var(--border)', boxShadow: 'var(--shadow-sm)'
    }}>
      {/* Main Image */}
      <button type="button" disabled={!images.length} aria-label={`Xem ảnh ${activeImage + 1} của ${title}`}
        onClick={() => openPreview(activeImage)} style={{
        aspectRatio: '4/3', borderRadius: 'var(--radius-lg)', overflow: 'hidden',
        marginBottom: '16px', position: 'relative', display: 'block', width: '100%', padding: 0,
        border: 0, background: 'transparent', cursor: images.length ? 'zoom-in' : 'default'
      }}>
        <GalleryImage
          key={images[activeImage] || 'empty'}
          src={images[activeImage]}
          alt={title}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
        {/* Status Badge */}
        <span style={{ position: 'absolute', top: 16, left: 16 }}>
          <span className={`badge ${type === 'lost' ? 'badge-lost' : 'badge-found'}`} style={{ fontSize: '0.8125rem', padding: '6px 16px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
            {type === 'lost' ? 'Cần tìm đồ thất lạc' : 'Đồ nhặt được'}
          </span>
        </span>
      </button>

      {/* Thumbnails */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '12px' }}>
        {images.map((img, i) => (
          <button
            key={i}
            type="button"
            onClick={() => openPreview(i)}
            aria-label={`Mở ảnh ${i + 1} của ${title}`}
            aria-pressed={activeImage === i}
            style={{
              aspectRatio: '1', borderRadius: 'var(--radius-md)', overflow: 'hidden',
              border: activeImage === i ? '2px solid var(--accent)' : '2px solid transparent',
              padding: 0, cursor: 'pointer', transition: 'all 200ms',
              opacity: activeImage === i ? 1 : 0.6
            }}
          >
            <GalleryImage key={img} src={img} alt={`Ảnh ${i + 1} của ${title}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </button>
        ))}
      </div>
      {previewOpen && images.length > 0 && <ImageViewer images={previewImages} initialIndex={activeImage}
        title="Ảnh bài đăng" onIndexChange={onSelectImage} onClose={() => setPreviewOpen(false)} />}
    </div>
  );
}
