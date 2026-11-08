import { useState } from "react";
import { CalendarDays, MapPin, Clock3, CheckCircle2 } from "lucide-react";
import { workflowApi } from "../api/workflowApi";
import { date } from "../officeUtils";
import Notice from "./Notice";
import IntakeReplyReason from "./IntakeReplyReason";

export default function AppointmentReply({ appointment, id, intake = false, onChanged, onBusyChange }) {
  const [declining, setDeclining] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  const [reason, setReason] = useState("");
  const expired = appointment.responseDeadline && new Date(appointment.responseDeadline) <= new Date();
  const status = result || (appointment.responseStatus === "PENDING" && expired ? "EXPIRED" : appointment.responseStatus);
  const pending = status === "PENDING";
  async function reply(decision) {
    if (busy) return;
    if (intake && decision === "DECLINE" && !reason.trim()) { setError("Vui lòng ghi lý do cần đổi lịch."); return; }
    setBusy(true); onBusyChange?.(true); setError("");
    try {
      const response = await workflowApi.reply(id, intake, { appointmentId: appointment.id, decision, reason: intake ? reason.trim() : null });
      setResult(response.status); setDeclining(false); onChanged();
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); onBusyChange?.(false); }
  }
  return <section className="of-appointment-reply" aria-label={intake ? "Phản hồi lịch mang đồ đến" : "Phản hồi lịch nhận đồ"}>
    <div className="of-appointment-heading"><CalendarDays size={22} /><div><small>{intake ? "LỊCH MANG ĐỒ ĐẾN" : "LỊCH NHẬN LẠI ĐỒ"}</small><h3>{date(appointment.scheduledAt)}</h3></div></div>
    <p className="of-appointment-location"><MapPin size={17} /><span>{appointment.location}</span></p>
    {appointment.note && <p className="of-prose">{appointment.note}</p>}
    <Notice error>{error}</Notice>
    {pending ? <>
      <p className="of-reply-deadline"><Clock3 size={16} />Phản hồi trước {date(appointment.responseDeadline)}</p>
      {!declining ? <><p>Bạn có thể đến vào thời gian này không? Chỉ cần phản hồi tại đây hoặc trong email.</p><div className="of-actions of-actions-wrap"><button className="of-button of-primary" disabled={busy} onClick={() => reply("CONFIRM")}>{busy ? "Đang lưu…" : "Đồng ý lịch hẹn"}</button><button className="of-button" disabled={busy} onClick={() => setDeclining(true)}>{intake ? "Đề nghị đổi lịch" : "Không thể đến"}</button></div></> : <div className="of-decline-confirm">{intake && <IntakeReplyReason value={reason} onChange={setReason} disabled={busy} />}<strong>{intake ? "Đề nghị văn phòng đổi lịch?" : "Từ chối lịch hẹn này?"}</strong><p>{intake ? "Yêu cầu tiếp nhận vẫn được giữ lại để văn phòng sắp xếp lịch khác." : "Yêu cầu nhận đồ sẽ bị từ chối và món đồ được mở lại cho yêu cầu khác."}</p><div className="of-actions of-actions-wrap"><button className="of-button" disabled={busy} onClick={() => setDeclining(false)}>Quay lại</button><button className="of-button of-danger" disabled={busy} onClick={() => reply("DECLINE")}>{busy ? "Đang lưu…" : intake ? "Gửi đề nghị đổi lịch" : "Xác nhận từ chối lịch"}</button></div></div>}
    </> : <p className="of-reply-outcome" role="status">{status === "CONFIRMED" && <CheckCircle2 size={18} />}{({ CONFIRMED: "Bạn đã đồng ý. Vui lòng đến đúng thời gian và địa điểm trên.", DECLINED: intake ? "Đã gửi đề nghị đổi lịch. Văn phòng sẽ sắp xếp lại." : "Bạn đã từ chối lịch nhận đồ.", EXPIRED: "Đã hết thời gian phản hồi. Vui lòng liên hệ văn phòng để được hỗ trợ.", CANCELLED: "Lịch này không còn hiệu lực.", UNSENT: "Văn phòng đang chuẩn bị lời mời. Bạn sẽ có thể phản hồi khi lịch được gửi." })[status]}</p>}
  </section>;
}
