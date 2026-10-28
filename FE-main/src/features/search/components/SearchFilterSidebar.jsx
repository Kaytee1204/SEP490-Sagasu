import { CalendarDays, Filter, Tags } from 'lucide-react';

const POST_TYPES = [['', 'Tất cả bài đăng'], ['LOST', 'Cần tìm'], ['FOUND', 'Nhặt được']];
const TIME_RANGES = [['', 'Bất kỳ lúc nào'], ['24H', '24 giờ qua'], ['7D', '7 ngày qua'], ['CUSTOM', 'Tùy chỉnh…']];

export default function SearchFilterSidebar({ values, onChange, onApply, onReset, error }) {
  return <aside className="post-search-sidebar" aria-label="Bộ lọc nâng cao">
    <form className="post-search-filter-panel" onSubmit={onApply}>
      <h2><Filter size={19} aria-hidden="true" />Bộ lọc nâng cao</h2>
      <fieldset className="post-search-filter-group">
        <legend><Tags size={15} aria-hidden="true" />Loại bài</legend>
        <div className="post-search-radio-list">
          {POST_TYPES.map(([value, label]) => <label key={value} className="post-search-radio">
            <input type="radio" name="postType" value={value} checked={values.postType === value} onChange={onChange} />
            {label}
          </label>)}
        </div>
      </fieldset>
      <fieldset className="post-search-filter-group">
        <legend><CalendarDays size={15} aria-hidden="true" />Thời gian</legend>
        <div className="post-search-radio-list">
          {TIME_RANGES.map(([value, label]) => <label key={value} className="post-search-radio">
            <input type="radio" name="timeRange" value={value} checked={values.timeRange === value} onChange={onChange}
              aria-controls="post-search-custom-dates" />
            {label}
          </label>)}
        </div>
        {values.timeRange === 'CUSTOM' && <div className="post-search-custom-dates" id="post-search-custom-dates">
          <p className="post-search-hint">Ngày mất / nhặt (giờ Việt Nam).</p>
          <div className="post-search-date-fields">
            <label>Từ ngày
              <input type="date" name="fromDate" value={values.fromDate} onChange={onChange}
                min="0001-01-01" max={values.toDate || '9999-12-31'} />
            </label>
            <label>Đến ngày
              <input type="date" name="toDate" value={values.toDate} onChange={onChange}
                min={values.fromDate || '0001-01-01'} max="9999-12-31" />
            </label>
          </div>
        </div>}
      </fieldset>
      {error && <p className="post-search-filter-error" role="alert">{error}</p>}
      <div className="post-search-filter-actions">
        <button type="submit" className="btn post-search-apply">Áp dụng bộ lọc</button>
        <button type="button" className="post-search-reset" onClick={onReset}>Xóa bộ lọc</button>
      </div>
    </form>
  </aside>;
}
