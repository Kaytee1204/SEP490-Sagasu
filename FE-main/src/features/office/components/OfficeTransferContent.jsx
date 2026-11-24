import "../PersonalOffice.css";
import { useState } from "react";
import { CalendarDays, CheckCircle2, Package, XCircle } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { officeApi } from "../api/officeApi";
import { workflowApi } from "../api/workflowApi";
import useLoad from "../hooks/useLoad";
import { date } from "../officeUtils";
import WorkflowPageFrame from "./WorkflowPageFrame";
import LoadState from "./LoadState";
import Notice from "./Notice";
import IntakeEvidencePicker from "./IntakeEvidencePicker";
import IntakeScheduleForm from "./IntakeScheduleForm";
import TransferStage from "./TransferStage";
import { transferStage } from "../transferStage";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";

const outcomes = { RECEIVED: "Văn phòng đã nhận đồ", REJECTED: "Yêu cầu đã bị từ chối", CANCELLED: "Yêu cầu đã hủy" };
export default function OfficeTransferContent({ id, mine, openSchedule, onClose, onDone }) {
  const { user } = useAuth();
  const state = useLoad(() => workflowApi.transfer(id), id);
  const locations = useLoad(() => !mine && user?.role === "APPROVER" ? officeApi.locations() : Promise.resolve([]), "locations");
  const [selectedMode, setMode] = useState(openSchedule ? "schedule" : null);
  const [form, setForm] = useState({ locationId: "", condition: "", reason: "", reconciliationNote: "" });
  const [checked, setChecked] = useState(false);
  const [files,setFiles] = useState([]);
  const [busy, setBusy] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const transfer = state.data;
  const pending = transfer?.status === "PENDING";
  const officer = !mine && user?.role === "APPROVER";
  const self = transfer && officer && transfer.requestedBy === user?.id;
  const mode = selectedMode === "schedule" && (!officer || self) ? null : selectedMode;
  const a = transfer?.appointment;
  const stage = transfer ? transferStage(transfer) : null;
  const needsSchedule = stage === "UNSCHEDULED";
  const receiving = mode === "receive";
  const change = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  function updated(result) {
    setMode(null); setError("");setChecked(false);setFiles([]);setConfirmOpen(false);
    setNotice(result === "FAILED" ? { error: true, text: "Lịch đã hiện trên hệ thống. Email chưa gửi được, bạn có thể gửi lại." } : result === "SENT" ? { text: "Đã gửi thông tin lịch và hạn tiếp nhận qua email." } : { text: "Đã cập nhật yêu cầu." });
    state.reload();onDone();
  }
  async function resend() {
    if (busy) return;setBusy(true);setError("");
    try { updated(await workflowApi.resendIntake(id)); } catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  function handleFormSubmit(event) {
    event.preventDefault();
    if (busy || (receiving && (!files.length || !checked))) return;
    setError("");
    setConfirmOpen(true);
  }
  async function executeSubmit() {
    if (busy || (receiving && (!files.length || !checked))) return;
    setBusy(true);setError("");
    try { await workflowApi.decide(id, { ...form, decision: receiving ? "RECEIVED" : mine ? "CANCELLED" : "REJECTED", locationId: form.locationId ? Number(form.locationId) : null, expectedVersion: transfer.version },files);updated(); }
    catch (failure) {setError(failure.message); setConfirmOpen(false);}finally {setBusy(false);}
  }
  const Frame=WorkflowPageFrame;
  return <Frame personal={mine} hideBack className={mine ? "" : "of-staff-transfer-detail"} title={mine ? "Gửi đồ đến văn phòng" : officer ? "Yêu cầu tiếp nhận đồ" : "Chi tiết chuyển giao"} onClose={onClose} busy={busy} resetKey={`${transfer?.version}:${mode}`}>
    {({ guard }) => <>
    <Notice error={notice?.error}>{notice?.text}</Notice>
    <LoadState state={state}>{transfer && <>
      {!pending && <div className={`of-transfer-outcome ${transfer.status === "RECEIVED" ? "is-received" : "is-ended"}`} role="status">
        {transfer.status === "RECEIVED" ? <CheckCircle2 size={28} /> : <XCircle size={28} />}
        <div><strong>{outcomes[transfer.status]}</strong><p>{date(transfer.processedAt)}</p></div>
      </div>}
      {mine ? (
        <><div className="of-intake-summary"><strong>{transfer.title}</strong><span>Yêu cầu #{id}</span></div><p className="of-muted">Người giao: {transfer.finder} · Gửi lúc {date(transfer.requestedAt)}</p></>
      ) : (
        <div className="of-transfer-detail-summary">
          <div className="of-transfer-item">
            {transfer.images?.[0] ? <img src={transfer.images[0]} alt="" /> : <span><Package size={24} aria-hidden="true" /></span>}
            <div>
              <small>Yêu cầu #{id} · Gửi lúc {date(transfer.requestedAt)}</small>
              <h2>{transfer.title}</h2>
              <p>Người giao: {transfer.finder || "Chưa ghi nhận"}</p>
            </div>
          </div>
          {pending && <TransferStage stage={stage} />}
          <button className="of-button of-transfer-close" disabled={busy} onClick={() => guard()}>Đóng</button>
        </div>
      )}
      <details open={mine || undefined} className="of-transfer-description"><summary>Thông tin và ảnh đồ vật</summary><p className="of-prose">{transfer.description}</p>
      <div className="of-gallery">{transfer.images.map((url, i) => <a href={url} target="_blank" rel="noreferrer" key={url}><img src={url} alt={`Đồ cần tiếp nhận, ảnh ${i + 1}`} /></a>)}</div>
      </details>
      {!pending ? <>
        {transfer.reason && <div className="of-reason"><strong>Lý do:</strong><p className="of-prose">{transfer.reason}</p></div>}
        {transfer.reconciliationNote && <p className="of-prose">{transfer.reconciliationNote}</p>}
        {transfer.receiptId && <p>Hồ sơ tiếp nhận #{transfer.receiptId} · Văn phòng đang phụ trách đồ vật này.</p>}
        {transfer.receiptId && <section><h3>Ảnh khi văn phòng tiếp nhận</h3>
          {transfer.receiptEvidence?.length ? <div className="of-gallery">{transfer.receiptEvidence.map((url,i)=><a key={url} href={url} target="_blank" rel="noreferrer"><img src={url} alt={`Ảnh tiếp nhận ${i+1}`} /></a>)}</div> : <p className="of-muted">Phiếu cũ chưa lưu ảnh tiếp nhận riêng.</p>}
        </section>}
        {mine && <div className="of-actions"><button className="of-button" onClick={() => guard()}>Đóng</button></div>}
      </> : <>
        {transfer.activeClaims > 0 && <p className="of-muted">Đồ vật này có {transfer.activeClaims} yêu cầu nhận lại đồ.</p>}
        {a && <section className={`of-intake-appointment${needsSchedule ? " is-old" : ""}`}>
          <h3><CalendarDays size={18} /> {needsSchedule ? "Lịch hẹn cũ" : "Lịch mang đồ đến"}</h3>
          <dl className="of-facts">
            <div><dt>Giờ hẹn</dt><dd>{date(a.scheduledAt)}</dd></div>
            <div><dt>Địa điểm tiếp nhận</dt><dd>{a.location}</dd></div>
            {!needsSchedule && a.intakeDeadline && <div><dt>Hạn tiếp nhận</dt><dd>{date(a.intakeDeadline)}</dd></div>}
          </dl>
          {!needsSchedule && a.intakeDeadline && <p>Yêu cầu tự hủy nếu văn phòng chưa nhận đồ trước hạn tiếp nhận.</p>}
          {!needsSchedule && !a.intakeDeadline && <p>Lịch cũ chưa có hạn tiếp nhận.</p>}
          {!mine && !needsSchedule && a.emailStatus === "FAILED" && <p>Email thông báo chưa gửi được.</p>}
          {a.note && <p className="of-prose"><strong>Lời nhắn:</strong> {a.note}</p>}
          {a.cancellationReason && <p className="of-prose"><strong>Lý do:</strong> {a.cancellationReason}</p>}
        </section>}
        {!a && <p className="of-callout">{mine ? "Văn phòng sẽ hẹn bạn mang đồ đến. Bạn cứ giữ đồ cẩn thận trong lúc chờ nhé." : officer && !self ? "Chưa có lịch. Nếu người giao đã đến quầy, có thể ghi nhận tiếp nhận ngay." : "Văn phòng chưa hẹn ngày mang đồ đến."}</p>}
        <Notice error>{error}</Notice>
        {self && <Notice>Nhờ một nhân viên khác xử lý yêu cầu tiếp nhận của bạn.</Notice>}
        {!mode && (officer && !self) && <div className={`of-actions of-actions-wrap${mine ? "" : " of-transfer-taskbar"}`}>
          {needsSchedule && <button type="button" className="of-button of-primary" onClick={() => setMode("schedule")}>{a ? "Sắp xếp lịch mới" : "Hẹn mang đồ đến"}</button>}
          <button type="button" className={`of-button${needsSchedule ? "" : " of-primary"}`} onClick={() => setMode("receive")}>Tiếp nhận tại quầy</button>
        </div>}
        {!mode && (mine || officer && !self) && <details className="of-secondary-actions"><summary>{mine ? "Hủy yêu cầu tiếp nhận" : "Điều chỉnh yêu cầu"}</summary><div className="of-actions of-actions-wrap">
          {mine ? <><p>Chỉ hủy nếu bạn không còn muốn gửi đồ cho văn phòng.</p><button className="of-button of-danger" disabled={busy} onClick={() => setMode("withdraw")}>Hủy yêu cầu</button></> : <>
            {a?.status === "SCHEDULED" && <button className="of-button" disabled={busy} onClick={() => setMode("schedule")}>Đổi lịch hẹn</button>}
            {a?.status === "SCHEDULED" && a.intakeDeadline && <button className="of-button" disabled={busy} onClick={resend}>{busy ? "Đang gửi…" : "Gửi lại email lịch hẹn"}</button>}
            <button className="of-button of-danger" disabled={busy} onClick={() => setMode("reject")}>Từ chối tiếp nhận</button>
          </>}
        </div></details>}
        {mode === "schedule" && <IntakeScheduleForm transfer={transfer} onBusyChange={setBusy} onClose={() => guard(() => setMode(null))} onDone={updated} />}
        {["receive", "reject", "withdraw"].includes(mode) && <form className="of-form of-intake-form" onSubmit={handleFormSubmit}>
          <h3>{receiving ? "Ghi nhận đồ đã nhận" : mine ? "Hủy yêu cầu tiếp nhận" : "Từ chối yêu cầu tiếp nhận"}</h3>
          {receiving && <p className="of-callout">Chỉ xác nhận khi người giao đã mang đồ đến và bạn đã kiểm tra thực tế. Việc hẹn lịch chưa chuyển trách nhiệm giữ đồ.</p>}
          {receiving ? <>
            <label>Vị trí lưu kho *<select required value={form.locationId} disabled={busy || locations.loading} onChange={(e) => change("locationId", e.target.value)}><option value="">Chọn vị trí</option>{(locations.data || []).filter((l) => l.active).map((l) => <option key={l.id} value={l.id}>{l.code} · {l.name}</option>)}</select></label>
            <Notice error>{locations.error}</Notice>
            <label>Tình trạng đồ khi nhận *<textarea required maxLength={2000} value={form.condition} disabled={busy} onChange={(e) => change("condition", e.target.value)} /></label>
            {transfer.previousHandovers > 0 && <label>Đối chiếu việc giao lại đồ *<small>Đã có biên bản giao đồ trước đó. Ghi rõ người giao lại và tình trạng đồ thực tế.</small><textarea required maxLength={2000} value={form.reconciliationNote} disabled={busy} onChange={(e) => change("reconciliationNote", e.target.value)} /></label>}
            <IntakeEvidencePicker files={files} setFiles={setFiles} disabled={busy} />
          <label className="of-check"><input type="checkbox" required checked={checked} disabled={busy} onChange={(e) => setChecked(e.target.checked)} /><span>Tôi đã trực tiếp nhận và kiểm tra đúng đồ.</span></label>
          </> : <label>Lý do *<textarea required maxLength={2000} disabled={busy} value={form.reason} onChange={(e) => change("reason", e.target.value)} placeholder={mine ? "Vì sao bạn muốn hủy yêu cầu?" : "Giải thích ngắn gọn để người giao biết…"} /></label>}
          <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={() => guard(() => setMode(null))}>Quay lại</button><button className={`of-button ${receiving ? "of-primary" : "of-danger"}`} disabled={busy || (receiving && (!files.length || !checked))}>{busy ? "Đang lưu…" : receiving ? "Xác nhận đã nhận đồ" : mine ? "Xác nhận hủy yêu cầu" : "Từ chối yêu cầu"}</button></div>
        </form>}
        <ConfirmActionPopup
          open={confirmOpen}
          variant={receiving ? "success" : "danger"}
          title={receiving ? "Xác nhận đã tiếp nhận đồ?" : mine ? "Bạn có chắc chắn hủy yêu cầu?" : "Từ chối yêu cầu tiếp nhận?"}
          description={receiving ? `Ghi nhận văn phòng đã trực tiếp nhận món đồ "${transfer.title}" vào kho và tạo hồ sơ tiếp nhận.` : mine ? "Yêu cầu gửi đồ về văn phòng sẽ bị hủy. Bạn có thể gửi lại sau nếu vẫn giữ đồ." : `Yêu cầu tiếp nhận "${transfer.title}" sẽ bị từ chối. Hành động này không thể hoàn tác.`}
          confirmLabel={receiving ? "Đồng ý tiếp nhận" : mine ? "Xác nhận hủy" : "Xác nhận từ chối"}
          cancelLabel="Quay lại"
          busy={busy}
          onConfirm={executeSubmit}
          onClose={() => setConfirmOpen(false)}
        />
      </>}
    </>}</LoadState>
    </>}
  </Frame>;
}
