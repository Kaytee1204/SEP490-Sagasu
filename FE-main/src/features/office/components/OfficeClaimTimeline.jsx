import { Check, ChevronRight, CircleDot, ClipboardList, History, X } from "lucide-react";
import { officeApi } from "../api/officeApi";
import useLoad from "../hooks/useLoad";
import { claimActivityReason, date, labels, progressLabels } from "../officeUtils";

const events = {
  SUBMIT: ["Tạo hồ sơ"],
  APPROVE: ["Duyệt yêu cầu", "Người duyệt", "Căn cứ phê duyệt"],
  REJECT: ["Từ chối yêu cầu", "Staff", "Lý do từ chối"],
  REQUEST_INFO: ["Yêu cầu bổ sung thông tin", "Người yêu cầu bổ sung", "Nội dung văn phòng cần làm rõ"],
  RESUBMIT: ["Đã bổ sung thông tin", "Người bổ sung"],
  CANCEL: ["Hủy yêu cầu", "Người hủy", "Lý do hủy"],
  TRANSFER_OFFICE: ["Văn phòng tiếp nhận xử lý", "Người tiếp nhận", "Nội dung chuyển giao"],
  RESCHEDULE: ["Đổi lịch hẹn", "Người đổi lịch", "Lý do đổi lịch"],
  OFFICE_CANCEL: ["Hủy lịch và thu hồi duyệt", "Người xử lý", "Lý do thu hồi duyệt"],
  REVOKE_APPROVAL: ["Thu hồi duyệt", "Người xử lý", "Lý do thu hồi duyệt"],
  HANDOVER: ["Ghi nhận giao đồ", "Người ghi nhận", "Nội dung giao đồ"],
  CONFIRM_RECEIPT: ["Hoàn tất trao trả", "Người ghi nhận", "Nội dung xác nhận"],
  AUTO_CLOSE: ["Đóng hồ sơ do đồ đã được trao trả", null, "Lý do đóng hồ sơ"],
  REPORT_ISSUE: ["Phản ánh việc nhận đồ", "Người gửi phản ánh", "Thông tin phản ánh"],
  INVITE_CONFIRM: ["Đồng ý lịch hẹn", "Người phản hồi"],
  INVITE_DECLINE: ["Từ chối lịch hẹn", "Người phản hồi"],
  INVITE_EXPIRE: ["Hết hạn phản hồi lịch hẹn"],
};

const terminalStages = ["COMPLETED", "REJECTED", "CANCELLED", "CLOSED"];

