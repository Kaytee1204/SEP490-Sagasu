import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CalendarDays, Mail } from "lucide-react";
import { officeApi } from "../api/officeApi";
import { workflowApi } from "../api/workflowApi";
import { date } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import ReviewForm from "../components/ReviewForm";
import SupplementForm from "../components/SupplementForm";
import OfficeClaimTimeline from "../components/OfficeClaimTimeline";
import Badge from "../components/Badge";
import Header from "../components/Header";
import Notice from "../components/Notice";
import LoadState from "../components/LoadState";
import ReceiptInfo from "../components/ReceiptInfo";
import OfficeReturnPanel from "../components/OfficeReturnPanel";
import "../Office.css";
import "../PersonalOffice.css";
import PersonalClaimDetail from '../components/PersonalClaimDetail';
// DaiHV integration: office complaints enter the shared Manager report workflow.
import ReportButton from "@/features/reports/components/ReportButton";

export default function OfficeClaimDetail({ mine = false }) {
  const { id } = useParams();
  const state = useLoad(() => mine ? officeApi.personalClaim(id) : officeApi.claim(id), `${mine}:${id}`);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const claim = state.data;
  const workflow = useLoad(
    () => claim ? workflowApi.detail(claim.id) : null,
    `${mine}:${claim?.id}:${claim?.version}`,
  );
  const appealDeadline = workflow.data?.appealDeadline;
  const refreshWorkflow = workflow.reload;
  useEffect(() => {
    if (!appealDeadline) return;
    const refresh = () => { if (document.visibilityState === "visible") refreshWorkflow(); };
    const remaining = Date.parse(appealDeadline) - Date.now();
    const timer = remaining > 0 ? window.setTimeout(refresh, remaining + 150) : null;
    const interval = window.setInterval(refresh, 30000);
    window.addEventListener("focus", refresh);
    return () => {
      window.clearTimeout(timer);
      window.clearInterval(interval);
      window.removeEventListener("focus", refresh);
    };
  }, [appealDeadline, refreshWorkflow]);
  if (mine) return <main className="office-page office-personal"><Notice error={notice?.error}>{notice?.text}</Notice><LoadState state={state}>{claim && <PersonalClaimDetail claim={claim} workflow={workflow} onChanged={state.reload}/>}</LoadState></main>;
  async function resend() {
    if (busy) return;
    setBusy(true);
    try {
      const r = await officeApi.resend(id);
      setNotice({
        error: r.emailStatus !== "SENT",
        text:
          r.emailStatus === "EXPIRED"
            ? "Đã hết hạn phản hồi. Yêu cầu bị từ chối và lịch hẹn đã hủy."
            : r.emailStatus === "SENT"
            ? "Đã gửi lại thư lịch hẹn."
            : "Chưa gửi được thư. Lịch hẹn vẫn được lưu; hãy thử lại sau.",
      });
      state.reload();
    } catch (err) {
      setNotice({ error: true, text: err.message });
      state.reload();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className={`office-page ${mine ? "office-personal op-detail" : ""}`}>
      <Link
        className="of-back"
        to={claim?.postId ? `/approver/office-claims/post/${claim.postId}` : "/approver/office-claims"}
      >
        ← Danh sách yêu cầu
      </Link>
      <Notice error={notice?.error}>{notice?.text}</Notice>
      <LoadState state={state}>
        {claim && (
          <>
            <Header
              eyebrow={mine ? "Nhận đồ tại văn phòng" : undefined}
              title={claim.postTitle || "Yêu cầu nhận đồ"}
              description={`Yêu cầu #${claim.id} · Gửi lúc ${date(claim.createdAt)}`}
            >
              <Badge value={claim.status} />
              <Badge value={claim.channel} />
              <ReportButton type="CLAIM" id={claim.id} label="Phản ánh về yêu cầu nhận đồ" />
            </Header>
            {!mine && claim.ownRequest && ["PENDING", "NEEDS_INFO"].includes(claim.status) && <Notice>Đây là yêu cầu nhận đồ của bạn. Nhờ một nhân viên khác xét duyệt nhé.</Notice>}
            <div className="of-detail-grid of-claim-grid">
              <div>
                <section className="of-panel">
                  <h2>{mine ? "Thông tin yêu cầu" : "Thông tin người khai"}</h2>
                  <dl className="of-facts">
                    <div>
                      <dt>Người yêu cầu nhận đồ</dt>
                      <dd>{claim.claimantName}</dd>
                    </div>
                    {claim.channel === "COUNTER" && <div>
                      <dt>Nhân viên tạo yêu cầu tại quầy</dt>
                      <dd>{claim.createdBy}</dd>
                    </div>}
                    <div>
                      <dt>Email liên hệ</dt>
                      <dd>{claim.claimantEmail || "Chưa cung cấp"}</dd>
                    </div>
                    <div>
                      <dt>Điện thoại</dt>
                      <dd>{claim.claimantPhone || "Chưa cung cấp"}</dd>
                    </div>
                  </dl>
                </section>
                <section className="of-panel">
                  <h2>Căn cứ sở hữu</h2>
                  <p className="of-prose">{claim.description}</p>
                  {!!claim.answers?.length && <dl className="of-question-answers">{claim.answers.map((answer) => <div key={answer.id}><dt>{answer.question}</dt><dd className="of-prose">{answer.answer}</dd></div>)}</dl>}
                  {claim.evidenceNote && (
                    <div className="of-callout of-prose">
                      {claim.evidenceNote}
                    </div>
                  )}
                  <div className="of-gallery">
                    {claim.evidence.map((e) => (
                      <a
                        key={e.id}
                        href={e.url}
                        target="_blank"
                        rel="noreferrer"
                      >
                        <img
                          src={e.url}
                          alt={e.originalName || "Ảnh bằng chứng"}
                        />
                      </a>
                    ))}
                  </div>
                  {!claim.evidence.length && (
                    <p className="of-muted">
                      Hồ sơ này cung cấp căn cứ bằng chữ, không có ảnh đính kèm.
                    </p>
                  )}
                  {!mine && claim.privateIdentifiers && (
                    <div className="of-callout">
                      <h3>Dấu hiệu riêng để đối chiếu · Chỉ nội bộ</h3>
                      <p className="of-prose">{claim.privateIdentifiers}</p>
                    </div>
                  )}
                </section>
                {!mine && <ReceiptInfo receipt={claim.receipt} />}
                <OfficeClaimTimeline claim={claim} workflow={workflow} />
              </div>
              <aside className="of-sticky">
                <OfficeReturnPanel claim={claim} state={workflow} onChanged={(result) => {
                  setNotice({ error: result?.emailStatus === "FAILED", text: result?.emailStatus === "FAILED" ? "Đã đổi lịch, nhưng chưa gửi được email. Hãy gửi lại thư lịch hẹn." : "Đã cập nhật tiến trình trao trả." });
                  state.reload();
                }} />
                {claim.appointment && !(mine && claim.status === "APPROVED") && (
                  <section className="of-panel of-appointment">
                    <CalendarDays size={26} color="#0f766e" />
                    <h2 style={{ marginTop: 12 }}>Lịch hẹn nhận đồ</h2>
                    <dl className="of-facts">
                      <div><dt>Thời gian hẹn</dt><dd>{date(claim.appointment.scheduledAt)}</dd></div>
                      <div><dt>Địa điểm nhận đồ</dt><dd className="of-prose">{claim.appointment.location}</dd></div>
                    </dl>
                    <Notice
                      error={["EXPIRED", "DECLINED"].includes(
                        claim.appointment.responseStatus,
                      )}
                    >
                      {claim.appointment.status === "COMPLETED" ? "Lịch hẹn đã hoàn tất." : claim.appointment.status === "CANCELLED" ? "Lịch hẹn đã hủy." :
                        {
                          UNSENT:
                            "Chưa gửi được lời mời; thời hạn 12 giờ chưa bắt đầu.",
                          LEGACY: "Lịch cũ chưa áp dụng phản hồi qua email.",
                          PENDING:
                            "Đang chờ người nhận phản hồi trên hệ thống hoặc qua email.",
                          CONFIRMED: "Người nhận đã đồng ý lịch hẹn.",
                          DECLINED: "Người nhận đã từ chối lịch hẹn.",
                          EXPIRED:
                            "Không phản hồi trong 12 giờ; yêu cầu bị từ chối và lịch hẹn đã hủy.",
                        }[claim.appointment.responseStatus]
                      }
                    </Notice>
                    {claim.appointment.status === "SCHEDULED" && claim.appointment.responseStatus === "PENDING" && claim.appointment.responseDeadline && (
                      <p>
                        Hạn phản hồi:{" "}
                        <strong>
                          {date(claim.appointment.responseDeadline)}
                        </strong>
                      </p>
                    )}
                    {claim.appointment.cancellationReason && (
                      <p className="of-prose">
                        <strong>Lý do lịch bị hủy: </strong>{claim.appointment.cancellationReason}
                      </p>
                    )}
                    {claim.appointment.note && (
                      <p className="of-prose"><strong>Lời nhắn về lịch hẹn: </strong>{claim.appointment.note}</p>
                    )}
                    {claim.appointment.status === "SCHEDULED" && <p className="of-muted">
                      Mang theo giấy tờ và bằng chứng để xác minh trực tiếp. Yêu
                      cầu được duyệt chưa có nghĩa đồ đã trao trả.
                    </p>}
                    {!mine &&
                      claim.appointment.status === "SCHEDULED" &&
                      claim.appointment.responseStatus !== "CONFIRMED" && (
                        <button
                          type="button"
                          className="of-button"
                          disabled={busy}
                          onClick={resend}
                        >
                          <Mail size={16} />
                          {busy ? "Đang gửi…" : "Gửi lại thư lịch hẹn"}
                        </button>
                      )}
                  </section>
                )}
                {!mine && !claim.ownRequest && !claim.canReview && ["PENDING", "NEEDS_INFO"].includes(claim.status) && (
                  <section className="of-panel">
                    <h2>Đã duyệt yêu cầu khác</h2>
                    <p className="of-muted">Hồ sơ này được giữ để xét tiếp nếu quyết định duyệt được thu hồi.</p>
                  </section>
                )}
                {!mine && claim.canReview && workflow.data && (
                  <ReviewForm
                    key={`${claim.id}:${claim.version}`}
                    claim={claim}
                    hasCompetitors={workflow.data.approvalHasCompetitors}
                    onDone={(r) => {
                      setNotice({
                        error: r.emailStatus === "FAILED",
                        text:
                          r.emailStatus === "FAILED"
                            ? "Đã duyệt và lưu lịch hẹn, nhưng gửi thư chưa thành công. Dùng nút gửi lại thư bên dưới."
                            : r.emailStatus === "SENT"
                              ? "Đã duyệt yêu cầu, lưu lịch hẹn và gửi thư cho người nhận."
                              : r.emailStatus === "APPEAL_WINDOW"
                                ? "Đã chấp thuận yêu cầu. Lịch hẹn được lập sau thời hạn gửi tranh chấp và sau khi các tranh chấp đã được xử lý."
                                : "Đã lưu kết quả xử lý hồ sơ.",
                      });
                      state.reload();
                    }}
                  />
                )}
                {mine && claim.ownRequest && claim.status === "NEEDS_INFO" && (
                  <SupplementForm
                    mine={mine}
                    claim={claim}
                    onDone={() => {
                      setNotice({
                        text: "Đã ghi nhận thông tin bổ sung. Hồ sơ trở lại hàng chờ xét duyệt.",
                      });
                      state.reload();
                    }}
                  />
                )}
                {!mine && !claim.appointment &&
                  !["PENDING", "NEEDS_INFO"].includes(claim.status) && (
                    <section className="of-panel">
                      <h2>Kết quả xử lý</h2>
                      <Badge value={claim.status} />
                      <p className="of-muted">Xem lý do trong lịch sử xử lý.</p>
                    </section>
                  )}
              </aside>
            </div>
          </>
        )}
      </LoadState>
    </div>
  );
}
