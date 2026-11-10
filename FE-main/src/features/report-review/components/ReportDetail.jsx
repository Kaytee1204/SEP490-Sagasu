import { useState } from "react";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { reviewApi } from "../api";
import { Badge, ErrorBox, Empty, Gallery } from "./Shared";
import { date, labels } from "../format";
import ContextPanel from "./ContextPanel";
import DecisionDialog from "./DecisionDialog";
import Inquiries from "./Inquiries";
import ChatContext from "./ChatContext";
import useReviewResource from "../hooks/useReviewResource";
export default function ReportDetail({ id, onBack, onRelated }) {
  const [tab, setTab] = useState("overview"),
    [action, setAction] = useState(null),
    [busy, setBusy] = useState(false),
    [refresh, setRefresh] = useState(0);
  const { data, error, setData, setError } = useReviewResource(
    "detail",
    id,
    refresh,
  );
  async function take() {
    setBusy(true);
    setError("");
    try {
      setData(
        await reviewApi.act(id, {
          version: data.report.version,
          action: "TAKE",
        }),
      );
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const r = data?.report;
  return (
    <>
      <div className="rr-row">
        <button onClick={onBack}>
          <ArrowLeft size={16} /> Danh sách báo cáo
        </button>
        <button disabled={busy} onClick={() => setRefresh((x) => x + 1)}>
          <RefreshCw size={16} /> Tải lại hồ sơ
        </button>
      </div>
      <ErrorBox>{error}</ErrorBox>
      {!data && !error && <Empty>Đang lấy thông tin đối chiếu…</Empty>}
      {data && (
        <>
          <section className="rr-card rr-summary">
            <div className="rr-row">
              <h2>Báo cáo #{r.report_id}</h2>
              <Badge value={r.status} />
            </div>
            <p className="rr-muted">
              {labels[r.kind]} · Gửi lúc {date(r.created_at)}
            </p>
            <p>{r.description}</p>
            <Gallery images={data.evidence} />
            {r.resolution_note && (
              <div className="rr-callout">
                <strong>Kết luận xử lý</strong>
                <p>{r.resolution_note}</p>
              </div>
            )}
            {data.hold.reason && (
              <p className="rr-callout">
                Đang tạm dừng giao nhận: {data.hold.reason}
              </p>
            )}
          </section>
          <div className="rr-layout">
            <div>
              <nav className="rr-tabs" aria-label="Thông tin báo cáo">
                {[
                  ["overview", "Tổng quan"],
                  ["current", "Dữ liệu hiện tại"],
                  ["snapshot", "Bản lưu"],
                  ["inquiries", `Trao đổi (${data.inquiries.length})`],
                  ["history", "Lịch sử xử lý"],
                ].map(([key, label]) => (
                  <button
                    key={key}
                    aria-current={tab === key ? "page" : undefined}
                    className={tab === key ? "active" : ""}
                    onClick={() => setTab(key)}
                  >
                    {label}
                  </button>
                ))}
              </nav>
              {tab === "overview" && (
                <div className="rr-stack">
                  <section className="rr-card">
                    <h3>Người liên quan</h3>
                    <div className="rr-people">
                      {data.current.participants.map((p) => (
                        <div key={p.id}>
                          <strong>{p.full_name}</strong>
                          <small>{p.email}</small>
                          <span>
                            {p.id === r.reporter_id
                              ? "Người gửi báo cáo · "
                              : ""}
                            {labels[p.role]}
                          </span>
                          <Badge value={p.status} />
                        </div>
                      ))}
                    </div>
                  </section>
                  {data.related.length > 0 && (
                    <section className="rr-card">
                      <h3>Báo cáo cùng đối tượng</h3>
                      {data.related.map((x) => (
                        <button
                          className="rr-related"
                          key={x.report_id}
                          onClick={() => onRelated(x.report_id)}
                        >
                          #{x.report_id} · {date(x.created_at)}{" "}
                          <Badge value={x.status} />
                        </button>
                      ))}
                    </section>
                  )}
                  <ChatContext
                    key={id}
                    id={id}
                    reportedMessage={r.message_id}
                  />
                </div>
              )}
              {tab === "current" && <ContextPanel data={data.current} />}
              {tab === "snapshot" && (
                <div className="rr-stack">
                  <p className="rr-callout">
                    {data.snapshot.capture_source === "SUBMISSION"
                      ? "Nội dung lưu khi gửi báo cáo"
                      : "Báo cáo cũ: nội dung được lưu từ lần đầu quản lý mở hồ sơ, không phải thời điểm gửi"}
                    . Thời điểm: {date(data.snapshot.captured_at)}.
                  </p>
                  <ContextPanel snapshot data={data.snapshot.payload} />
                </div>
              )}
              {tab === "inquiries" && (
                <Inquiries data={data} onSaved={setData} />
              )}
              {tab === "history" && (
                <section className="rr-card">
                  <h3>Lịch sử xử lý</h3>
                  <p className="rr-muted">
                    Ghi chú và căn cứ nội bộ chỉ quản lý được xem.
                  </p>
                  <ol className="rr-timeline">
                    {data.history.map((x) => (
                      <li key={x.activity_id}>
                        <strong>{labels[x.action] || x.action}</strong>
                        <small>
                          {x.actor} · {date(x.created_at)}
                        </small>
                        {x.public_note && <p>{x.public_note}</p>}
                        {x.internal_note && (
                          <div className="rr-private">
                            <strong>Nội bộ</strong>
                            <p>{x.internal_note}</p>
                          </div>
                        )}
                      </li>
                    ))}
                  </ol>
                </section>
              )}
            </div>
            <aside className="rr-card rr-decision">
              <h3>Xử lý báo cáo</h3>
              {data.conflict ? (
                <p className="rr-callout">
                  Bạn có liên quan đến vụ việc. Cần một quản lý khác đưa ra
                  quyết định.
                </p>
              ) : data.canTake ? (
                <>
                  <p>Nhận xử lý để yêu cầu bổ sung và ghi kết luận.</p>
                  <button disabled={busy} className="rr-primary" onClick={take}>
                    {busy ? "Đang nhận…" : "Nhận xử lý"}
                  </button>
                </>
              ) : data.canProcess ? (
                <>
                  <p className="rr-muted">
                    Đối chiếu bằng chứng trước khi quyết định. Mỗi thao tác đều
                    cần ghi căn cứ.
                  </p>
                  <button onClick={() => setTab("inquiries")}>
                    Yêu cầu thông tin từ các bên
                  </button>
                  <button onClick={() => setAction("NOTE")}>
                    Ghi chú nội bộ
                  </button>
                  {data.current.post && (
                    <>
                      <button
                        onClick={() =>
                          setAction(data.hold.reason ? "RELEASE" : "HOLD")
                        }
                      >
                        {data.hold.reason
                          ? "Gỡ tạm dừng giao nhận"
                          : "Tạm dừng giao nhận"}
                      </button>
                      <button onClick={() => setAction("HIDE_POST")}>
                        Ẩn bài đăng liên quan
                      </button>
                    </>
                  )}
                  <button onClick={() => setAction("SUSPEND_ACCOUNT")}>
                    Đình chỉ tài khoản liên quan
                  </button>
                  <hr />
                  <strong>Kết luận vụ việc</strong>
                  {(data.p2pDispute
                    ? [
                        "HANDOVER_COMPLETED",
                        "HANDOVER_CONTINUE",
                        "CLAIM_REJECTED",
                      ]
                    : ["RESOLVE", "DISMISS"]
                  ).map((value) => (
                    <button
                      key={value}
                      className={value === "RESOLVE" ? "rr-primary" : ""}
                      onClick={() => setAction(value)}
                    >
                      {labels[value]}
                    </button>
                  ))}
                </>
              ) : (
                <p>
                  {["RESOLVED", "REJECTED"].includes(r.status)
                    ? "Báo cáo đã kết thúc. Bạn có thể xem toàn bộ căn cứ và lịch sử xử lý."
                    : "Báo cáo đang được quản lý khác xử lý."}
                </p>
              )}
            </aside>
          </div>
          {action && (
            <DecisionDialog
              key={action}
              action={action}
              data={data}
              onClose={() => setAction(null)}
              onSaved={setData}
            />
          )}
        </>
      )}
    </>
  );
}
