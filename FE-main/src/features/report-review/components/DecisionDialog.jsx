import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";
import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox } from "./Shared";
import { appealActionHelp, handoverActionLabel, labels, officeAppealActionHelp } from "../format";
const warnings = {
  REQUEST_POST_CORRECTION: "Người đăng sẽ nhận yêu cầu chỉnh sửa và có thể xem hướng dẫn trong hồ sơ.",
  HIDE_POST: "Bài đăng sẽ được ẩn khỏi tìm kiếm công khai.",
  WARN_ACCOUNT: "Gửi cảnh báo và lưu lý do vào lịch sử xử lý của báo cáo.",
  SUSPEND_ACCOUNT:
    "Tài khoản được chọn sẽ bị đình chỉ truy cập. Quản lý chỉ xử lý tài khoản người dùng hoặc nhân viên tiếp nhận.",
  HOLD: "Tạm dừng tiếp nhận và bàn giao đồ liên quan đến báo cáo này.",
  RELEASE:
    "Gỡ lệnh tạm dừng của báo cáo này. Lệnh từ báo cáo khác vẫn có hiệu lực.",
  HANDOVER_CORRECTION: "Hai bên sẽ nhận hướng dẫn riêng và có 12 giờ làm việc để phản hồi. Các câu hỏi trước đó được đóng. Hồ sơ tiếp tục mở để quản lý kiểm tra kết quả.",
  HANDOVER_VERIFY_HOLDER: "Ghi việc cần xác minh cho quản lý phụ trách. Hồ sơ tiếp tục nằm trong hàng Cần xử lý.",
  HANDOVER_UNVERIFIED: "Đóng báo cáo và gửi lý do cho hai bên. Nếu đang chờ xác nhận bàn giao, người nhận có thể tiếp tục kiểm tra món đồ.",
  HANDOVER_COMPLETED:
    "Xác nhận đồ đã trả đúng người: hoàn tất bàn giao, đóng bài đăng và các yêu cầu đang chờ.",
  HANDOVER_CONTINUE:
    "Cho phép người nhận tiếp tục xác nhận bàn giao. Báo cáo này được kết thúc, bằng chứng vẫn được giữ lại.",
  HANDOVER_RETRY: "Hủy lượt giao hiện tại và giữ nguyên người đã được chấp thuận. Người nhặt có thể ghi nhận lượt giao mới.",
  HANDOVER_REOPEN: "Thu hồi chấp thuận, đưa hồ sơ về chờ xét duyệt và mở lại việc chọn người nhận.",
  CLAIM_VIOLATION_REJECT: "Bác hồ sơ được báo cáo và gửi lý do cho người yêu cầu.",
  REQUEST_ITEM_RECOVERY: "Gửi hướng dẫn thu hồi cho các bên. Bàn giao tiếp tục tạm dừng trong khi quản lý xác minh kết quả.",
  CONFIRM_ITEM_RECOVERY: "Ghi nhận món đồ đã về người giữ sau khi đối chiếu bằng chứng.",
  RESOLVE:
    "Ghi kết quả giải quyết. Phiếu trả đồ đã hoàn tất sẽ không bị đảo ngược.",
  DISMISS:
    "Bác bỏ báo cáo với lý do rõ ràng. Người liên quan sẽ nhận được kết luận.",
};
const findings = {
  VIOLATION: "Có vi phạm",
  NO_VIOLATION: labels.NO_VIOLATION,
  INSUFFICIENT_EVIDENCE: labels.INSUFFICIENT_EVIDENCE,
};
const appealFindings = {
  INSUFFICIENT_EVIDENCE: "Chưa đủ căn cứ để thay đổi người nhận",
  INVALID_CLAIM: "Đã xác minh yêu cầu nhận đồ của người gửi tranh chấp là sai",
};
const batchActions = ["RESOLVE", "DISMISS", ...Object.keys(findings)];

