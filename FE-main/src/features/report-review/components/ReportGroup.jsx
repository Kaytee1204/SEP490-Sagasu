import { useState } from "react";
import { AlertCircle, ChevronDown, ClipboardList, FileText, MessageSquare, UserRound } from "lucide-react";
import { Badge, ErrorBox } from "./Shared";
import { date, labels, reportTargets } from "../format";
import useReviewResource from "../hooks/useReviewResource";

function Reports({ filters, onOpen }) {
  const [page, setPage] = useState(1);
  const [retry, setRetry] = useState(0);
  const { data, error } = useReviewResource("queue", { ...filters, page }, retry);
  return (
    <div className="rr-group-reports">
      <ErrorBox>{error}</ErrorBox>
      {error && <button onClick={() => setRetry(retry + 1)}>Thử lại</button>}
      {!data && !error && <p role="status">Đang tải báo cáo…</p>}
      {data && (
        <>
          {!data.content.length && <p>Không còn báo cáo phù hợp bộ lọc.</p>}
          <ul>
            {data.content.map((report) => (
              <li key={report.report_id}>
                <div>
                  <strong>#{report.report_id} · {report.reason_label_snapshot || labels[report.kind]}</strong>
                  {reportNote(report) && <p>{reportNote(report)}</p>}
                  <small>{report.reporter} · {date(report.created_at)}</small>
                </div>
                <Badge value={report.status} />
                <button onClick={() => onOpen(report.report_id)}>Mở báo cáo #{report.report_id}</button>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && (
            <footer className="rr-pagination">
              <button disabled={page === 1} onClick={() => setPage(page - 1)}>Trước</button>
              <span>Trang {page}/{data.totalPages}</span>
              <button disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button>
            </footer>
          )}
        </>
      )}
    </div>
  );
}

const priorityReasons = {
  MANUAL_HIGH: "Quản lý đánh dấu cần xem sớm. Xem căn cứ trong lịch sử xử lý.",
  OFFICE_SOON: "Có lịch nhận đồ trong 24 giờ tới",
  P2P_ACTIVE: "Bàn giao trực tiếp đang chờ xác nhận hoặc có tranh chấp",
  HANDOVER: "Cần làm rõ vướng mắc trao trả",
};

const icons = { POST: FileText, CLAIM: ClipboardList, MESSAGE: MessageSquare, USER: UserRound };
const messageTypes = { IMAGE: "Tin nhắn có ảnh", LOCATION: "Tin nhắn vị trí", SYSTEM: "Tin nhắn hệ thống" };
const visibility = { VISIBLE: "Bài đang hiển thị", HIDDEN: "Bài đã ẩn", PENDING: "Bài chờ duyệt", REJECTED: "Bài bị từ chối" };

function reportNote(report) {
  const description = report.description?.trim() || "";
  const reason = report.reason_label_snapshot;
  if (reason && description === reason) return "";
  return reason && description.startsWith(reason + "\n") ? description.slice(reason.length).trim() : description;
}

export default function ReportGroup({ report, filters, onOpen }) {
  const [expanded, setExpanded] = useState(false);
  const { post_id, claim_id, message_id, reported_account_id } = report;
  const target = post_id ? "POST" : claim_id ? "CLAIM" : message_id ? "MESSAGE" : "USER";
  const targetId = post_id ?? claim_id ?? message_id ?? reported_account_id;
  const multiple = report.object_count > 1;
  const priority = priorityReasons[report.priority_reason];
  const Icon = icons[target];
  const targetLabel = `${reportTargets[target]} #${targetId}`;
  const title = target === "MESSAGE"
    ? (report.message_preview?.trim() || messageTypes[report.message_type] || "Nội dung tin nhắn")
    : (report.subject_title || targetLabel);
  const reasons = Object.entries(report.reason_summary ?? {}).sort((a, b) => b[1] - a[1]);
  const note = reportNote(report);
  return (
    <article className={`rr-report-group${report.priority_rank === 0 ? " rr-group-urgent" : ""}`}>
      <div className="rr-group-header">
        <span className="rr-group-icon"><Icon size={19} aria-hidden="true" /></span>
        <div className="rr-group-title">
          <p className="rr-muted">{targetLabel} · {report.object_count ?? 1} báo cáo
            {multiple && report.open_count != null && ` · ${report.open_count} chưa kết thúc`}
          </p>
          <h3>{title}</h3>
          {target === "POST" && report.subject_name && <p className="rr-muted">Người đăng: {report.subject_name}</p>}
          {target === "MESSAGE" && report.subject_name && <p className="rr-muted">Người gửi tin: {report.subject_name}</p>}
          {target === "CLAIM" && <p className="rr-muted">
            {[report.related_post_id && `Bài #${report.related_post_id}`, report.claimant_name && `Người yêu cầu: ${report.claimant_name}`, labels[report.claim_status]].filter(Boolean).join(" · ")}
          </p>}
        </div>
        {multiple ? (
          <button aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
            {expanded ? "Thu gọn" : `Xem ${report.object_count} báo cáo`}
            <ChevronDown size={16} className={expanded ? "rr-chevron-open" : ""} aria-hidden="true" />
          </button>
        ) : (
          <button onClick={() => onOpen(report.report_id)}>Mở báo cáo #{report.report_id}</button>
        )}
      </div>
      <div className="rr-group-preview">
        {priority && <p className="rr-group-priority">
          <AlertCircle size={16} aria-hidden="true" /> {priority}
        </p>}
        <ul className="rr-group-reasons" aria-label="Lý do báo cáo">
          {(reasons.length ? reasons : [[report.reason_label_snapshot || labels[report.kind], 1]]).map(([reason, quantity]) => (
            <li key={reason}>{multiple && <strong>{quantity} </strong>}{reason}</li>
          ))}
        </ul>
        {note && <p className="rr-group-note">{multiple && <strong>Báo cáo #{report.report_id}: </strong>}{note}</p>}
        <div className="rr-group-meta">
          {multiple && !note && <span>Báo cáo #{report.report_id}</span>}
          <Badge value={report.status} />
          {target === "POST" && visibility[report.moderation_status] && <span>{visibility[report.moderation_status]}</span>}
          {!multiple && report.reporter && <span>Người báo cáo: {report.reporter}</span>}
          <span>{report.assignee ? `Phụ trách: ${report.assignee}` : "Chưa nhận xử lý"}</span>
          <span>{date(report.created_at)}</span>
        </div>
      </div>
      {expanded && <Reports filters={{ ...filters, target, targetId }} onOpen={onOpen} />}
    </article>
  );
}
