import { earliestAppointment, pickupScheduleError, pickupScheduleHint, validateRequiredText } from "../officeUtils";
import { useState } from "react";
import { CalendarDays } from "lucide-react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

export default function ReviewForm({ claim, hasCompetitors, onDone }) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [form, setForm] = useState({
    decision: "APPROVED",
    reason: "",
    scheduledAt: "",
    location: "",
    email: claim.claimantEmail || "",
    appointmentNote: "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  const reapproving = form.decision === "APPROVED" && claim.priorWrongRecipientConclusion;
  function handleFormSubmit(e) {
    e.preventDefault();
    if (busy) return;
    setError("");
    if (!validateRequiredText(e.currentTarget)) return;
    const scheduleError = form.decision === "APPROVED" && !hasCompetitors && pickupScheduleError(form.scheduledAt);
    if (scheduleError) { setError(scheduleError); return; }
    setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const r = await officeApi.review(claim.id, {
        ...form,
        expectedVersion: claim.version,
        email: form.email.trim() || null,
        scheduledAt:
          form.decision === "APPROVED" && !hasCompetitors && form.scheduledAt
            ? `${form.scheduledAt}:00+07:00`
            : null,
      });
      setConfirmOpen(false);
      onDone(r);
    } catch (err) {
      setError(err.message);
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  }
  const confirmMeta =
    form.decision === "APPROVED"
      ? {
          variant: "success",
          title: hasCompetitors ? "Xác nhận chấp thuận yêu cầu?" : "Xác nhận duyệt và gửi lịch hẹn?",
          description: hasCompetitors
            ? `Chấp thuận yêu cầu #${claim.id} của ${claim.claimantName}. Các hồ sơ đang chờ có 24 giờ để gửi tranh chấp trước khi lập lịch hẹn.`
            : `Chấp thuận yêu cầu #${claim.id} của ${claim.claimantName} và gửi thư mời xác nhận lịch hẹn.`,
          confirmLabel: "Đồng ý duyệt",
        }
      : {
            variant: "danger",
            title: "Bạn có chắc chắn từ chối?",
            description: `Yêu cầu nhận đồ #${claim.id} của ${claim.claimantName} sẽ bị từ chối. Hành động này không thể hoàn tác.`,
            confirmLabel: "Xác nhận từ chối",
        };
  return (
    <section className="of-panel">
      <h2>Xét duyệt yêu cầu</h2>
      <form className="of-form" onSubmit={handleFormSubmit}>
        <Notice error>{error}</Notice>
        <label>
          Quyết định
          <select
            value={form.decision}
            disabled={busy}
            onChange={(e) => change("decision", e.target.value)}
          >
            <option value="APPROVED">{hasCompetitors ? "Chấp thuận yêu cầu" : "Chấp thuận và lập lịch"}</option>
            <option value="REJECTED">Từ chối yêu cầu</option>
          </select>
        </label>
        {reapproving && <div className="of-callout">
          <strong>Kết luận tranh chấp trước đó</strong>
          <p className="of-prose">{claim.priorWrongRecipientConclusion}</p>
        </div>}
        <label>
          {reapproving ? "Căn cứ mới để chấp thuận lại *" : "Căn cứ quyết định *"}
          <textarea
            required
            maxLength={2000}
            disabled={busy}
            value={form.reason}
            onChange={(e) => change("reason", e.target.value)}
            placeholder={reapproving ? "Nêu căn cứ mới đã được đối chiếu…" : "Nêu kết quả đối chiếu căn cứ sở hữu…"}
          />
        </label>
        {form.decision === "APPROVED" && hasCompetitors &&
          <Notice>Lập lịch sau khi hết hạn gửi tranh chấp và các tranh chấp đã được xử lý.</Notice>}
        {form.decision === "APPROVED" && !hasCompetitors && (
          <>
            <div className="of-callout">
              <CalendarDays size={20} />
              <strong>Hẹn người nhận đến văn phòng</strong>
              <p className="of-muted">
                Lịch xác minh có hiệu lực ngay khi lưu. Hệ thống sẽ thông báo giờ hẹn, địa điểm và hạn đến quầy cho người nhận.
              </p>
            </div>
            <label>
              Ngày và giờ hẹn (UTC+7) *
              <input
                required
                type="datetime-local" min={earliestAppointment(1)}
                disabled={busy}
                value={form.scheduledAt}
                onChange={(e) => change("scheduledAt", e.target.value)}
              />
              <small>{pickupScheduleHint}<br />
                Vui lòng chọn giờ hẹn sau thời điểm hiện tại.
              </small>
            </label>
            <label>
              Địa điểm nhận đồ *
              <input
                required
                maxLength={1000}
                disabled={busy}
                value={form.location}
                onChange={(e) => change("location", e.target.value)}
                placeholder="VD: Quầy Lost & Found, phòng A101"
              />
              <small>Nhập nơi gặp người nhận, không phải ngăn lưu kho.</small>
            </label>
            <label>
              Thư điện tử người nhận *
              <input
                required
                type="email"
                maxLength={255}
                disabled={busy}
                value={form.email}
                onChange={(e) => change("email", e.target.value)}
              />
            </label>
            <label>
              Hướng dẫn thêm
              <textarea
                maxLength={2000}
                disabled={busy}
                value={form.appointmentNote}
                onChange={(e) => change("appointmentNote", e.target.value)}
                placeholder="Giấy tờ cần mang theo, hướng dẫn đến quầy…"
              />
            </label>
          </>
        )}
        <button
          className={`of-button ${form.decision === "REJECTED" ? "of-danger" : "of-primary"}`}
          disabled={busy}
        >
          {busy
            ? "Đang lưu & xử lý…"
            : form.decision === "APPROVED"
              ? hasCompetitors ? "Chấp thuận yêu cầu" : "Duyệt và gửi lịch hẹn"
              : "Xác nhận từ chối"}
        </button>
      </form>
      <ConfirmActionPopup
        open={confirmOpen}
        variant={confirmMeta.variant}
        title={confirmMeta.title}
        description={confirmMeta.description}
        confirmLabel={confirmMeta.confirmLabel}
        cancelLabel="Quay lại"
        busy={busy}
        onConfirm={executeSubmit}
        onClose={() => setConfirmOpen(false)}
      />
    </section>
  );
}
