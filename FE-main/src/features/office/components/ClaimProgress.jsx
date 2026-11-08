import { date, progressLabels } from "../officeUtils";
export default function ClaimProgress({ stage, scheduledAt }) {
  const tone = stage === "COMPLETED" ? "complete" : ["REJECTED", "CANCELLED", "DISPUTED", "VERIFICATION_FAILED", "REPLY_EXPIRED"].includes(stage) ? "attention" : "active";
  return <div className="of-claim-progress"><span className={`of-progress-badge ${tone}`}><span aria-hidden="true" />{progressLabels[stage] || "Chờ cập nhật"}</span>{scheduledAt && <small>Hẹn: {date(scheduledAt)}</small>}</div>;
}
