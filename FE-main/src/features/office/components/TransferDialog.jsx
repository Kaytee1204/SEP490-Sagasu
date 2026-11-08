import { useState } from "react";
import { CalendarDays, CheckCircle2, XCircle } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { officeApi } from "../api/officeApi";
import { workflowApi } from "../api/workflowApi";
import useLoad from "../hooks/useLoad";
import { date } from "../officeUtils";
import Dialog from "./Dialog";
import LoadState from "./LoadState";
import Notice from "./Notice";
import IntakeScheduleForm from "./IntakeScheduleForm";
import AppointmentReply from "./AppointmentReply";

const responses = { UNSENT: "Chưa gửi được email", PENDING: "Chờ người giao phản hồi", CONFIRMED: "Người giao đã đồng ý", DECLINED: "Người giao chưa sắp xếp được", EXPIRED: "Chưa phản hồi trong thời hạn" };
const outcomes = { RECEIVED: "Văn phòng đã nhận đồ", REJECTED: "Yêu cầu đã bị từ chối", CANCELLED: "Yêu cầu đã hủy" };
export default function TransferDialog({ id, mine, onClose, onDone }) {
  const { user } = useAuth();
  const state = useLoad(() => workflowApi.transfer(id), id);
  const locations = useLoad(() => !mine && user?.role === "APPROVER" ? officeApi.locations() : Promise.resolve([]), "locations");
  const [mode, setMode] = useState(null);
  const [form, setForm] = useState({ locationId: "", condition: "", reason: "", reconciliationNote: "" });
  const [checked, setChecked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(null);
  const transfer = state.data;
  const pending = transfer?.status === "PENDING";
  const officer = !mine && user?.role === "APPROVER";
  const self = transfer && officer && transfer.requestedBy === user?.id;
  const a = transfer?.appointment;
  const receiving = mode === "receive";
  const change = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  function updated(result) {
    setMode(null); setError("");setChecked(false);
    setNotice(result === "FAILED" ? { error: true, text: "Đã lưu lịch nhưng chưa gửi được email. Bạn có thể gửi lại bên dưới." } : result === "EXPIRED" ? { text: "Lịch này đã hết hạn phản hồi. Hãy hẹn lại thời gian phù hợp." } : result === "SENT" ? { text: "Đã gửi lịch mang đồ đến qua email." } : { text: "Đã cập nhật yêu cầu." });
    state.reload();onDone();
  }
  async function resend() {
    if (busy) return;setBusy(true);setError("");
    try { updated(await workflowApi.resendIntake(id)); } catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    setBusy(true);setError("");
    try { await workflowApi.decide(id, { ...form, decision: receiving ? "RECEIVED" : mine ? "CANCELLED" : "REJECTED", locationId: form.locationId ? Number(form.locationId) : null, expectedVersion: transfer.version });updated(); }
    catch (failure) {setError(failure.message);}finally {setBusy(false);}
  }
  return <Dialog title="Yêu cầu tiếp nhận đồ" onClose={onClose} busy={busy}>
    <Notice error={notice?.error}>{notice?.text}</Notice>
    <LoadState state={state}>{transfer && <>
      {!pending && <div className={`of-transfer-outcome ${transfer.status === "RECEIVED" ? "is-received" : "is-ended"}`} role="status">
        {transfer.status === "RECEIVED" ? <CheckCircle2 size={28} /> : <XCircle size={28} />}
        <div><strong>{outcomes[transfer.status]}</strong><p>{date(transfer.processedAt)}</p></div>
      </div>}
      <h3>{transfer.title}</h3><p className="of-muted">Người giao: {transfer.finder} · Gửi lúc {date(transfer.requestedAt)}</p>
      <details className="of-transfer-description"><summary>Thông tin và ảnh đồ vật</summary><p className="of-prose">{transfer.description}</p>
      <div className="of-gallery">{transfer.images.map((url, i) => <a href={url} target="_blank" rel="noreferrer" key={url}><img src={url} alt={`Đồ cần tiếp nhận, ảnh ${i + 1}`} /></a>)}</div>
      </details>
      {!pending ? <>
        {transfer.reason && <div className="of-reason"><strong>Lý do:</strong><p className="of-prose">{transfer.reason}</p></div>}
        {transfer.reconciliationNote && <p className="of-prose">{transfer.reconciliationNote}</p>}
        {transfer.receiptId && <p>Phiếu tiếp nhận #{transfer.receiptId} · Văn phòng đang phụ trách đồ vật này.</p>}
        <div className="of-actions"><button className="of-button" onClick={onClose}>Đóng</button></div>
      </> : <>
        {transfer.activeClaims > 0 && <p className="of-muted">Đồ vật này có {transfer.activeClaims} yêu cầu nhận lại đồ.</p>}
        {mine && a && <AppointmentReply key={a.id} appointment={a} id={id} intake onBusyChange={setBusy} onChanged={() => { state.reload(); onDone(); }} />}
        {!mine && a && <section className="of-intake-appointment"><h3><CalendarDays size={18} /> Lịch mang đồ đến</h3><strong>{date(a.scheduledAt)}</strong><p className="of-prose">{a.location}</p><p className="of-stage-label">{responses[a.responseStatus]}</p>{a.note && <p className="of-prose">{a.note}</p>}{a.cancellationReason && <p className="of-prose"><strong>Lý do: </strong>{a.cancellationReason}</p>}{a.responseStatus === "PENDING" && <small>Hạn phản hồi: {date(a.responseDeadline)} · Qua email hoặc trên hệ thống.</small>}</section>}
        {!a && <p className="of-callout">{mine ? "Văn phòng sẽ hẹn bạn mang đồ đến. Bạn cứ giữ đồ cẩn thận trong lúc chờ nhé." : "Hẹn thời gian và địa điểm để người giao mang đồ đến văn phòng."}</p>}
        <Notice error>{error}</Notice>
        {self && <Notice>Nhờ một nhân viên khác xử lý yêu cầu tiếp nhận của bạn.</Notice>}
        {!mode && <div className="of-actions of-actions-wrap">
          {officer && !self && <>
            {<button className="of-button of-primary" onClick={() => setMode("receive")}>Đã nhận đồ tại quầy</button>}
            {(!a || ["DECLINED", "EXPIRED"].includes(a.responseStatus)) && <button className="of-button of-primary" onClick={() => setMode("schedule")}>{a ? "Sắp xếp lịch mới" : "Hẹn mang đồ đến"}</button>}
          </>}
          <button className="of-button" disabled={busy} onClick={() => state.reload()}>Làm mới trạng thái</button>
        </div>}
        {!mode && (mine || officer && !self) && <details className="of-secondary-actions"><summary>{mine ? "Hủy yêu cầu tiếp nhận" : "Điều chỉnh yêu cầu"}</summary><div className="of-actions of-actions-wrap">
          {mine ? <><p>Chỉ hủy nếu bạn không còn muốn gửi đồ cho văn phòng.</p><button className="of-button of-danger" disabled={busy} onClick={() => setMode("withdraw")}>Hủy yêu cầu</button></> : <>
            {a && !["DECLINED", "EXPIRED"].includes(a.responseStatus) && <button className="of-button" disabled={busy} onClick={() => setMode("schedule")}>Đổi lịch hẹn</button>}
            {a?.status === "SCHEDULED" && ["UNSENT", "PENDING"].includes(a.responseStatus) && <button className="of-button" disabled={busy} onClick={resend}>{busy ? "Đang gửi…" : "Gửi lại email lịch hẹn"}</button>}
            <button className="of-button of-danger" disabled={busy} onClick={() => setMode("reject")}>Từ chối tiếp nhận</button>
          </>}
        </div></details>}
        {mode === "schedule" && <IntakeScheduleForm transfer={transfer} onBusyChange={setBusy} onClose={() => setMode(null)} onDone={updated} />}
        {["receive", "reject", "withdraw"].includes(mode) && <form className="of-form of-intake-form" onSubmit={submit}>
          <h3>{receiving ? "Ghi nhận đồ đã nhận" : mine ? "Hủy yêu cầu tiếp nhận" : "Từ chối yêu cầu tiếp nhận"}</h3>
          {receiving ? <>
            <label>Vị trí lưu kho *<select required value={form.locationId} disabled={busy || locations.loading} onChange={(e) => change("locationId", e.target.value)}><option value="">Chọn vị trí</option>{(locations.data || []).filter((l) => l.active).map((l) => <option key={l.id} value={l.id}>{l.code} · {l.name}</option>)}</select></label>
            <Notice error>{locations.error}</Notice>
            <label>Tình trạng đồ khi nhận *<textarea required maxLength={2000} value={form.condition} disabled={busy} onChange={(e) => change("condition", e.target.value)} /></label>
            {transfer.previousHandovers > 0 && <label>Đối chiếu việc giao lại đồ *<small>Đã có biên bản giao đồ trước đó. Ghi rõ người giao lại và tình trạng đồ thực tế.</small><textarea required maxLength={2000} value={form.reconciliationNote} disabled={busy} onChange={(e) => change("reconciliationNote", e.target.value)} /></label>}
            <label className="of-check"><input type="checkbox" required checked={checked} disabled={busy} onChange={(e) => setChecked(e.target.checked)} /><span>Tôi đã trực tiếp nhận và kiểm tra đúng đồ.</span></label>
          </> : <label>Lý do *<textarea required maxLength={2000} disabled={busy} value={form.reason} onChange={(e) => change("reason", e.target.value)} placeholder={mine ? "Vì sao bạn muốn hủy yêu cầu?" : "Giải thích ngắn gọn để người giao biết…"} /></label>}
          <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={() => setMode(null)}>Quay lại</button><button className={`of-button ${receiving ? "of-primary" : "of-danger"}`} disabled={busy}>{busy ? "Đang lưu…" : receiving ? "Xác nhận đã nhận đồ" : mine ? "Xác nhận hủy yêu cầu" : "Từ chối yêu cầu"}</button></div>
        </form>}
      </>}
    </>}</LoadState>
  </Dialog>;
}
