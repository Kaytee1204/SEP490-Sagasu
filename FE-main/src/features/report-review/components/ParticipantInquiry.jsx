import { useState } from "react";
import { reviewApi } from "../api";
import { Badge, ErrorBox, Gallery } from "./Shared";
import { date } from "../format";
export default function ParticipantInquiry({ inquiry: i, onSaved }) {
  const [response, setResponse] = useState(""),
    [files, setFiles] = useState([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await reviewApi.reply(i.inquiry_id, response.trim(), files);
      onSaved();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  function pick(e) {
    const values = [...e.target.files];
    e.target.value = "";
    if (
      values.length > 5 ||
      values.some(
        (f) =>
          !["image/jpeg", "image/png"].includes(f.type) ||
          !f.size ||
          f.size > 5242880,
      )
    ) {
      setError("Chọn tối đa 5 ảnh JPG/PNG, không quá 5 MB mỗi ảnh.");
      return;
    }
    setError("");
    setFiles(values);
  }
  return (
    <section className="rr-card">
      <div className="rr-row">
        <h2>Báo cáo #{i.report_id}</h2>
        <Badge value={i.status} />
      </div>
      <small>Yêu cầu bổ sung lúc {date(i.created_at)}</small>
      <p className="rr-callout">{i.question}</p>
      {i.responded_at ? (
        <>
          <h3>Thông tin bạn đã gửi</h3>
          <p>{i.response}</p>
          <small>{date(i.responded_at)}</small>
          <Gallery images={i.evidence} />
        </>
      ) : ["OPEN", "IN_REVIEW"].includes(i.status) ? (
        <form className="rr-stack" onSubmit={submit}>
          <label>
            Phản hồi của bạn *
            <textarea
              required
              maxLength={5000}
              rows={4}
              value={response}
              disabled={busy}
              onChange={(e) => setResponse(e.target.value)}
            />
          </label>
          <label>
            Ảnh đối chiếu (tối đa 5 ảnh JPG/PNG, 5 MB mỗi ảnh)
            <input
              type="file"
              accept="image/jpeg,image/png"
              multiple
              disabled={busy}
              onChange={pick}
            />
          </label>
          {files.map((f, index) => (
            <div className="rr-row" key={index}>
              <span>{f.name}</span>
              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  setFiles((old) => old.filter((_, n) => n !== index))
                }
              >
                Bỏ ảnh
              </button>
            </div>
          ))}
          <p className="rr-muted">
            Phản hồi chỉ được gửi một lần và được lưu nguyên vẹn để đối chiếu.
          </p>
          <ErrorBox>{error}</ErrorBox>
          <button className="rr-primary" disabled={busy || !response.trim()}>
            {busy ? "Đang gửi…" : "Gửi thông tin bổ sung"}
          </button>
        </form>
      ) : (
        <p>Vụ việc đã kết thúc, không cần gửi thêm thông tin.</p>
      )}
      {i.resolution_note && (
        <p className="rr-callout">Kết luận: {i.resolution_note}</p>
      )}
    </section>
  );
}
