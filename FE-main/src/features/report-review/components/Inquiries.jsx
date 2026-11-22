import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox, Gallery, Empty } from "./Shared";
import { availableRecipients, date } from "../format";
export default function Inquiries({ data, onSaved, open, setOpen, draftData }) {
  const recipients = availableRecipients(draftData || data);
  const [recipientId, setRecipient] = useState(""),
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
          version: draftData.report.version,
          recipientId: Number(recipientId),
          question: question.trim(),
        }),
      );
      setOpen(false);
      setQuestion("");
      setRecipient("");
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="rr-card">
      <div className="rr-row">
        <h3>Trao đổi với người liên quan</h3>
        {recipients.length > 0 && (data.canAsk ?? data.canProcess) && (
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
      {[...data.inquiries].reverse().map((i) => (
        <article className="rr-record" key={i.inquiry_id}>
          <div className="rr-row">
            <strong>{i.recipient}</strong>
            <span className="rr-badge">
              {i.responded_at ? "Đã phản hồi" : i.closed_at ? "Đã kết thúc" : i.overdue ? "Quá hạn phản hồi" : "Chờ phản hồi"}
            </span>
          </div>
          <small>{date(i.created_at)}</small>
          {i.response_due_at && !i.responded_at && !i.closed_at && <small> · Hạn phản hồi {date(i.response_due_at)}</small>}
          {i.reminded_at && <small> · Đã nhắc lúc {date(i.reminded_at)}</small>}
          <p><strong>Quản lý hỏi</strong><br />{i.question}</p>
          {i.responded_at && (
            <blockquote>
              <p><strong>{i.recipient} phản hồi</strong><br />{i.response}</p>
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
            <p className="rr-muted">Người nhận có 12 giờ làm việc để trả lời. Hết hạn, hồ sơ về mục Cần xử lý để bạn kết luận dựa trên căn cứ hiện có.</p>
            <label>
              Người nhận *
              <select
                autoFocus
                required
                value={recipientId}
                onChange={(e) => setRecipient(e.target.value)}
              >
                <option value="">Chọn người liên quan</option>
                {recipients.map((p) => (
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
            {data.report.reason_code === "HANDOVER_MISSING_ITEMS" && !question && (
              <div className="rr-actions" aria-label="Câu hỏi gợi ý">
                <button type="button" onClick={() => setQuestion("Khi nhận đồ, bạn đã nhận những gì và còn thiếu phụ kiện nào? Nếu có, hãy gửi ảnh để đối chiếu.")}>
                  Hỏi người nhận còn thiếu gì
                </button>
                <button type="button" onClick={() => setQuestion("Khi nhặt được món đồ, bạn thấy những phụ kiện nào đi kèm? Bạn đã giao những gì và hiện còn giữ phụ kiện nào?")}>
                  Hỏi người nhặt đang giữ gì
                </button>
                <button type="button" onClick={() => setQuestion("Bạn đã nhận phần phụ kiện được giao bổ sung chưa? Hãy cho biết đã nhận những gì và còn thiếu gì, nếu có.")}>
                  Kiểm tra sau khi giao bổ sung
                </button>
              </div>
            )}
            <ErrorBox>{error}</ErrorBox>
            <button className="rr-primary" disabled={busy || !recipientId || !question.trim()}>
              {busy ? "Đang gửi…" : "Gửi yêu cầu"}
            </button>
          </form>
        </Dialog>
      )}
    </section>
  );
}
