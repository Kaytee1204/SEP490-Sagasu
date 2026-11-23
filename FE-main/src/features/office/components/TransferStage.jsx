const stages = {
  RECEIVED: ["Đã nhận đồ", "complete"],
  REJECTED: ["Đã từ chối", "closed"],
  CANCELLED: ["Đã hủy yêu cầu", "closed"],
  UNSCHEDULED: ["Cần hẹn lịch", "attention"],
  SCHEDULED: ["Đã hẹn mang đồ đến", "active"],
  PENDING: ["Chờ tiếp nhận", "active"],
};

export default function TransferStage({ stage }) {
  const [label, tone] = stages[stage] ?? stages.PENDING;
  return <span className={`of-progress-badge ${tone}`}><span aria-hidden="true" />{label}</span>;
}
