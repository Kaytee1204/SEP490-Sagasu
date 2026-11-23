import { useState } from "react";
import { reviewApi } from "../api";
import { Dialog, ErrorBox, Gallery, Empty } from "./Shared";
import { availableRecipients, date, participantRoles, usesVerification, verificationOptions, verificationLabels } from "../format";
function exchangesByRecipient(inquiries) {
  const latest = i => Date.parse(i.responded_at || i.created_at);
  const groups = new Map();
  for (const inquiry of [...inquiries].sort((a, b) => latest(b) - latest(a) || b.inquiry_id - a.inquiry_id)) {
    if (!groups.has(inquiry.recipient_id)) groups.set(inquiry.recipient_id, []);
    groups.get(inquiry.recipient_id).push(inquiry);
  }
  return [...groups.values()];
}

function CustodyCheck({ data, inquiry, onSaved }) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(e) {
    e.preventDefault();
    if (busy) return;
    const fields = Object.fromEntries(new FormData(e.currentTarget));
    if (!fields.source.trim() || !fields.basis.trim()) { setError("Hãy ghi nguồn và căn cứ đã đối chiếu."); return; }
    setBusy(true);
    setError("");
    try {
      onSaved(await reviewApi.verifyCustody(data.report.report_id, inquiry.inquiry_id, { version: data.report.version, ...fields }));
      setOpen(false);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  return <><button type="button" onClick={() => setOpen(true)}>Xác minh tình trạng món đồ</button>
    {open && <Dialog title="Ghi nhận xác minh sau hạn phản hồi" busy={busy} onClose={() => setOpen(false)}>
      <form className="rr-stack" onSubmit={submit}>
        <label>Kết quả<select name="result" defaultValue="" required><option value="">Chọn kết quả</option>
          <option value="HELD">Người nhặt vẫn giữ đồ</option><option value="DELIVERED">Đã giao đồ</option>
          <option value="UNKNOWN">Chưa rõ ai giữ đồ · tiếp tục chờ</option></select></label>
        <label>Nguồn xác minh<input name="source" required maxLength={500} /></label>
        <label>Bằng chứng đã đối chiếu<textarea name="basis" required maxLength={5000} /></label>
        <ErrorBox>{error}</ErrorBox><button className="rr-primary" disabled={busy}>Lưu kết quả xác minh</button>
      </form></Dialog>}</>;
}

export default function Inquiries({ data, onSaved, open, setOpen, draftData }) {
  const inquiryData = draftData || data;
  const recipients = availableRecipients(inquiryData);
  const [recipientId, setRecipient] = useState(""),
    [question, setQuestion] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const structured = usesVerification(inquiryData);
  const options = structured ? verificationOptions(inquiryData) : [];
  const [kind, setKind] = useState("");
  const selected = options.find(option => option.kind === kind && !option.reason);
  const targetId = structured ? selected?.person.id : Number(recipientId);
  const canSend = targetId && question.trim();
  function close() {
    setOpen(false);
    setQuestion("");
    setRecipient("");
    setKind("");
    setError("");
  }
  async function ask(e) {
    e.preventDefault();
    if (!canSend || busy) return;
    setBusy(true);
    setError("");
    try {
      onSaved(
        await reviewApi.ask(data.report.report_id, {
          version: inquiryData.report.version,
          verificationKind: structured ? kind : null,
          recipientId: targetId,
          question: question.trim(),
        }),
      );
      close();
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
        {data.caseKind !== "GENERAL" && (structured ? options : recipients).length > 0 && (data.canAsk ?? data.canProcess) && (
          <button
            onClick={() => {
              setError("");
              setOpen(true);
            }}
          >
            {structured ? "Yêu cầu xác minh" : "Yêu cầu bổ sung"}
          </button>
        )}
      </div>
      {!data.inquiries.length && <Empty>Chưa có yêu cầu xác minh.</Empty>}
      {exchangesByRecipient(data.inquiries).map(exchanges => {
        const person = exchanges[0];
        const role = participantRoles(data, person.recipient_id).join(" · ");
        return <section className="rr-inquiry-person" key={person.recipient_id}>
          <h4>{person.recipient}</h4>
          {role && <small className="rr-inquiry-role">{role}</small>}
          {exchanges.map(i => {
        const pending = !i.responded_at && !i.closed_at;
        const status = i.responded_at ? "Đã phản hồi" : i.closed_at ? "Đã đóng" : i.overdue ? "Quá hạn" : "Chờ phản hồi";
        return <article className="rr-inquiry-entry" key={i.inquiry_id}>
          <div className="rr-row">
            <small>Hỏi lúc {date(i.created_at)}</small>
            <span className="rr-badge" data-state={pending ? i.overdue ? "late" : "waiting" : "done"}>{status}</span>
          </div>
          <dl>
            <dt>{data.verificationOptions?.find(o => o.kind === i.verification_kind)?.label || verificationLabels[i.verification_kind] || "Câu hỏi"}</dt><dd>{i.question}</dd>
            {i.responded_at && <><dt>Trả lời</dt><dd>{i.response}<Gallery images={i.evidence} /></dd></>}
            {i.verified_custody && <><dt>Quản lý xác minh</dt><dd>
              {{HELD: "Người nhặt vẫn giữ đồ", DELIVERED: "Đã giao đồ", UNKNOWN: "Chưa rõ ai giữ đồ"}[i.verified_custody]}
              <small>{i.verification_source} · {i.verification_basis}</small></dd></>}
          </dl>
          {pending && i.overdue && i.verification_kind === "CUSTODY" && data.appeal?.active && data.canProcess &&
            <CustodyCheck data={data} inquiry={i} onSaved={onSaved} />}
          <small className="rr-muted">{pending && i.response_due_at ? "Hạn trả lời · " + date(i.response_due_at)
            : i.responded_at ? "Đã trả lời · " + date(i.responded_at) : "Đã đóng · " + date(i.closed_at)}</small>
        </article>;
          })}
        </section>;
      })}
      {open && (
        <Dialog
          title={structured ? "Yêu cầu xác minh" : "Yêu cầu bổ sung thông tin"}
          busy={busy}
          onClose={close}
        >
          <form className="rr-stack" onSubmit={ask}>
            <p className="rr-muted">Người nhận có 12 giờ làm việc để trả lời. Hết hạn, hồ sơ về mục Cần xử lý để bạn kết luận dựa trên căn cứ hiện có.</p>
            {structured ? <>
              <label>Nội dung cần xác minh
                <select autoFocus required value={kind} onChange={e => { setKind(e.target.value); setQuestion(options.find(o => o.kind === e.target.value)?.question || ""); }}>
                  <option value="">Chọn việc cần xác minh</option>
                  {options.map(option => <option key={option.kind} value={option.kind} disabled={Boolean(option.reason)}>
                    {option.label || verificationLabels[option.kind]}{option.reason && ` · ${option.reason}`}
                  </option>)}
                </select>
              </label>
              {selected && <p>Gửi đến <strong>{selected.person.full_name}</strong> · {selected.role}</p>}
            </> : <label>
              Người nhận *
              <select
                autoFocus
                required
                value={recipientId}
                onChange={(e) => setRecipient(e.target.value)}
              >
                <option value="">Chọn người liên quan</option>
                {recipients.map((person) => (
                  <option key={person.id} value={person.id}>
                    {[...participantRoles(inquiryData, person.id), person.full_name, person.email].filter(Boolean).join(" · ")}
                  </option>
                ))}
              </select>
            </label>}
            {(!structured || selected) && <label>
              {kind === "OWNERSHIP" ? "Bạn cần thêm bằng chứng nào?" : kind === "DISCREPANCY" ? "Chi tiết nào cần người nhận mô tả lại?" : "Nội dung cần làm rõ *"}
              <textarea
                required
                placeholder={kind === "DISCREPANCY" ? "Ví dụ: Bạn có thể mô tả vị trí vết nứt trên máy?" : undefined}
                maxLength={5000}
                rows={5}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
              />
            </label>}
            <ErrorBox>{error}</ErrorBox>
            <button className="rr-primary" disabled={busy || !canSend}>
              {busy ? "Đang gửi…" : "Gửi yêu cầu"}
            </button>
          </form>
        </Dialog>
      )}
    </section>
  );
}
