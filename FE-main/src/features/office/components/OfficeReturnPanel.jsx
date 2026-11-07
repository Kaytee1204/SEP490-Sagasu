import { useState } from "react";
import { CheckCircle2, CalendarClock, ShieldCheck, PackageCheck } from "lucide-react";
import useLoad from "../hooks/useLoad";
import { workflowApi } from "../api/workflowApi";
import { date } from "../officeUtils";
import LoadState from "./LoadState";
import Notice from "./Notice";
import ReturnActionDialog from "./ReturnActionDialog";

export default function OfficeReturnPanel({ claim, mine, onChanged }) {
  const state = useLoad(() => workflowApi.detail(claim.id), `${claim.id}:${claim.version}`);
  const [action, setAction] = useState(null);
  const [message, setMessage] = useState(null);
  const workflow = state.data;
  const h = workflow?.handover;
  const done = h?.status === "COMPLETED";
  const latest = workflow?.verifications[0];
  const steps = [
    [CalendarClock, "Đồng ý lịch hẹn", claim.appointment?.responseStatus === "CONFIRMED" || !!h],
    [ShieldCheck, "Xác minh tại quầy", latest?.result === "PASSED" && latest?.appointmentId === claim.appointment?.id || !!h],
    [PackageCheck, "Giao đồ", !!h],
    [CheckCircle2, "Xác nhận đã nhận", done],
  ];
  return <section className="of-panel of-return-panel">
    <h2>Tiến trình trao trả</h2>
    <Notice error={message?.error}>{message?.text}</Notice>
    <LoadState state={state}>{workflow && <>
      <ol className="of-return-steps">{steps.map(([Icon, label, complete], i) => <li key={label} className={complete ? "is-complete" : ""}><span><Icon size={18} /></span><div><small>Bước {i + 1}</small><strong>{label}</strong></div>{complete && <CheckCircle2 size={16} aria-label="Hoàn tất" />}</li>)}</ol>
      <p className="of-callout" role="status">{workflow.nextStep}</p>
      <div className="of-actions of-actions-wrap">
        {!mine && workflow.canVerify && <button className={`of-button ${workflow.canRecord ? "" : "of-primary"}`} onClick={() => setAction("verify")}>{latest ? "Xác minh lại" : "Xác minh tại quầy"}</button>}
        {!mine && workflow.canRecord && <button className="of-button of-primary" onClick={() => setAction("handover")}>Ghi nhận giao đồ</button>}
        {!mine && workflow.canReschedule && <button className="of-button" onClick={() => setAction("reschedule")}>Đổi lịch</button>}
        {!mine && workflow.canCancel && <button className="of-button of-danger" onClick={() => setAction("cancel")}>Hủy lịch và thu hồi duyệt</button>}
        {workflow.canConfirm && <><button className="of-button of-primary" onClick={() => setAction("confirm")}>Tôi đã nhận đúng đồ</button><button className="of-button" onClick={() => setAction("issue")}>Có vấn đề khi nhận đồ</button></>}
      </div>
      {h && <div className="of-return-record"><h3>Biên bản giao đồ #{h.id}</h3><dl className="of-facts"><div><dt>Người giao</dt><dd>{h.officer}</dd></div><div><dt>Người nhận</dt><dd>{h.recipient}</dd></div><div><dt>Thời điểm giao</dt><dd>{date(h.recordedAt)}</dd></div>{h.confirmedAt && <div><dt>Xác nhận đã nhận</dt><dd>{date(h.confirmedAt)}</dd></div>}</dl><p className="of-prose">{h.condition}</p><div className="of-gallery">{h.evidence.map((e) => <a href={e.url} target="_blank" rel="noreferrer" key={e.id}><img src={e.url} alt={e.name || "Ảnh giao đồ"} /></a>)}</div><Notice error>{h.issueNote}</Notice></div>}
      {!!workflow.verifications.length && <details className="of-verification-history"><summary>Lịch sử xác minh ({workflow.verifications.length})</summary><ol className="of-timeline">{workflow.verifications.map((v) => <li key={v.id}><strong>{v.result === "PASSED" ? "Đạt" : "Chưa đạt"}</strong><p className="of-muted">{v.officer} · {date(v.at)}</p><p className="of-prose">{v.note}</p></li>)}</ol></details>}
      {action && <ReturnActionDialog action={action} claim={claim} workflow={workflow} onClose={() => setAction(null)} onDone={(result) => {setAction(null);setMessage({error: result.emailStatus === "FAILED",text: result.emailStatus === "FAILED" ? "Đã đổi lịch nhưng chưa gửi được email. Hãy dùng nút gửi lại thư lịch hẹn." : "Đã cập nhật tiến trình trao trả."});state.reload();onChanged(result);}} />}
    </>}</LoadState>
  </section>;
}
