import React from 'react';

export default function UserActionModal({ actionModal, setActionModal, actionReason, setActionReason, executeAction }) {
  if (!actionModal.isOpen) return null;

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1050, background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(3px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
      <div style={{ background: 'white', borderRadius: '12px', width: '100%', maxWidth: '420px', padding: '24px', boxShadow: '0 20px 40px rgba(0,0,0,0.15)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: '1.1rem', fontWeight: 700, margin: '0 0 12px', color: 'var(--text-primary)' }}>
          {actionModal.title}
        </h3>

        <div style={{ marginBottom: '16px' }}>
          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>
            Lý do thực hiện <span style={{ color: '#ef4444' }}>*</span>
          </label>
          <textarea
            rows={3}
            placeholder={actionModal.placeholder}
            value={actionReason}
            onChange={e => setActionReason(e.target.value)}
            style={{ width: '100%', padding: '10px', borderRadius: '6px', border: '1px solid var(--border)', fontSize: '0.85rem', fontFamily: 'var(--font-body)', outline: 'none', resize: 'vertical' }}
          />
        </div>

        <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end' }}>
          <button
            type="button"
            onClick={() => setActionModal({ isOpen: false, type: '', payload: null, title: '', placeholder: '' })}
            style={{ padding: '8px 14px', borderRadius: '6px', background: '#f1f5f9', border: '1px solid rgba(0,0,0,0.06)', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={executeAction}
            style={{
              padding: '8px 16px', borderRadius: '6px', border: 'none', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer',
              background: actionModal.type === 'DELETE_USER' ? '#ef4444' : 'var(--teal-600)', color: 'white'
            }}
          >
            Xác nhận
          </button>
        </div>
      </div>
    </div>
  );
}
