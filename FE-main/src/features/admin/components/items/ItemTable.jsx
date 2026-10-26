import React from 'react';
import { MapPin, Phone, Trash2, RotateCcw } from 'lucide-react';

export default function ItemTable({ filteredItems, setSelectedItemDetail, setActionModal }) {
  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
      <thead>
        <tr style={{ background: '#f8fafc', borderBottom: '1px solid rgba(0,0,0,0.06)', color: 'var(--text-secondary)' }}>
          <th style={{ padding: '12px 24px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Vật phẩm</th>
          <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Phân loại</th>
          <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Người đăng</th>
          <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Khu vực &amp; Thời gian</th>
          <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em' }}>Trạng thái</th>
          <th style={{ padding: '12px 24px', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.04em', textAlign: 'right' }}>Thao tác</th>
        </tr>
      </thead>
      <tbody>
        {filteredItems.map(item => (
          <tr
            key={item.id}
            onClick={() => setSelectedItemDetail(item)}
            style={{
              borderBottom: '1px solid rgba(0,0,0,0.05)',
              background: item.status === 'hidden' ? '#fffbfb' : 'white',
              cursor: 'pointer',
              transition: 'background 120ms'
            }}
            onMouseEnter={e => {
              e.currentTarget.style.background = item.status === 'hidden' ? '#fff1f2' : '#f8fafc';
            }}
            onMouseLeave={e => {
              e.currentTarget.style.background = item.status === 'hidden' ? '#fffbfb' : 'white';
            }}
          >
            {/* Item Thumbnail & Info */}
            <td style={{ padding: '14px 24px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <img
                  src={item.img}
                  alt={item.title}
                  style={{
                    width: '42px', height: '42px', borderRadius: '8px',
                    objectFit: 'cover', border: '1px solid rgba(0,0,0,0.08)', flexShrink: 0
                  }}
                />
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 600, color: 'var(--text-primary)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '240px' }}>
                    {item.title}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '2px' }}>
                    <span style={{ fontSize: '0.675rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)', background: '#f1f5f9', padding: '1px 5px', borderRadius: '4px' }}>
                      {item.id}
                    </span>
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      · {item.category}
                    </span>
                  </div>
                </div>
              </div>
            </td>

            {/* Type Badge */}
            <td style={{ padding: '14px 16px' }}>
              <span style={{
                display: 'inline-flex', alignItems: 'center', gap: '4px',
                fontSize: '0.75rem', fontWeight: 600, padding: '3px 8px', borderRadius: '6px',
                background: item.type === 'lost' ? '#fef2f2' : 'rgba(45,212,191,0.12)',
                color: item.type === 'lost' ? '#dc2626' : 'var(--teal-700)'
              }}>
                {item.type === 'lost' ? 'Cần tìm' : 'Nhặt được'}
              </span>
            </td>

            {/* Poster Info */}
            <td style={{ padding: '14px 16px' }}>
              <div style={{ fontWeight: 600, color: 'var(--text-primary)', fontSize: '0.825rem' }}>
                {item.reporter}
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--teal-700)', display: 'flex', alignItems: 'center', gap: '4px', marginTop: '1px' }}>
                <Phone size={11} /> {item.reporterPhone}
              </div>
            </td>

            {/* Location & Time */}
            <td style={{ padding: '14px 16px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: 'var(--text-primary)', fontSize: '0.825rem' }}>
                <MapPin size={12} style={{ color: 'var(--text-muted)', flexShrink: 0 }} />
                <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '180px' }}>
                  {item.location}
                </span>
              </div>
              <div style={{ fontSize: '0.725rem', color: 'var(--text-muted)', marginTop: '2px', paddingLeft: '16px' }}>
                {item.date}
              </div>
            </td>

            {/* Status */}
            <td style={{ padding: '14px 16px' }}>
              <span style={{
                display: 'inline-flex', alignItems: 'center', gap: '5px',
                fontSize: '0.75rem', fontWeight: 600, padding: '3px 8px', borderRadius: '6px',
                background:
                  item.status === 'hidden' ? '#fef2f2' :
                    item.status === 'resolved' ? '#eef2ff' :
                      item.status === 'matched' ? 'rgba(16,185,129,0.1)' : '#f0fdf4',
                color:
                  item.status === 'hidden' ? '#dc2626' :
                    item.status === 'resolved' ? '#6366f1' :
                      item.status === 'matched' ? '#059669' : '#059669'
              }}>
                <span style={{
                  width: 5, height: 5, borderRadius: '50%',
                  background: item.status === 'hidden' ? '#dc2626' : item.status === 'resolved' ? '#6366f1' : '#10b981'
                }} />
                {item.status === 'hidden' ? 'Bị ẩn' :
                  item.status === 'resolved' ? 'Đã trao trả' :
                    item.status === 'matched' ? 'Khớp AI' : 'Đang tìm'}
              </span>
            </td>

            {/* Action Buttons */}
            <td style={{ padding: '14px 24px', textAlign: 'right' }}>
              <div style={{ display: 'inline-flex', gap: '6px' }} onClick={e => e.stopPropagation()}>
                {item.status !== 'hidden' ? (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setActionModal({ isOpen: true, type: 'HIDE_ITEM', payload: item.id, title: 'Gỡ bài đăng vi phạm', placeholder: 'Nhập lý do gỡ bài này...' });
                    }}
                    style={{
                      display: 'inline-flex', alignItems: 'center', gap: '4px',
                      padding: '5px 10px', borderRadius: '6px', border: '1px solid #fecaca',
                      background: '#fffbfb', color: '#dc2626', cursor: 'pointer', fontSize: '0.75rem', fontWeight: 600
                    }}
                    title="Gỡ tin vi phạm"
                  >
                    <Trash2 size={13} />
                    <span>Gỡ bài</span>
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      setActionModal({ isOpen: true, type: 'RESTORE_ITEM', payload: item.id, title: 'Khôi phục bài đăng', placeholder: 'Nhập lý do khôi phục...' });
                    }}
                    style={{
                      display: 'inline-flex', alignItems: 'center', gap: '4px',
                      padding: '5px 10px', borderRadius: '6px', border: '1px solid #a7f3d0',
                      background: '#ecfdf5', color: '#059669', cursor: 'pointer', fontSize: '0.75rem', fontWeight: 600
                    }}
                    title="Khôi phục tin"
                  >
                    <RotateCcw size={13} />
                    <span>Hiện lại</span>
                  </button>
                )}
              </div>
            </td>
          </tr>
        ))}

        {filteredItems.length === 0 && (
          <tr>
            <td colSpan={6} style={{ textAlign: 'center', padding: '48px', color: 'var(--text-muted)' }}>
              Không tìm thấy tin đăng nào phù hợp với bộ lọc hiện tại.
            </td>
          </tr>
        )}
      </tbody>
    </table>
  );
}