function TimelineField({ label, children, block = false }) {
  if (!children) return null;
  return (
    <div className={block ? "of-claim-timeline-field of-claim-timeline-field-block" : "of-claim-timeline-field"}>
      <dt>{label}{!block && ":"}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function TimelineDisclosure({ label, count, children }) {
  return (
    <details className="of-claim-timeline-disclosure">
      <summary>
        <ChevronRight size={16} aria-hidden="true" />
        <span>{label}</span>
        {count > 0 && <span className="of-muted">{count} ảnh</span>}
      </summary>
      <div className="of-claim-timeline-evidence">{children}</div>
    </details>
  );
}

function SupplementDetails({ activity, request, entry, state }) {
  const images = entry?.images ?? [];
  const question = entry?.question || request?.reason;
  const information = entry?.information || activity.reason || "Hồ sơ không có nội dung trả lời bằng chữ.";
  return (
    <TimelineDisclosure label="Xem phản hồi bổ sung" count={images.length}>
      <dl className="of-claim-timeline-fields">
        {request?.createdAt && (
          <TimelineField label="Văn phòng yêu cầu lúc"><time dateTime={request.createdAt}>{date(request.createdAt)}</time></TimelineField>
        )}
        <TimelineField label="Nội dung văn phòng cần làm rõ" block>{question}</TimelineField>
        <TimelineField label="Phản hồi của người yêu cầu" block>{information}</TimelineField>
      </dl>
      {!!images.length && (
        <>
          <h4>Ảnh đính kèm</h4>
          <div className="of-gallery">
            {images.map((image, index) => (
              <a key={image.image_id} href={image.url} target="_blank" rel="noreferrer">
                <img src={image.url} alt={image.original_name || `Ảnh bổ sung ${index + 1}`} loading="lazy" />
              </a>
            ))}
          </div>
        </>
      )}
      {state.loading && <p role="status" className="of-muted">Đang tải ảnh bổ sung…</p>}
      {state.error && (
        <div>
          <p role="status">Chưa tải được ảnh bổ sung.</p>
          <button type="button" className="of-button" onClick={state.reload}>Thử lại</button>
        </div>
      )}
    </TimelineDisclosure>
  );
}

function VerificationDetails({ verification, mine }) {
  return (
    <>
      <h3>Xác minh tại quầy</h3>
      <dl className="of-claim-timeline-fields">
        <TimelineField label="Kết quả xác minh">{verification.result === "PASSED" ? "Đạt" : "Không đạt"}</TimelineField>
        {!mine && <TimelineField label="Nhân viên xác minh">{verification.officer}</TimelineField>}
        <TimelineField label="Thời điểm xác minh">
          <time dateTime={verification.at}>{date(verification.at)}</time>
        </TimelineField>
      </dl>
      {!mine && verification.note && (
        <TimelineDisclosure label="Xem nội dung đối chiếu">
          <dl className="of-claim-timeline-fields">
            <TimelineField label="Nội dung đã đối chiếu" block>{verification.note}</TimelineField>
          </dl>
        </TimelineDisclosure>
      )}
    </>
  );
}

export default function OfficeClaimTimeline({ claim, workflow, mine = false }) {
  const activities = claim.activities ?? [];
  const hasSupplements = activities.some(({ action }) => action === "RESUBMIT");
  const supplements = useLoad(
    () => hasSupplements ? officeApi.supplements(claim.id) : [],
    `${claim.id}:${claim.version}:${hasSupplements}`,
  );
  const supplementsByActivity = new Map((supplements.data ?? []).map((entry) => [
    String(entry.activity_id), entry,
  ]));
  const claimHistory = activities.some(({ action }) => action === "SUBMIT")
    ? activities
    : [{ id: "created", action: "SUBMIT", createdAt: claim.createdAt }, ...activities];
  const requestsByResponse = new Map();
  let latestRequest;
  for (const activity of activities) {
    if (activity.action === "REQUEST_INFO") latestRequest = activity;
    if (activity.action === "RESUBMIT" && latestRequest) requestsByResponse.set(activity.id, latestRequest);
  }
  const verifications = workflow.data?.verifications ?? [];
  const hasFailedVerification = verifications.some(({ result }) => result === "FAILED");
  const history = [
    ...verifications.map((verification) => ({ key: `verification-${verification.id}`, at: verification.at, verification })),
    ...claimHistory.map((activity) => ({ key: `activity-${activity.id}`, at: activity.createdAt, activity })),
  ].sort((a, b) => new Date(a.at) - new Date(b.at));
  const stage = claim.stage || claim.status;
  const ended = terminalStages.includes(stage);
  const closed = ended && stage !== "COMPLETED";
  const currentLabel = mine && claim.stage === "EMAIL_UNSENT"
    ? "Chờ văn phòng gửi lịch hẹn"
    : progressLabels[claim.stage] || labels[claim.status] || "Chờ cập nhật";
  const StateIcon = ended ? (closed ? X : Check) : CircleDot;

  return (
    <section className="of-panel of-claim-history" aria-labelledby="office-claim-history">
      <h2 id="office-claim-history"><History size={20} aria-hidden="true" /> Lịch sử xử lý</h2>
      {workflow.error && (
        <p className="of-claim-timeline-warning">
          Chưa tải được các lần xác minh tại quầy.{" "}
          <button type="button" className="of-button" onClick={workflow.reload}>Thử lại</button>
        </p>
      )}
      <ol className="of-claim-timeline">
        {history.map(({ key, activity, verification }) => {
          if (verification) {
            return (
              <li className="of-claim-timeline-row" key={key}>
                <span className="of-claim-timeline-marker" aria-hidden="true" />
                <div className="of-claim-timeline-content">
                  <VerificationDetails verification={verification} mine={mine} />
                </div>
              </li>
            );
          }
          const { action, actorName, createdAt } = activity;
          const missedPickup = action === "CANCEL" && activity.reason === "Không đến xác minh trong thời hạn.";
          const [title, role, noteLabel = "Nội dung cập nhật"] = missedPickup
            ? ["Hết hạn đến quầy xác minh", null, "Lý do hủy"]
            : events[action] || ["Cập nhật yêu cầu", "Người thực hiện"];
          const created = action === "SUBMIT";
          const creator = actorName || claim.createdBy || (claim.channel === "ONLINE" ? claim.claimantName : null);
          const automatic = action === "AUTO_CLOSE" || action === "INVITE_EXPIRE" || missedPickup;
          // Email responses for requests without accounts can be recorded under the creator's account.
          const actor = action.startsWith("INVITE_") && claim.claimantId == null ? claim.claimantName : actorName;
          const note = claimActivityReason(activity, mine || hasFailedVerification);
          const actorLabel = created ? (claim.channel === "COUNTER" ? "Nhân viên lập hồ sơ tại quầy" : "Người gửi yêu cầu") : role;
          return (
            <li className="of-claim-timeline-row" key={key}>
              <span className={`of-claim-timeline-marker${created ? " of-claim-timeline-start" : ""}`} aria-hidden="true">
                {created && <ClipboardList size={15} />}
              </span>
              <div className="of-claim-timeline-content">
                <h3>{title}</h3>
                <dl className="of-claim-timeline-fields">
                  <TimelineField label={automatic ? "Cập nhật bởi" : actorLabel}>
                    {automatic ? "Hệ thống" : created ? creator : actor}
                  </TimelineField>
                  {createdAt && <TimelineField label="Thời điểm"><time dateTime={createdAt}>{date(createdAt)}</time></TimelineField>}
                  {action !== "RESUBMIT" && <TimelineField label={noteLabel} block>{note}</TimelineField>}
                </dl>
                {action === "RESUBMIT" && (
                  <SupplementDetails
                    activity={activity}
                    request={requestsByResponse.get(activity.id)}
                    entry={supplementsByActivity.get(String(activity.id))}
                    state={supplements}
                  />
                )}
              </div>
            </li>
          );
        })}
        <li className={`of-claim-timeline-row of-claim-timeline-last${closed ? " of-claim-timeline-closed" : ""}`} aria-current="step">
          <span className="of-claim-timeline-marker of-claim-timeline-end" aria-hidden="true"><StateIcon size={16} /></span>
          <div className="of-claim-timeline-content">
            <p>{ended ? "Kết quả cuối cùng" : "Trạng thái hiện tại"}</p>
            <h3>{currentLabel}</h3>
          </div>
        </li>
      </ol>
    </section>
  );
}
