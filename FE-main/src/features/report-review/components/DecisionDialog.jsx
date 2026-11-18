import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox } from "./Shared";
import { labels } from "../format";
const warnings = {
  PRIORITY_HIGH: "Đưa báo cáo lên nhóm cần xem sớm. Ghi rõ nguy cơ hoặc mức nghiêm trọng đã xác định.",
  PRIORITY_NORMAL: "Đưa báo cáo về mức bình thường. Ghi rõ lý do không còn cần ưu tiên cao.",
  PRIORITY_AUTO: "Dùng lại mức ưu tiên hệ thống xác định từ tình trạng giao nhận hiện tại.",
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
};
export default function DecisionDialog({ action, data, onClose, onSaved }) {
  const [selected, setSelected] = useState([]);
  const [sameIncident, setSameIncident] = useState(false);
  const candidates = ["RESOLVE","DISMISS"].includes(action) ? (data.related || []).filter(r =>
    ["OPEN","IN_REVIEW"].includes(r.status) && r.assigned_to === data.report.assigned_to &&
    ["kind","reason_code","post_id","claim_id","message_id","reported_account_id"].every(key => (r[key] ?? null) === (data.report[key] ?? null))) : [];
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
      if (selected.length) {
        await reviewApi.batch({reports:[data.report,...candidates.filter(r => selected.includes(r.report_id))].map(r => ({id:r.report_id,version:r.version})), action, conclusion:conclusion.trim(), basis:basis.trim(), sameIncidentConfirmed:sameIncident});
        onSaved(await reviewApi.detail(data.report.report_id));
        onClose();
        return;
      }
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
        {candidates.length > 0 && <fieldset className="rr-batch-selection">
          <legend>Áp dụng cho báo cáo cùng sự việc</legend>
          <p>Mặc định chỉ kết luận báo cáo #{data.report.report_id}. Chỉ chọn thêm sau khi đã đối chiếu từng nội dung và bằng chứng. Các báo cáo mới gửi sau này không tự nhận kết luận này.</p>
          {candidates.map(r => <label className="rr-check" key={r.report_id}>
            <input type="checkbox" disabled={busy || (!selected.includes(r.report_id) && selected.length>=19)} checked={selected.includes(r.report_id)} onChange={e => {setSelected(old => e.target.checked ? [...old,r.report_id] : old.filter(id => id!==r.report_id)); setSameIncident(false);}}/>
            <span><strong>#{r.report_id} · {r.reason_label_snapshot || labels[r.kind]}</strong><br/>{r.description}<br/><a href={`/admin/reports/${r.report_id}`} target="_blank" rel="noreferrer">Mở bằng chứng trong tab mới</a></span>
          </label>)}
          {selected.length>0 && <label className="rr-check"><input type="checkbox" required checked={sameIncident} onChange={e => setSameIncident(e.target.checked)}/>Tôi đã đối chiếu bằng chứng và xác nhận {selected.length+1} báo cáo này cùng sự việc, cùng kết luận. Kết quả được lưu và thông báo riêng cho từng báo cáo.</label>}
        </fieldset>}
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
          Nội dung gửi cho người liên quan{" "}
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
            disabled={busy || !conclusion.trim() || !basis.trim() || (selected.length>0 && !sameIncident)}
          >
            {busy ? "Đang lưu…" : "Xác nhận xử lý"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
