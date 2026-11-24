import { useState } from "react";
import { CheckCircle2, CalendarClock, ShieldCheck, PackageCheck } from "lucide-react";
import { claimActivityReason, date } from "../officeUtils";
import LoadState from "./LoadState";
import Notice from "./Notice";
import ReturnActionDialog from "./ReturnActionDialog";
import AppointmentReply from "./AppointmentReply";

const FILED_APPEAL_MESSAGES = {
  OPEN: "Đang chờ quản lý xem xét.",
  IN_REVIEW: "Quản lý đang xem xét tranh chấp.",
  RESOLVED: "Quản lý đã có kết luận.",
  REJECTED: "Quản lý đã bác bỏ tranh chấp.",
};

export default function OfficeReturnPanel({ claim, state, mine, onChanged }) {
  const [action, setAction] = useState(null);
  const [message, setMessage] = useState(null);
  const workflow = state.data;
  const filedAppeal = !mine ? workflow?.filedAppeal : null;
  const appealOpen = ["OPEN", "IN_REVIEW"].includes(filedAppeal?.status);
  const h = workflow?.handover;
  const latest = workflow?.verifications[0];
  const currentVerification = latest?.appointmentId === claim.appointment?.id ? latest : null;
  const verified = currentVerification?.result === "PASSED";
  const ended = ["REJECTED", "CANCELLED", "CLOSED", "COMPLETED"].includes(claim.status);
  const needsNewSchedule = claim.appointment && ["LEGACY", "UNSENT"].includes(claim.appointment.responseStatus) && new Date(claim.appointment.scheduledAt) <= new Date();
  const lastReason = [...(claim.activities || [])].reverse().find(a => a.reason && ["REJECT", "CANCEL", "OFFICE_CANCEL", "AUTO_CLOSE"].includes(a.action));
  const steps = [
    [CalendarClock, "Đồng ý lịch hẹn", claim.appointment?.responseStatus === "CONFIRMED" || !!h],
    [ShieldCheck, "Xác minh tại quầy", verified || !!h],
    [PackageCheck, "Trao trả hoàn tất", h?.status === "COMPLETED"],
  ];
  return <section className="of-panel of-return-panel">
    <h2>{mine && ended ? "Kết quả yêu cầu" : "Tiến trình trao trả"}</h2>
    <Notice error={message?.error}>{message?.text}</Notice>
    <LoadState state={state}>{workflow && <>
      {mine && claim.status === "APPROVED" && claim.appointment && !h && <AppointmentReply key={claim.appointment.id} appointment={claim.appointment} id={claim.id} onChanged={() => onChanged({})} />}
      {!(mine && (ended || needsNewSchedule)) && <ol className="of-return-steps">{steps.map(([Icon, label, complete], i) => <li key={label} className={complete ? "is-complete" : ""}><span><Icon size={18} /></span><div><small>Bước {i + 1}</small><strong>{label}</strong></div>{complete && <CheckCircle2 size={16} aria-label="Hoàn tất" />}</li>)}</ol>}
      {mine && ended && <p className="op-result-title">{{COMPLETED:"Đã nhận lại đồ",REJECTED:"Yêu cầu đã bị từ chối",CANCELLED:"Yêu cầu đã hủy",CLOSED:"Yêu cầu đã đóng"}[claim.status]}</p>}
      {mine && ended && lastReason && <p className="of-prose"><strong>Lý do: </strong>{claimActivityReason(lastReason, true)}</p>}
      {currentVerification && (
        <p><strong>Kết quả xác minh tại quầy: </strong>{verified ? "Đạt" : "Không đạt"}</p>
      )}
      <div className="of-callout" role="status">
        {filedAppeal && <>
          <strong>Đã ghi nhận tranh chấp #{filedAppeal.reportId}</strong>
          <p>{FILED_APPEAL_MESSAGES[filedAppeal.status]}</p>
        </>}
        {(!appealOpen || claim.status !== "PENDING") && <p>{workflow.nextStep}</p>}
        {workflow.appealDeadline && new Date(workflow.appealDeadline)>new Date() &&
          <p>Hạn gửi tranh chấp <strong>{date(workflow.appealDeadline)}</strong>.</p>}
      </div>
      <div className="of-actions of-actions-wrap">
        {!mine && workflow.canSchedule && <button className="of-button of-primary" onClick={() => setAction("schedule")}>Lập lịch nhận tài sản</button>}
        {!mine && workflow.canRevoke && <button className="of-button" onClick={() => setAction("revoke")}>Thu hồi chấp thuận</button>}
        {!mine && workflow.canVerify && <button className={`of-button ${workflow.canRecord ? "" : "of-primary"}`} onClick={() => setAction("verify")}>{latest ? "Xác minh lại" : "Xác minh tại quầy"}</button>}
        {!mine && workflow.canRecord && <button className="of-button of-primary" onClick={() => setAction("handover")}>Hoàn tất trao trả</button>}
        {!mine && !verified && workflow.canReschedule && <button className="of-button" onClick={() => setAction("reschedule")}>Đổi lịch</button>}
        {!mine && workflow.canComplete && <button className="of-button of-primary" onClick={() => setAction("complete")}>Xác nhận hoàn tất trao trả</button>}
      </div>
      {h && <div className="of-return-record">
        <h3>Biên bản giao đồ #{h.id}</h3>
        <dl className="of-facts">
          <div><dt>Người giao</dt><dd>{h.officer}</dd></div>
          <div><dt>Người nhận</dt><dd>{h.recipient}</dd></div>
          <div><dt>Thời điểm giao</dt><dd>{date(h.recordedAt)}</dd></div>
          {h.confirmedAt && <div><dt>Hoàn tất lúc</dt><dd>{date(h.confirmedAt)}</dd></div>}
        </dl>
        {h.condition && <p className="of-prose"><strong>Tình trạng đồ khi giao: </strong>{h.condition}</p>}
        {!!h.evidence.length && <>
          <h4>Ảnh giao đồ</h4>
          <div className="of-gallery">{h.evidence.map((e) => <a href={e.url} target="_blank" rel="noreferrer" key={e.id}><img src={e.url} alt={e.name || "Ảnh giao đồ"} /></a>)}</div>
        </>}
        {h.issueNote && <Notice error><strong>Nội dung phản ánh: </strong>{h.issueNote}</Notice>}
      </div>}
      {action && <ReturnActionDialog action={action} claim={claim} workflow={workflow} onClose={() => setAction(null)} onDone={(result) => {setAction(null);setMessage({error: result.emailStatus === "FAILED",text: result.emailStatus === "FAILED" ? "Đã lưu lịch. Email chưa gửi được, người nhận vẫn có thể phản hồi trên hệ thống." : "Đã cập nhật tiến trình trao trả."});onChanged(result);}} />}
    </>}</LoadState>
  </section>;
}
