export const incidentPhaseLabels = {
  BEFORE: 'Trước khi ghi nhận giao đồ',
  DURING: 'Đã ghi nhận giao, chờ xác nhận',
  AFTER: 'Sau khi hoàn tất trao trả',
};

export const handoverStatusLabels = {
  NOT_RECORDED: 'Chưa ghi nhận giao đồ',
  WAITING_CONFIRMATION: 'Chờ người nhận xác nhận',
  COMPLETED: 'Đã hoàn tất trao trả',
  DISPUTED: 'Có sự cố đang được xem xét',
  CANCELLED: 'Đã kết thúc bàn giao',
};

export function handoverIssueDeadline(handover) {
  if (!handover || handover.issueDeadline !== undefined) return handover?.issueDeadline;
  if (handover.status !== 'COMPLETED') return null;

  // Older API responses include the confirmation time but no reporting deadline.
  const confirmedAt = Date.parse(handover.confirmedAt);
  return Number.isFinite(confirmedAt) ? new Date(confirmedAt + 7 * 24 * 60 * 60 * 1000).toISOString() : null;
}
