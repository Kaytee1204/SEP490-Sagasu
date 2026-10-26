import React from 'react';
import { Filter, Calendar, Map } from 'lucide-react';

export default function SearchFilterSidebar() {
  return (
    <aside>
      <div style={{
        background: 'white', borderRadius: 'var(--radius-xl)',
        border: '1px solid var(--border)', padding: '24px',
        position: 'sticky', top: 160
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '24px', fontWeight: 700, fontSize: '1.0625rem' }}>
          <Filter size={18} />
          Bộ lọc nâng cao
        </div>

        {/* Time Filter */}
        <div style={{ marginBottom: '24px' }}>
          <div style={{ fontWeight: 600, fontSize: '0.875rem', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Calendar size={14} className="text-muted" /> Thời gian
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <label className="checkbox-wrap">
              <input type="radio" name="time" defaultChecked /> Bất kỳ lúc nào
            </label>
            <label className="checkbox-wrap">
              <input type="radio" name="time" /> 24 giờ qua
            </label>
            <label className="checkbox-wrap">
              <input type="radio" name="time" /> 7 ngày qua
            </label>
            <label className="checkbox-wrap">
              <input type="radio" name="time" /> Tuỳ chỉnh...
            </label>
          </div>
        </div>

        <div className="divider" style={{ margin: '20px 0' }} />

        {/* Location Filter */}
        <div style={{ marginBottom: '24px' }}>
          <div style={{ fontWeight: 600, fontSize: '0.875rem', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Map size={14} className="text-muted" /> Khu vực
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <label className="checkbox-wrap">
              <input type="checkbox" defaultChecked /> Hà Nội
            </label>
            <label className="checkbox-wrap">
              <input type="checkbox" /> TP. Hồ Chí Minh
            </label>
            <label className="checkbox-wrap">
              <input type="checkbox" /> Khác
            </label>
          </div>
        </div>

        <button type="button" className="btn btn-outline-white btn-full" style={{ borderColor: 'var(--border-strong)', color: 'var(--text-primary)' }}>
          Áp dụng bộ lọc
        </button>
      </div>
    </aside>
  );
}
