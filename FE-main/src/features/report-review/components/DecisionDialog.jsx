import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox } from "./Shared";
import { labels } from "../format";
const warnings = {
  HIDE_POST: "Bài đăng sẽ được ẩn khỏi tìm kiếm công khai.",
  SUSPEND_ACCOUNT:
    "Tài khoản được chọn sẽ bị đình chỉ truy cập. Quản lý chỉ xử lý tài khoản người dùng hoặc nhân viên tiếp nhận.",
  HOLD: "Tạm dừng tiếp nhận và bàn giao đồ liên quan đến báo cáo này.",
  RELEASE:
    "Gỡ lệnh tạm dừng của báo cáo này. Lệnh từ báo cáo khác vẫn có hiệu lực.",
  HANDOVER_COMPLETED:
    "Xác nhận đồ đã trả đúng người: hoàn tất bàn giao, đóng bài đăng và các yêu cầu đang chờ.",
  HANDOVER_CONTINUE:
    "Cho phép người nhận tiếp tục xác nhận bàn giao. Báo cáo này được kết thúc, bằng chứng vẫn được giữ lại.",
  CLAIM_REJECTED:
    "Kết thúc yêu cầu nhận đồ này và mở lại bài đăng. Chỉ dùng khi đã xác minh đồ vẫn ở người giữ.",
  RESOLVE:
    "Ghi kết quả giải quyết. Phiếu trả đồ đã hoàn tất sẽ không bị đảo ngược.",
  DISMISS:
    "Bác bỏ báo cáo với lý do rõ ràng. Người liên quan sẽ nhận được kết luận.",
  NOTE: "Ghi chú này chỉ quản lý được xem.",
};
export default function DecisionDialog({ action, data, onClose, onSaved }) {
  const [conclusion, setConclusion] = useState(""),
    [basis, setBasis] = useState(""),
    [targetId, setTarget] = useState(""),
    [custodyVerified, setCustody] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await reviewApi.act(data.report.report_id, {
          version: data.report.version,
          action,
          conclusion: conclusion.trim(),
          basis: basis.trim(),
          targetId: targetId ? Number(targetId) : null,
          custodyVerified,
        }),
      );
      onClose();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog title={labels[action]} busy={busy} onClose={onClose}>
      <form onSubmit={submit} className="rr-stack">
        <p className="rr-callout">{warnings[action]}</p>
        {action === "SUSPEND_ACCOUNT" && (
          <label>
            Tài khoản cần xử lý *
            <select
              required
              value={targetId}
              onChange={(e) => setTarget(e.target.value)}
            >
              <option value="">Chọn tài khoản</option>
              {data.current.participants
                .filter(
                  (p) =>
                    p.id !== data.report.reporter_id &&
                    ["USER", "APPROVER"].includes(p.role),
                )
                .map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.full_name} · {p.email}
                  </option>
                ))}
            </select>
          </label>
        )}
        <label>
          {action === "NOTE"
            ? "Nội dung ghi chú"
            : "Kết luận gửi cho người liên quan"}{" "}
          *
          <textarea
            autoFocus
            required
            maxLength={5000}
            rows={4}
            value={conclusion}
            onChange={(e) => setConclusion(e.target.value)}
            placeholder="Nêu kết quả rõ ràng, không đưa thông tin riêng tư của người khác vào kết luận."
          />
        </label>
        <label>
          Căn cứ đối chiếu (nội bộ) *
          <textarea
            required
            maxLength={5000}
            rows={4}
            value={basis}
            onChange={(e) => setBasis(e.target.value)}
            placeholder="Dẫn ảnh, thời điểm, nội dung trao đổi hoặc biên bản đã kiểm tra."
          />
        </label>
        {action === "CLAIM_REJECTED" && (
          <label className="rr-check">
            <input
              type="checkbox"
              required
              checked={custodyVerified}
              onChange={(e) => setCustody(e.target.checked)}
            />
            Đã xác minh đồ vẫn ở người giữ và có thể trao cho người khác.
          </label>
        )}
        {[
          "RESOLVE",
          "DISMISS",
          "HANDOVER_COMPLETED",
          "HANDOVER_CONTINUE",
          "CLAIM_REJECTED",
        ].includes(action) &&
          data.inquiries.some((i) => !i.responded_at) && (
            <p className="rr-callout">
              Vẫn còn yêu cầu bổ sung chưa được trả lời. Nếu đã đủ căn cứ, ghi
              rõ lý do kết luận ở trên.
            </p>
          )}
        <ErrorBox>{error}</ErrorBox>
        <footer className="rr-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Quay lại
          </button>
          <button
            className="rr-primary"
            disabled={busy || !conclusion.trim() || !basis.trim()}
          >
            {busy ? "Đang lưu…" : "Xác nhận xử lý"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
