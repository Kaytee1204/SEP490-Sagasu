import { useEffect, useId, useRef, useState } from 'react';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import './ImageViewer.css';

export default function ImageViewer({ images, initialIndex, onClose, title = 'Ảnh', onIndexChange }) {
  const titleId = useId();
  const dialogRef = useRef(null);
  const [index, setIndex] = useState(initialIndex);
  const image = images[index];
  function selectImage(nextIndex) {
    setIndex(nextIndex);
    onIndexChange?.(nextIndex);
  }
  const move = step => selectImage(Math.max(0, Math.min(images.length - 1, index + step)));

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) previousFocus.focus();
    };
  }, []);

  return <dialog ref={dialogRef} className="image-viewer-preview" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); onClose(); }}
    onKeyDown={event => {
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        event.preventDefault();
        move(event.key === 'ArrowLeft' ? -1 : 1);
      }
    }}
    onClick={event => {
      if (event.target === event.currentTarget || event.target.classList.contains('image-viewer-stage')) onClose();
    }}>
    <img className="image-viewer-background" src={image.url} alt="" aria-hidden="true" />
    <div className="image-viewer-preview-content">
      <header className="image-viewer-preview-heading">
        <div><h2 id={titleId}>{title}</h2>
          <span role="status" aria-label={`Ảnh ${index + 1} trên ${images.length}`}>{index + 1}/{images.length}</span></div>
        <button type="button" className="image-viewer-control" onClick={onClose} aria-label="Đóng ảnh"><X size={24} /></button>
      </header>
      <figure className="image-viewer-stage">
        <img className="image-viewer-current" src={image.url} alt={image.name} />
        <figcaption aria-live="polite">{image.name}</figcaption>
        {images.length > 1 && <>
          <button type="button" className="image-viewer-control image-viewer-previous" disabled={index === 0}
            onClick={() => move(-1)} aria-label="Ảnh trước"><ChevronLeft size={28} /></button>
          <button type="button" className="image-viewer-control image-viewer-next" disabled={index === images.length - 1}
            onClick={() => move(1)} aria-label="Ảnh tiếp theo"><ChevronRight size={28} /></button>
        </>}
      </figure>
      {images.length > 1 && <nav className="image-viewer-navigation" aria-label="Chuyển ảnh">
        {images.map((item, position) => <button type="button" key={item.id} className="image-viewer-thumb"
          aria-label={`Xem ảnh ${position + 1}: ${item.name}`} aria-pressed={position === index} onClick={() => selectImage(position)}>
          <img src={item.url} alt="" />
        </button>)}
      </nav>}
    </div>
  </dialog>;
}
