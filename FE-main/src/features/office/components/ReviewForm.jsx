import { earliestAppointment } from "../officeUtils";
import { useState } from "react";
import { CalendarDays } from "lucide-react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";
import RequestedInformation from "./RequestedInformation";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

export default function ReviewForm({ claim, onDone }) {
  const [ending, setEnding] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
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
  if (claim.status === "NEEDS_INFO" && !ending) return <section className="of-panel of-form">
    <h2>Đang chờ bổ sung thông tin</h2>
    <RequestedInformation claim={claim} />
    <p className="of-muted">Người yêu cầu cần phản hồi trước khi tiếp tục xét duyệt. Nội dung bổ sung sẽ được ghi trong lịch sử xử lý.</p>
    <details><summary>Cần kết thúc yêu cầu?</summary><p>Chỉ từ chối khi có căn cứ; việc chưa phản hồi không tự xác định người yêu cầu không phải chủ đồ.</p>
      <button type="button" className="of-button of-danger" onClick={() => setEnding(true)}>Từ chối yêu cầu…</button>
    </details>
  </section>;
  function handleFormSubmit(e) {
    e.preventDefault();
    if (busy || !form.reason.trim()) return;
    setError("");
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
          form.decision === "APPROVED" && form.scheduledAt
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
          title: "Xác nhận duyệt & hẹn nhận đồ?",
          description: `Bạn sắp duyệt yêu cầu #${claim.id} của ${claim.claimantName} và gửi email lịch hẹn nhận đồ. Hãy chắc chắn đã đối chiếu kỹ căn cứ sở hữu.`,
          confirmLabel: "Đồng ý duyệt",
        }
      : form.decision === "REJECTED"
        ? {
            variant: "danger",
            title: "Bạn có chắc chắn từ chối?",
            description: `Yêu cầu nhận đồ #${claim.id} của ${claim.claimantName} sẽ bị từ chối. Hành động này không thể hoàn tác.`,
            confirmLabel: "Xác nhận từ chối",
          }
        : {
            variant: "info",
            title: "Gửi yêu cầu bổ sung thông tin?",
            description: `Hồ sơ #${claim.id} sẽ chuyển sang trạng thái chờ ${claim.claimantName} phản hồi bổ sung căn cứ.`,
            confirmLabel: "Gửi yêu cầu",
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
                Email có nút đồng ý hoặc từ chối lịch hẹn, không cần đăng nhập.
                Không phản hồi trong 12 giờ từ khi gửi thành công sẽ tự động bị
                từ chối.
              </p>
            </div>
            <label>
              Ngày và giờ hẹn (UTC+7) *
              <input
                required
                type="datetime-local" min={earliestAppointment()}
                disabled={busy}
                value={form.scheduledAt}
                onChange={(e) => change("scheduledAt", e.target.value)}
              />
              <small>
                Chọn giờ hẹn sau ít nhất 12 giờ và 1 phút để người nhận có đủ
                thời gian phản hồi.
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
        {ending && <button type="button" className="of-button" disabled={busy} onClick={() => setEnding(false)}>Tiếp tục chờ bổ sung</button>}
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
