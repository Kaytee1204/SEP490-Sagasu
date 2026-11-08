import { useState } from "react";
import Notice from "./Notice";
import { workflowApi } from "../api/workflowApi";
export default function IntakeScheduleForm({ transfer, onClose, onDone, onBusyChange }) {
  const needsReason = transfer.appointment && !["DECLINED", "EXPIRED"].includes(transfer.appointment.responseStatus);
  const [form, setForm] = useState({ scheduledAt: "", location: transfer.appointment?.location || "", note: "", reason: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  async function submit(e) {
    e.preventDefault(); if (busy) return;
    setBusy(true); onBusyChange(true); setError("");
    try { const result = await workflowApi.schedule(transfer.id, { ...form, scheduledAt: `${form.scheduledAt}:00+07:00`, expectedVersion: transfer.version }); onDone(result); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); onBusyChange(false); }
  }
  return <form className="of-form of-intake-form" onSubmit={submit}>
    <h3>{transfer.appointment ? "Hẹn lại ngày mang đồ đến" : "Hẹn người giao mang đồ đến"}</h3>
    <p className="of-muted">Lịch hẹn được gửi qua email để người giao đồng ý hoặc từ chối trong 12 giờ. Văn phòng chỉ tiếp nhận đồ khi bạn xác nhận đã nhận thực tế.</p>
    <Notice error>{error}</Notice>
    <label>Ngày và giờ (UTC+7) *<input type="datetime-local" required disabled={busy} value={form.scheduledAt} onChange={(e) => change("scheduledAt", e.target.value)} /><small>Chọn thời điểm cách hiện tại hơn 12 giờ.</small></label>
    <label>Địa điểm tiếp nhận *<input required maxLength={1000} disabled={busy} value={form.location} onChange={(e) => change("location", e.target.value)} placeholder="Văn phòng, tòa nhà, số phòng…" /></label>
    <label>Lời nhắn<textarea maxLength={2000} disabled={busy} value={form.note} onChange={(e) => change("note", e.target.value)} placeholder="Hướng dẫn liên hệ hoặc đồ cần mang kèm…" /></label>
    {needsReason && <label>Lý do văn phòng đổi lịch *<textarea required maxLength={2000} disabled={busy} value={form.reason} onChange={(e) => change("reason", e.target.value)} /></label>}
    {transfer.appointment && !needsReason && <p className="of-muted">{transfer.appointment.responseStatus === "DECLINED" ? "Hẹn lại theo đề nghị của người giao; không cần nhập thêm lý do." : "Lịch trước đã hết hạn phản hồi. Bạn có thể sắp xếp lịch mới."}</p>}
    <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={onClose}>Để sau</button><button className="of-button of-primary" disabled={busy}>{busy ? "Đang lưu…" : "Lưu lịch và gửi email"}</button></div>
  </form>;
}
