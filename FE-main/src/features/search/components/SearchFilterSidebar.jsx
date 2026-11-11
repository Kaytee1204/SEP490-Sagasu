import { useEffect, useId, useRef, useState } from 'react';
import { CalendarDays, Filter, Tags, X } from 'lucide-react';

const POST_TYPES = [['', 'Tất cả bài đăng'], ['LOST', 'Cần tìm'], ['FOUND', 'Nhặt được']];
const TIME_RANGES = [['', 'Bất kỳ lúc nào'], ['24H', '24 giờ qua'], ['7D', '7 ngày qua'], ['CUSTOM', 'Tùy chỉnh…']];

export default function SearchFilterSidebar({ values, onChange, onApply, onReset, error }) {
  const [mobile, setMobile] = useState(() => window.matchMedia('(max-width: 760px)').matches);
  const [open, setOpen] = useState(false);
  const dialog = useRef(null);
  const trigger = useRef(null);
  const titleId = useId();
  const datesId = useId();
  useEffect(() => {
    const query = window.matchMedia('(max-width: 760px)');
    const update = () => { setMobile(query.matches); setOpen(false); };
    query.addEventListener('change', update);
    return () => query.removeEventListener('change', update);
  }, []);
  useEffect(() => {
    if (!mobile || !open) return;
    const element = dialog.current, overflow = document.body.style.overflow;
    element.showModal(); document.body.style.overflow = 'hidden';
    return () => { element.close(); document.body.style.overflow = overflow; if (trigger.current?.isConnected) trigger.current.focus(); };
  }, [mobile, open]);
  const activeCount = Number(Boolean(values.postType)) + Number(Boolean(values.timeRange));
  const form = <form className="post-search-filter-panel" onSubmit={event => {
    onApply(event);
    if (!(values.fromDate && values.toDate && values.fromDate > values.toDate)) setOpen(false);
  }}>
      <h2 id={titleId}><Filter size={19} aria-hidden="true" />Bộ lọc nâng cao</h2>
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
              aria-controls={datesId} />
            {label}
          </label>)}
        </div>
        {values.timeRange === 'CUSTOM' && <div className="post-search-custom-dates" id={datesId}>
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
    </form>;
  if (!mobile) return <aside className="post-search-sidebar" aria-label="Bộ lọc nâng cao">{form}</aside>;
  return <div className="post-search-mobile-filters">
    <button ref={trigger} type="button" className="btn btn-ghost" aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}>
      <Filter size={18} /> Bộ lọc{activeCount > 0 ? ` (${activeCount})` : ''}
    </button>
    {open && <dialog ref={dialog} className="post-search-filter-dialog" aria-labelledby={titleId}
      onCancel={event => { event.preventDefault(); setOpen(false); }}>
      <button className="post-search-filter-close" type="button" aria-label="Đóng bộ lọc" onClick={() => setOpen(false)}><X size={20} /></button>
      {form}
    </dialog>}
  </div>;
}
