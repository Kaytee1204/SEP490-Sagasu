import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CalendarDays, Mail } from "lucide-react";
import { officeApi } from "../api/officeApi";
import { date } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import ReviewForm from "../components/ReviewForm";
import SupplementForm from "../components/SupplementForm";
import Badge from "../components/Badge";
import Header from "../components/Header";
import Notice from "../components/Notice";
import LoadState from "../components/LoadState";
import ReceiptInfo from "../components/ReceiptInfo";
import OfficeReturnPanel from "../components/OfficeReturnPanel";
import "../Office.css";

export default function OfficeClaimDetail({ mine = false }) {
  const { id } = useParams();
  const state = useLoad(() => officeApi.claim(id), id);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const claim = state.data;
  async function resend() {
    if (busy) return;
    setBusy(true);
    try {
      const r = await officeApi.resend(id);
      setNotice({
        error: r.emailStatus !== "SENT",
        text:
          r.emailStatus === "SENT"
            ? "Đã gửi lại thư lịch hẹn."
            : "Chưa gửi được thư. Lịch hẹn vẫn được lưu; hãy thử lại sau.",
      });
      state.reload();
    } catch (err) {
      setNotice({ error: true, text: err.message });
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="office-page">
      <Link
        className="of-back"
        to={mine ? "/van-phong/claims" : "/approver/office-claims"}
      >
        ← Danh sách yêu cầu
      </Link>
      <Notice error={notice?.error}>{notice?.text}</Notice>
      <LoadState state={state}>
        {claim && (
          <>
            <Header
              title={`Hồ sơ #${claim.id}`}
              description={`${claim.postTitle} · Gửi lúc ${date(claim.createdAt)}`}
            >
              <Badge value={claim.status} />
              <Badge value={claim.channel} />
            </Header>
            <div className="of-detail-grid of-claim-grid">
              <div>
                <section className="of-panel">
                  <h2>Thông tin người khai</h2>
                  <dl className="of-facts">
                    <div>
                      <dt>Người nhận đồ</dt>
                      <dd>{claim.claimantName}</dd>
                    </div>
                    <div>
                      <dt>Người lập hồ sơ</dt>
                      <dd>{claim.createdBy}</dd>
                    </div>
                    <div>
                      <dt>Địa chỉ thư điện tử</dt>
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
                <section className="of-panel">
                  <h2>Lịch sử xử lý</h2>
                  <ol className="of-timeline">
                    {claim.activities.map((a) => (
                      <li key={a.id}>
                        <strong>
                          {{
                            SUBMIT: "Tạo hồ sơ",
                            APPROVE: "Duyệt yêu cầu",
                            REJECT: "Từ chối",
                            REQUEST_INFO: "Yêu cầu bổ sung",
                            RESUBMIT: "Bổ sung thông tin",
                            CANCEL: "Hủy hồ sơ",
                            TRANSFER_OFFICE: "Chuyển trách nhiệm sang văn phòng",
                            RESCHEDULE: "Đổi lịch hẹn",
                            OFFICE_CANCEL: "Hủy lịch và thu hồi duyệt",
                            HANDOVER: "Ghi nhận giao đồ",
                            CONFIRM_RECEIPT: "Người nhận xác nhận đã nhận đồ",
                            AUTO_CLOSE: "Đóng hồ sơ do đồ đã được trao trả",
                            REPORT_ISSUE: "Phản ánh việc nhận đồ",
                            INVITE_CONFIRM: "Đồng ý lịch hẹn qua email",
                            INVITE_DECLINE: "Từ chối lịch hẹn qua email",
                            INVITE_EXPIRE:
                              "Hết hạn phản hồi — hệ thống tự động",
                          }[a.action] || a.action}
                        </strong>
                        <p className="of-muted">
                          {a.action === "INVITE_EXPIRE"
                            ? "Hệ thống"
                            : a.actorName}{" "}
                          · {date(a.createdAt)}
                        </p>
                        {a.reason && <p className="of-prose">{a.reason}</p>}
                      </li>
                    ))}
                  </ol>
                </section>
              </div>
              <aside className="of-sticky">
                <OfficeReturnPanel claim={claim} mine={mine} onChanged={(result) => {
                  setNotice({ error: result?.emailStatus === "FAILED", text: result?.emailStatus === "FAILED" ? "Đã đổi lịch, nhưng chưa gửi được email. Hãy gửi lại thư lịch hẹn." : "Đã cập nhật tiến trình trao trả." });
                  state.reload();
                }} />
                {claim.appointment && (
                  <section className="of-panel of-appointment">
                    <CalendarDays size={26} color="#0f766e" />
                    <h2 style={{ marginTop: 12 }}>Lịch hẹn nhận đồ</h2>
                    <strong>{date(claim.appointment.scheduledAt)}</strong>
                    <p className="of-prose">{claim.appointment.location}</p>
                    <Notice
                      error={["EXPIRED", "DECLINED"].includes(
                        claim.appointment.responseStatus,
                      )}
                    >
                      {
                        {
                          UNSENT:
                            "Chưa gửi được lời mời; thời hạn 12 giờ chưa bắt đầu.",
                          LEGACY: "Lịch cũ chưa áp dụng phản hồi qua email.",
                          PENDING:
                            "Đang chờ phản hồi qua liên kết trong email.",
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
                        {claim.appointment.cancellationReason}
                      </p>
                    )}
                    {claim.appointment.note && (
                      <p className="of-prose">{claim.appointment.note}</p>
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
                {!mine && ["PENDING", "NEEDS_INFO"].includes(claim.status) && (
                  <ReviewForm
                    key={`${claim.id}:${claim.version}`}
                    claim={claim}
                    onDone={(r) => {
                      setNotice({
                        error: r.emailStatus === "FAILED",
                        text:
                          r.emailStatus === "FAILED"
                            ? "Đã duyệt và lưu lịch hẹn, nhưng gửi thư chưa thành công. Dùng nút gửi lại thư bên dưới."
                            : r.emailStatus === "SENT"
                              ? "Đã duyệt yêu cầu, lưu lịch hẹn và gửi thư cho người nhận."
                              : "Đã lưu kết quả xử lý hồ sơ.",
                      });
                      state.reload();
                    }}
                  />
                )}
                {claim.status === "NEEDS_INFO" && (
                  <SupplementForm
                    claim={claim}
                    onDone={() => {
                      setNotice({
                        text: "Đã ghi nhận thông tin bổ sung. Hồ sơ trở lại hàng chờ xét duyệt.",
                      });
                      state.reload();
                    }}
                  />
                )}
                {!claim.appointment &&
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
