import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox } from "./Shared";
import { date, labels } from "../format";

const generalOutcomes = [
  [
    "VIOLATION",
    "Có vi phạm cần xử lý",
    "Chọn biện pháp phù hợp với vi phạm đã xác minh.",
  ],
  [
    "SETTLED",
    "Vấn đề đã được giải quyết",
    "Ghi rõ việc đã khắc phục hoặc kết quả các bên đã thống nhất.",
  ],
  [
    "UNSUBSTANTIATED",
    "Chưa đủ căn cứ xác định vi phạm",
    "Nêu rõ nội dung đã kiểm tra và vì sao không thể xác định vi phạm.",
  ],
];
const handoverOutcomes = [
  [
    "HANDOVER_COMPLETED",
    "Đã xác minh đồ được trả đúng người",
    "Hoàn tất bàn giao và đóng các yêu cầu nhận món đồ này đang chờ.",
  ],
  [
    "HANDOVER_CONTINUE",
    "Cho phép tiếp tục xác nhận nhận đồ",
    "Kết thúc tranh chấp này; người nhận tiếp tục bước xác nhận bàn giao.",
  ],
  [
    "CLAIM_REJECTED",
    "Không chấp nhận yêu cầu nhận đồ này",
    "Chỉ mở lại bài khi đã xác minh đồ vẫn ở người giữ.",
  ],
];

