import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Shield, User, AlertOctagon, Edit2, Ban, UserCheck, Trash2 } from 'lucide-react';

export default function UserTable({ filteredUsers, openCrudModal, setActionModal }) {
  const navigate = useNavigate();

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
      <thead>
        <tr style={{ background: '#f8fafc', borderBottom: '1px solid rgba(0,0,0,0.06)', color: 'var(--text-secondary)' }}>
          <th style={{ padding: '12px 24px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Thành viên</th>
          <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Vai trò</th>
          <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Điểm uy tín</th>
          <th style={{ padding: '12px 18px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Trạng thái</th>
          <th style={{ padding: '12px 24px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', textAlign: 'right' }}>Thao tác</th>
        </tr>
      </thead>
      <tbody>
        {filteredUsers.map(user => {
          const isWarning = user.trustScore < 50 || user.role === 'Cảnh báo';
          return (
            <tr
              key={user.id}
              onClick={() => navigate(`/admin/users/${user.id}`)}
              style={{
                borderBottom: '1px solid rgba(0,0,0,0.05)',
                cursor: 'pointer',
                transition: 'background 120ms'
              }}
              onMouseEnter={e => { e.currentTarget.style.background = '#f8fafc'; }}
              onMouseLeave={e => { e.currentTarget.style.background = 'white'; }}
            >
              {/* Thành viên & Email */}
              <td style={{ padding: '14px 24px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <div style={{
                    width: '36px', height: '36px', borderRadius: '8px',
                    background: user.role === 'Admin' ? '#f3e8ff' : '#f1f5f9',
                    color: user.role === 'Admin' ? '#7e22ce' : 'var(--teal-700)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontWeight: 700, fontSize: '0.9rem', flexShrink: 0
                  }}>
                    {user.name.charAt(0)}
                  </div>
                  <div>
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                      <span>{user.name}</span>
                      <span style={{ fontSize: '0.675rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', background: '#f1f5f9', padding: '1px 5px', borderRadius: '4px' }}>
                        {user.id}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.775rem', color: 'var(--text-muted)', marginTop: '2px' }}>{user.email}</div>
                  </div>
                </div>
              </td>

              {/* Vai trò */}
              <td style={{ padding: '14px 18px' }}>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: '5px',
                  fontSize: '0.75rem', fontWeight: 600, padding: '3px 8px', borderRadius: '6px',
                  background: user.role === 'Admin' ? '#f3e8ff' : user.role === 'Thành viên Tích Cực' ? 'rgba(45,212,191,0.12)' : isWarning ? '#fff7ed' : '#f1f5f9',
                  color: user.role === 'Admin' ? '#7e22ce' : user.role === 'Thành viên Tích Cực' ? 'var(--teal-700)' : isWarning ? '#c2410c' : 'var(--text-secondary)'
                }}>
                  {user.role === 'Admin' ? <Shield size={12} /> : isWarning ? <AlertOctagon size={12} /> : <User size={12} />}
                  {user.role}
                </span>
              </td>

              {/* Điểm uy tín */}
              <td style={{ padding: '14px 18px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <div style={{ width: '70px', height: '6px', background: '#e2e8f0', borderRadius: '3px', overflow: 'hidden' }}>
                    <div style={{
                      width: `${user.trustScore}%`, height: '100%',
                      background: user.trustScore > 80 ? '#10b981' : user.trustScore > 50 ? '#f59e0b' : '#ef4444',
                      borderRadius: '3px'
                    }} />
                  </div>
                  <span style={{
                    fontWeight: 700, fontSize: '0.8rem', fontVariantNumeric: 'tabular-nums',
                    color: user.trustScore > 80 ? '#059669' : user.trustScore > 50 ? '#d97706' : '#dc2626'
                  }}>
                    {user.trustScore}
                  </span>
                </div>
              </td>

              {/* Trạng thái */}
              <td style={{ padding: '14px 18px' }}>
                <span style={{
                  display: 'inline-flex', alignItems: 'center', gap: '5px',
                  fontSize: '0.75rem', fontWeight: 600, padding: '3px 8px', borderRadius: '6px',
                  background: user.status === 'Hoạt động' ? '#ecfdf5' : '#fef2f2',
                  color: user.status === 'Hoạt động' ? '#059669' : '#dc2626'
                }}>
                  <span style={{ width: 5, height: 5, borderRadius: '50%', background: user.status === 'Hoạt động' ? '#10b981' : '#ef4444' }} />
                  {user.status}
                </span>
              </td>

              {/* Thao tác (Direct actions only, click row to view profile) */}
              <td style={{ padding: '14px 24px', textAlign: 'right' }}>
                <div style={{ display: 'inline-flex', gap: '6px' }} onClick={e => e.stopPropagation()}>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      openCrudModal('edit', user);
                    }}
                    style={{
                      padding: '5px 8px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.1)',
                      background: 'white', color: 'var(--text-secondary)', cursor: 'pointer'
                    }}
                    title="Sửa thông tin"
                  >
                    <Edit2 size={13} />
                  </button>

                  {user.status === 'Hoạt động' ? (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActionModal({ isOpen: true, type: 'BAN_USER', payload: user.id, title: 'Khóa tài khoản', placeholder: 'Nhập lý do khóa tài khoản này...' });
                      }}
                      style={{
                        padding: '5px 8px', borderRadius: '6px', border: '1px solid #fed7aa',
                        background: '#fff7ed', color: '#ea580c', cursor: 'pointer'
                      }}
                      title="Tạm khóa tài khoản"
                    >
                      <Ban size={13} />
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setActionModal({ isOpen: true, type: 'UNBAN_USER', payload: user.id, title: 'Mở khóa tài khoản', placeholder: 'Nhập lý do mở khóa...' });
                      }}
                      style={{
                        padding: '5px 8px', borderRadius: '6px', border: '1px solid #a7f3d0',
                        background: '#ecfdf5', color: '#059669', cursor: 'pointer'
                      }}
                      title="Mở khóa tài khoản"
                    >
                      <UserCheck size={13} />
                    </button>
                  )}

                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setActionModal({ isOpen: true, type: 'DELETE_USER', payload: user.id, title: 'Xóa tài khoản', placeholder: 'Nhập lý do xóa vĩnh viễn tài khoản...' });
                    }}
                    style={{
                      padding: '5px 8px', borderRadius: '6px', border: '1px solid #fecaca',
                      background: '#fef2f2', color: '#dc2626', cursor: 'pointer'
                    }}
                    title="Xóa tài khoản"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </td>
            </tr>
          );
        })}

        {filteredUsers.length === 0 && (
          <tr>
            <td colSpan={5} style={{ textAlign: 'center', padding: '48px', color: 'var(--text-muted)' }}>
              Không tìm thấy tài khoản phù hợp với điều kiện tìm kiếm.
            </td>
          </tr>
        )}
      </tbody>
    </table>
  );
}
