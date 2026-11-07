import { useState } from "react";
import Dialog from "./Dialog";
import Notice from "./Notice";
import EvidencePicker from "./EvidencePicker";
import { workflowApi } from "../api/workflowApi";

const titles = {
  verify: "Xác minh người nhận tại quầy", reschedule: "Đổi lịch hẹn nhận đồ",
  cancel: "Hủy lịch và thu hồi duyệt", handover: "Ghi nhận giao đồ",
  confirm: "Xác nhận đã nhận đồ", issue: "Phản ánh việc nhận đồ",
};

export default function ReturnActionDialog({ action, claim, workflow, onClose, onDone }) {
  const [form, setForm] = useState({ result: "PASSED", note: "", reason: "", condition: "", scheduledAt: "", location: claim.appointment?.location || "" });
  const [files, setFiles] = useState([]);
  const [checked, setChecked] = useState(false);
  const [requestId] = useState(() => crypto.randomUUID());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    if (action === "handover" && !files.length) { setError("Thêm ít nhất một ảnh chứng minh việc giao đồ."); return; }
    setBusy(true); setError("");
    try {
      const expectedVersion = ["confirm", "issue"].includes(action) ? workflow.handover.version : claim.version;
      const body = { ...form, expectedVersion };
      if (action === "reschedule") body.scheduledAt = `${form.scheduledAt}:00+07:00`;
      const result = action === "handover"
        ? await workflowApi.record(claim.id, { expectedVersion, condition: form.condition, clientRequestId: requestId }, files)
        : await workflowApi.action(claim.id, action, body);
      onDone(result);
    } catch (failure) { setError(failure.message || "Chưa lưu được. Hãy thử lại."); }
    finally { setBusy(false); }
  }
  return <Dialog title={titles[action]} busy={busy} onClose={onClose}>
    <form className="of-form" onSubmit={submit}>
      <Notice error>{error}</Notice>
      <p className="of-muted">Hồ sơ #{claim.id} · {claim.postTitle}</p>
      {action === "verify" && <>
        <p>Đối chiếu người đến quầy với <strong>{claim.claimantName}</strong> và căn cứ sở hữu trong hồ sơ. Chỉ ghi kết quả kiểm tra, tránh lưu toàn bộ số giấy tờ cá nhân.</p>
        <label>Kết quả<select value={form.result} disabled={busy} onChange={(e) => change("result", e.target.value)}><option value="PASSED">Đạt — đủ căn cứ trao trả</option><option value="FAILED">Chưa đạt — chưa được giao đồ</option></select></label>
        <label>Nội dung đã đối chiếu *<textarea required maxLength={2000} value={form.note} disabled={busy} onChange={(e) => change("note", e.target.value)} placeholder="Danh tính, dấu hiệu riêng và bằng chứng đã đối chiếu…" /></label>
      </>}
      {action === "reschedule" && <>
        <Notice>Lịch cũ sẽ hết hiệu lực. Người nhận cần đồng ý lịch mới qua email trong 12 giờ từ khi gửi thành công.</Notice>
        <label>Ngày và giờ mới (UTC+7) *<input type="datetime-local" required value={form.scheduledAt} disabled={busy} onChange={(e) => change("scheduledAt", e.target.value)} /><small>Chọn thời điểm cách hiện tại hơn 12 giờ.</small></label>
        <label>Địa điểm *<input required maxLength={1000} value={form.location} disabled={busy} onChange={(e) => change("location", e.target.value)} /></label>
        <label>Lời nhắn cho người nhận<textarea maxLength={2000} value={form.note} disabled={busy} onChange={(e) => change("note", e.target.value)} /></label>
      </>}
      {action === "cancel" && <Notice error>Lịch hẹn sẽ bị hủy, hồ sơ chuyển sang từ chối. Đồ vẫn ở văn phòng để xét duyệt người khác.</Notice>}
      {action === "issue" && <Notice>Biên bản sẽ tạm dừng hoàn tất. Văn phòng giữ lại nội dung phản ánh; vui lòng liên hệ trực tiếp để được hỗ trợ.</Notice>}
      {["reschedule", "cancel", "issue"].includes(action) && <label>{action === "issue" ? "Nội dung phản ánh" : "Lý do"} *<textarea required maxLength={2000} value={form.reason} disabled={busy} onChange={(e) => change("reason", e.target.value)} /></label>}
      {action === "handover" && <>
        <p>Người nhận: <strong>{claim.claimantName}</strong>. Sau khi ghi nhận, đồ được đưa ra khỏi danh sách lưu kho và chờ người nhận xác nhận.</p>
        <label>Tình trạng đồ và phụ kiện khi giao *<textarea required maxLength={2000} value={form.condition} disabled={busy} onChange={(e) => change("condition", e.target.value)} /></label>
        <EvidencePicker files={files} setFiles={setFiles} disabled={busy} />
      </>}
      {action === "confirm" && <p>Chỉ xác nhận khi bạn đã nhận đúng đồ từ văn phòng và kiểm tra tình trạng. Sau bước này, bài đăng hoàn tất trao trả và các hồ sơ đang chờ được đóng.</p>}
      {["handover", "confirm"].includes(action) && <label className="of-check"><input type="checkbox" required checked={checked} disabled={busy} onChange={(e) => setChecked(e.target.checked)} /><span>{action === "handover" ? "Tôi đã trực tiếp giao đúng đồ cho người nhận đã được xác minh." : "Tôi đã nhận và kiểm tra đúng đồ của mình."}</span></label>}
      <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={onClose}>Để sau</button><button className={`of-button ${action === "cancel" ? "of-danger" : "of-primary"}`} disabled={busy}>{busy ? "Đang lưu…" : titles[action]}</button></div>
    </form>
  </Dialog>;
}
