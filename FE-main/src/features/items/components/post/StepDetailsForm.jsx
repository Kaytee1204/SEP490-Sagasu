import React from 'react';
import { Calendar, Clock, MapPin } from 'lucide-react';

export default function StepDetailsForm({ formData, postType, onChange, categories = [] }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div className="input-group">
        <label className="input-label" htmlFor="itemName">Tên / Tiêu đề ngắn gọn</label>
        <input
          id="itemName"
          type="text"
          required
          maxLength={200}
          className="input"
          placeholder="VD: Ví da nam Pedro màu đen..."
          value={formData.itemName}
          onChange={e => onChange({ ...formData, itemName: e.target.value })}
        />
      </div>

      <div className="input-group">
        <label className="input-label" htmlFor="categoryId">Danh mục</label>
        <select id="categoryId" className="input" required value={formData.categoryId || ''}
          onChange={e => onChange({ ...formData, categoryId: e.target.value })}>
          <option value="">Chọn danh mục</option>
          {categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
        <div className="input-group">
          <label className="input-label" htmlFor="date">{postType === 'lost' ? 'Ngày mất' : 'Ngày nhặt được'}</label>
          <div className="input-icon-wrap">
            <span className="input-icon"><Calendar size={15} /></span>
            <input
              id="date"
              type="date"
              required
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
              required
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
            required
            maxLength={1000}
            className="input"
            placeholder="VD: Dọc tuyến đường Lê Thanh Nghị, đoạn ngã tư..."
            value={formData.location}
            onChange={e => onChange({ ...formData, location: e.target.value })}
          />
        </div>
      </div>

      <div className="input-group">
        <label className="input-label" htmlFor="desc">Mô tả công khai</label>
        <textarea
          id="desc"
          className="input"
          rows={4}
          required
          maxLength={10000}
          placeholder="Mô tả màu sắc, loại đồ vật và hoàn cảnh mất/nhặt được. Không nhập số seri, thông tin cá nhân hoặc đặc điểm bí mật tại đây."
          value={formData.description}
          onChange={e => onChange({ ...formData, description: e.target.value })}
          style={{ resize: 'vertical' }}
        />
      </div>
    </div>
  );
}
