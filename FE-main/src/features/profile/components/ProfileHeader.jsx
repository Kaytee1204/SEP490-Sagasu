import React from 'react';
import { Camera, CheckCircle, Shield, Edit3, Mail, Phone } from 'lucide-react';

export default function ProfileHeader({ user, editMode, onToggleEdit }) {
  const isActive = user.status === 'ACTIVE';

  return (
    <section
      style={{
        background: 'linear-gradient(135deg, var(--teal-900) 0%, var(--teal-800) 100%)',
        padding: '32px 0',
        borderBottom: '1px solid rgba(255, 255, 255, 0.08)',
      }}
    >
      <div className="container">
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '28px',
            flexWrap: 'wrap',
          }}
        >
          {/* Left: Avatar + Name & Role underneath + Contact details */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '24px',
              flexWrap: 'wrap',
            }}
          >
            {/* Avatar Column: Avatar with Name & Role underneath */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '8px',
                minWidth: '110px',
              }}
            >
              <div style={{ position: 'relative' }}>
                <div
                  style={{
                    padding: '3px',
                    borderRadius: '50%',
                    background: 'rgba(255, 255, 255, 0.14)',
                    border: '1px solid rgba(255, 255, 255, 0.22)',
                  }}
                >
                  <img
                    src={user.avatar}
                    alt={user.name}
                    style={{
                      width: 80,
                      height: 80,
                      borderRadius: '50%',
                      display: 'block',
                      objectFit: 'cover',
                    }}
                  />
                </div>
                <button
                  id="avatar-upload-btn"
                  type="button"
                  style={{
                    position: 'absolute',
                    bottom: 2,
                    right: 2,
                    width: 26,
                    height: 26,
                    borderRadius: '50%',
                    background: '#ffffff',
                    border: '2px solid var(--teal-900)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: 'pointer',
                    color: 'var(--accent)',
                  }}
                  aria-label="Thay ảnh đại diện"
                >
                  <Camera size={12} />
                </button>
              </div>

              {/* Name & Role directly below Avatar */}
              <div style={{ textAlign: 'center' }}>
                <div
                  style={{
                    fontWeight: 700,
                    fontSize: '0.9375rem',
                    color: '#ffffff',
                    lineHeight: 1.25,
                  }}
                >
                  {user.name}
                </div>
                <div
                  style={{
                    marginTop: '3px',
                    display: 'inline-block',
                    fontSize: '0.7rem',
                    fontWeight: 600,
                    letterSpacing: '0.04em',
                    color: '#a7e2e2',
                    background: 'rgba(255, 255, 255, 0.1)',
                    border: '1px solid rgba(255, 255, 255, 0.16)',
                    padding: '2px 10px',
                    borderRadius: '6px',
                  }}
                >
                  {user.role}
                </div>
              </div>
            </div>

            {/* Divider on desktop */}
            <div
              style={{
                width: '1px',
                height: '72px',
                background: 'rgba(255, 255, 255, 0.12)',
              }}
              className="hidden sm:block"
            />

            {/* Account Summary Details */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
                <h1
                  style={{
                    fontFamily: 'var(--font-display)',
                    fontSize: '1.5rem',
                    fontWeight: 700,
                    color: '#ffffff',
                    margin: 0,
                    lineHeight: 1.2,
                  }}
                >
                  {user.name}
                </h1>
                <span
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: '5px',
                    fontSize: '0.72rem',
                    fontWeight: 600,
                    background: isActive ? 'rgba(16, 185, 129, 0.16)' : 'rgba(245, 158, 11, 0.2)',
                    color: isActive ? '#6ee7b7' : '#fde68a',
                    border: `1px solid ${isActive ? 'rgba(110, 231, 183, 0.28)' : 'rgba(253, 230, 138, 0.3)'}`,
                    padding: '3px 9px',
                    borderRadius: '6px',
                  }}
                >
                  <CheckCircle size={11} />
                  {isActive ? 'Đang hoạt động' : user.status}
                </span>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '16px',
                  flexWrap: 'wrap',
                  fontSize: '0.84rem',
                  color: 'rgba(255, 255, 255, 0.82)',
                }}
              >
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                  <Mail size={14} style={{ color: '#5ec4c4' }} />
                  {user.email}
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                  <Phone size={14} style={{ color: '#5ec4c4' }} />
                  {user.phone}
                </span>
              </div>

              <div
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '0.78rem',
                  color: 'rgba(255, 255, 255, 0.65)',
                }}
              >
                <Shield size={13} style={{ color: '#5ec4c4' }} />
                <span>{user.securityLabel}</span>
              </div>
            </div>
          </div>

          {/* Right: Edit button */}
          <button
            id="profile-edit-btn"
            type="button"
            onClick={onToggleEdit}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '9px 18px',
              background: editMode ? '#ffffff' : 'rgba(255, 255, 255, 0.1)',
              border: '1px solid rgba(255, 255, 255, 0.22)',
              borderRadius: '8px',
              color: editMode ? 'var(--teal-900)' : '#ffffff',
              fontSize: '0.84rem',
              fontWeight: 600,
              cursor: 'pointer',
              fontFamily: 'var(--font-body)',
              transition: 'background 150ms ease, color 150ms ease',
              flexShrink: 0,
            }}
            onMouseEnter={(e) => {
              if (!editMode) e.currentTarget.style.background = 'rgba(255, 255, 255, 0.18)';
            }}
            onMouseLeave={(e) => {
              if (!editMode) e.currentTarget.style.background = 'rgba(255, 255, 255, 0.1)';
            }}
          >
            <Edit3 size={14} />
            {editMode ? 'Đóng chỉnh sửa' : 'Chỉnh sửa hồ sơ'}
          </button>
        </div>
      </div>
    </section>
  );
}
