import { useId, useState } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle, ArrowRight, ChevronDown, ClipboardList, FileText, MessageSquare, Package, Scale, UserRound,
} from "lucide-react";
import { Badge, ErrorBox } from "./Shared";
import { date, labels, reportTargets } from "../format";
import useReviewResource from "../hooks/useReviewResource";

const priorityReasons = {
  OFFICE_SOON: "Lịch nhận trước khi hết ca",
  OFFICE_UPCOMING: "Có lịch nhận sắp tới",
  P2P_RISK: "Có phản ánh chưa nhận hoặc giao nhầm",
  P2P_ACTIVE: "Bàn giao trực tiếp cần kiểm tra",
  POST_PRIVACY: "Nghi lộ thông tin trên bài đang hiển thị",
  POST_SCAM: "Nghi lừa đảo trên bài đang hiển thị",
  USER_SAFETY_THREAT: "Có phản ánh đe dọa an toàn",
  HANDOVER: "Cần làm rõ sự cố bàn giao",
  CLAIM_APPEAL: "Chờ xử lý trước bàn giao",
  CLAIM_REOPEN: "Đủ điều kiện xác minh để mở lại xét duyệt",
  RECOVERY_OVERDUE: "Đã qua hạn phản hồi về thu hồi đồ",
  REPLY_OVERDUE: "Đã qua hạn bổ sung thông tin",
  RECOVERY_CONFIRMED: "Đã thu hồi đồ, cần ghi kết quả",
};
const waitingReasons = {
  WAITING_RECOVERY: "Chờ thu hồi hoặc phản hồi về người đang giữ đồ",
  WAITING_REPLY: "Chờ người liên quan phản hồi",
  WAITING_APPEAL_WINDOW: "Chờ hết thời hạn kháng cáo",
  WAITING_OTHER_APPEALS: "Chờ xử lý kháng cáo cùng đợt",
  WAITING_REOPEN: "Chờ đủ điều kiện mở lại xét duyệt",
};
const priorityNames = ["Cần xử lý đầu ca", "Sẵn sàng xử lý", "Xử lý theo lượt"];
const icons = { POST: FileText, CLAIM: ClipboardList, MESSAGE: MessageSquare, USER: UserRound };

function ReplyBadge({ reason }) {
  return <span className="rr-reply-badge">
    {reason === "RECOVERY_REPLY" ? "Có phản hồi về thu hồi đồ" : "Có phản hồi mới"}
  </span>;
}

