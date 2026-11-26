import { claimDate } from '@/features/claims/utils/claimUtils';
import '../styles/Handover.css';

export default function HandoverIssueDeadline({ deadline, timeLeft }) {
  if (!deadline) return null;

  return <p className="handover-issue-deadline">
    <strong>{timeLeft ? `Còn ${timeLeft} để báo sự cố` : 'Đã hết hạn báo sự cố'}</strong>
    <small>Hạn gửi <time dateTime={deadline}>{claimDate(deadline)}</time></small>
  </p>;
}
