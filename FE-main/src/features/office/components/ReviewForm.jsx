import { useState } from "react";
import { CalendarDays } from "lucide-react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";

export default function ReviewForm({ claim, onDone }) {
  const [form, setForm] = useState({
    decision: claim.status === "NEEDS_INFO" ? "REJECTED" : "APPROVED",
    reason: "",
    scheduledAt: "",
    location: "",
    email: claim.claimantEmail || "",
    appointmentNote: "",
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const r = await officeApi.review(claim.id, {
        ...form,
        expectedVersion: claim.version,
        email: form.email.trim() || null,
        scheduledAt:
          form.decision === "APPROVED" && form.scheduledAt
            ? `${form.scheduledAt}:00+07:00`
            : null,
      });
      onDone(r);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="of-panel">
      <h2>Xét duyệt yêu cầu</h2>
      <form className="of-form" onSubmit={submit}>
        <Notice error>{error}</Notice>
        <label>
          Quyết định
          <select
            value={form.decision}
            disabled={busy}
            onChange={(e) => change("decision", e.target.value)}
          >
            {claim.status === "PENDING" && (
              <>
                <option value="APPROVED">Duyệt & hẹn nhận đồ</option>
                <option value="NEEDS_INFO">Yêu cầu bổ sung thông tin</option>
              </>
            )}
            <option value="REJECTED">Từ chối yêu cầu</option>
          </select>
        </label>
        <label>
          {form.decision === "NEEDS_INFO"
            ? "Thông tin cần bổ sung *"
            : "Căn cứ quyết định *"}
          <textarea
            required
            maxLength={2000}
            disabled={busy}
            value={form.reason}
            onChange={(e) => change("reason", e.target.value)}
            placeholder="Nêu rõ kết quả đối chiếu hoặc thông tin cần bổ sung…"
          />
        </label>
        {form.decision === "APPROVED" && (
          <>
            <div className="of-callout">
              <CalendarDays size={20} />
              <strong>Hẹn người nhận đến văn phòng</strong>
              <p className="of-muted">
                Lịch được lưu cùng quyết định duyệt, sau đó hệ thống gửi thư.
              </p>
            </div>
            <label>
              Ngày và giờ hẹn (UTC+7) *
              <input
                required
                type="datetime-local"
                disabled={busy}
                value={form.scheduledAt}
                onChange={(e) => change("scheduledAt", e.target.value)}
              />
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
          disabled={busy || !form.reason.trim()}
        >
          {busy
            ? "Đang lưu & xử lý…"
            : form.decision === "APPROVED"
              ? "Duyệt & gửi lịch hẹn"
              : form.decision === "REJECTED"
                ? "Xác nhận từ chối"
                : "Gửi yêu cầu bổ sung"}
        </button>
      </form>
    </section>
  );
}
