import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox, Gallery, Empty } from "./Shared";
import { date } from "../format";
export default function Inquiries({ data, onSaved }) {
  const [open, setOpen] = useState(false),
    [recipientId, setRecipient] = useState(""),
    [question, setQuestion] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function ask(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      onSaved(
        await reviewApi.ask(data.report.report_id, {
          version: data.report.version,
          recipientId: Number(recipientId),
          question: question.trim(),
        }),
      );
      setOpen(false);
      setQuestion("");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="rr-card">
      <div className="rr-row">
        <h3>Thông tin từ các bên</h3>
        {data.canProcess && (
          <button
            onClick={() => {
              setError("");
              setOpen(true);
            }}
          >
            Yêu cầu bổ sung
          </button>
        )}
      </div>
      <p className="rr-muted">
        Mỗi người chỉ thấy câu hỏi dành cho mình. Phản hồi đã gửi được giữ
        nguyên để đối chiếu.
      </p>
      {!data.inquiries.length && <Empty>Chưa yêu cầu bổ sung thông tin.</Empty>}
      {data.inquiries.map((i) => (
        <article className="rr-record" key={i.inquiry_id}>
          <div className="rr-row">
            <strong>{i.recipient}</strong>
            <span className="rr-badge">
              {i.responded_at ? "Đã phản hồi" : "Chờ phản hồi"}
            </span>
          </div>
          <small>{date(i.created_at)}</small>
          <p>{i.question}</p>
          {i.responded_at && (
            <blockquote>
              <p>{i.response}</p>
              <small>Phản hồi lúc {date(i.responded_at)}</small>
              <Gallery images={i.evidence} />
            </blockquote>
          )}
        </article>
      ))}
      {open && (
        <Dialog
          title="Yêu cầu bổ sung thông tin"
          busy={busy}
          onClose={() => setOpen(false)}
        >
          <form className="rr-stack" onSubmit={ask}>
            <label>
              Người nhận *
              <select
                autoFocus
                required
                value={recipientId}
                onChange={(e) => setRecipient(e.target.value)}
              >
                <option value="">Chọn người liên quan</option>
                {data.current.participants.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.full_name} · {p.email}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Nội dung cần làm rõ *
              <textarea
                required
                maxLength={5000}
                rows={5}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
              />
            </label>
            <ErrorBox>{error}</ErrorBox>
            <button className="rr-primary" disabled={busy || !question.trim()}>
              {busy ? "Đang gửi…" : "Gửi yêu cầu"}
            </button>
          </form>
        </Dialog>
      )}
    </section>
  );
}
