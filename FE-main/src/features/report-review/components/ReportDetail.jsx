import { useState } from "react";
import { ArrowDown, ArrowLeft, ArrowRight, ChevronDown, ClipboardCheck, FileText, Flag, History, MessageSquare, Package, RefreshCw, Scale, Users } from "lucide-react";
import { reviewApi } from "../api";
import { Badge, ErrorBox, Empty, Gallery } from "./Shared";
import { date, labels } from "../format";
import ContextPanel from "./ContextPanel";
import DecisionDialog from "./DecisionDialog";
import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";
import Inquiries from "./Inquiries";
import ChatContext from "./ChatContext";
import AppealEvidenceComparison from "./AppealEvidenceComparison";
import useReviewResource from "../hooks/useReviewResource";

const caseIcons = { CLAIM_APPEAL: Scale, HANDOVER_ISSUE: Package, GENERAL: Flag };
const findings = ["VIOLATION", "NO_VIOLATION", "INSUFFICIENT_EVIDENCE"];
const followUpActions = ["HOLD", "RELEASE", "REQUEST_ITEM_RECOVERY", "CONFIRM_ITEM_RECOVERY", "APPEAL_REOPEN"];

function Overview({ data, onRelated }) {
  const { report, caseKind, current, related } = data;
  const description = report.description?.trim() || "";
  const reason = report.reason_label_snapshot;
  const explanation = reason && (description === reason || description.startsWith(reason + "\n"))
    ? description.slice(reason.length).trim() : description;
  const decision = data.decisions?.findLast(item => findings.includes(item.action));
  return <div className="rr-stack">
    <section className="rr-card rr-report-content">
      <h2>Nội dung báo cáo</h2>
      {caseKind !== "CLAIM_APPEAL" && reason && <p className="rr-report-reason">{reason}</p>}
      <p>{explanation || "Người gửi chưa ghi mô tả bổ sung."}</p>
      {data.evidence.length > 0 && <>
        <h3>Ảnh người gửi đính kèm <span className="rr-muted">({data.evidence.length})</span></h3>
        <Gallery images={data.evidence} />
      </>}
      {report.resolution_note && <div className="rr-callout">
        <strong>{decision ? labels[decision.action] : "Kết luận đã gửi"}</strong>
        <p>{report.resolution_note}</p>
        {decision?.applied_measure && <p><small>Biện pháp đã áp dụng</small><strong>{labels[decision.applied_measure]}</strong></p>}
      </div>}
    </section>
    {current.participants.length > 0 && <section className="rr-card">
      <h2>Người liên quan</h2>
      <div className="rr-people">
        {current.participants.map(person => <div key={person.id}>
          <strong>{person.full_name}</strong>
          <small>{person.email}</small>
          <span>{person.id === report.reporter_id ? "Người gửi báo cáo · " : ""}{labels[person.role]}</span>
          <Badge value={person.status} />
        </div>)}
      </div>
    </section>}
    {related.length > 0 && <section className="rr-card">
      <h2>Hồ sơ liên quan</h2>
      <div className="rr-related-list">
        {related.map(item => <button type="button" className="rr-related" key={item.report_id} onClick={() => onRelated(item.report_id)}>
          <span><strong>#{item.report_id} · {item.reason_label_snapshot || labels[item.kind]}</strong><small>{date(item.created_at)}</small></span>
          <Badge value={item.status} /><ArrowRight size={16} aria-hidden="true" />
        </button>)}
      </div>
    </section>}
  </div>;
}

function ContextDisclosure({ title, children }) {
  return <details className="rr-context-disclosure">
    <summary>{title}<ChevronDown size={17} aria-hidden="true" /></summary>
    <div className="rr-stack">{children}</div>
  </details>;
}

function Evidence({ data }) {
  const { current, approvedContext, snapshot } = data;
  return <div className="rr-stack">
    {approvedContext ? <>
      <AppealEvidenceComparison current={current} approved={approvedContext} />
      <ContextDisclosure title="Hồ sơ đầy đủ của người kháng cáo"><ContextPanel data={current} /></ContextDisclosure>
      <ContextDisclosure title="Hồ sơ đầy đủ của người được chấp thuận"><ContextPanel data={approvedContext} /></ContextDisclosure>
    </> : <ContextPanel data={current} />}
    {snapshot?.payload && <ContextDisclosure title="Bản lưu hồ sơ">
      <p className="rr-muted">
        {snapshot.capture_source === "SUBMISSION" ? "Lưu khi gửi báo cáo" : "Lưu khi quản lý mở hồ sơ lần đầu"} · {date(snapshot.captured_at)}
      </p>
      <ContextPanel snapshot data={snapshot.payload} />
    </ContextDisclosure>}
  </div>;
}

