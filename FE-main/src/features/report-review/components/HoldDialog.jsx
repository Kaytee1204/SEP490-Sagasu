import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox } from "./Shared";

export default function HoldDialog({ action, data, onClose, onSaved }) {
  const [reason, setReason] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const hold = action === "HOLD";
  async function submit(event) {
    event.preventDefault();
    if (busy || !reason.trim()) return;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await reviewApi.act(data.report.report_id, {
          version: data.report.version,
          action,
          conclusion: reason.trim(),
          basis: reason.trim(),
        }),
      );
      onClose();
    } catch (problem) {
      setError(problem.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={hold ? "Tạm dừng giao nhận" : "Gỡ tạm dừng giao nhận"}
      busy={busy}
      onClose={onClose}
    >
      <form className="rr-stack" onSubmit={submit}>
        <p className="rr-callout">
          {hold
            ? "Chưa tiếp nhận hoặc bàn giao món đồ này trong lúc xác minh. Báo cáo vẫn tiếp tục được xem xét."
            : "Cho phép tiếp tục giao nhận nếu món đồ không còn lệnh tạm dừng từ báo cáo khác."}
        </p>
        <label>
          {hold ? "Lý do cần tạm dừng" : "Lý do có thể tiếp tục giao nhận"} *
          <textarea
            autoFocus
            required
            disabled={busy}
            rows={4}
            maxLength={5000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </label>
        <ErrorBox>{error}</ErrorBox>
        <footer className="rr-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Quay lại
          </button>
          <button className="rr-primary" disabled={busy || !reason.trim()}>
            {busy
              ? "Đang lưu…"
              : hold
                ? "Xác nhận tạm dừng"
                : "Xác nhận gỡ tạm dừng"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
