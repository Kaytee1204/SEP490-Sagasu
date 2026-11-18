const stages = {
  RECEIVED: ["Đã nhận đồ", "complete"],
  REJECTED: ["Đã từ chối", "closed"],
  CANCELLED: ["Đã hủy yêu cầu", "closed"],
  UNSCHEDULED: ["Cần hẹn lịch", "attention"],
  UNSENT: ["Chưa gửi được lịch", "attention"],
  CONFIRMED: ["Đã đồng ý lịch", "complete"],
  PENDING: ["Chờ phản hồi lịch", "active"],
};

export default function TransferStage({ stage }) {
  const [label, tone] = stages[stage] ?? stages.PENDING;
  return <span className={`of-progress-badge ${tone}`}><span aria-hidden="true" />{label}</span>;
}