function HistoryPanel({ history, decisions = [] }) {
  return <section className="rr-card">
    <h2>Lịch sử xử lý</h2>
    <p className="rr-muted">Ghi chú và căn cứ nội bộ chỉ quản lý được xem.</p>
    {history.length === 0 ? <Empty>Chưa có hoạt động xử lý.</Empty> :
      <ol className="rr-timeline">
        {history.map(item => <li key={item.activity_id}>
          <strong>{labels[item.action] || item.action}</strong>
          <small>{item.actor} · {date(item.created_at)}</small>
          {item.public_note && <p>{item.public_note}</p>}
          {item.internal_note && <div className="rr-private">
            <strong>Nội bộ</strong>
            <p>{item.internal_note}</p>
          </div>}
        </li>)}
      </ol>}
    {decisions.some(item => item.applied_measure) && <div className="rr-record">
      <h3>Biện pháp đã áp dụng</h3>
      {decisions.filter(item => item.applied_measure).map(item => <div key={item.decision_id}>
        <strong>{labels[item.applied_measure]}</strong>
        <small>{item.actor} · {date(item.created_at)}</small>
      </div>)}
    </div>}
  </section>;
}

function FollowUp({ data }) {
  const { caseKind, report, appeal, recoveryRequested, decisions } = data;
  const appealCase = caseKind === "CLAIM_APPEAL";
  const assigned = data.assigneeName || "Quản lý phụ trách";
  const lastDecision = decisions?.at(-1)?.action;
  const needsReview = appealCase && report.status === "RESOLVED" && appeal?.active;
  let text;
  let owner = assigned;

  if (data.recovery?.holder_name && !data.recovery.confirmed_at) {
    text = "Chờ phản hồi về tình trạng món đồ. Quản lý cần đối chiếu căn cứ và xác nhận việc thu hồi.";
    owner = data.recovery.holder_name;
  } else if (needsReview && appeal.window_elapsed === false) {
    text = "Chờ hết hạn gửi kháng cáo. Quản lý có thể xác minh ai đang giữ đồ trong thời gian này.";
  } else if (needsReview && appeal.waiting_other_appeals) {
    text = "Cần xử lý các kháng cáo còn lại của đợt này trước khi mở lại xét duyệt.";
  } else if (needsReview && appeal.ready_for_reopen) {
    text = "Xác minh món đồ đã về người giữ, sau đó thu hồi chấp thuận và mở lại xét duyệt.";
  } else if (needsReview) {
    text = "Kiểm tra tình trạng bàn giao và các lệnh tạm dừng liên quan trước khi mở lại xét duyệt.";
  } else if (["APPEAL_REOPEN", "HANDOVER_REOPEN"].includes(lastDecision)) {
    text = "Đã mở lại xét duyệt người nhận.";
    owner = "Người nhặt";
  } else if (caseKind === "HANDOVER_ISSUE" && lastDecision === "HANDOVER_CONTINUE") {
    text = data.issuePhase === "BEFORE" ? "Người nhặt có thể tiến hành giao đồ khi đủ điều kiện." : "Chờ người nhận xác nhận đã nhận đúng đồ.";
    owner = data.issuePhase === "BEFORE" ? "Người nhặt" : "Người nhận";
  } else if (lastDecision === "HANDOVER_RETRY") {
    text = "Người nhặt có thể hẹn và ghi nhận lượt giao mới với người đã được chấp thuận.";
    owner = "Người nhặt";
  } else if (caseKind === "HANDOVER_ISSUE" && lastDecision === "CLAIM_REJECTED") {
    text = "Đã hủy bàn giao và mở lại xét duyệt.";
    owner = "Người nhặt";
  } else if (data.recovery?.confirmed_at) {
    text = "Quản lý đã xác nhận món đồ về người giữ.";
  } else if (recoveryRequested && ["OPEN", "IN_REVIEW"].includes(report.status)) {
    text = "Chờ đưa món đồ về người giữ. Quản lý xác nhận tình trạng món đồ sau khi thu hồi.";
    owner = "Người đang giữ món đồ";
  }

  return text && <div className="rr-callout rr-follow-up">
    <strong>{needsReview ? "Đã kết luận cần xét lại người nhận" : "Việc cần làm với món đồ"}</strong>
    <p>{text}</p>
    <small>Phụ trách · {owner}</small>
    {data.recovery?.due_at && !data.recovery.confirmed_at && <p>Hạn phản hồi · {date(data.recovery.due_at)}</p>}
  </div>;
}