export default function DecisionDialog({ action: initialAction, data, onClose, onSaved }) {
  const outcome = initialAction === "CONCLUDE_HANDOVER";
  const [choice, setChoice] = useState(outcome && data.actions.length === 1 ? data.actions[0] : "");
  const action = outcome ? choice : initialAction;
  const measures = ["HANDOVER_CORRECTION", "REQUEST_ITEM_RECOVERY", "CONFIRM_ITEM_RECOVERY", "HANDOVER_VERIFY_HOLDER", "HOLD", "RELEASE"];
  const outcomes = ["HANDOVER_COMPLETED", "HANDOVER_CONTINUE", "HANDOVER_RETRY", "HANDOVER_REOPEN", "RESOLVE", "DISMISS", "HANDOVER_UNVERIFIED"];
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [selected, setSelected] = useState([]);
  const [sameIncident, setSameIncident] = useState(false);
  const structured = action === "CONCLUDE_VIOLATION";
  const [finding, setFinding] = useState("");
  const [measure, setMeasure] = useState("");
  const decision = structured ? finding : action;
  const effect = measure || decision;
  const candidates = data.caseKind !== "HANDOVER_ISSUE" && !measure && batchActions.includes(decision) ? (data.related || []).filter(r =>
    ["OPEN","IN_REVIEW"].includes(r.status) && r.assigned_to === data.report.assigned_to &&
    r.kind === data.caseKind &&
    ["reason_code","post_id","claim_id","message_id","reported_account_id"].every(key => (r[key] ?? null) === (data.report[key] ?? null))) : [];
  const [conclusion, setConclusion] = useState(""),
    [holder, setHolder] = useState(""),
    [appealFinding, setAppealFinding] = useState(""),
    [custodyVerified, setCustody] = useState(false),
    [wrongRecipientVerified, setWrongRecipientVerified] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const accountAction = ["WARN_ACCOUNT", "SUSPEND_ACCOUNT"].includes(effect);
  const isRecoveryRequest = action === "REQUEST_ITEM_RECOVERY";
  const isKeepApproval = action === "APPEAL_KEEP";
  const isWrongRecipientDecision = action === "APPEAL_WRONG_RECIPIENT";
  const requiresRecoveryCheck = data.recoveryRequested && action === "HANDOVER_CONTINUE";
  const confirmingCorrection = ["HANDOVER_COMPLETED", "RESOLVE"].includes(action) && ["HANDOVER_WRONG_ITEM", "HANDOVER_MISSING_ITEMS"].includes(data.report.reason_code) && data.caseKind === "HANDOVER_ISSUE";
  const officeHandover = data.handoverSource === "OFFICE";
  const requiresCustody = confirmingCorrection || requiresRecoveryCheck
    || ["HANDOVER_COMPLETED", "HANDOVER_RETRY", "HANDOVER_REOPEN", "CONFIRM_ITEM_RECOVERY"].includes(action)
    || (isKeepApproval && data.recoveryRequested);
  const officeAppeal = data.appeal?.claim_type === "OFFICE";
  const warning = (data.caseKind === "CLAIM_APPEAL" && (officeAppeal ? officeAppealActionHelp : appealActionHelp)[effect]) || warnings[effect];
  const unresolvedInquiries = data.inquiries.filter(item => !item.responded_at && !item.closed_at);
  const overdue = unresolvedInquiries.some(item => item.overdue);
  function changeDecision(setValue, value) {
    setValue(value);
    setSelected([]);
    setSameIncident(false);
  }

  const cannotSubmit = busy || !decision || !conclusion.trim()
    || (isRecoveryRequest && !holder) || (requiresCustody && !custodyVerified)
    || (isKeepApproval && !appealFinding)
    || (isWrongRecipientDecision && !wrongRecipientVerified) || (selected.length > 0 && !sameIncident);

  function submit(event) {
    event.preventDefault();
    if (!cannotSubmit) {
      setError("");
      if (isWrongRecipientDecision || isKeepApproval) void executeSubmit();
      else setConfirmOpen(true);
    }
  }

  async function executeSubmit() {
    if (cannotSubmit) return;
    setBusy(true);
    setError("");
    try {
      if (selected.length) {
        await reviewApi.batch({reports:[data.report,...candidates.filter(r => selected.includes(r.report_id))].map(r => ({id:r.report_id,version:r.version})), action: decision, conclusion:conclusion.trim(), sameIncidentConfirmed:sameIncident});
        onSaved(await reviewApi.detail(data.report.report_id));
        onClose();
        return;
      }
      onSaved(
        await reviewApi.act(data.report.report_id, {
          version: data.report.version,
          action: decision,
          measure: measure || null,
          conclusion: conclusion.trim(),
          targetId: isRecoveryRequest ? Number(holder) : accountAction ? data.report.reported_account_id : null,
          custodyVerified,
          ...(isKeepApproval && { appealFinding }),
          ...(isWrongRecipientDecision && { wrongRecipientVerified }),
        }),
      );
      onClose();
    } catch (e) {
      if (data.caseKind === "CLAIM_APPEAL") {
        const latest = await reviewApi.detail(data.report.report_id).catch(() => null);
        if (latest && ["RESOLVED","REJECTED"].includes(latest.report.status)) {
          onSaved(latest);
          onClose();
          return;
        }
      }
      setError(e.message);
      setConfirmOpen(false);
    } finally {
      setBusy(false);
    }
  }
  return (<>
    <Dialog title={outcome ? "Xử lý sự cố bàn giao" : structured ? "Ghi kết luận vi phạm" : data.caseKind === "HANDOVER_ISSUE" ? handoverActionLabel(action, data) : labels[action]} busy={busy} onClose={onClose}>
      <form onSubmit={submit} className="rr-stack rr-decision-form">
        {outcome && <label>
          Chọn cách xử lý *
          <select autoFocus required disabled={busy} value={choice} onChange={event => {
            changeDecision(setChoice, event.target.value);
            setCustody(false);
            setHolder("");
            setError("");
          }}>
            <option value="">Chọn một phương án…</option>
            {[["Tiếp tục xử lý", measures], ["Kết luận sự cố", outcomes]].map(([label, options]) => {
              const available = options.filter(value => data.actions.includes(value));
              return available.length > 0 && <optgroup key={label} label={label}>
                {available.map(value => <option key={value} value={value}>{handoverActionLabel(value, data)}</option>)}
              </optgroup>;
            })}
          </select>
        </label>}
        {structured && <>
          <fieldset className="rr-decision-choice" disabled={busy}>
            <legend>Kết luận *</legend>
            {Object.entries(findings).map(([value, label]) => <label key={value}>
              <input type="radio" name="finding" value={value} required checked={finding === value}
                onChange={() => { changeDecision(setFinding, value); setMeasure(""); }} />
              {label}
            </label>)}
          </fieldset>
          {finding === "VIOLATION" && <label>
            Biện pháp áp dụng
            <select value={measure} disabled={busy} onChange={event => changeDecision(setMeasure, event.target.value)}>
              <option value="">Ghi nhận vi phạm</option>
              {(data.availableMeasures || []).map(value => <option key={value} value={value}>{labels[value]}</option>)}
            </select>
          </label>}
        </>}
        {isKeepApproval && <>
          <fieldset className="rr-decision-choice" disabled={busy}>
            <legend>Căn cứ giữ người đã duyệt *</legend>
            {Object.entries(appealFindings).map(([value, label]) => <label key={value}>
              <input type="radio" name="appealFinding" value={value} required checked={appealFinding === value}
                onChange={() => setAppealFinding(value)} />
              {label}
            </label>)}
          </fieldset>
          {appealFinding === "INSUFFICIENT_EVIDENCE" && <p className="rr-callout">
            Giữ nguyên người được chấp thuận và lưu kết luận cho các báo cáo đang mở cùng lần duyệt. Người đủ điều kiện vẫn có thể gửi tranh chấp trong thời hạn đã thông báo.
          </p>}
          {appealFinding === "INVALID_CLAIM" && <p className="rr-callout">
            Bác yêu cầu của người gửi báo cáo này và chặn tài khoản đó gửi lại yêu cầu trên bài đăng. Các báo cáo còn mở trong vụ việc vẫn cần được xem xét.
          </p>}
        </>}
        {!isKeepApproval && warning && <p className="rr-callout">{warning}</p>}
        {candidates.length > 0 && <fieldset className="rr-batch-selection">
          <legend>Áp dụng cho báo cáo cùng sự việc</legend>
          <p>Mặc định chỉ kết luận báo cáo #{data.report.report_id}. Chỉ chọn thêm sau khi đã đối chiếu từng nội dung và bằng chứng. Các báo cáo mới gửi sau này không tự nhận kết luận này.</p>
          {candidates.map(r => <label className="rr-check" key={r.report_id}>
            <input type="checkbox" disabled={busy || (!selected.includes(r.report_id) && selected.length>=19)} checked={selected.includes(r.report_id)} onChange={e => {setSelected(old => e.target.checked ? [...old,r.report_id] : old.filter(id => id!==r.report_id)); setSameIncident(false);}}/>
            <span><strong>#{r.report_id} · {r.reason_label_snapshot || labels[r.kind]}</strong><br/>{r.description}<br/><a href={`/admin/reports/${r.report_id}`} target="_blank" rel="noreferrer">Mở bằng chứng trong tab mới</a></span>
          </label>)}
          {selected.length>0 && <label className="rr-check"><input type="checkbox" required checked={sameIncident} onChange={e => setSameIncident(e.target.checked)}/>Tôi đã đối chiếu bằng chứng và xác nhận {selected.length+1} báo cáo này cùng sự việc, cùng kết luận. Kết quả được lưu và thông báo riêng cho từng báo cáo.</label>}
        </fieldset>}
        {accountAction && (
          <p>Tài khoản được báo cáo <strong>{data.current.account?.full_name || `#${data.report.reported_account_id}`}</strong></p>
        )}
        {isRecoveryRequest && <label>
          Người đang giữ món đồ *
          <select required value={holder} onChange={event => setHolder(event.target.value)} disabled={busy}>
            <option value="">Chọn người cần phản hồi về thu hồi</option>
            {(data.current.participants || []).map(person => <option key={person.id} value={person.id}>{person.full_name}</option>)}
          </select>
        </label>}
        <label>
          {action === "HANDOVER_VERIFY_HOLDER" ? "Việc quản lý phụ trách cần xác minh" : "Nội dung gửi cho người liên quan"}{" "}
          *
          <textarea
            autoFocus={!structured && !outcome && !isKeepApproval}
            required
            maxLength={5000}
            rows={3}
            value={conclusion}
            onChange={(e) => setConclusion(e.target.value)}
            placeholder="Nêu kết quả rõ ràng, không đưa thông tin riêng tư của người khác vào kết luận."
          />
        </label>
        {isWrongRecipientDecision && <label className="rr-check">
          <input
            type="checkbox"
            required
            checked={wrongRecipientVerified}
            onChange={(event) => setWrongRecipientVerified(event.target.checked)}
          />
          Tôi đã đối chiếu căn cứ và xác nhận người được duyệt không phải chủ món đồ.
        </label>}
        {requiresCustody && (
          <label className="rr-check">
            <input
              type="checkbox"
              required
              checked={custodyVerified}
              onChange={(e) => setCustody(e.target.checked)}
            />
            {officeHandover && confirmingCorrection ? "Tôi đã đối chiếu biên bản với tài liệu liên quan và xác minh kết quả khắc phục với văn phòng và người nhận."
              : confirmingCorrection ? (data.report.reason_code === "HANDOVER_WRONG_ITEM" ? "Đã xác minh người nhận đang giữ đúng món và đã đối chiếu việc trả lại món giao nhầm, nếu có." : "Đã đối chiếu danh sách phụ kiện và xác minh người nhận đã nhận đủ.")
              : action === "HANDOVER_COMPLETED" ? "Đã đối chiếu căn cứ xác nhận đúng món đồ và đúng người nhận."
              : requiresRecoveryCheck
              ? "Đã xác minh tình trạng món đồ và có đủ căn cứ áp dụng kết quả bàn giao này."
              : "Đã xác minh đồ đang ở người giữ và có thể tiến hành theo quyết định này."}
          </label>
        )}
        {(batchActions.includes(decision) || [
          "HANDOVER_UNVERIFIED",
          "HANDOVER_COMPLETED",
          "HANDOVER_CONTINUE",
          "HANDOVER_RETRY",
          "HANDOVER_REOPEN",
          "APPEAL_KEEP",
          "APPEAL_WRONG_RECIPIENT",
        ].includes(decision)) && unresolvedInquiries.length > 0 && (
            <p className="rr-callout">
              {overdue ? "Đã quá hạn bổ sung thông tin. Hãy kết luận dựa trên căn cứ hiện có."
                : "Vẫn còn yêu cầu bổ sung chưa được trả lời. Nếu đã đủ căn cứ, ghi rõ lý do kết luận ở trên."}
            </p>
          )}
        <ErrorBox>{error}</ErrorBox>
        <footer className="rr-actions">
          <button type="button" disabled={busy} onClick={onClose}>
            Quay lại
          </button>
          <button
            className="rr-primary"
            disabled={cannotSubmit}
          >
            {busy ? "Đang lưu…" : isWrongRecipientDecision ? "Xác nhận mở lại xét duyệt"
              : isKeepApproval ? labels.APPEAL_KEEP
              : action === "HANDOVER_CORRECTION" ? "Gửi yêu cầu cho hai bên" : structured || outcomes.includes(action) ? "Xác nhận kết luận" : "Xác nhận xử lý"}
          </button>
        </footer>
      </form>
    </Dialog>
    <ConfirmActionPopup open={confirmOpen}
      variant={["HIDE_POST", "SUSPEND_ACCOUNT", "CLAIM_VIOLATION_REJECT"].includes(effect) ? "danger" : ["HANDOVER_COMPLETED", "RESOLVE"].includes(effect) ? "success" : "warning"}
      title="Xác nhận thực hiện quyết định?"
      description={warning || conclusion.trim()}
      confirmLabel="Xác nhận thực hiện" cancelLabel="Quay lại"
      busy={busy} onConfirm={executeSubmit} onClose={() => setConfirmOpen(false)} />
  </>);
}
