import { useState } from "react";
import { workflowApi } from "../api/workflowApi";
import Notice from "./Notice";

export default function OfficeReceiptConfirmation({ claimId, handover, onConfirmed }) {
  const [acknowledged, setAcknowledged] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    if (!acknowledged || saving) return;
    setSaving(true);
    setError("");
    try {
      await workflowApi.action(claimId, "complete", { handoverId: handover.id, expectedVersion: handover.version, acknowledged });
      onConfirmed();
    } catch (problem) {
      setError(problem.message || "Chưa thể lưu xác nhận. Vui lòng thử lại.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form className="of-form of-receipt-confirmation" onSubmit={submit}>
      <h3>Xác nhận biên bản tại quầy</h3>
      <p>Vui lòng kiểm tra tài sản và phụ kiện theo biên bản trên trước khi xác nhận.</p>
      <p>Quý vị có thể gửi phản ánh về kết quả trao trả trong vòng <strong>7 ngày kể từ khi xác nhận</strong>.</p>
      <label className="of-check">
        <input type="checkbox" required checked={acknowledged} disabled={saving} onChange={(event) => setAcknowledged(event.target.checked)} />
        <span>Tôi đã kiểm tra, xác nhận nhận đúng và đủ tài sản, phụ kiện theo biên bản, đồng thời đồng ý với thời hạn tiếp nhận phản ánh nêu trên.</span>
      </label>
      <Notice error>{error}</Notice>
      <div className="of-actions of-actions-wrap">
        <button type="submit" className="of-button of-primary" disabled={!acknowledged || saving}>
          {saving ? "Đang lưu xác nhận…" : "Xác nhận đã nhận đúng và đủ tài sản"}
        </button>
      </div>
    </form>
  );
}
