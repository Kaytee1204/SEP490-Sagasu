import { useState } from "react";
import { CalendarDays, MapPin, Clock3, CheckCircle2 } from "lucide-react";
import { workflowApi } from "../api/workflowApi";
import { date } from "../officeUtils";
import Notice from "./Notice";
import IntakeReplyReason from "./IntakeReplyReason";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

export default function AppointmentReply({ appointment, id, intake = false, onChanged, onBusyChange }) {
  const [declining, setDeclining] = useState(false);
  const [confirmDecision, setConfirmDecision] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [reason, setReason] = useState("");
  const expired = appointment.responseDeadline && new Date(appointment.responseDeadline) <= new Date();
  const status = ["CANCELLED", "COMPLETED"].includes(appointment.status) ? appointment.status : result || (appointment.responseStatus === "PENDING" && expired ? "EXPIRED" : appointment.responseStatus);
  const pending = status === "PENDING";
  const oldAppointment = ["LEGACY", "UNSENT"].includes(status);
  const past = new Date(appointment.scheduledAt) <= new Date();
  function requestReply(decision) {
    if (busy) return;
    if (intake && decision === "DECLINE" && !reason.trim()) { setError("Vui lòng ghi lý do cần đổi lịch."); return; }
    setError("");
    setConfirmDecision(decision);
  }
  async function executeReply() {
    if (busy || !confirmDecision) return;
    setBusy(true); onBusyChange?.(true); setError("");
    try {
      const response = await workflowApi.reply(id, intake, { appointmentId: appointment.id, decision: confirmDecision, reason: intake ? reason.trim() : null });
      setConfirmDecision(null); setResult(response.status); setDeclining(false); onChanged();
    } catch (failure) { setError(failure.message); setConfirmDecision(null); }
    finally { setBusy(false); onBusyChange?.(false); }
  }
  return <section className="of-appointment-reply" aria-label={intake ? "Phản hồi lịch mang đồ đến" : "Phản hồi lịch nhận đồ"}>
    <div className="of-appointment-heading"><CalendarDays size={22} /><div><small>{intake ? "LỊCH MANG ĐỒ ĐẾN" : "LỊCH NHẬN LẠI ĐỒ"}</small><h3>{date(appointment.scheduledAt)}</h3></div></div>
    <p className="of-appointment-location"><MapPin size={17} aria-hidden="true" /><span><strong>Địa điểm: </strong>{appointment.location}</span></p>
    {appointment.note && <p className="of-prose"><strong>Lời nhắn về lịch hẹn: </strong>{appointment.note}</p>}
    <Notice error>{error}</Notice>
    {status === "COMPLETED" && <p className="of-reply-outcome" role="status">Lịch hẹn đã hoàn tất.</p>}
    {oldAppointment && <p className="of-reply-outcome" role="status">{past ? "Lịch hẹn này đã qua. Vui lòng liên hệ văn phòng để được hẹn lại." : status === "LEGACY" ? "Lịch hẹn cũ chưa áp dụng xác nhận trực tuyến. Vui lòng liên hệ văn phòng để xác nhận lịch." : "Văn phòng đang chuẩn bị lời mời. Bạn chưa cần phản hồi."}</p>}
    {pending ? <>
      <p className="of-reply-deadline"><Clock3 size={16} />Phản hồi trước {date(appointment.responseDeadline)}</p>
      {!declining ? <><p>Bạn có thể đến vào thời gian này không? Chỉ cần phản hồi tại đây hoặc trong email.</p><div className="of-actions of-actions-wrap"><button className="of-button of-primary" disabled={busy} onClick={() => requestReply("CONFIRM")}>{busy ? "Đang lưu…" : "Đồng ý lịch hẹn"}</button><button className="of-button" disabled={busy} onClick={() => setDeclining(true)}>{intake ? "Đề nghị đổi lịch" : "Không thể đến"}</button></div></> : <div className="of-decline-confirm">{intake && <IntakeReplyReason value={reason} onChange={setReason} disabled={busy} />}<strong>{intake ? "Đề nghị văn phòng đổi lịch?" : "Từ chối lịch hẹn này?"}</strong><p>{intake ? "Yêu cầu tiếp nhận vẫn được giữ lại để văn phòng sắp xếp lịch khác." : "Yêu cầu nhận đồ sẽ bị từ chối và món đồ được mở lại cho yêu cầu khác."}</p><div className="of-actions of-actions-wrap"><button className="of-button" disabled={busy} onClick={() => setDeclining(false)}>Quay lại</button><button className="of-button of-danger" disabled={busy} onClick={() => requestReply("DECLINE")}>{busy ? "Đang lưu…" : intake ? "Gửi đề nghị đổi lịch" : "Xác nhận từ chối lịch"}</button></div></div>}
    </> : <p className="of-reply-outcome" role="status">{status === "CONFIRMED" && <CheckCircle2 size={18} />}{({ CONFIRMED: "Bạn đã đồng ý. Vui lòng đến đúng thời gian và địa điểm trên.", DECLINED: intake ? "Đã gửi đề nghị đổi lịch. Văn phòng sẽ sắp xếp lại." : "Bạn đã từ chối lịch nhận đồ.", EXPIRED: "Đã hết thời gian phản hồi. Vui lòng liên hệ văn phòng để được hỗ trợ.", CANCELLED: "Lịch này không còn hiệu lực.", UNSENT: "Văn phòng đang chuẩn bị lời mời. Bạn sẽ có thể phản hồi khi lịch được gửi." })[status]}</p>}
    <ConfirmActionPopup
      open={Boolean(confirmDecision)}
      variant={confirmDecision === "CONFIRM" ? "success" : "danger"}
      title={confirmDecision === "CONFIRM" ? "Xác nhận đồng ý lịch hẹn?" : intake ? "Gửi đề nghị đổi lịch?" : "Bạn có chắc chắn từ chối lịch hẹn?"}
      description={confirmDecision === "CONFIRM" ? `Bạn xác nhận sẽ đến văn phòng vào lúc ${date(appointment.scheduledAt)} tại ${appointment.location}.` : intake ? "Văn phòng sẽ nhận được lý do và sắp xếp lại lịch mới cho bạn." : "Yêu cầu nhận đồ sẽ bị từ chối và món đồ được mở lại cho yêu cầu khác. Hành động này không thể hoàn tác."}
      confirmLabel={confirmDecision === "CONFIRM" ? "Đồng ý lịch hẹn" : intake ? "Gửi đề nghị" : "Xác nhận từ chối"}
      cancelLabel="Quay lại"
      busy={busy}
      onConfirm={executeReply}
      onClose={() => setConfirmDecision(null)}
    />
  </section>;
}
