import React from 'react';
import { Camera, CheckCircle, MapPin, Calendar, Shield, Edit3 } from 'lucide-react';

export default function ProfileHeader({ user, editMode, onToggleEdit }) {
  return (
    <section style={{
      background: 'linear-gradient(160deg, var(--teal-900) 0%, #0f3d3d 100%)',
      padding: '56px 0 80px',
      position: 'relative',
      overflow: 'hidden',
    }}>
      <div style={{
        position: 'absolute', inset: 0,
        backgroundImage: 'radial-gradient(circle at 80% 40%, rgba(94,196,196,0.08) 0%, transparent 60%)',
        pointerEvents: 'none',
      }} />
      <div className="container" style={{ position: 'relative', zIndex: 1 }}>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: '28px', flexWrap: 'wrap' }}>
          {/* Avatar block */}
          <div style={{ position: 'relative', flexShrink: 0 }}>
            <div style={{
              padding: '4px',
              borderRadius: '50%',
              background: 'rgba(255,255,255,0.15)',
              border: '1px solid rgba(255,255,255,0.2)',
            }}>
              <img
                src={user.avatar}
                alt={user.name}
                style={{ width: 96, height: 96, borderRadius: '50%', display: 'block' }}
              />
            </div>
            <button
              id="avatar-upload-btn"
              type="button"
              style={{
                position: 'absolute', bottom: 4, right: 4,
                width: 28, height: 28, borderRadius: '50%',
                background: 'white', border: '2px solid var(--teal-900)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                cursor: 'pointer', color: 'var(--accent)',
                transition: 'transform 200ms cubic-bezier(0.34,1.56,0.64,1)',
              }}
              onMouseEnter={e => e.currentTarget.style.transform = 'scale(1.15)'}
              onMouseLeave={e => e.currentTarget.style.transform = 'scale(1)'}
              aria-label="Thay ảnh đại diện"
            >
              <Camera size={13} />
            </button>
          </div>

          {/* User info */}
          <div style={{ flex: 1, minWidth: '220px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '6px', flexWrap: 'wrap' }}>
              <h1 style={{
                fontFamily: 'var(--font-display)', fontSize: 'clamp(1.5rem, 3vw, 2rem)',
                fontWeight: 800, color: 'white', letterSpacing: '-0.02em', lineHeight: 1.1,
              }}>
                {user.name}
              </h1>
              {user.verified && (
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: '4px',
                  fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.08em',
                  background: 'rgba(16,185,129,0.18)', color: '#6ee7b7',
                  border: '1px solid rgba(110,231,183,0.25)',
                  padding: '2px 9px', borderRadius: '4px',
                }}>
                  <CheckCircle size={10} />
                  Đã xác minh
                </span>
              )}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '10px' }}>
              <span style={{
                fontFamily: 'var(--font-mono)', fontSize: '0.8125rem',
                color: 'rgba(255,255,255,0.55)', letterSpacing: '0.05em',
              }}>
                SĐT: {user.phone}
              </span>
              <span style={{ color: 'rgba(255,255,255,0.2)' }}>·</span>
              <span style={{ fontSize: '0.8125rem', color: 'rgba(255,255,255,0.55)' }}>
                {user.job}
              </span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '0.8125rem', color: 'rgba(255,255,255,0.5)' }}>
                <MapPin size={12} />
                {user.address}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '0.8125rem', color: 'rgba(255,255,255,0.5)' }}>
                <Calendar size={12} />
                Tham gia {user.joinDate}
              </div>
              {user.twoFA && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '5px', fontSize: '0.8125rem', color: 'rgba(94,196,196,0.9)', fontWeight: 600 }}>
                  <Shield size={12} />
                  2FA Bật
                </div>
              )}
            </div>
          </div>

          {/* Edit button */}
          <button
            id="profile-edit-btn"
            type="button"
            onClick={onToggleEdit}
            style={{
              display: 'flex', alignItems: 'center', gap: '8px',
              padding: '10px 20px',
              background: editMode ? 'white' : 'rgba(255,255,255,0.12)',
              border: '1px solid rgba(255,255,255,0.2)',
              borderRadius: 'var(--radius-lg)',
              color: editMode ? 'var(--teal-900)' : 'white',
              fontSize: '0.875rem', fontWeight: 600,
              cursor: 'pointer', fontFamily: 'var(--font-body)',
              transition: 'all 200ms cubic-bezier(0.34,1.56,0.64,1)',
              flexShrink: 0,
            }}
            onMouseEnter={e => { if (!editMode) e.currentTarget.style.background = 'rgba(255,255,255,0.2)'; }}
            onMouseLeave={e => { if (!editMode) e.currentTarget.style.background = 'rgba(255,255,255,0.12)'; }}
          >
            <Edit3 size={15} strokeWidth={1.8} />
            {editMode ? 'Huỷ chỉnh sửa' : 'Chỉnh sửa hồ sơ'}
          </button>
        </div>
      </div>
    </section>
  );
}
