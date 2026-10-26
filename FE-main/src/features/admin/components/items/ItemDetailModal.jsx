import React from 'react';
import { X, CheckCircle } from 'lucide-react';

export default function ItemDetailModal({ selectedItemDetail, setSelectedItemDetail, setActionModal }) {
  if (!selectedItemDetail) return null;

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 1000,
      background: 'rgba(0,0,0,0.45)', backdropFilter: 'blur(3px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '20px'
    }}>
      <div style={{
        background: 'white', borderRadius: '12px',
        width: '100%', maxWidth: '600px', maxHeight: '90vh', overflowY: 'auto',
        boxShadow: '0 20px 40px rgba(0,0,0,0.15)', border: '1px solid rgba(0,0,0,0.08)'
      }}>
        <div style={{ padding: '18px 22px', borderBottom: '1px solid rgba(0,0,0,0.06)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <span style={{ fontSize: '0.7rem', fontWeight: 700, color: 'var(--teal-700)', fontFamily: 'var(--font-mono)' }}>{selectedItemDetail.id}</span>
            <h3 style={{ fontFamily: 'var(--font-display)', fontSize: '1.15rem', fontWeight: 700, margin: '2px 0 0', color: 'var(--text-primary)' }}>
              {selectedItemDetail.title}
            </h3>
          </div>
          <button
            type="button"
            onClick={() => setSelectedItemDetail(null)}
            style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: '4px' }}
          >
            <X size={18} />
          </button>
        </div>

        <div style={{ padding: '22px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <img
            src={selectedItemDetail.img}
            alt={selectedItemDetail.title}
            style={{ width: '100%', maxHeight: '260px', objectFit: 'cover', borderRadius: '8px' }}
          />

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '12px', background: '#f8fafc', padding: '14px', borderRadius: '8px', border: '1px solid rgba(0,0,0,0.04)' }}>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Người đăng</div>
              <div style={{ fontWeight: 600, fontSize: '0.85rem', marginTop: '2px' }}>{selectedItemDetail.reporter}</div>
              <div style={{ fontSize: '0.775rem', color: 'var(--teal-700)', marginTop: '2px', fontWeight: 600 }}>
                SĐT: {selectedItemDetail.reporterPhone}
              </div>
            </div>
            <div>
              <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 600 }}>Địa điểm &amp; Thời gian</div>
              <div style={{ fontWeight: 600, fontSize: '0.85rem', marginTop: '2px' }}>{selectedItemDetail.location}</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '2px' }}>{selectedItemDetail.date}</div>
            </div>
          </div>

          {selectedItemDetail.aiScore && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 14px', borderRadius: '6px', background: '#ecfdf5', color: '#059669', fontSize: '0.825rem', fontWeight: 600 }}>
              <CheckCircle size={15} /> Độ khớp tìm kiếm: {selectedItemDetail.aiScore}%
            </div>
          )}

          <div>
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-secondary)', textTransform: 'uppercase', marginBottom: '6px' }}>
              Nội dung mô tả:
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-primary)', lineHeight: 1.6, background: '#f8fafc', padding: '12px', borderRadius: '6px', border: '1px solid rgba(0,0,0,0.04)', margin: 0 }}>
              {selectedItemDetail.desc}
            </p>
          </div>

          <div style={{ display: 'flex', gap: '10px', justifyContent: 'flex-end', marginTop: '8px' }}>
            {selectedItemDetail.status !== 'hidden' ? (
              <button
                type="button"
                onClick={() => {
                  setSelectedItemDetail(null);
                  setActionModal({ isOpen: true, type: 'HIDE_ITEM', payload: selectedItemDetail.id, title: 'Gỡ bài đăng vi phạm', placeholder: 'Nhập lý do gỡ bài...' });
                }}
                style={{ padding: '8px 16px', borderRadius: '6px', background: '#fef2f2', color: '#dc2626', border: '1px solid #fecaca', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
              >
                Gỡ bài vi phạm
              </button>
            ) : (
              <button
                type="button"
                onClick={() => {
                  setSelectedItemDetail(null);
                  setActionModal({ isOpen: true, type: 'RESTORE_ITEM', payload: selectedItemDetail.id, title: 'Khôi phục bài đăng', placeholder: 'Nhập lý do khôi phục...' });
                }}
                style={{ padding: '8px 16px', borderRadius: '6px', background: '#ecfdf5', color: '#059669', border: '1px solid #a7f3d0', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
              >
                Khôi phục bài
              </button>
            )}
            <button
              type="button"
              onClick={() => setSelectedItemDetail(null)}
              style={{ padding: '8px 16px', borderRadius: '6px', background: '#f1f5f9', color: 'var(--text-primary)', border: '1px solid rgba(0,0,0,0.06)', fontWeight: 600, fontSize: '0.85rem', cursor: 'pointer' }}
            >
              Đóng
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
