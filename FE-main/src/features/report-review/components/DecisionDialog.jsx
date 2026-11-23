import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";
import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox } from "./Shared";
import { appealActionHelp, labels, officeAppealActionHelp } from "../format";
const warnings = {
  REQUEST_POST_CORRECTION: "Người đăng sẽ nhận yêu cầu chỉnh sửa và có thể xem hướng dẫn trong hồ sơ.",
  HIDE_POST: "Bài đăng sẽ được ẩn khỏi tìm kiếm công khai.",
  WARN_ACCOUNT: "Gửi cảnh báo và lưu lý do vào lịch sử xử lý của báo cáo.",
  SUSPEND_ACCOUNT:
    "Tài khoản được chọn sẽ bị đình chỉ truy cập. Quản lý chỉ xử lý tài khoản người dùng hoặc nhân viên tiếp nhận.",
  HOLD: "Tạm dừng tiếp nhận và bàn giao đồ liên quan đến báo cáo này.",
  RELEASE:
    "Gỡ lệnh tạm dừng của báo cáo này. Lệnh từ báo cáo khác vẫn có hiệu lực.",
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
  const interim = initialAction === "HANDOVER_INTERIM";
  const [choice, setChoice] = useState("");
  const action = outcome || interim ? choice : initialAction;
  const choices = interim ? {
    HOLD: "Tạm dừng giao nhận trong lúc xác minh",
    RELEASE: "Gỡ lệnh tạm dừng của hồ sơ này",
    REQUEST_ITEM_RECOVERY: "Yêu cầu người đang giữ trả lại món đồ",
    CONFIRM_ITEM_RECOVERY: "Xác nhận đã lấy lại món đồ",
  } : {
    HANDOVER_COMPLETED: "Người nhận đã nhận đúng và đủ đồ",
    HANDOVER_CONTINUE: data.issuePhase === "BEFORE" ? "Hai bên tiếp tục giao theo lịch đã thống nhất" : "Người nhận sẽ tự xác nhận trên lượt giao hiện tại",
    HANDOVER_RETRY: "Người nhặt đang giữ đồ và sẽ giao lại cho cùng người nhận",
    HANDOVER_REOPEN: "Đồ đã về tay người nhặt và cần chọn lại người nhận",
    RESOLVE: "Sự cố đã được khắc phục",
    DISMISS: "Bác phản ánh sau khi đối chiếu",
  };
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [selected, setSelected] = useState([]);
  const [sameIncident, setSameIncident] = useState(false);
  const structured = action === "CONCLUDE_VIOLATION";
  const [finding, setFinding] = useState("");
  const [measure, setMeasure] = useState("");
  const decision = structured ? finding : action;
  const effect = measure || decision;
  const candidates = !measure && batchActions.includes(decision) ? (data.related || []).filter(r =>
    ["OPEN","IN_REVIEW"].includes(r.status) && r.assigned_to === data.report.assigned_to &&
    r.kind === data.caseKind &&
    ["reason_code","post_id","claim_id","message_id","reported_account_id"].every(key => (r[key] ?? null) === (data.report[key] ?? null))) : [];
  const [conclusion, setConclusion] = useState(""),
    [basis, setBasis] = useState(""),
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
  const requiresCustody = isWrongRecipientDecision || requiresRecoveryCheck
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

  const cannotSubmit = busy || !decision || !conclusion.trim() || !basis.trim()
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
        await reviewApi.batch({reports:[data.report,...candidates.filter(r => selected.includes(r.report_id))].map(r => ({id:r.report_id,version:r.version})), action: decision, conclusion:conclusion.trim(), basis:basis.trim(), sameIncidentConfirmed:sameIncident});
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
          basis: basis.trim(),
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
    <Dialog title={outcome ? "Ghi kết luận sự cố bàn giao" : interim ? "Xử lý trong lúc xác minh" : structured ? "Ghi kết luận vi phạm" : labels[action]} busy={busy} onClose={onClose}>
      <form onSubmit={submit} className="rr-stack rr-decision-form">
        {(outcome || interim) && <label>
          {outcome ? "Kết quả đã xác minh" : "Việc cần thực hiện"} *
          <select autoFocus required disabled={busy} value={choice} onChange={event => {
            changeDecision(setChoice, event.target.value);
            setCustody(false);
            setHolder("");
            setError("");
          }}>
            <option value="">Chọn một phương án…</option>
            {Object.entries(choices).filter(([value]) => data.actions.includes(value)).map(([value, label]) =>
              <option key={value} value={value}>{label}</option>)}
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
            Kết thúc tranh chấp và giữ nguyên yêu cầu nhận đồ của người gửi.
          </p>}
          {appealFinding === "INVALID_CLAIM" && <p className="rr-callout">
            Bác yêu cầu nhận đồ của người gửi tranh chấp và chặn tài khoản đó gửi lại yêu cầu trên bài đăng này.
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
          Nội dung gửi cho người liên quan{" "}
          *
          <textarea
            autoFocus={!structured && !interim && !outcome && !isKeepApproval}
            required
            maxLength={5000}
            rows={3}
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
            rows={3}
            value={basis}
            onChange={(e) => setBasis(e.target.value)}
            placeholder={isWrongRecipientDecision
              ? "Ghi rõ căn cứ nào cho thấy người được duyệt không phải chủ món đồ."
              : appealFinding === "INVALID_CLAIM" ? "Ghi rõ bằng chứng cho thấy yêu cầu nhận đồ của người gửi là sai."
                : "Dẫn ảnh, thời điểm, nội dung trao đổi hoặc biên bản đã kiểm tra."}
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
            {isWrongRecipientDecision ? (officeAppeal ? "Tôi đã xác minh tài sản còn lưu kho tại văn phòng." : "Tôi đã xác minh người nhặt vẫn đang giữ món đồ.")
              : action === "HANDOVER_COMPLETED" ? "Đã đối chiếu căn cứ xác nhận đúng món đồ và đúng người nhận."
              : requiresRecoveryCheck
              ? "Đã xác minh tình trạng món đồ và có đủ căn cứ áp dụng kết quả bàn giao này."
              : "Đã xác minh đồ đang ở người giữ và có thể tiến hành theo quyết định này."}
          </label>
        )}
        {(batchActions.includes(decision) || [
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
              : structured || outcome ? "Xác nhận kết luận" : "Xác nhận xử lý"}
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
