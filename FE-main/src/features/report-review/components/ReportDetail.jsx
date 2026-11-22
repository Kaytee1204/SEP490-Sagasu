import ConfirmActionPopup from "@/components/common/ConfirmActionPopup";
import { useEffect, useState } from "react";
import { ArrowLeft, ArrowRight, ChevronDown, ClipboardCheck, History, MessageSquare, RefreshCw, RotateCcw, ShieldCheck } from "lucide-react";
import { reviewApi } from "../api";
import { Badge, ErrorBox, Empty, Gallery } from "./Shared";
import { appealActionHelp, availableRecipients, date, labels, officeAppealActionHelp, reviewKinds } from "../format";
import ContextPanel from "./ContextPanel";
import DecisionDialog from "./DecisionDialog";
import Inquiries from "./Inquiries";
import ChatContext from "./ChatContext";
import AppealEvidenceComparison from "./AppealEvidenceComparison";
import useReportResource from "@/features/reports/hooks/useReportResource";
import { appealInstruction, appealSteps } from "@/features/reports/utils/reportUtils";
import { handoverStatusLabels, incidentPhaseLabels } from "@/features/handover/utils/issueForm";

const findings = ["VIOLATION", "NO_VIOLATION", "INSUFFICIENT_EVIDENCE"];
const itemMeasures = ["HOLD", "RELEASE", "REQUEST_ITEM_RECOVERY"];

function Submission({ data }) {
  const { report } = data;
  const description = report.description?.trim() || "";
  const reason = report.reason_label_snapshot;
  const explanation = reason && (description === reason || description.startsWith(reason + "\n"))
    ? description.slice(reason.length).trim() : description;
  return <section className="rr-card rr-report-content">
      <h2>{data.caseKind === "CLAIM_APPEAL" ? "Nội dung tranh chấp" : "Nội dung báo cáo"}</h2>
      {data.incident?.phase && <p><strong>Giai đoạn trao trả</strong> · {incidentPhaseLabels[data.incident.phase]}</p>}
      {data.incident?.submission_status && <p><strong>Trạng thái lúc gửi</strong> · {handoverStatusLabels[data.incident.submission_status]}</p>}
      {reason && <p className="rr-report-reason">{reason}</p>}
      {explanation ? <p className="rr-submission-text">{explanation}</p> : !reason && <p>Chưa có mô tả từ người gửi.</p>}
      {data.incident?.message_body && <div>
        <h3>Tin nhắn kèm báo cáo</h3>
        <p>{data.incident.message_sender} · {date(data.incident.message_created_at)}</p>
        <blockquote className="rr-submission-text">{data.incident.message_body}</blockquote>
      </div>}
      {data.evidence.length > 0 && <>
        <h3>Ảnh kèm báo cáo ({data.evidence.length})</h3><Gallery images={data.evidence} />
      </>}
    </section>;
}

