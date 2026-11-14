import { useState } from 'react';
import { Expand } from 'lucide-react';
import ImageViewer from '@/components/common/ImageViewer';

function GalleryImage({ src, alt }) {
  const [failed, setFailed] = useState(false);
  if (failed || !src) {
    return <span className="post-gallery-placeholder" role="img" aria-label={alt}>Ảnh chưa khả dụng</span>;
  }
  return <img src={src} alt={alt} onError={() => setFailed(true)} />;
}

export default function ItemGallery({ images, title, activeImage, onSelectImage }) {
  const [previewOpen, setPreviewOpen] = useState(false);
  const previewImages = images.map((url, index) => ({ id: index, url, name: `Ảnh ${index + 1} của ${title}` }));
  function openPreview(index) {
    onSelectImage(index);
    setPreviewOpen(true);
  }
  return (
    <section className="post-detail-card post-gallery" aria-label="Ảnh món đồ">
      <button type="button" disabled={!images.length} aria-label={`Xem ảnh ${activeImage + 1} của ${title}`}
        className="post-gallery-main" onClick={() => openPreview(activeImage)}>
        <GalleryImage
          key={images[activeImage] || 'empty'}
          src={images[activeImage]}
          alt={title}
        />
        {images.length > 0 && <span className="post-gallery-expand"><Expand size={15} aria-hidden="true" />Xem ảnh {activeImage + 1}/{images.length}</span>}
      </button>

      {images.length > 1 && <div className="post-gallery-thumbnails" aria-label="Các ảnh của món đồ">
        {images.map((img, i) => (
          <button
            key={i}
            type="button"
            onClick={() => onSelectImage(i)}
            aria-label={`Chọn ảnh ${i + 1} của ${title}`}
            aria-pressed={activeImage === i}
          >
            <GalleryImage key={img} src={img} alt={`Ảnh ${i + 1} của ${title}`} />
          </button>
        ))}
      </div>}
      {previewOpen && images.length > 0 && <ImageViewer images={previewImages} initialIndex={activeImage}
        title="Ảnh bài đăng" onIndexChange={onSelectImage} onClose={() => setPreviewOpen(false)} />}
    </section>
  );
}
