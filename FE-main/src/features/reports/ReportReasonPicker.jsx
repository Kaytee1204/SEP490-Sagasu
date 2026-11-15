import { useId } from 'react';
import './Reports.css';

export default function ReportReasonPicker({ options, error, reload, value, onChange, disabled }) {
  const name = useId();
  return <fieldset className="report-reason-picker" disabled={disabled}>
    <legend>Lý do báo cáo <span aria-hidden="true">*</span></legend>
    {error ? <div className="report-catalog-error" role="alert">
      <p>{error}</p><button type="button" className="btn btn-ghost" onClick={reload}>Thử lại</button>
    </div> : !options ? <p role="status">Đang tải lý do…</p> :
      options.reasons.map(reason => <label key={reason.code} className="report-reason-option">
        <input type="radio" name={name} value={reason.code} required checked={value === reason.code}
          onChange={() => onChange(reason.code)} />
        <span>{reason.label}</span>
      </label>)}
  </fieldset>;
}
