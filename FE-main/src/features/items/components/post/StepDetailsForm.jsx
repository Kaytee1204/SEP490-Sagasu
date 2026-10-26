import React from 'react';
import { Calendar, Clock, MapPin } from 'lucide-react';

export default function StepDetailsForm({ formData, postType, onChange }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div className="input-group">
        <label className="input-label" htmlFor="itemName">Tên / Tiêu đề ngắn gọn</label>
        <input
          id="itemName"
          type="text"
          className="input"
          placeholder="VD: Ví da nam Pedro màu đen..."
          value={formData.itemName}
          onChange={e => onChange({ ...formData, itemName: e.target.value })}
        />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <div className="input-group">
          <label className="input-label" htmlFor="date">{postType === 'lost' ? 'Ngày mất' : 'Ngày nhặt được'}</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><Calendar size={15} /></span>
            <input
              id="date"
              type="date"
              className="input"
              value={formData.date}
              onChange={e => onChange({ ...formData, date: e.target.value })}
            />
          </div>
        </div>
        <div className="input-group">
          <label className="input-label" htmlFor="time">Khoảng thời gian</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><Clock size={15} /></span>
            <input
              id="time"
              type="time"
              className="input"
              value={formData.time}
              onChange={e => onChange({ ...formData, time: e.target.value })}
            />
          </div>
        </div>
      </div>

      <div className="input-group">
        <label className="input-label" htmlFor="location">Địa điểm (Vị trí)</label>
        <div className="input-icon-wrap">
          <span className="input-icon"><MapPin size={15} /></span>
          <input
            id="location"
            type="text"
            className="input"
            placeholder="VD: Dọc tuyến đường Lê Thanh Nghị, đoạn ngã tư..."
            value={formData.location}
            onChange={e => onChange({ ...formData, location: e.target.value })}
          />
        </div>
        {/* Fake map UI snippet */}
        <div style={{
          height: '160px', background: 'var(--zinc-100)', borderRadius: 'var(--radius-lg)',
          marginTop: '8px', border: '1px solid var(--border)', display: 'flex',
          alignItems: 'center', justifyContent: 'center', color: 'var(--text-muted)',
          position: 'relative', overflow: 'hidden'
        }}>
          <img
            src="https://images.unsplash.com/photo-1524661135-423995f22d0b?w=800&q=80"
            alt="Map"
            style={{ width: '100%', height: '100%', objectFit: 'cover', opacity: 0.5 }}
          />
          <div style={{ position: 'absolute', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <MapPin size={32} color="var(--accent)" fill="var(--teal-100)" />
            <div style={{ background: 'white', padding: '4px 12px', borderRadius: '999px', fontSize: '0.75rem', fontWeight: 600, marginTop: '4px', boxShadow: 'var(--shadow-sm)' }}>
              Chọn trên bản đồ
            </div>
          </div>
        </div>
      </div>

      <div className="input-group">
        <label className="input-label" htmlFor="desc">Đặc điểm nhận dạng (Quan trọng)</label>
        <textarea
          id="desc"
          className="input"
          rows={4}
          placeholder="Mô tả chi tiết màu sắc, kích thước, số seri, hoặc đặc điểm đặc thù chỉ bạn biết..."
          value={formData.description}
          onChange={e => onChange({ ...formData, description: e.target.value })}
          style={{ resize: 'vertical' }}
        />
      </div>
    </div>
  );
}
