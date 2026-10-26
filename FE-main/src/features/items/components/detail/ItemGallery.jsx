import React from 'react';

export default function ItemGallery({ images, title, type, activeImage, onSelectImage }) {
  return (
    <div style={{
      background: 'white', padding: '16px', borderRadius: 'var(--radius-2xl)',
      border: '1px solid var(--border)', boxShadow: 'var(--shadow-sm)'
    }}>
      {/* Main Image */}
      <div style={{
        aspectRatio: '4/3', borderRadius: 'var(--radius-lg)', overflow: 'hidden',
        marginBottom: '16px', position: 'relative'
      }}>
        <img
          src={images[activeImage]}
          alt={title}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
        {/* Status Badge */}
        <div style={{ position: 'absolute', top: 16, left: 16 }}>
          <span className={`badge ${type === 'lost' ? 'badge-lost' : 'badge-found'}`} style={{ fontSize: '0.8125rem', padding: '6px 16px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)' }}>
            {type === 'lost' ? 'Cần tìm đồ thất lạc' : 'Đồ nhặt được'}
          </span>
        </div>
      </div>

      {/* Thumbnails */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '12px' }}>
        {images.map((img, i) => (
          <button
            key={i}
            type="button"
            onClick={() => onSelectImage(i)}
            style={{
              aspectRatio: '1', borderRadius: 'var(--radius-md)', overflow: 'hidden',
              border: activeImage === i ? '2px solid var(--accent)' : '2px solid transparent',
              padding: 0, cursor: 'pointer', transition: 'all 200ms',
              opacity: activeImage === i ? 1 : 0.6
            }}
          >
            <img src={img} alt={`Thumb ${i}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          </button>
        ))}
      </div>
    </div>
  );
}
