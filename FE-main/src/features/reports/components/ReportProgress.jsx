import WorkflowSteps from '@/components/common/WorkflowSteps';

const stages = {
  OPEN: { completed: 1, reportMessage: 'Đang chờ Quản lý nhận báo cáo.', appealMessage: 'Đang chờ Quản lý nhận kháng cáo.' },
  IN_REVIEW: { completed: 1, reportMessage: 'Quản lý đang xem xét báo cáo.', appealMessage: 'Quản lý đang xem xét kháng cáo.' },
  RESOLVED: { completed: 3, reportMessage: 'Báo cáo đã có kết luận.', appealMessage: 'Quản lý yêu cầu xét lại người nhận.' },
  REJECTED: { completed: 2, reportMessage: 'Quản lý đã bác báo cáo.', appealMessage: 'Quản lý đã bác kháng cáo.' },
};

export default function ReportProgress({ status, appeal = false, compact = false }) {
  const stage = stages[status];
  if (!stage) return null;
  const label = appeal ? 'Tiến trình kháng cáo' : 'Tiến trình xử lý báo cáo';
  return <div className={compact ? undefined : 'report-progress'}>
    {!compact && <h2>{label}</h2>}
    <WorkflowSteps label={label}
      steps={[appeal ? 'Gửi kháng cáo' : 'Gửi báo cáo', status === 'OPEN' ? 'Chờ Quản lý xử lý' : 'Quản lý xem xét', status === 'REJECTED' ? 'Đã bác bỏ' : 'Kết quả xử lý']}
      completed={stage.completed} state={status === 'REJECTED' ? 'stopped' : 'active'} />
    <p role="status">{appeal ? stage.appealMessage : stage.reportMessage}</p>
  </div>;
}