function RelatedInformation({ data, onRelated }) {
  const { current, report, related } = data;
  const relatedReports = data.caseKind === "CLAIM_APPEAL" ? related.filter(item => item.same_approval) : related;
  return <div className="rr-stack">
    {current.participants.length > 0 && <section className="rr-card rr-participants">
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
    {relatedReports.length > 0 && <section className="rr-card">
      <h2>{data.caseKind === "CLAIM_APPEAL" ? "Tranh chấp cùng lần duyệt" : "Báo cáo liên quan"}</h2>
      <div className="rr-related-list">
        {relatedReports.map(item => <button type="button" className="rr-related" key={item.report_id} onClick={() => onRelated(item.report_id)}>
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
  const { current, approvedContext } = data;
  return <div className="rr-stack">
    {approvedContext ? <>
      <AppealEvidenceComparison current={current} approved={approvedContext} />
      <ContextDisclosure title="Hồ sơ đầy đủ của người gửi tranh chấp"><ContextPanel data={current} /></ContextDisclosure>
      <ContextDisclosure title="Hồ sơ đầy đủ của người được chấp thuận"><ContextPanel data={approvedContext} /></ContextDisclosure>
    </> : <ContextPanel data={current} />}
  </div>;
}

function HistoryPanel({ history, decisions = [] }) {
  return <section className="rr-card">
    <h2>Lịch sử xử lý</h2>
    <p className="rr-muted">Ghi chú và căn cứ nội bộ chỉ quản lý được xem.</p>
    {history.length === 0 ? <Empty>Chưa có hoạt động xử lý.</Empty> :
      <ol className="rr-timeline">
        {[...history].reverse().map(item => <li key={item.activity_id}>
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

  const recovery = data.recovery;
  const questions = data.inquiries.filter(i => !i.responded_at && !i.closed_at);
  const holderReply = data.inquiries.find(i => i.inquiry_id === recovery?.inquiry_id);
  if (recovery?.holder_name && !recovery.confirmed_at && (!["RESOLVED", "REJECTED"].includes(report.status) || needsReview)) {
    if (holderReply?.responded_at || holderReply?.overdue) {
      text = holderReply.responded_at ? "Người giữ đồ đã phản hồi. Đối chiếu thông tin trước khi xác nhận đã thu hồi." : "Đã qua hạn phản hồi về thu hồi. Kiểm tra tình trạng món đồ trước khi xác nhận.";
    } else {
      owner = recovery.holder_name;
      text = "Người giữ đồ cần phản hồi yêu cầu thu hồi. Quản lý xác nhận sau khi đối chiếu bằng chứng.";
    }
  } else if (appeal?.follow_up) {
    text = appealInstruction(appeal.follow_up, appeal.claim_type === "OFFICE");
    if (appeal.follow_up === "READY_TO_REOPEN" && appeal.claim_type !== "OFFICE") owner = "Người nhặt";
  } else if (questions.length && ["OPEN","IN_REVIEW"].includes(report.status)) {
    const overdue = questions.some(i => i.overdue);
    text = overdue ? "Đã qua hạn bổ sung. Xem trao đổi và đánh giá các căn cứ hiện có." : "Chờ bổ sung thông tin. Mở Trao đổi để xem câu hỏi và hạn phản hồi của từng người.";
    if (!overdue) owner = [...new Set(questions.map(i => i.recipient))].join(", ");
  } else if (["APPEAL_REOPEN", "HANDOVER_REOPEN"].includes(lastDecision)) {
    text = "Đã mở lại xét duyệt người nhận.";
    owner = appeal?.claim_type === "OFFICE" ? "Văn phòng" : "Người nhặt";
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

  if (!text && data.canProcess) text = caseKind === "CLAIM_APPEAL"
    ? "Đối chiếu câu trả lời và ảnh của hai người trước khi ghi kết luận."
    : report.reason_code === "HANDOVER_MISSING_ITEMS" ? "Hỏi người nhận còn thiếu gì và đối chiếu với phụ kiện người nhặt đã nhận giữ. Nếu cần giao bổ sung, theo dõi phản hồi trong mục Trao đổi trước khi kết luận."
    : caseKind === "HANDOVER_ISSUE" ? "Đối chiếu ảnh bàn giao và xác minh ai đang giữ món đồ trước khi ghi kết luận."
      : "Đối chiếu nội dung bị báo cáo, ghi kết luận và chọn biện pháp nếu xác định có vi phạm.";
  return text && <div className="rr-callout rr-follow-up">
    <strong>{recovery?.holder_name && !recovery.confirmed_at ? "Thu hồi món đồ" : appealSteps[appeal?.follow_up] || "Bước tiếp theo"}</strong>
    <p>{text}</p>
    {owner !== assigned && <small>Người cần thực hiện · {owner}</small>}
    {data.recovery?.due_at && !data.recovery.confirmed_at && <p>Hạn phản hồi · {date(data.recovery.due_at)}</p>}
  </div>;
}

const appealActionIcons = { APPEAL_KEEP: ShieldCheck, APPEAL_WRONG_RECIPIENT: RotateCcw };

function AppealActions({ actions, onAction, office }) {
  const decisions = ["APPEAL_KEEP", "APPEAL_WRONG_RECIPIENT"];
  const groups = [
    { title: "Kết luận tranh chấp", values: decisions.filter(value => actions.includes(value)) },
    { title: "Theo dõi yêu cầu thu hồi đã gửi", values: actions.filter(value => value === "CONFIRM_ITEM_RECOVERY") },
  ];
  return groups.filter(group => group.values.length).map(({ title, values }) => (
    <section className="rr-appeal-actions" key={title}>
      <h3>{title}</h3>
      {values.map(value => {
        const Icon = appealActionIcons[value];
        const help = (office ? officeAppealActionHelp : appealActionHelp)[value];
        return <div className="rr-appeal-action" key={value}>
          <button type="button"
            aria-describedby={help ? `rr-help-${value}` : undefined} onClick={() => onAction(value)}>
            {Icon && <Icon size={16} aria-hidden="true" />}<span>{labels[value]}</span>
          </button>
          {help && <p id={`rr-help-${value}`} className="rr-appeal-tooltip" role="tooltip">{help}</p>}
        </div>;
      })}
    </section>
  ));
}

function ProcessingPanel({ data, busy, take, onAsk, onAction }) {
  const { report, caseKind, hold, actions, canProcess, canTake, conflict, assigneeName } = data;
  const isClosed = ["RESOLVED", "REJECTED"].includes(report.status);
  const canAsk = (data.canAsk ?? canProcess) && availableRecipients(data).length > 0;
  const isAppeal = caseKind === "CLAIM_APPEAL";
  const conclusion = data.decisions?.findLast(item => findings.includes(item.action)
    || ["APPEAL_KEEP", "APPEAL_REJECT_CLAIM", "APPEAL_REQUIRE_REVIEW", "APPEAL_WRONG_RECIPIENT"].includes(item.action));
  const title = isClosed ? "Tiếp tục xử lý" : caseKind === "HANDOVER_ISSUE" ? "Kết quả bàn giao" : "Kết luận báo cáo";
  const structured = actions.some(value => findings.includes(value));
  const otherActions = actions.filter(value => !itemMeasures.includes(value));
  const isHandover = caseKind === "HANDOVER_ISSUE";
  return <aside id="rr-process" tabIndex={-1} className="rr-card rr-decision rr-workflow" aria-labelledby="rr-process-title">
    <div className="rr-process-heading">
      <span className="rr-case-icon"><ClipboardCheck size={20} aria-hidden="true" /></span>
      <div>
        <h2 id="rr-process-title">{isClosed ? "Kết quả và bước tiếp theo" : isAppeal ? "Xử lý tranh chấp" : "Xử lý hồ sơ"}</h2>
        <small>{canProcess || actions.length ? "Bạn phụ trách" : assigneeName ? `Quản lý phụ trách · ${assigneeName}` : canTake ? "Chưa có người phụ trách" : ""}</small>
      </div>
    </div>
    {isClosed && <div className="rr-public-result">
      <strong>{conclusion ? labels[conclusion.action] : "Kết luận đã gửi cho người liên quan"}</strong>
      <p>{report.resolution_note || labels[report.status]}</p>
      {conclusion?.applied_measure && <small>Biện pháp · {labels[conclusion.applied_measure]}</small>}
    </div>}
    {hold?.reason && <div className="rr-callout">
      <strong>Đang tạm dừng giao nhận</strong><p>{hold.reason}</p>
    </div>}
    <FollowUp data={data} />
    {conflict && !isClosed ? <p className="rr-callout">Bạn có liên quan đến vụ việc. Cần một quản lý khác đưa ra quyết định.</p>
      : canTake ? <>
        <p>Nhận xử lý để yêu cầu bổ sung thông tin hoặc ghi kết luận.</p>
        <button type="button" disabled={busy} className="rr-primary" onClick={take}>{busy ? "Đang nhận…" : "Nhận xử lý"}</button>
      </> : canProcess || canAsk || actions.length > 0 ? <div className="rr-workflow-actions">
        {canAsk && <button type="button" onClick={onAsk}><MessageSquare size={16} aria-hidden="true" />
          {report.reason_code === "HANDOVER_MISSING_ITEMS" ? "Hỏi về phụ kiện còn thiếu" : "Hỏi người liên quan"}</button>}
        {isAppeal ? <>
          <AppealActions actions={actions} onAction={onAction} office={data.appeal?.claim_type === "OFFICE"} />
          {canProcess && !actions.includes("APPEAL_WRONG_RECIPIENT") && <p className="rr-callout">
            Hiện chưa đủ điều kiện mở lại xét duyệt. Nếu đồ đã giao, xử lý trong Sự cố bàn giao.
          </p>}
        </>
        : structured ? <button type="button" className="rr-primary" onClick={() => onAction("CONCLUDE_VIOLATION")}>
          Ghi kết luận báo cáo
        </button> : <>
          {isHandover && <>
            {actions.some(value => itemMeasures.includes(value) || value === "CONFIRM_ITEM_RECOVERY") &&
              <button type="button" onClick={() => onAction("HANDOVER_INTERIM")}>
                {actions.includes("CONFIRM_ITEM_RECOVERY") ? "Theo dõi việc trả lại đồ"
                  : actions.includes("REQUEST_ITEM_RECOVERY") ? "Tạm dừng hoặc yêu cầu trả lại đồ"
                  : actions.includes("RELEASE") ? "Gỡ lệnh tạm dừng giao nhận" : "Tạm dừng giao nhận"}
              </button>}
            {otherActions.some(value => value !== "CONFIRM_ITEM_RECOVERY") &&
              <button type="button" className="rr-primary" onClick={() => onAction("CONCLUDE_HANDOVER")}>Ghi kết luận sự cố</button>}
          </>}
          {!isHandover && otherActions.length > 0 && <div className="rr-process-actions">
            <h3>{title}</h3>
            {otherActions.map(value => <button type="button" key={value}
              className={["RESOLVE", "HANDOVER_CONTINUE", "CONFIRM_ITEM_RECOVERY"].includes(value) ? "rr-primary" : ""}
              onClick={() => onAction(value)}>
              {value === "RESOLVE" && caseKind === "HANDOVER_ISSUE" ? "Ghi kết quả bàn giao" : labels[value]}
            </button>)}
          </div>}
          {!isHandover && actions.some(value => itemMeasures.includes(value)) && <details className="rr-context-disclosure rr-optional-actions">
            <summary>Quản lý món đồ<ChevronDown size={16} aria-hidden="true" /></summary>
            <div className="rr-workflow-actions">
              {actions.filter(value => itemMeasures.includes(value)).map(value =>
                <button type="button" key={value} onClick={() => onAction(value)}>{labels[value]}</button>)}
            </div>
          </details>}
        </>}
      </div> : !isClosed && <p>Hồ sơ đang được quản lý khác xử lý.</p>}
  </aside>;
}

export default function ReportDetail({ id, onBack, onRelated, showConversation }) {
  const [tab, setTab] = useState(showConversation ? "inquiries" : "process");
  const [inquiryData, setInquiryData] = useState(null);
  const [action, setAction] = useState(null);
  const [busy, setBusy] = useState(false);
  const [confirmTake, setConfirmTake] = useState(false);
  const { data, error, setData, reload } = useReportResource(id, () => reviewApi.detail(id));
  const [actionError, setError] = useState("");

  const hasDetail = Boolean(data);
  useEffect(() => {
    if (showConversation && hasDetail) {
      const section = document.getElementById("trao-doi");
      section?.focus({ preventScroll: true });
      section?.scrollIntoView({ block: "start" });
    }
  }, [showConversation, hasDetail]);

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
  const reporter = current?.participants.find(person => person.id === report.reporter_id);
  const thumbnail = current?.postImages?.[0]?.url || data?.snapshot?.payload?.postImages?.[0]?.url;
  const targetLabel = report?.post_id ? "Bài đăng" : report?.claim_id ? "Yêu cầu nhận đồ" : "";
  const type = kind === "GENERAL" && targetLabel ? `Vi phạm · ${targetLabel}` : reviewKinds[kind];
  const subject = report?.reported_account_id ? current.account?.full_name || "Tài khoản bị báo cáo"
    : report?.message_id ? "Tin nhắn đã báo cáo"
    : current?.post?.title || data?.snapshot?.payload?.post?.title || targetLabel || "Chi tiết báo cáo";

  return <>
    <div className="rr-detail-bar">
      <button type="button" onClick={() => onBack(kind)}><ArrowLeft size={17} aria-hidden="true" />Danh sách báo cáo</button>
      <button type="button" disabled={busy} onClick={() => { setError(""); reload(); }}>
        <RefreshCw size={15} aria-hidden="true" />Tải lại hồ sơ
      </button>
    </div>
    <ErrorBox>{actionError || error}</ErrorBox>
    {!data && !error && <Empty>Đang tải hồ sơ…</Empty>}
    {data && <>
      <header className="rr-card rr-detail-heading">
        {thumbnail && <img key={thumbnail} className="rr-detail-thumbnail" src={thumbnail} alt=""
          onError={event => { event.currentTarget.hidden = true; }} />}
        <div>
          <div className="rr-detail-meta">
            <span>{type}</span>
          </div>
          <h1>{subject}</h1>
          <div className="rr-detail-meta">
            <small>Người báo cáo · {reporter?.full_name || `Tài khoản #${report.reporter_id}`}</small>
            <small>Gửi lúc <time dateTime={report.created_at}>{date(report.created_at)}</time></small>
          </div>
        </div>
        {appealSteps[data.appeal?.follow_up] ? <span className="rr-badge">Tranh chấp đã có kết luận</span> : <Badge value={report.status} />}
      </header>
      <div className="rr-detail-sections">
          <div className="rr-detail-nav" role="group" aria-label="Thông tin báo cáo">
            {[
              ["process", "Hồ sơ và xử lý", ClipboardCheck],
              ["inquiries", data.inquiries.length ? `Trao đổi (${data.inquiries.length})` : "Trao đổi", MessageSquare],
              ["history", "Lịch sử", History],
            ].map(([key, label, TabIcon]) => <button type="button" key={key} aria-pressed={tab === key}
              onClick={event => {
                setTab(key);
                const sections = event.currentTarget.closest(".rr-detail-sections");
                if (sections.getBoundingClientRect().top < 0) sections.scrollIntoView({ block: "start" });
              }}><TabIcon size={16} aria-hidden="true" />{label}</button>)}
          </div>
          {tab === "process" && <div className="rr-review-sheet">
            <div className="rr-sheet-main">
              <Submission data={data} />
              <Evidence data={data} />
            </div>
            <div className="rr-sheet-side">
              <ProcessingPanel data={data} busy={busy} take={() => setConfirmTake(true)} onAsk={() => { setTab("inquiries"); setInquiryData(data); }}
                onAction={value => setAction({ value, data })} />
              <RelatedInformation data={data} onRelated={onRelated} />
            </div>
          </div>}
          {tab === "inquiries" && <div id="trao-doi" tabIndex={-1} className="rr-stack">
            <Inquiries data={data} onSaved={setData} open={!!inquiryData} setOpen={value => setInquiryData(value ? data : null)} draftData={inquiryData} />
            {!data.conflict && (report.claim_id || report.message_id) && <ChatContext id={id} reportedMessage={report.message_id} />}
          </div>}
          {tab === "history" && <div className="rr-stack">
            <HistoryPanel history={data.history} decisions={data.decisions} />
            {data.snapshot?.payload && <ContextDisclosure title="Bản lưu hồ sơ">
              <p className="rr-muted">{data.snapshot.capture_source === "SUBMISSION" ? "Lưu khi gửi báo cáo" : "Lưu khi quản lý mở hồ sơ lần đầu"} · {date(data.snapshot.captured_at)}</p>
              <ContextPanel snapshot data={data.snapshot.payload} />
            </ContextDisclosure>}
          </div>}
      </div>
      <ConfirmActionPopup open={confirmTake} variant="info"
        title="Nhận xử lý báo cáo này?"
        description="Báo cáo sẽ được phân công cho bạn để yêu cầu bổ sung và ghi kết luận."
        confirmLabel="Nhận xử lý" cancelLabel="Hủy bỏ"
        busy={busy} onConfirm={take} onClose={() => setConfirmTake(false)} />
      {action && <DecisionDialog key={action.value} action={action.value} data={action.data} onClose={() => setAction(null)} onSaved={setData} />}
    </>}
  </>;
}
