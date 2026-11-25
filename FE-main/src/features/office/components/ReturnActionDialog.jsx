import { earliestAppointment, pickupScheduleError, pickupScheduleHint, validateRequiredText } from "../officeUtils";
import { useState } from "react";
import Dialog from "./Dialog";
import Notice from "./Notice";
import EvidencePicker from "./EvidencePicker";
import CounterConfirmationNotice from "./CounterConfirmationNotice";
import { workflowApi } from "../api/workflowApi";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

const titles = {
  schedule: "Lập lịch nhận tài sản", revoke: "Thu hồi chấp thuận",
  verify: "Xác minh người nhận tại quầy", reschedule: "Đổi lịch hẹn nhận đồ",
  handover: "Lập biên bản bàn giao",
  "reopen-confirmation": "Mở lại xác nhận tại quầy",
  "stop-handover": "Dừng lượt bàn giao",
};

export default function ReturnActionDialog({ action, claim, workflow, onClose, onDone }) {
  const receipt = claim.receipt;
  const [form, setForm] = useState({ result: "", note: "", reason: "", items: receipt?.items || "",
    condition: receipt?.condition || "", differenceNote: "", scheduledAt: "",
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
  const normalize = (value) => value?.trim().replace(/\s+/g, " ") || "";
  const hasDifference = action === "handover" && (
    (normalize(receipt?.items) && normalize(receipt.items) !== normalize(form.items)) ||
    (normalize(receipt?.condition) && normalize(receipt.condition) !== normalize(form.condition))
  );
  function handleFormSubmit(event) {
    event.preventDefault();
    if (busy) return;
    setError("");
    if (!validateRequiredText(event.currentTarget)) return;
    const scheduleError = ["schedule", "reschedule"].includes(action) && pickupScheduleError(form.scheduledAt);
    if (scheduleError) { setError(scheduleError); return; }
    if (action === "handover" && !files.length) { setError("Vui lòng đính kèm ít nhất một ảnh bàn giao."); return; }
    setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy) return;
    setBusy(true); setError("");
    try {
      let result;
      if (action === "handover") {
        result = await workflowApi.record(claim.id, { expectedVersion: claim.version,
          items: form.items.trim(), condition: form.condition.trim(), differenceNote: hasDifference ? form.differenceNote.trim() : null,
          clientRequestId: requestId, assetsCheckedAtCounter: checked }, files);
      } else if (["reopen-confirmation", "stop-handover"].includes(action)) {
        const reference = { handoverId: workflow.handover.id, expectedVersion: workflow.handover.version };
        const body = action === "reopen-confirmation" ? { ...reference, recipientPresent: checked }
          : { ...reference, itemAtOffice: checked, reason: form.reason.trim() };
        result = await workflowApi.action(claim.id, action, body);
      } else {
        const body = { ...form, expectedVersion: claim.version };
        if (action === "schedule" || action === "reschedule") body.scheduledAt = `${form.scheduledAt}:00+07:00`;
        result = await workflowApi.action(claim.id, action, body);
      }
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
    handover: { variant: "info", title: "Ghi nhận đã trao trả?", description: `Hệ thống sẽ mở biên bản để ${claim.claimantName} xác nhận ngay tại quầy.`, confirmLabel: "Ghi nhận đã trao trả" },
    "reopen-confirmation": { variant: "info", title: "Mở lại xác nhận tại quầy?", description: "Người nhận có 15 phút để đối chiếu và xác nhận biên bản này.", confirmLabel: "Mở lại xác nhận" },
    "stop-handover": { variant: "warning", title: "Dừng lượt bàn giao này?", description: "Biên bản được lưu trong lịch sử. Khi đủ điều kiện, nhân viên có thể lập biên bản mới.", confirmLabel: "Dừng bàn giao" },
  }[action] || { variant: "warning", title: "Bạn có chắc chắn?", description: "Xác nhận thực hiện thao tác xử lý hồ sơ này.", confirmLabel: "Xác nhận" };
  return <Dialog title={titles[action]} busy={busy} onClose={onClose}>
    {({ guard }) => <>
    <form className="of-form" onSubmit={handleFormSubmit}>
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
        <p><strong>Người nhận tài sản</strong> · {claim.claimantName}</p>
        <section className="of-handover-baseline" aria-label="Thông tin khi tiếp nhận">
          <h3>Thông tin khi tiếp nhận</h3>
          {receipt?.code && <p>Mã phiếu <strong>{receipt.code}</strong></p>}
          <dl className="of-facts">
            <div><dt>Tài sản và phụ kiện</dt><dd className="of-prose">{receipt?.items || "Phiếu cũ chưa ghi danh sách tài sản. Vui lòng đối chiếu thực tế và bổ sung bên dưới."}</dd></div>
            <div><dt>Tình trạng</dt><dd className="of-prose">{receipt?.condition || "Chưa ghi nhận"}</dd></div>
          </dl>
        </section>
        <label>Tài sản và phụ kiện bàn giao *<textarea required maxLength={2000} value={form.items} disabled={busy}
          onChange={(e) => change("items", e.target.value)} placeholder="Ví dụ: 1 máy ảnh, 1 pin, 1 dây đeo" /></label>
        <label>Tình trạng tài sản khi bàn giao *<textarea required maxLength={2000} value={form.condition} disabled={busy}
          onChange={(e) => change("condition", e.target.value)} placeholder="Ví dụ: Thân máy có vết trầy nhẹ ở góc phải" /></label>
        {Boolean(hasDifference) && <label>Giải trình chênh lệch so với phiếu tiếp nhận *<textarea required maxLength={2000} value={form.differenceNote} disabled={busy}
          onChange={(e) => change("differenceNote", e.target.value)} placeholder="Vui lòng nêu nội dung thay đổi và lý do đã xác minh." /></label>}
        <p className="of-callout">Vui lòng chụp rõ tài sản, phụ kiện và tình trạng khi giao. Nếu người nhận đồng ý, xin ghi lại hình ảnh người nhận cùng tài sản tại thời điểm bàn giao. Vui lòng tránh để lộ giấy tờ và thông tin cá nhân.</p>
        <EvidencePicker files={files} setFiles={setFiles} disabled={busy} onProcessing={setImagesProcessing} label="Thêm ảnh bàn giao" />
        <CounterConfirmationNotice />
      </>}
      {action === "stop-handover" && <label>Lý do dừng bàn giao *<textarea required maxLength={2000} value={form.reason} disabled={busy} onChange={(e) => change("reason", e.target.value)} /></label>}
      {["handover", "reopen-confirmation", "stop-handover"].includes(action) && <label className="of-check">
        <input type="checkbox" required checked={checked} disabled={busy} onChange={(e) => setChecked(e.target.checked)} />
        <span>{{ handover: "Tôi xác nhận đã đối chiếu tài sản và phụ kiện tại quầy với người nhận đã được xác minh.",
          "reopen-confirmation": "Tôi xác nhận người nhận đang có mặt tại quầy để xác nhận biên bản.",
          "stop-handover": "Tôi xác nhận tài sản và phụ kiện vẫn do văn phòng lưu giữ." }[action]}</span>
      </label>}
      <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={() => guard()}>Quay lại</button><button className="of-button of-primary" disabled={busy}>{busy ? "Đang lưu…" : (action === "handover" ? "Kiểm tra và lưu" : titles[action])}</button></div>
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
    </>}
  </Dialog>;
}
