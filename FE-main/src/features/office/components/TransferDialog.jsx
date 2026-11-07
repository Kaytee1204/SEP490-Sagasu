import { useState } from "react";
import { useAuth } from "@/context/AuthContext";
import { officeApi } from "../api/officeApi";
import { workflowApi } from "../api/workflowApi";
import useLoad from "../hooks/useLoad";
import { date } from "../officeUtils";
import Dialog from "./Dialog";
import LoadState from "./LoadState";
import Notice from "./Notice";

export default function TransferDialog({ id, mine, onClose, onDone }) {
  const { user } = useAuth();
  const state = useLoad(() => workflowApi.transfer(id), id);
  const locations = useLoad(() => !mine && user?.role === "APPROVER" ? officeApi.locations() : Promise.resolve([]), "locations");
  const [form, setForm] = useState({ decision: mine ? "CANCELLED" : "RECEIVED", locationId: "", condition: "", reason: "", reconciliationNote: "" });
  const [checked, setChecked] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const transfer = state.data;
  const editable = transfer?.status === "PENDING" && (mine || user?.role === "APPROVER");
  const receiving = form.decision === "RECEIVED";
  const change = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError("");
    try { await workflowApi.decide(id, { ...form, locationId: form.locationId ? Number(form.locationId) : null, expectedVersion: transfer.version }); onDone(); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <Dialog title="Tiếp nhận chuyển giao" onClose={onClose} busy={busy}>
    <LoadState state={state}>{transfer && <>
      <h3>{transfer.title}</h3><p className="of-muted">Người giao: {transfer.finder} · Yêu cầu lúc {date(transfer.requestedAt)}</p>
      <p className="of-prose">{transfer.description}</p>
      <div className="of-gallery">{transfer.images.map((url, i) => <a href={url} target="_blank" rel="noreferrer" key={url}><img src={url} alt={`Đồ cần tiếp nhận, ảnh ${i + 1}`} /></a>)}</div>
      <Notice>{transfer.activeClaims} hồ sơ đang xử lý sẽ chuyển trách nhiệm sang văn phòng khi nhận đồ. Hồ sơ đã duyệt phải được văn phòng xét duyệt lại.</Notice>
      {transfer.previousHandovers > 0 && <Notice error>Đã có {transfer.previousHandovers} biên bản giao đồ trước đó. Cần đối chiếu và ghi rõ việc thu hồi/giao lại đồ thực tế trước khi tiếp nhận. Lịch sử cũ được giữ nguyên.</Notice>}
      {!editable && <><p>Thời điểm xử lý: {date(transfer.processedAt)}</p><p className="of-prose">{transfer.reason}</p><p className="of-prose">{transfer.reconciliationNote}</p>{transfer.receiptId && <p>Mã phiếu tiếp nhận: #{transfer.receiptId}</p>}</>}
      {editable && <form className="of-form" onSubmit={submit}>
        <Notice error>{error}</Notice>
        {!mine && <label>Thao tác<select value={form.decision} disabled={busy} onChange={(e) => change("decision", e.target.value)}><option value="RECEIVED">Xác nhận đã nhận đồ</option><option value="REJECTED">Từ chối tiếp nhận</option></select></label>}
        {receiving ? <>
          <label>Vị trí lưu kho *<select required value={form.locationId} disabled={busy || locations.loading} onChange={(e) => change("locationId", e.target.value)}><option value="">Chọn vị trí</option>{(locations.data || []).filter((l) => l.active).map((l) => <option key={l.id} value={l.id}>{l.code} · {l.name}</option>)}</select></label>
          <Notice error>{locations.error}</Notice>
          {!locations.loading && !locations.error && !(locations.data || []).some((l) => l.active) && <Notice error>Chưa có vị trí đang hoạt động. Liên hệ người quản lý để bổ sung vị trí kho.</Notice>}
          <label>Tình trạng đồ khi nhận *<textarea required maxLength={2000} value={form.condition} disabled={busy} onChange={(e) => change("condition", e.target.value)} placeholder="Màu sắc, tình trạng, phụ kiện đi kèm…" /></label>
          {transfer.previousHandovers > 0 && <label>Kết quả đối chiếu biên bản trước đó *<textarea required maxLength={2000} value={form.reconciliationNote} disabled={busy} onChange={(e) => change("reconciliationNote", e.target.value)} placeholder="Ai giao lại đồ, tình trạng và căn cứ xác nhận văn phòng đang giữ đồ…" /></label>}
          <label className="of-check"><input type="checkbox" required checked={checked} disabled={busy} onChange={(e) => setChecked(e.target.checked)} /><span>Tôi đã trực tiếp nhận và kiểm tra đúng đồ. Đồng ý chuyển các hồ sơ liên quan sang văn phòng.</span></label>
        </> : <label>Lý do {mine ? "rút yêu cầu" : "từ chối"} *<textarea required maxLength={2000} disabled={busy} value={form.reason} onChange={(e) => change("reason", e.target.value)} /></label>}
        <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={onClose}>Đóng</button><button className="of-button of-primary" disabled={busy}>{busy ? "Đang lưu…" : receiving ? "Xác nhận đã nhận đồ" : mine ? "Rút yêu cầu" : "Từ chối tiếp nhận"}</button></div>
      </form>}
    </>}</LoadState>
  </Dialog>;
}
