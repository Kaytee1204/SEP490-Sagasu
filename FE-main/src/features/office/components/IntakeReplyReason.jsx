export default function IntakeReplyReason({ value, onChange, disabled }) {
  return <label className="of-reply-reason">Lý do cần đổi lịch *<textarea required maxLength={2000} disabled={disabled} value={value} onChange={e => onChange(e.target.value)} placeholder="Ví dụ: Bận giờ học; có thể đến chiều thứ ba…" /><small>Bạn có thể ghi thêm thời gian thuận tiện để văn phòng hẹn lại.</small></label>;
}
