import React from 'react';
import { Calendar, Clock, MapPin } from 'lucide-react';

export default function StepDetailsForm({ formData, postType, onChange, categories = [], showPrivate = true }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      {postType === 'found' && <div className="input-group">
        <label className="input-label" htmlFor="custodyMode">Bạn muốn giữ đồ như thế nào?</label>
        <select id="custodyMode" className="input" required value={formData.custodyMode}
          onChange={e => onChange({ ...formData, custodyMode: e.target.value,
            ...(e.target.value === 'P2P' ? { handoverDate: '', handoverTime: '' } : {}) })}>
          <option value="P2P">Tôi tự giữ để trao trả</option>
          <option value="OFFICE">Tôi chọn giao đồ cho Approver</option>
        </select>
        {formData.custodyMode === 'OFFICE' && <>
          <p>Bài sẽ chờ Approver xét duyệt. Bạn đề xuất lịch giao đồ bên dưới; lịch này chưa được xác nhận.</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 16 }}>
            <div className="input-group"><label className="input-label" htmlFor="handoverDate">Ngày dự kiến giao đồ</label>
              <input id="handoverDate" type="date" className="input" required
                min={new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })}
                value={formData.handoverDate || ''} onChange={e => onChange({ ...formData, handoverDate: e.target.value })} /></div>
            <div className="input-group"><label className="input-label" htmlFor="handoverTime">Giờ dự kiến giao đồ</label>
              <input id="handoverTime" type="time" className="input" required value={formData.handoverTime || ''}
                onChange={e => onChange({ ...formData, handoverTime: e.target.value })} /></div>
          </div>
          <p>Chọn thời gian trong tương lai, theo giờ Việt Nam.</p>
        </>}
      </div>}
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
      {showPrivate && <div className="input-group">
        <label className="input-label" htmlFor="privateIdentifiers">Dấu hiệu nhận dạng riêng tư (không bắt buộc)</label>
        <textarea id="privateIdentifiers" className="input" rows={4} maxLength={5000}
          value={formData.privateIdentifiers} onChange={e => onChange({ ...formData, privateIdentifiers: e.target.value })}
          placeholder="Số seri, dấu vết riêng, chi tiết bên trong…" />
        <p>Chỉ bạn đọc và sửa được; không xuất hiện trong bài công khai. Tối đa 5.000 ký tự.</p>
      </div>}
    </div>
  );
}
