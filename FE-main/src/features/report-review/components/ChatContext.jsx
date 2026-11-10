import { useState } from "react";
import { reviewApi } from "../api";
import { ErrorBox, Gallery, Empty } from "./Shared";
import { date } from "../format";
export default function ChatContext({ id, reportedMessage }) {
  const [data, setData] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function load() {
    setBusy(true);
    setError("");
    try {
      const result = await reviewApi.chat(id, data?.before);
      setData((old) => ({
        ...result,
        content: [...result.content, ...(old?.content || [])],
      }));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="rr-card">
      <h3>Hội thoại liên quan</h3>
      <p className="rr-muted">
        Chỉ đọc cuộc trao đổi gắn với yêu cầu hoặc tin nhắn bị báo cáo. Lượt
        truy cập được ghi vào lịch sử xử lý.
      </p>
      <ErrorBox>{error}</ErrorBox>
      {(!data || data.hasMore) && (
        <button disabled={busy} onClick={load}>
          {busy
            ? "Đang tải…"
            : data
              ? "Tải tin nhắn trước đó"
              : "Mở hội thoại để đối chiếu"}
        </button>
      )}
      {data && !data.content.length && (
        <Empty>Không có hội thoại liên quan.</Empty>
      )}
      <ol className="rr-chat">
        {data?.content.map((m) => (
          <li
            key={m.message_id}
            className={m.message_id === reportedMessage ? "rr-reported" : ""}
          >
            <strong>
              {m.sender}
              {m.message_id === reportedMessage ? " · Tin nhắn bị báo cáo" : ""}
            </strong>
            <small>{date(m.created_at)}</small>
            <p>{m.body || m.location_label || "Tệp đính kèm"}</p>
            <Gallery images={m.attachments} />
          </li>
        ))}
      </ol>
    </section>
  );
}
