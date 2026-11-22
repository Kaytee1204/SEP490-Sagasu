import { date, progressLabels } from "../officeUtils";
export default function ClaimProgress({ stage, scheduledAt }) {
  const tone = stage === "COMPLETED" ? "complete"
    : ["CANCELLED", "CLOSED"].includes(stage) ? "closed"
    : ["REVIEW", "APPEAL_WAIT", "APPEAL_REVIEW", "APPEAL_REASSESS", "REJECTED", "DISPUTED",
        "VERIFICATION_FAILED", "REPLY_EXPIRED", "APPOINTMENT_REVIEW"].includes(stage) ? "attention"
    : "active";
  return <div className="of-claim-progress"><span className={`of-progress-badge ${tone}`}><span aria-hidden="true" />{progressLabels[stage] || "Chờ cập nhật"}</span>{scheduledAt && <small>Hẹn: {date(scheduledAt)}</small>}</div>;
}
