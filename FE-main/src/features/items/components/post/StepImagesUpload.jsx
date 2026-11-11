import React from 'react';
import { Camera, X } from 'lucide-react';

export default function StepImagesUpload({ images, fileInputRef, onUpload, onRemove, compact = false }) {
  if (compact) return <div className="post-upload-compact">
    <div className="post-upload-heading"><span>Hình ảnh ({images.length}/5)</span><small>JPG/PNG · tối đa 5 MB/ảnh</small></div>
    <div className="post-upload-strip">
      {images.map((src, index) => <div className="post-upload-thumb" key={src}>
        <img src={src} alt={`Ảnh đã chọn ${index + 1}`} />
        <button type="button" aria-label={`Xóa ảnh ${index + 1}`} onClick={event => onRemove(index, event)}><X size={12} /></button>
      </div>)}
      {images.length < 5 && <button type="button" className="post-upload-add" onClick={() => fileInputRef.current?.click()}><Camera size={18} /><span>Thêm ảnh</span></button>}
    </div>
    <input type="file" ref={fileInputRef} onChange={onUpload} accept="image/jpeg,image/png" multiple hidden />
  </div>;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div>
        <label className="input-label" style={{ marginBottom: '12px', display: 'block', fontSize: '1rem' }}>
          Tải lên hình ảnh vật phẩm ({images.length}/5)
        </label>

        {/* Lưới ảnh đã tải lên */}
        {images.length > 0 && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', marginBottom: '16px' }}>
            {images.map((imgSrc, idx) => (
              <div
                key={idx}
                style={{
                  position: 'relative',
                  width: compact ? '72px' : '100px',
                  height: compact ? '72px' : '100px',
                  borderRadius: 'var(--radius-lg)',
                  overflow: 'hidden',
                  border: '1px solid var(--border)',
                }}
              >
                <img src={imgSrc} alt={`Preview ${idx}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                <button
                  type="button"
                  onClick={(e) => onRemove(idx, e)}
                  style={{
                    position: 'absolute',
                    top: 4,
                    right: 4,
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: 'rgba(0,0,0,0.6)',
                    color: 'white',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    border: 'none',
                    cursor: 'pointer',
                  }}
                  aria-label="Xoá ảnh"
                >
                  <X size={12} />
                </button>
              </div>
            ))}
          </div>
        )}

        {/* Vùng tải ảnh (chỉ hiện khi chưa đủ 5 ảnh) */}
        {images.length < 5 && (
          <div
            onClick={() => fileInputRef.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInputRef.current?.click(); } }}
            aria-label="Chọn ảnh JPG hoặc PNG"
            style={{
              border: '2px dashed var(--accent)',
              borderRadius: 'var(--radius-xl)',
              padding: compact ? '20px 12px' : images.length === 0 ? '48px 24px' : '24px',
              textAlign: 'center',
              cursor: 'pointer',
              background: 'var(--teal-50)',
              position: 'relative',
              overflow: 'hidden',
              transition: 'all 200ms',
            }}
          >
            <div
              style={{
                width: images.length === 0 ? 64 : 40,
                height: images.length === 0 ? 64 : 40,
                borderRadius: '50%',
                background: 'white',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto 12px',
                color: 'var(--accent)',
                boxShadow: '0 4px 12px rgba(30,107,107,0.1)',
              }}
            >
              <Camera size={images.length === 0 ? 28 : 20} />
            </div>
            <div
              style={{
                fontWeight: 600,
                fontSize: images.length === 0 ? '1.0625rem' : '0.9375rem',
                color: 'var(--accent)',
                marginBottom: '8px',
              }}
            >
              {images.length === 0 ? 'Bấm để chọn ảnh (không bắt buộc)' : 'Thêm ảnh khác'}
            </div>
            <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
              Định dạng JPG, PNG. Tối đa 5MB.
            </div>
            <input
              type="file"
              ref={fileInputRef}
              onChange={onUpload}
              accept="image/jpeg,image/png"
              multiple
              style={{ display: 'none' }}
            />
          </div>
        )}
      </div>
    </div>
  );
}
