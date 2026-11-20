import { ArrowUpDown, Calendar, RotateCcw } from 'lucide-react';
import { TIME_RANGE_OPTIONS } from '../utils/claimUtils';

export default function ClaimTimeFilter({
  timeRange,
  fromDate,
  toDate,
  sortOrder,
  onTimeRangeChange,
  onFromDateChange,
  onToDateChange,
  onSortOrderChange,
  onReset,
}) {
  const hasCustomTime = timeRange !== 'ALL' || Boolean(fromDate) || Boolean(toDate) || sortOrder !== 'NEWEST';

  return (
    <div className="claim-time-filter-bar" role="group" aria-label="Bộ lọc theo thời gian">
      <div className="claim-time-field">
        <label htmlFor="claim-time-range">
          <Calendar size={13} aria-hidden="true" />
          Thời gian
        </label>
        <select
          id="claim-time-range"
          value={timeRange}
          onChange={event => onTimeRangeChange(event.target.value)}
        >
          {TIME_RANGE_OPTIONS.map(opt => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {timeRange === 'CUSTOM' && (
        <div className="claim-time-custom-range">
          <div className="claim-time-field">
            <label htmlFor="claim-date-from">Từ ngày</label>
            <input
              id="claim-date-from"
              type="date"
              value={fromDate}
              max={toDate || undefined}
              onChange={event => onFromDateChange(event.target.value)}
            />
          </div>
          <div className="claim-time-field">
            <label htmlFor="claim-date-to">Đến ngày</label>
            <input
              id="claim-date-to"
              type="date"
              value={toDate}
              min={fromDate || undefined}
              onChange={event => onToDateChange(event.target.value)}
            />
          </div>
        </div>
      )}

      <div className="claim-time-field">
        <label htmlFor="claim-sort-order">
          <ArrowUpDown size={13} aria-hidden="true" />
          Sắp xếp
        </label>
        <select
          id="claim-sort-order"
          value={sortOrder}
          onChange={event => onSortOrderChange(event.target.value)}
        >
          <option value="NEWEST">Mới nhất trước</option>
          <option value="OLDEST">Cũ nhất trước</option>
        </select>
      </div>

      {hasCustomTime && (
        <button
          type="button"
          className="claim-time-reset"
          onClick={onReset}
          title="Đặt lại bộ lọc thời gian"
        >
          <RotateCcw size={14} aria-hidden="true" />
          Đặt lại
        </button>
      )}
    </div>
  );
}