function workDate(value, timeZone) {
  return new Intl.DateTimeFormat("vi-VN", {
    timeZone: timeZone || "Asia/Ho_Chi_Minh", day: "numeric", month: "numeric",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).format(new Date(value));
}

export default function ReportGroup({ report, filters, reportPath, timeZone }) {
  const [expanded, setExpanded] = useState(false);
  const contentId = useId();
  const { post_id, claim_id, message_id, reported_account_id } = report;
  const target = post_id ? "POST" : claim_id ? "CLAIM" : message_id ? "MESSAGE" : "USER";
  const targetId = post_id ?? claim_id ?? message_id ?? reported_account_id;
  const multiple = report.object_count > 1;
  const priority = priorityReasons[report.priority_reason];
  const level = report.work_state === "READY" ? priorityNames[report.priority_rank] : null;
  const waiting = waitingReasons[report.waiting_reason];
  const appointment = report.next_pickup && workDate(report.next_pickup, timeZone);
  const Icon = report.kind === "CLAIM_APPEAL" ? Scale : report.kind === "HANDOVER_ISSUE" ? Package : icons[target];
  const reference = `${target === "MESSAGE" ? "Tin nhắn (báo cáo cũ)" : reportTargets[target]} #${targetId}`;
  const title = target === "MESSAGE" ? report.message_preview?.trim() || "Tin nhắn đính kèm" : report.subject_title || reference;
  const person = target === "CLAIM" ? report.claimant_name : target === "POST" || target === "MESSAGE" ? report.subject_name : "";
  const reasons = Object.entries(report.reason_summary ?? {}).sort((a, b) => b[1] - a[1]);
  const primaryReason = reasons[0]?.[0] || report.reason_label_snapshot || labels[report.kind];
  return <tbody role="rowgroup">
    <tr role="row" className={`rr-case-row${report.priority_rank === 0 ? " is-priority" : ""}`}>
      <td role="cell" className="rr-case-subject">
        <div className="rr-case-identity">
          <span className="rr-case-icon"><Icon size={18} aria-hidden="true" /></span>
          <div className="rr-case-name">
            <small>{reference} <span aria-hidden="true">·</span> {multiple ? `${report.object_count} báo cáo` : `Báo cáo #${report.report_id}`}</small>
            {multiple ? <strong>{title}</strong>
              : <Link to={reportPath(report.report_id)}>{title}</Link>}
            <small>{person && `${person} · `}<time dateTime={report.created_at}>{date(report.created_at)}</time></small>
          </div>
        </div>
      </td>
      <td role="cell" className="rr-case-reason">
        <span className="rr-case-mobile-label">Lý do</span>
        <strong>{primaryReason}</strong>
        {reasons.length > 1 && <small>+{reasons.length - 1} lý do khác</small>}
        {(level || waiting || priority) && <span className={`rr-case-priority${report.priority_rank === 0 ? " is-urgent" : ""}`}>
          <AlertCircle size={13} aria-hidden="true" />{level || waiting || priority}
        </span>}
        {level && priority && <small>{priority}</small>}
        {appointment && <small>Lịch nhận <time dateTime={report.next_pickup}>{appointment}</time></small>}
        {report.waiting_reason === "WAITING_APPEAL_WINDOW" && report.appeal_until &&
          <small>Đến <time dateTime={report.appeal_until}>{workDate(report.appeal_until, timeZone)}</time></small>}
      </td>
      <td role="cell" className="rr-case-status">
        <span className="rr-case-mobile-label">Trạng thái</span>
        <Badge value={report.status} />
        {report.carried_over && <small>Tồn từ ngày làm việc trước</small>}
        {level && (report.has_new_reply || report.group_has_new_reply) &&
          <ReplyBadge reason={report.priority_reason} />}
        {report.response_due_at && <small className={report.response_overdue ? "rr-response-overdue" : undefined}>
          {report.response_overdue ? "Quá hạn phản hồi" : "Phản hồi trước"}{" "}
          <time dateTime={report.response_due_at}>{workDate(report.response_due_at, timeZone)}</time>
        </small>}
        {multiple && report.open_count > 0 && <small>{report.open_count} báo cáo đang mở</small>}
      </td>
      <td role="cell" className="rr-case-assignee">
        <span className="rr-case-mobile-label">Phụ trách</span>
        <span>{multiple ? "Theo từng báo cáo" : report.assignee || "Chưa phân công"}</span>
      </td>
      <td role="cell" className="rr-case-open">
        {multiple ? <button type="button" aria-expanded={expanded} aria-controls={contentId}
          onClick={() => setExpanded(value => !value)}>
          {expanded ? "Thu gọn" : "Xem nhóm"}
          <ChevronDown size={15} className={expanded ? "rr-chevron-open" : ""} aria-hidden="true" />
        </button> : <Link className="rr-case-link" to={reportPath(report.report_id)} aria-label={`Mở báo cáo #${report.report_id}`}>
          Mở hồ sơ <ArrowRight size={15} aria-hidden="true" />
        </Link>}
      </td>
    </tr>
    {multiple && <tr role="row" className="rr-case-expansion" hidden={!expanded}>
      <td role="cell" colSpan={5} id={contentId}>
        {expanded && <Reports filters={{ ...filters, kind: report.kind, target, targetId }} reportPath={reportPath} />}
      </td>
    </tr>}
  </tbody>;
}

function Reports({ filters, reportPath }) {
  const [page, setPage] = useState(1);
  const [retry, setRetry] = useState(0);
  const { data, error } = useReviewResource("queue", { ...filters, page }, retry);
  return <div className="rr-case-children">
    <ErrorBox>{error}</ErrorBox>
    {error && <button type="button" onClick={() => setRetry(value => value + 1)}>Thử lại</button>}
    {!data && !error && <p role="status">Đang tải báo cáo…</p>}
    {data && <>
      {!data.content.length && <p>Không có báo cáo phù hợp bộ lọc.</p>}
      <ul>{data.content.map(report => <li key={report.report_id}>
        <div>
          <strong>#{report.report_id} · {report.reason_label_snapshot || labels[report.kind]}</strong>
          <small>{report.reporter && `${report.reporter} · `}{date(report.created_at)} · {report.assignee || "Chưa phân công"}</small>
        </div>
        <div className="rr-case-child-status">
          <Badge value={report.status} />
          {report.work_state === "READY" && report.has_new_reply && <ReplyBadge reason={report.priority_reason} />}
        </div>
        <Link className="rr-case-link" to={reportPath(report.report_id)} aria-label={`Mở báo cáo #${report.report_id}`}>
          Mở hồ sơ <ArrowRight size={15} aria-hidden="true" />
        </Link>
      </li>)}</ul>
      {data.totalPages > 1 && <nav className="rr-queue-pagination" aria-label="Trang báo cáo trong nhóm">
        <span>Trang {page} / {data.totalPages}</span>
        <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}>Trước</button>
        <button type="button" disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button>
      </nav>}
    </>}
  </div>;
}