export default function ConclusionDialog({ data, onClose, onSaved }) {
  const [outcome, setOutcome] = useState("");
  const [hidePost, setHidePost] = useState(false),
    [suspend, setSuspend] = useState(false);
  const [targetId, setTarget] = useState(""),
    [previousDecisionId, setPrevious] = useState("");
  const [conclusion, setConclusion] = useState(""),
    [basis, setBasis] = useState("");
  const [custodyVerified, setCustody] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const choices = data.p2pDispute ? handoverOutcomes : generalOutcomes;
  const selected = choices.find(([value]) => value === outcome);
  const post = data.current.post;
  const canHide =
    post?.post_id && !post.deleted_at && post.moderation_status === "VISIBLE";
  const accounts = data.current.participants.filter(
    (p) =>
      p.id !== data.report.reporter_id &&
      ["USER", "APPROVER"].includes(p.role) &&
      p.status === "ACTIVE",
  );
  const previous = data.decisions.filter((d) =>
    ["HIDE_POST", "SUSPEND_ACCOUNT"].includes(d.action),
  );
  const hasMeasure = hidePost || (suspend && targetId) || previousDecisionId;
  const ready =
    outcome &&
    conclusion.trim() &&
    basis.trim() &&
    (outcome !== "VIOLATION" || hasMeasure) &&
    (!suspend || targetId) &&
    (outcome !== "CLAIM_REJECTED" || custodyVerified);
  function choose(value) {
    setOutcome(value);
    setHidePost(false);
    setSuspend(false);
    setTarget("");
    setPrevious("");
    setCustody(false);
    setError("");
  }
  async function submit(event) {
    event.preventDefault();
    if (busy || !ready) return;
    setBusy(true);
    setError("");
    try {
      const result = await reviewApi.act(data.report.report_id, {
        version: data.report.version,
        action: "CONCLUDE",
        outcome,
        conclusion: conclusion.trim(),
        basis: basis.trim(),
        hidePost: outcome === "VIOLATION" && hidePost,
        targetId: outcome === "VIOLATION" && suspend ? Number(targetId) : null,
        previousDecisionId:
          outcome === "VIOLATION" && previousDecisionId
            ? Number(previousDecisionId)
            : null,
        custodyVerified,
      });
      onSaved(result);
      onClose();
    } catch (problem) {
      setError(problem.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog title="Kết luận báo cáo" busy={busy} onClose={onClose}>
      <form className="rr-stack" onSubmit={submit}>
        <fieldset className="rr-conclusion-fields" disabled={busy}>
          <legend>Kết quả đối chiếu *</legend>
          <div className="rr-outcomes">
            {choices.map(([value, title, description]) => (
              <label
                className={`rr-outcome ${outcome === value ? "selected" : ""}`}
                key={value}
              >
                <input
                  type="radio"
                  name="report-outcome"
                  value={value}
                  checked={outcome === value}
                  onChange={() => choose(value)}
                  required
                />
                <span>
                  <strong>{title}</strong>
                  <small>{description}</small>
                </span>
              </label>
            ))}
          </div>
          {outcome === "VIOLATION" && (
            <section className="rr-measures">
              <h3>Biện pháp xử lý *</h3>
              {canHide && (
                <label className="rr-check">
                  <input
                    type="checkbox"
                    checked={hidePost}
                    onChange={(e) => setHidePost(e.target.checked)}
                  />
                  Ẩn bài đăng “{post.title}”
                </label>
              )}
              {post?.moderation_status === "HIDDEN" && (
                <p className="rr-muted">Bài đăng hiện đã bị ẩn.</p>
              )}
              {accounts.length > 0 && (
                <>
                  <label className="rr-check">
                    <input
                      type="checkbox"
                      checked={suspend}
                      onChange={(e) => {
                        setSuspend(e.target.checked);
                        setTarget("");
                      }}
                    />
                    Đình chỉ tài khoản vi phạm
                  </label>
                  {suspend && (
                    <label>
                      Tài khoản cần đình chỉ *
                      <select
                        required
                        value={targetId}
                        onChange={(e) => setTarget(e.target.value)}
                      >
                        <option value="">Chọn tài khoản</option>
                        {accounts.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.full_name} · {p.email}
                          </option>
                        ))}
                      </select>
                      <small>Tài khoản được chọn sẽ bị chặn truy cập.</small>
                    </label>
                  )}
                </>
              )}
              {previous.length > 0 && (
                <label>
                  Biện pháp đã thực hiện cho báo cáo này
                  <select
                    value={previousDecisionId}
                    onChange={(e) => setPrevious(e.target.value)}
                  >
                    <option value="">
                      Chọn nếu sử dụng kết quả xử lý trước đó
                    </option>
                    {previous.map((d) => (
                      <option key={d.decision_id} value={d.decision_id}>
                        {labels[d.action]} · {date(d.created_at)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {!canHide && !accounts.length && !previous.length && (
                <p className="rr-callout">
                  Không có biện pháp khả dụng để chọn. Nếu vấn đề đã được khắc
                  phục, chọn “Vấn đề đã được giải quyết” và nêu kết quả thực tế.
                </p>
              )}
            </section>
          )}
          {selected && (
            <>
              <label>
                {outcome === "SETTLED"
                  ? "Kết quả đã giải quyết"
                  : outcome === "UNSUBSTANTIATED"
                    ? "Lý do chưa thể xác định vi phạm"
                    : "Kết luận gửi cho các bên"}{" "}
                *
                <textarea
                  required
                  rows={3}
                  maxLength={5000}
                  value={conclusion}
                  onChange={(e) => setConclusion(e.target.value)}
                  placeholder={
                    outcome === "SETTLED"
                      ? "Ví dụ: Hai bên đã đối chiếu lại, người nhận xác nhận không còn vướng mắc."
                      : "Nêu rõ kết quả kiểm tra để người liên quan hiểu quyết định."
                  }
                />
              </label>
              <label>
                Căn cứ cho kết luận *
                <textarea
                  required
                  rows={3}
                  maxLength={5000}
                  value={basis}
                  onChange={(e) => setBasis(e.target.value)}
                  placeholder="Nêu ảnh, biên bản hoặc phản hồi đã dùng để đối chiếu."
                />
                <small>Chỉ quản lý xem được phần căn cứ này.</small>
              </label>
              {outcome === "CLAIM_REJECTED" && (
                <label className="rr-check">
                  <input
                    type="checkbox"
                    required
                    checked={custodyVerified}
                    onChange={(e) => setCustody(e.target.checked)}
                  />
                  Đã xác minh đồ vẫn ở người giữ và có thể trao cho người khác.
                </label>
              )}
              {!!Object.keys(data.current.officeHandover || {}).length && (
                <p className="rr-muted">
                  Kết luận phản ánh không thay đổi biên bản trao trả văn phòng
                  đã ghi nhận.
                </p>
              )}
              {data.inquiries.some((i) => !i.responded_at) && (
                <p className="rr-callout">
                  Còn người chưa phản hồi yêu cầu bổ sung. Chỉ kết luận khi đã
                  đủ căn cứ; ghi rõ lý do có thể kết luận ở trên.
                </p>
              )}
              <div className="rr-callout" aria-live="polite">
                <strong>Sau khi xác nhận</strong>
                <ul>
                  {outcome === "VIOLATION" && hidePost && (
                    <li>Bài đăng bị ẩn khỏi tìm kiếm công khai.</li>
                  )}
                  {outcome === "VIOLATION" && suspend && targetId && (
                    <li>
                      Đình chỉ tài khoản{" "}
                      {
                        accounts.find((p) => String(p.id) === targetId)
                          ?.full_name
                      }
                      .
                    </li>
                  )}
                  {outcome === "VIOLATION" && previousDecisionId && (
                    <li>
                      Ghi nhận biện pháp đã thực hiện, không thực hiện lại.
                    </li>
                  )}
                  {data.p2pDispute && <li>{selected[2]}</li>}
                  {data.hold.reason && (
                    <li>
                      Gỡ tạm dừng của báo cáo này; lệnh từ báo cáo khác vẫn giữ
                      hiệu lực.
                    </li>
                  )}
                  <li>
                    Lưu kết luận, kết thúc báo cáo và thông báo cho các bên.
                  </li>
                </ul>
              </div>
            </>
          )}
        </fieldset>
        <ErrorBox>{error}</ErrorBox>
        <footer className="rr-actions">
          <button type="button" onClick={onClose} disabled={busy}>
            Quay lại
          </button>
          <button className="rr-primary" disabled={busy || !ready}>
            {busy ? "Đang xử lý…" : "Xác nhận kết luận"}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
