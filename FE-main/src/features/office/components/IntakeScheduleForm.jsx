import { earliestAppointment } from "../officeUtils";
import { useState } from "react";
import Notice from "./Notice";
import { workflowApi } from "../api/workflowApi";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";
export default function IntakeScheduleForm({ transfer, onClose, onDone, onBusyChange }) {
  const needsReason = transfer.appointment?.status === "SCHEDULED";
  const [form, setForm] = useState({ scheduledAt: "", location: transfer.appointment?.location || "", note: "", reason: "" });
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  function handleFormSubmit(e) {
    e.preventDefault(); if (busy) return;
    setError(""); setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy) return;
    setBusy(true); onBusyChange(true); setError("");
    try { const result = await workflowApi.schedule(transfer.id, { ...form, scheduledAt: `${form.scheduledAt}:00+07:00`, expectedVersion: transfer.version }); setConfirmOpen(false); onDone(result); }
    catch (failure) { setError(failure.message); setConfirmOpen(false); }
    finally { setBusy(false); onBusyChange(false); }
  }
  return <form className="of-form of-intake-form" onSubmit={handleFormSubmit}>
    <h3>{transfer.appointment ? "Hẹn lại ngày mang đồ đến" : "Hẹn người giao mang đồ đến"}</h3>
    <p className="of-muted">Người giao sẽ nhận giờ hẹn và hạn tiếp nhận. Yêu cầu tự hủy nếu văn phòng chưa nhận đồ sau giờ hẹn 72 giờ.</p>
    <Notice error>{error}</Notice>
    <label>Ngày và giờ (UTC+7) *<input type="datetime-local" min={earliestAppointment()} required disabled={busy} value={form.scheduledAt} onChange={(e) => change("scheduledAt", e.target.value)} /><small>Chọn giờ hẹn sau ít nhất 12 giờ và 1 phút.</small></label>
    <label>Địa điểm tiếp nhận *<input required maxLength={1000} disabled={busy} value={form.location} onChange={(e) => change("location", e.target.value)} placeholder="Văn phòng, tòa nhà, số phòng…" /></label>
    <label>Lời nhắn<textarea maxLength={2000} disabled={busy} value={form.note} onChange={(e) => change("note", e.target.value)} placeholder="Hướng dẫn liên hệ hoặc đồ cần mang kèm…" /></label>
    {needsReason && <label>Lý do văn phòng đổi lịch *<textarea required maxLength={2000} disabled={busy} value={form.reason} onChange={(e) => change("reason", e.target.value)} /></label>}
    <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={onClose}>Để sau</button><button className="of-button of-primary" disabled={busy}>{busy ? "Đang lưu…" : "Lưu lịch và gửi email"}</button></div>
    <ConfirmActionPopup
      open={confirmOpen}
      variant="info"
      title={transfer.appointment ? "Xác nhận đổi lịch tiếp nhận?" : "Xác nhận gửi lịch hẹn?"}
      description="Hệ thống sẽ lưu lịch hẹn mang đồ đến văn phòng và gửi email thông báo cho người giao."
      confirmLabel="Lưu & gửi email"
      cancelLabel="Quay lại"
      busy={busy}
      onConfirm={executeSubmit}
      onClose={() => setConfirmOpen(false)}
    />
  </form>;
}
