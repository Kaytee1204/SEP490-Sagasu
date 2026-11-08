import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { CalendarDays, CheckCircle, Clock, XCircle } from "lucide-react";
import { invitationApi, intakeInvitationApi } from "../api/invitationApi";
import { date } from "../officeUtils";
import Notice from "../components/Notice";
import "../Office.css";

const statuses = {
  CONFIRMED: [
    "Đã đồng ý lịch hẹn",
    "Văn phòng đã ghi nhận. Vui lòng đến đúng lịch và mang theo giấy tờ, bằng chứng sở hữu.",
  ],
  DECLINED: [
    "Đã từ chối lịch hẹn",
    "Lịch hẹn đã hủy và yêu cầu nhận đồ này đã kết thúc.",
  ],
  EXPIRED: [
    "Đã hết hạn phản hồi",
    "Bạn chưa phản hồi trong thời hạn 12 giờ. Yêu cầu sẽ được hệ thống từ chối và lịch hẹn bị hủy.",
  ],
  CANCELLED: [
    "Lịch hẹn không còn hiệu lực",
    "Vui lòng liên hệ văn phòng nếu cần hỗ trợ.",
  ],
};
const intakeStatuses = {
  CONFIRMED: ["Đã đồng ý lịch mang đồ đến", "Cảm ơn bạn. Vui lòng mang đồ đến văn phòng theo lịch trên; nhân viên sẽ kiểm tra và xác nhận tiếp nhận tại quầy."],
  DECLINED: ["Đã từ chối lịch hẹn", "Yêu cầu tiếp nhận vẫn được giữ. Văn phòng sẽ liên hệ để hẹn lại lịch phù hợp."],
  EXPIRED: ["Lịch hẹn cần được sắp xếp lại", "Chưa nhận được phản hồi trong 12 giờ. Yêu cầu tiếp nhận vẫn được giữ để văn phòng hẹn lại."],
  CANCELLED: ["Lịch hẹn không còn hiệu lực", "Hãy kiểm tra yêu cầu tiếp nhận hoặc mở email lịch hẹn mới nhất."],
};
export default function InvitationPage({ intake = false }) {
  const { hash } = useLocation();
  return <InvitationContent key={`${intake}:${hash}`} hash={hash} intake={intake} />;
}
function InvitationContent({ hash, intake }) {
  const api = intake ? intakeInvitationApi : invitationApi;
  const params = new URLSearchParams(hash.slice(1));
  const token = params.get("token") || "";
  const proposed = params.get("decision") === "DECLINE" ? "DECLINE" : "CONFIRM";
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [decision, setDecision] = useState(proposed);
  const [attempt, setAttempt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    let active = true;
    Promise.resolve()
      .then(() => {
        if (!/^[A-Za-z0-9_-]{43}$/.test(token))
          throw new Error(
            "Liên kết không hợp lệ. Hãy mở nút trong email lịch hẹn mới nhất.",
          );
        return api.preview(token);
      })
      .then((value) => {
        if (active) setData(value);
      })
      .catch((failure) => {
        if (active) setError(failure.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token, proposed, attempt, api]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const expired =
    data?.status === "PENDING" &&
    data.deadline &&
    now >= new Date(data.deadline).getTime();
  const status = expired ? "EXPIRED" : data?.status;
  const result = (intake ? intakeStatuses : statuses)[status];
  async function submit() {
    if (busy || status !== "PENDING") return;
    setBusy(true);
    setError("");
    try {
      setData(await api.respond(token, decision));
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="office-page of-invitation-page">
      <section className="of-panel">
        <p className="of-eyebrow">Văn phòng · Sagasu</p>
        <CalendarDays size={32} aria-hidden="true" />
        <h1>{intake ? "Lịch mang đồ đến văn phòng" : "Xác nhận lịch hẹn nhận đồ"}</h1>
        <p className="of-muted">
          Không cần đăng nhập. Mở liên kết chưa được tính là phản hồi.
        </p>
        <Notice error>{error}</Notice>
        {loading && <p role="status">Đang kiểm tra lời mời…</p>}
        {!loading && !data && (
          <button
            className="of-button"
            onClick={() => {
              setLoading(true);
              setError("");
              setAttempt((v) => v + 1);
            }}
          >
            Thử lại
          </button>
        )}
        {data && (
          <>
            <dl className="of-facts">
              <div>
                <dt>Đồ vật</dt>
                <dd>{data.title}</dd>
              </div>
              <div>
                <dt>{intake ? "Thời gian mang đồ đến" : "Thời gian nhận đồ"}</dt>
                <dd>{date(data.scheduledAt)}</dd>
              </div>
              <div>
                <dt>Địa điểm</dt>
                <dd>{data.location}</dd>
              </div>
              <div>
                <dt>Hạn phản hồi</dt>
                <dd>{date(data.deadline)}</dd>
              </div>
            </dl>
            {result ? (
              <div className="of-callout" role="status">
                {status === "CONFIRMED" ? (
                  <CheckCircle />
                ) : status === "EXPIRED" ? (
                  <Clock />
                ) : (
                  <XCircle />
                )}
                <h2>{result[0]}</h2>
                <p>{result[1]}</p>
              </div>
            ) : status === "PENDING" ? (
              <form
                className="of-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  submit();
                }}
              >
                <fieldset className="of-invitation-options" disabled={busy}>
                  <legend>Lựa chọn của bạn</legend>
                  <label className="of-checkbox">
                    <input
                      type="radio"
                      name="response"
                      checked={decision === "CONFIRM"}
                      onChange={() => setDecision("CONFIRM")}
                    />
                    Đồng ý lịch hẹn
                  </label>
                  <label className="of-checkbox">
                    <input
                      type="radio"
                      name="response"
                      checked={decision === "DECLINE"}
                      onChange={() => setDecision("DECLINE")}
                    />
                    Từ chối lịch hẹn
                  </label>
                </fieldset>
                {decision === "DECLINE" && (
                  <Notice error>
                    {intake ? "Nếu lịch chưa phù hợp, văn phòng sẽ giữ yêu cầu tiếp nhận để hẹn lại với bạn." : "Từ chối sẽ hủy lịch và kết thúc yêu cầu nhận đồ này."}
                  </Notice>
                )}
                <button
                  type="submit"
                  className={`of-button ${decision === "DECLINE" ? "of-danger" : "of-primary"}`}
                  disabled={busy}
                >
                  {busy
                    ? "Đang ghi nhận…"
                    : decision === "CONFIRM"
                      ? "Xác nhận đồng ý"
                      : "Xác nhận từ chối"}
                </button>
              </form>
            ) : (
              <Notice>
                Lời mời chưa sẵn sàng. Vui lòng liên hệ văn phòng.
              </Notice>
            )}
          </>
        )}
        <p className="of-muted">
          Không chia sẻ liên kết này. Gửi lại email không gia hạn thời gian phản
          hồi.
        </p>
        <Link to="/" className="of-back">
          Về trang chủ Sagasu
        </Link>
      </section>
    </main>
  );
}
