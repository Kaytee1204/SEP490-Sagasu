import { earliestAppointment, pickupScheduleError, pickupScheduleHint, validateRequiredText } from "../officeUtils";
import { useState } from "react";
import Dialog from "./Dialog";
import Notice from "./Notice";
import EvidencePicker from "./EvidencePicker";
import { workflowApi } from "../api/workflowApi";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

const titles = {
  schedule: "Lập lịch nhận tài sản", revoke: "Thu hồi chấp thuận",
  verify: "Xác minh người nhận tại quầy", reschedule: "Đổi lịch hẹn nhận đồ",
  handover: "Hoàn tất trao trả",
  complete: "Xác nhận hoàn tất trao trả",
};

export default function ReturnActionDialog({ action, claim, workflow, onClose, onDone }) {
  const [form, setForm] = useState({ result: "", note: "", reason: "", condition: "", scheduledAt: "",
    location: claim.appointment?.location || "", email: claim.claimantEmail || "" });
  const [files, setFiles] = useState([]);
  const [checked, setChecked] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [requestId] = useState(() => crypto.randomUUID());
  const [saving, setBusy] = useState(false);
  const [imagesProcessing, setImagesProcessing] = useState(false);
  const busy = saving || imagesProcessing;
  const [error, setError] = useState("");
  const change = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  function handleFormSubmit(event) {
    event.preventDefault();
    if (busy) return;
    setError("");
    if (!validateRequiredText(event.currentTarget)) return;
    const scheduleError = ["schedule", "reschedule"].includes(action) && pickupScheduleError(form.scheduledAt);
    if (scheduleError) { setError(scheduleError); return; }
    if (action === "handover" && !files.length) { setError("Thêm ít nhất một ảnh chứng minh việc giao đồ."); return; }
    setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy) return;
    setBusy(true); setError("");
    try {
      const expectedVersion = action === "complete" ? workflow.handover.version : claim.version;
      const body = { ...form, expectedVersion };
      if (action === "schedule" || action === "reschedule") body.scheduledAt = `${form.scheduledAt}:00+07:00`;
      const result = action === "handover"
        ? await workflowApi.record(claim.id, { expectedVersion, condition: form.condition, clientRequestId: requestId }, files)
        : await workflowApi.action(claim.id, action, body);
      setConfirmOpen(false);
      onDone(result);
    } catch (failure) {
      setError(failure.message || "Chưa lưu được. Hãy thử lại.");
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  }
  const confirmMeta = {
    schedule: { variant: "info", title: "Xác nhận lịch hẹn?", description: "Người nhận có 12 giờ để phản hồi kể từ khi lưu lịch. Hệ thống cũng sẽ gửi email mời.", confirmLabel: "Lập lịch" },
    revoke: { variant: "warning", title: "Thu hồi chấp thuận?",
      description: claim.appointment?.status === "SCHEDULED"
        ? "Lịch hẹn hiện tại sẽ bị hủy. Yêu cầu được đưa về bước xét duyệt."
        : "Quyết định duyệt hiện tại sẽ hết hiệu lực. Yêu cầu được đưa về bước xét duyệt.",
      confirmLabel: "Thu hồi chấp thuận" },
    verify: form.result === "PASSED"
      ? { variant: "success", title: "Xác nhận kết quả Đạt?", description: `Ghi nhận ${claim.claimantName} đã vượt qua bước đối chiếu trực tiếp tại quầy và đủ điều kiện nhận đồ.`, confirmLabel: "Xác nhận Đạt" }
      : { variant: "danger", title: "Xác nhận Không đạt & Từ chối?", description: `Yêu cầu #${claim.id} sẽ bị từ chối và lịch hẹn bị hủy. Người này sẽ không thể gửi lại yêu cầu cho món đồ này.`, confirmLabel: "Từ chối nhận đồ" },
    reschedule: { variant: "info", title: "Xác nhận đổi lịch hẹn?", description: "Lịch hẹn cũ sẽ hết hiệu lực. Người nhận có 12 giờ để phản hồi lịch mới kể từ khi lưu.", confirmLabel: "Đồng ý đổi lịch" },
    handover: { variant: "success", title: "Xác nhận hoàn tất trao trả?", description: `Món đồ sẽ được ghi nhận đã bàn giao cho ${claim.claimantName}, xuất khỏi kho và đóng các yêu cầu đang chờ.`, confirmLabel: "Hoàn tất trao trả" },
    complete: { variant: "success", title: "Xác nhận hoàn tất biên bản?", description: "Xác nhận văn phòng đã giao đúng và đủ đồ cho người nhận theo biên bản.", confirmLabel: "Xác nhận hoàn tất" },
  }[action] || { variant: "warning", title: "Bạn có chắc chắn?", description: "Xác nhận thực hiện thao tác xử lý hồ sơ này.", confirmLabel: "Xác nhận" };
  return <Dialog title={titles[action]} busy={busy} onClose={onClose}>
    {({ guard }) => <>
    <form className="of-form" inert={confirmOpen} onSubmit={handleFormSubmit}>
      <Notice error>{error}</Notice>
      <p className="of-muted">Hồ sơ #{claim.id} · {claim.postTitle}</p>
      {action === "verify" && <>
        <p>Đối chiếu người đến quầy với <strong>{claim.claimantName}</strong> và căn cứ sở hữu trong hồ sơ. Chỉ ghi kết quả kiểm tra, tránh lưu toàn bộ số giấy tờ cá nhân.</p>
        <label>Kết quả *<select required value={form.result} disabled={busy} onChange={(e) => change("result", e.target.value)}><option value="" disabled>Chọn kết quả</option><option value="PASSED">Đạt — đủ căn cứ trao trả</option><option value="FAILED">Không đạt — từ chối nhận đồ</option></select></label>
        {form.result === "FAILED" && <Notice error>Yêu cầu sẽ bị từ chối, lịch hẹn bị hủy và đồ được mở cho người khác nhận. Tài khoản này không được yêu cầu lại món đồ này, kể cả tại quầy.</Notice>}
        <label>Nội dung đã đối chiếu *<textarea required maxLength={2000} value={form.note} disabled={busy} onChange={(e) => change("note", e.target.value)} placeholder="Danh tính, dấu hiệu riêng và bằng chứng đã đối chiếu…" /></label>
      </>}
      {["schedule", "reschedule"].includes(action) && <>
        <label>Ngày và giờ hẹn (UTC+7) *<input type="datetime-local" min={earliestAppointment()} required value={form.scheduledAt} disabled={busy} onChange={(e) => change("scheduledAt", e.target.value)} /><small>{pickupScheduleHint}<br />Chọn thời điểm cách hiện tại ít nhất 12 giờ và 1 phút.</small></label>
        <label>Địa điểm *<input required maxLength={1000} value={form.location} disabled={busy} onChange={(e) => change("location", e.target.value)} /></label>
        {action === "schedule" && <label>Thư điện tử người nhận *<input type="email" required maxLength={255} value={form.email} disabled={busy} onChange={(e) => change("email", e.target.value)} /></label>}
        <label>Lời nhắn cho người nhận<textarea maxLength={2000} value={form.note} disabled={busy} onChange={(e) => change("note", e.target.value)} /></label>
      </>}
      {["reschedule", "revoke"].includes(action) && <label>Lý do *<textarea required maxLength={2000} value={form.reason} disabled={busy} onChange={(e) => change("reason", e.target.value)} /></label>}
      {action === "handover" && <>
        <p>Trao trả cho <strong>{claim.claimantName}</strong>. Sau khi lưu, yêu cầu này hoàn tất, đồ ra khỏi kho và các yêu cầu còn chờ được kết thúc.</p>
        <label>Tình trạng đồ và phụ kiện khi giao *<textarea required maxLength={2000} value={form.condition} disabled={busy} onChange={(e) => change("condition", e.target.value)} /></label>
        <EvidencePicker files={files} setFiles={setFiles} disabled={busy} onProcessing={setImagesProcessing} />
      </>}
      {action === "complete" && <p>Biên bản đã ghi nhận giao đồ trước đó. Chỉ hoàn tất khi văn phòng đã giao đủ đồ cho người nhận và kiểm tra thông tin trong biên bản.</p>}
      {["handover", "complete"].includes(action) && <label className="of-check"><input type="checkbox" required checked={checked} disabled={busy} onChange={(e) => setChecked(e.target.checked)} /><span>Tôi xác nhận văn phòng đã giao đúng và đủ đồ cho người nhận đã được xác minh.</span></label>}
      <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={() => guard()}>Để sau</button><button className="of-button of-primary" disabled={busy}>{busy ? "Đang lưu…" : titles[action]}</button></div>
    </form>
    <ConfirmActionPopup
      inline
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
    </>}
  </Dialog>;
}
