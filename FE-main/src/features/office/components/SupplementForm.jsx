import { useState } from "react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";
import RequestedInformation from "./RequestedInformation";
import EvidencePicker from "./EvidencePicker";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

export default function SupplementForm({ claim, onDone }) {
  const [information, setInformation] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState("");
  const [files, setFiles] = useState([]);
  const [processing, setProcessing] = useState(false);
  function handleFormSubmit(e) {
    e.preventDefault();
    if (busy || processing || !information.trim()) return;
    setError("");
    setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy || processing || !information.trim()) return;
    setBusy(true);
    setError("");
    try {
      await officeApi.supplement(claim.id, {
        information: information.trim(),
        expectedVersion: claim.version,
      }, files);
      setConfirmOpen(false);
      onDone();
    } catch (err) {
      setError(err.message);
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="of-panel">
      <h2>Bổ sung thông tin</h2>
      <RequestedInformation claim={claim} />
      <p className="of-muted">Trả lời nội dung văn phòng đang hỏi. Sau khi gửi, yêu cầu của bạn sẽ được xét duyệt lại.</p>
      <form className="of-form" onSubmit={handleFormSubmit}>
        <Notice error>{error}</Notice>
        <label>
          Thông tin bạn bổ sung *
          <textarea
            required
            maxLength={2000}
            value={information}
            disabled={busy}
            onChange={(e) => setInformation(e.target.value)}
          />
        </label>
        <p className="of-muted">Có thể đính kèm ảnh để làm rõ câu trả lời. Mỗi lần bổ sung được lưu riêng; thông tin và ảnh đã gửi trước đó vẫn được giữ nguyên.</p>
        <EvidencePicker files={files} setFiles={setFiles} disabled={busy} onProcessing={setProcessing} />
        <button
          className="of-button of-primary"
          disabled={busy || processing || !information.trim()}
        >
          {busy ? "Đang gửi…" : "Gửi thông tin bổ sung"}
        </button>
      </form>
      <ConfirmActionPopup
        open={confirmOpen}
        variant="info"
        title="Gửi thông tin bổ sung?"
        description="Nội dung và ảnh bổ sung sẽ được gửi đến nhân viên văn phòng để tiếp tục xét duyệt hồ sơ của bạn."
        confirmLabel="Xác nhận gửi"
        cancelLabel="Kiểm tra lại"
        busy={busy}
        onConfirm={executeSubmit}
        onClose={() => setConfirmOpen(false)}
      />
    </section>
  );
}