function ProcessingPanel({ data, busy, take, onAsk, onAction }) {
  const { report, caseKind, appeal, hold, actions, canProcess, canTake, conflict, assigneeName } = data;
  const isClosed = ["RESOLVED", "REJECTED"].includes(report.status);
  const canAsk = data.canAsk ?? canProcess;
  const title = caseKind === "CLAIM_APPEAL" ? "Quyết định kháng cáo"
    : caseKind === "HANDOVER_ISSUE" ? "Kết quả bàn giao" : "Kết luận báo cáo";
  const structured = actions.some(value => findings.includes(value));
  return <aside id="rr-process" tabIndex={-1} className="rr-card rr-decision" aria-labelledby="rr-process-title">
    <div className="rr-process-heading">
      <span className="rr-case-icon"><ClipboardCheck size={20} aria-hidden="true" /></span>
      <div>
        <h2 id="rr-process-title">Xử lý hồ sơ</h2>
        <small>{canProcess || actions.length ? "Bạn phụ trách" : assigneeName || (canTake ? "Chưa nhận xử lý" : labels[report.status])}</small>
      </div>
    </div>
    {appeal?.appeal_until && <div className="rr-process-deadline">
      <small>Hạn gửi kháng cáo của đợt này</small>
      <time dateTime={appeal.appeal_until}>{date(appeal.appeal_until)}</time>
    </div>}
    {hold?.reason && <div className="rr-callout">
      <strong>Đang tạm dừng giao nhận</strong><p>{hold.reason}</p>
    </div>}
    <FollowUp data={data} />
    {conflict ? <p className="rr-callout">Bạn có liên quan đến vụ việc. Cần một quản lý khác đưa ra quyết định.</p>
      : canTake ? <>
        <p>Nhận xử lý để yêu cầu bổ sung thông tin hoặc ghi kết luận.</p>
        <button type="button" disabled={busy} className="rr-primary" onClick={take}>{busy ? "Đang nhận…" : "Nhận xử lý"}</button>
      </> : canProcess || canAsk || actions.length > 0 ? <>
        {canAsk && <button type="button" onClick={onAsk}><MessageSquare size={16} aria-hidden="true" />Yêu cầu bổ sung</button>}
        {structured ? <button type="button" className="rr-primary" onClick={() => onAction("CONCLUDE_VIOLATION")}>
          Ghi kết luận báo cáo
        </button> : [
          [title, actions.filter(value => !followUpActions.includes(value))],
          ["Việc cần làm với món đồ", actions.filter(value => followUpActions.includes(value))],
        ].map(([heading, items]) => items.length > 0 && <div className="rr-process-actions" key={heading}>
          <h3>{heading}</h3>
          {items.map(value => <button type="button" key={value}
            className={["RESOLVE", "HANDOVER_CONTINUE", "APPEAL_REOPEN"].includes(value) ? "rr-primary" : ""}
            onClick={() => onAction(value)}>
            {value === "RESOLVE" && caseKind === "HANDOVER_ISSUE" ? "Ghi kết quả bàn giao" : labels[value]}
          </button>)}
        </div>)}
      </> : !(appeal?.active && report.status === "RESOLVED") && <p>{isClosed
        ? "Có thể xem kết quả và căn cứ trong lịch sử xử lý."
        : "Hồ sơ đang được quản lý khác xử lý."}</p>}
  </aside>;
}

