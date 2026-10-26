import React from 'react';

export default function UserCrudModal({ crudModal, setCrudModal, formData, setFormData, submitCrud }) {
  if (!crudModal.isOpen) return null;

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1050, background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(3px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px' }}>
      <div style={{ background: 'white', borderRadius: '12px', width: '100%', maxWidth: '480px', padding: '24px', boxShadow: '0 20px 40px rgba(0,0,0,0.15)', border: '1px solid rgba(0,0,0,0.08)' }}>
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: '1.15rem', fontWeight: 700, margin: '0 0 16px', color: 'var(--text-primary)' }}>
          {crudModal.mode === 'create' ? 'Tạo mới tài khoản thành viên' : 'Chỉnh sửa tài khoản'}
        </h3>

        <form onSubmit={submitCrud} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>Họ và tên</label>
            <input
              type="text" required
              value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid var(--border)', outline: 'none', fontSize: '0.85rem' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>Email</label>
            <input
              type="email" required
              value={formData.email} onChange={e => setFormData({ ...formData, email: e.target.value })}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid var(--border)', outline: 'none', fontSize: '0.85rem' }}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>Vai trò</label>
              <select
                value={formData.role} onChange={e => setFormData({ ...formData, role: e.target.value })}
                style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid var(--border)', outline: 'none', background: 'white', fontSize: '0.85rem' }}
              >
                <option value="Thành viên">Thành viên</option>
                <option value="Thành viên Tích Cực">Thành viên Tích Cực</option>
                <option value="Cảnh báo">Trong diện cảnh báo</option>
                <option value="Admin">Quản trị viên (Admin)</option>
              </select>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>Điểm uy tín (0-100)</label>
              <input
                type="number" min="0" max="100"
                value={formData.trustScore} onChange={e => setFormData({ ...formData, trustScore: Number(e.target.value) })}
                style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid var(--border)', outline: 'none', fontSize: '0.85rem' }}
              />
            </div>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-secondary)' }}>Trạng thái tài khoản</label>
            <select
              value={formData.status} onChange={e => setFormData({ ...formData, status: e.target.value })}
              style={{ width: '100%', padding: '9px 12px', borderRadius: '6px', border: '1px solid var(--border)', outline: 'none', background: 'white', fontSize: '0.85rem' }}
            >
              <option value="Hoạt động">Hoạt động bình thường</option>
              <option value="Đã tạm khóa">Đã tạm khóa</option>
            </select>
          </div>

          <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '8px' }}>
            <button
              type="button"
              onClick={() => setCrudModal({ isOpen: false, mode: 'create', data: null })}
              style={{ padding: '8px 14px', borderRadius: '6px', background: '#f1f5f9', border: '1px solid rgba(0,0,0,0.06)', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
            >
              Hủy
            </button>
            <button
              type="submit"
              style={{ padding: '8px 16px', borderRadius: '6px', background: 'var(--teal-600)', color: 'white', border: 'none', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
            >
              {crudModal.mode === 'create' ? 'Tạo tài khoản' : 'Lưu thay đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
