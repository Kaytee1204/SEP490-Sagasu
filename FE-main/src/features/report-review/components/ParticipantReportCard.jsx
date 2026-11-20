import { ArrowUpRight } from "lucide-react";
import { Link } from "react-router-dom";
import { reportTitles } from "@/features/reports/constants/reportConstants";
import { Badge } from "./Shared";

const finderAppealSteps = {
  OPEN: {
    text: "Chờ Quản lý xử lý kháng cáo. Giữ món đồ và theo dõi tiến trình bàn giao.",
    action: "Theo dõi bàn giao",
  },
  REJECTED: {
    text: "Quản lý đã bác kháng cáo này. Mở hồ sơ để kiểm tra khi nào có thể giao đồ.",
    action: "Theo dõi bàn giao",
  },
  RESOLVED: {
    text: "Quản lý yêu cầu xét lại. Khi mọi kháng cáo đã được xử lý, mở hồ sơ và thu hồi chấp thuận nếu bạn vẫn đang giữ món đồ.",
    action: "Mở hồ sơ để xét lại",
  },
};

export default function ParticipantReportCard({ report, timestamp, children }) {
  const isAccount = report.report_type === "USER";
  const subjectLabel = isAccount ? "Tài khoản" : "Bài đăng";
  const subject = report.report_subject || (report.report_subject_id ? `#${report.report_subject_id}` : null);
  const title = reportTitles[report.report_type];
  const step = finderAppealSteps[report.status === "IN_REVIEW" ? "OPEN" : report.status];
  const claimPath = report.finder_approved_claim_id && report.report_subject_id
    ? `/bai-dang/${report.report_subject_id}/claims/${report.finder_approved_claim_id}`
    : null;

  return (
    <article className="rr-card exchange-inquiry">
      <header className="exchange-report-heading">
        <div>
          <span className="exchange-report-number">Báo cáo #{report.report_id}</span>
          {title ? <h2>{title}</h2> : <h2>Chi tiết báo cáo</h2>}
        </div>
        <Badge value={report.status} />
      </header>
      {(subject || report.report_reason) && <dl className="exchange-report-meta">
        {subject && <div><dt>{subjectLabel}</dt><dd>{subject}</dd></div>}
        {report.report_reason && <div><dt>Lý do</dt><dd>{report.report_reason}</dd></div>}
      </dl>}
      {timestamp && <small className="exchange-report-date">{timestamp}</small>}
      {children}
      {claimPath && step && <div className="exchange-appeal-next">
        <div>
          <h3>Việc người đăng cần làm</h3>
          <p>{step.text}</p>
        </div>
        <Link className={report.status === "RESOLVED" ? "btn btn-accent" : "btn btn-ghost"}
          to={`${claimPath}${report.status === "RESOLVED" ? "#review-approval" : "#handover"}`}>
          {step.action}<ArrowUpRight size={15} aria-hidden="true" />
        </Link>
      </div>}
    </article>
  );
}
