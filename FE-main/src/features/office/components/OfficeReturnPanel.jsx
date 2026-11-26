import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { CheckCircle2, CalendarClock, ShieldCheck, PackageCheck } from "lucide-react";
import useDeadlineCountdown from "@/hooks/useDeadlineCountdown";
import { claimActivityReason, date } from "../officeUtils";
import LoadState from "./LoadState";
import Notice from "./Notice";
import ReturnActionDialog from "./ReturnActionDialog";
import OfficePickupAppointment from "./OfficePickupAppointment";
import OfficeReceiptConfirmation from "./OfficeReceiptConfirmation";
import OfficeCounterSession from "./OfficeCounterSession";
import ReportButton from "@/features/reports/components/ReportButton";
import { workflowApi } from "../api/workflowApi";
import { counterConfirmationAnchor } from "../officeRoutes";

const FILED_APPEAL_MESSAGES = {
  OPEN: "Đang chờ quản lý xem xét.",
  IN_REVIEW: "Quản lý đang xem xét tranh chấp.",
  RESOLVED: "Quản lý đã có kết luận.",
  REJECTED: "Quản lý đã bác bỏ tranh chấp.",
};

export default function OfficeReturnPanel({ claim, state, mine, reportExpired, onChanged, onRefresh }) {
  const [action, setAction] = useState(null);
  const workflow = state.data;
  const { hash } = useLocation();
  const timeLeft = useDeadlineCountdown(workflow?.appealState === "OPEN" ? workflow.appealDeadline : null);
  const filedAppeal = !mine ? workflow?.filedAppeal : null;
  const appealOpen = ["OPEN", "IN_REVIEW"].includes(filedAppeal?.status);
  const h = workflow?.handover;
  const handoverId = h?.id;
  const handoverVersion = h?.version;
  const handoverStatus = h?.status;
  const waiting = handoverStatus === "WAITING_CONFIRMATION";
  const canConfirm = workflow?.canConfirmReceipt;
  const canReopen = workflow?.canReopenConfirmation;
  const confirmationOpen = workflow?.counterConfirmationOpen;
  const confirmedByRecipient = h?.status === "COMPLETED" && h.completionMethod === "RECIPIENT";
  const approachingCounter = mine && !handoverId && claim.status === "APPROVED" && claim.appointment?.status === "SCHEDULED";
  const verificationDeadline = workflow?.verificationDeadline;
  const latest = workflow?.verifications[0];
  const currentVerification = latest?.appointmentId === claim.appointment?.id ? latest : null;
  const verified = currentVerification?.result === "PASSED";
  const ended = ["REJECTED", "CANCELLED", "CLOSED", "COMPLETED"].includes(claim.status);
  const lastReason = [...(claim.activities || [])].reverse().find(a => a.reason && ["REJECT", "CANCEL", "OFFICE_CANCEL", "AUTO_CLOSE"].includes(a.action));
  const steps = [
    [CalendarClock, "Đã lập lịch xác minh", claim.appointment?.status === "SCHEDULED" || !!h],
    [ShieldCheck, "Xác minh tại quầy", verified || !!h],
    [PackageCheck, "Xác nhận đã nhận tài sản", h?.status === "COMPLETED"],
  ];

  useEffect(() => {
    if ((!waiting && !approachingCounter) || action || !onRefresh) return;
    let stopped = false;
    let loading = false;
    async function refresh() {
      if (loading || document.visibilityState !== "visible") return;
      loading = true;
      try {
        const next = await workflowApi.detail(claim.id);
        if (!stopped && (next.handover?.id !== handoverId || next.handover?.version !== handoverVersion ||
          next.handover?.status !== handoverStatus || next.verificationDeadline !== verificationDeadline ||
          (waiting && (next.counterConfirmationOpen !== confirmationOpen ||
            next.canConfirmReceipt !== canConfirm || next.canReopenConfirmation !== canReopen)))) {
          stopped = true;
          onRefresh();
        }
      } catch {
        // A later refresh can recover from a temporary connection issue.
      } finally {
        loading = false;
      }
    }
    const interval = window.setInterval(refresh, 3000);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      stopped = true;
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [action, approachingCounter, canConfirm, canReopen, claim.id, confirmationOpen, handoverId, handoverStatus, handoverVersion, onRefresh, verificationDeadline, waiting]);

  useEffect(() => {
    if (mine && handoverId && hash === `#${counterConfirmationAnchor}`) {
      const target = canConfirm ? counterConfirmationAnchor : "ket-qua-ban-giao";
      const frame = window.requestAnimationFrame(() => document.getElementById(target)?.scrollIntoView({ block: "start" }));
      return () => window.cancelAnimationFrame(frame);
    }
  }, [canConfirm, handoverId, hash, mine]);

  return <section className="of-panel of-return-panel" id={mine ? "ket-qua-ban-giao" : undefined}>
    <h2>{mine && ended ? "Kết quả yêu cầu" : "Tiến trình trao trả"}</h2>
    <LoadState state={state}>{workflow && <>
      {mine && claim.status === "APPROVED" && claim.appointment && !h && <OfficePickupAppointment appointment={claim.appointment} verificationDeadline={workflow.verificationDeadline} />}
      {!(mine && ended) && <ol className="of-return-steps">{steps.map(([Icon, label, complete], i) => <li key={label} className={complete ? "is-complete" : ""}><span><Icon size={18} /></span><div><small>Bước {i + 1}</small><strong>{label}</strong></div>{complete && <CheckCircle2 size={16} aria-label="Hoàn tất" />}</li>)}</ol>}
      {mine && ended && <p className="op-result-title">{{COMPLETED:h?.completionMethod === "TIMEOUT" ? "Đã kết thúc bàn giao" : "Đã nhận lại đồ",REJECTED:"Yêu cầu đã bị từ chối",CANCELLED:"Yêu cầu đã hủy",CLOSED:"Yêu cầu đã đóng"}[claim.status]}</p>}
      {mine && ended && lastReason && <p className="of-prose"><strong>Lý do: </strong>{claimActivityReason(lastReason, true)}</p>}
      {currentVerification && (
        <p><strong>Kết quả xác minh tại quầy: </strong>{verified ? "Đạt" : "Không đạt"}</p>
      )}
      {confirmedByRecipient ? <div className="of-counter-confirmed" role="status">
        <CheckCircle2 size={22} aria-hidden="true" />
        <div><strong>Người nhận đã xác nhận</strong><p>Thời điểm xác nhận <time dateTime={h.confirmedAt}>{date(h.confirmedAt)}</time></p></div>
      </div> : <div className="of-callout" role="status">
        {filedAppeal && <>
          <strong>Đã ghi nhận tranh chấp #{filedAppeal.reportId}</strong>
          <p>{FILED_APPEAL_MESSAGES[filedAppeal.status]}</p>
        </>}
        {(!appealOpen || claim.status !== "PENDING") && <p>{workflow.nextStep}</p>}
        {!mine && workflow.verificationDeadline && <p>Hạn đến quầy xác minh <strong>{date(workflow.verificationDeadline)}</strong>.</p>}
        {timeLeft ? <p><strong>Còn {timeLeft} để gửi tranh chấp.</strong> Hạn kết thúc lúc {date(workflow.appealDeadline)}.</p>
          : workflow.appealDeadline && new Date(workflow.appealDeadline)>new Date() &&
          <p>Hạn gửi tranh chấp <strong>{date(workflow.appealDeadline)}</strong>.</p>}
      </div>}
      {mine && h && (workflow.reportDeadline || workflow.openHandoverReportId) && <div className="of-callout of-receipt-response">
        {workflow.reportDeadline && (reportExpired
          ? <p>Thời hạn tiếp nhận phản ánh về kết quả trao trả đã kết thúc.</p>
          : <p className="of-report-deadline">Nếu có khiếu nại về kết quả trao trả, bạn vui lòng gửi phản ánh trên Sagasu trước <strong>{date(workflow.reportDeadline)}</strong>.</p>)}
        {workflow.openHandoverReportId
          ? <Link className="of-button" to={`/bao-cao/${workflow.openHandoverReportId}`}>Theo dõi phản ánh đã gửi</Link>
          : workflow.reportDeadline && !reportExpired && <ReportButton type="CLAIM" id={claim.id} targetLabel={claim.postTitle}
            officeHandover={{ id: h.id, confirmedAt: h.confirmedAt, reportDeadline: workflow.reportDeadline }}
            label="Báo cáo sự cố bàn giao" onReported={state.reload} />}
      </div>}
      {!mine && waiting && <OfficeCounterSession claimId={claim.id} deadline={h.confirmationDeadline}
        expired={!confirmationOpen} onRefresh={onRefresh} />}
      <div className="of-actions of-actions-wrap">
        {!mine && workflow.canSchedule && <button className="of-button of-primary" onClick={() => setAction("schedule")}>Lập lịch nhận tài sản</button>}
        {!mine && workflow.canRevoke && <button className="of-button" onClick={() => setAction("revoke")}>Thu hồi chấp thuận</button>}
        {!mine && workflow.canVerify && <button className={`of-button ${workflow.canRecord ? "" : "of-primary"}`} onClick={() => setAction("verify")}>{latest ? "Xác minh lại" : "Xác minh tại quầy"}</button>}
        {!mine && workflow.canRecord && <button className="of-button of-primary" onClick={() => setAction("handover")}>Lập biên bản bàn giao</button>}
        {!mine && !verified && workflow.canReschedule && <button className="of-button" onClick={() => setAction("reschedule")}>Đổi lịch</button>}
        {!mine && canReopen && <button type="button" className="of-button of-primary" onClick={() => setAction("reopen-confirmation")}>Mở lại xác nhận tại quầy</button>}
        {!mine && workflow.canStopHandover && <button type="button" className="of-button of-danger" onClick={() => setAction("stop-handover")}>Dừng lượt bàn giao</button>}
      </div>
      {h && <div className="of-return-record" id={mine ? counterConfirmationAnchor : undefined}>
        <h3>Biên bản bàn giao #{h.id}</h3>
        <dl className="of-facts">
          <div><dt>Người giao</dt><dd>{h.officer}</dd></div>
          <div><dt>Người nhận</dt><dd>{h.recipient}</dd></div>
          <div><dt>Thời điểm lập biên bản</dt><dd>{date(h.recordedAt)}</dd></div>
          {h.confirmedAt && <div><dt>Hoàn tất lúc</dt><dd>{date(h.confirmedAt)}</dd></div>}
        </dl>
        <dl className="of-facts">
          {h.items && <div><dt>Tài sản và phụ kiện bàn giao</dt><dd className="of-prose">{h.items}</dd></div>}
          <div><dt>Tình trạng tài sản khi bàn giao</dt><dd className="of-prose">{h.condition}</dd></div>
          {h.differenceNote && <div><dt>Giải trình chênh lệch</dt><dd className="of-prose">{h.differenceNote}</dd></div>}
        </dl>
        {!!h.evidence.length && <>
          <h4>Ảnh giao đồ</h4>
          <div className="of-gallery">{h.evidence.map((e) => <a href={e.url} target="_blank" rel="noreferrer" key={e.id}><img src={e.url} alt={e.name || "Ảnh giao đồ"} /></a>)}</div>
        </>}
        {h.issueNote && <Notice error><strong>Nội dung phản ánh: </strong>{h.issueNote}</Notice>}
        {mine && canConfirm && <OfficeReceiptConfirmation key={`${h.id}:${h.version}`} claimId={claim.id} handover={h} onConfirmed={onChanged} />}
      </div>}
      {action && <ReturnActionDialog action={action} claim={claim} workflow={workflow} onClose={() => setAction(null)} onDone={(result) => {
        setAction(null);
        onChanged(result);
      }} />}
    </>}</LoadState>
  </section>;
}
