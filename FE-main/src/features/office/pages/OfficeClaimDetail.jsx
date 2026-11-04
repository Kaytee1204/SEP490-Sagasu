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
        to={mine ? "/van-phong/claims" : "/admin/office-claims"}
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
            <div className="of-detail-grid">
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
                          }[a.action] || a.action}
                        </strong>
                        <p className="of-muted">
                          {a.actorName} · {date(a.createdAt)}
                        </p>
                        {a.reason && <p className="of-prose">{a.reason}</p>}
                      </li>
                    ))}
                  </ol>
                </section>
              </div>
              <aside className="of-sticky">
                {claim.appointment && (
                  <section className="of-panel of-appointment">
                    <CalendarDays size={26} color="#0f766e" />
                    <h2 style={{ marginTop: 12 }}>Lịch hẹn nhận đồ</h2>
                    <strong>{date(claim.appointment.scheduledAt)}</strong>
                    <p className="of-prose">{claim.appointment.location}</p>
                    {claim.appointment.note && (
                      <p className="of-prose">{claim.appointment.note}</p>
                    )}
                    <p className="of-muted">
                      Mang theo giấy tờ và bằng chứng để xác minh trực tiếp. Yêu
                      cầu được duyệt chưa có nghĩa đồ đã trao trả.
                    </p>
                    {!mine && (
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