export default function ReportDetail({ id, onBack, onRelated, showConversation }) {
  const [tab, setTab] = useState(showConversation ? "inquiries" : "overview");
  const [inquiryOpen, setInquiryOpen] = useState(false);
  const [action, setAction] = useState(null);
  const [confirmTake, setConfirmTake] = useState(false);
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const { data, error, setData, setError } = useReviewResource("detail", id, refresh);

  async function take() {
    setBusy(true);
    setError("");
    try {
      setData(await reviewApi.act(id, { version: data.report.version, action: "TAKE" }));
    } catch (error) {
      setError(error.message);
    } finally {
      setBusy(false);
      setConfirmTake(false);
    }
  }

  const report = data?.report;
  const kind = data?.caseKind;
  const current = data?.current;
  const Icon = caseIcons[kind] || Flag;
  const subject = report?.reported_account_id ? current.account?.full_name || "Tài khoản bị báo cáo"
    : report?.message_id ? "Tin nhắn đã báo cáo"
    : current?.post?.title || (report?.claim_id ? `Yêu cầu nhận đồ #${report.claim_id}` : `Báo cáo #${id}`);

  return <>
    <div className="rr-detail-bar">
      <button type="button" onClick={() => onBack(kind)}><ArrowLeft size={17} aria-hidden="true" />Danh sách báo cáo</button>
      <button type="button" disabled={busy} onClick={() => setRefresh(value => value + 1)}>
        <RefreshCw size={15} aria-hidden="true" />Tải lại hồ sơ
      </button>
    </div>
    <ErrorBox>{error}</ErrorBox>
    {!data && !error && <Empty>Đang tải hồ sơ…</Empty>}
    {data && <>
      <header className="rr-card rr-detail-heading">
        <span className="rr-detail-icon"><Icon size={25} aria-hidden="true" /></span>
        <div>
          <div className="rr-detail-meta"><span>{labels[kind]}</span><span>Báo cáo #{report.report_id}</span></div>
          <h1>{subject}</h1>
          <small>Gửi lúc <time dateTime={report.created_at}>{date(report.created_at)}</time></small>
        </div>
        <Badge value={report.status} />
      </header>
      <div className="rr-layout rr-detail-layout">
        <div className="rr-detail-sections">
          <a className="rr-process-jump" href="#rr-process" onClick={event => {
            event.preventDefault();
            const panel = document.getElementById("rr-process");
            panel?.focus({ preventScroll: true });
            panel?.scrollIntoView({ block: "start" });
          }}>Đến phần xử lý <ArrowDown size={15} aria-hidden="true" /></a>
          <div className="rr-detail-nav" role="group" aria-label="Thông tin báo cáo">
            {[
              ["overview", "Tổng quan", FileText],
              ["evidence", kind === "CLAIM_APPEAL" ? "Đối chiếu hồ sơ" : "Dữ liệu đối chiếu", Users],
              ["inquiries", `Trao đổi (${data.inquiries.length})`, MessageSquare],
              ["history", "Lịch sử", History],
            ].map(([key, label, TabIcon]) => <button type="button" key={key} aria-pressed={tab === key}
              onClick={() => setTab(key)}><TabIcon size={16} aria-hidden="true" />{label}</button>)}
          </div>
          {tab === "overview" && <Overview data={data} onRelated={onRelated} />}
          {tab === "evidence" && <Evidence data={data} />}
          {tab === "inquiries" && <div className="rr-stack">
            <Inquiries data={data} onSaved={setData} open={inquiryOpen} setOpen={setInquiryOpen} />
            {!data.conflict && (report.claim_id || report.message_id) && <ChatContext id={id} reportedMessage={report.message_id} />}
          </div>}
          {tab === "history" && <HistoryPanel history={data.history} decisions={data.decisions} />}
        </div>
        <ProcessingPanel data={data} busy={busy} take={() => setConfirmTake(true)} onAsk={() => { setTab("inquiries"); setInquiryOpen(true); }} onAction={setAction} />
      </div>
      {action && <DecisionDialog key={action} action={action} data={data} onClose={() => setAction(null)} onSaved={setData} />}
      <ConfirmActionPopup
        open={confirmTake}
        variant="info"
        title="Nhận xử lý báo cáo này?"
        description={`Báo cáo #${report.report_id} sẽ được phân công cho bạn để yêu cầu thông tin bổ sung và đưa ra kết luận.`}
        confirmLabel="Nhận xử lý"
        busy={busy}
        onConfirm={take}
        onClose={() => setConfirmTake(false)}
      />
    </>}
  </>;
}
