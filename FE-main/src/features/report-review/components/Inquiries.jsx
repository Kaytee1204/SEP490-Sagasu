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
  const handoverVerification = structured && inquiryData.caseKind === "HANDOVER_ISSUE";
  const options = structured ? verificationOptions(inquiryData) : [];
  const [selectedKinds, setSelectedKinds] = useState([]);
  const [handoverQuestions, setHandoverQuestions] = useState({});
  const selected = options.filter(option => !option.reason && selectedKinds.includes(option.kind));
  const singleOption = selected.length === 1 ? selected[0] : null;
  const sendingToBoth = selected.length === 2 && selected[0].person.id !== selected[1].person.id;
  const handoverQuestion = option => handoverQuestions[option.kind] ?? "";
  const message = question.trim();
  const verificationQuestions = selected.map(option => ({
    verificationKind: option.kind,
    recipientId: option.person.id,
    question: handoverVerification ? handoverQuestion(option).trim() : message,
  }));
  const canSend = structured
    ? (Boolean(singleOption) || sendingToBoth) && verificationQuestions.every(item => item.question)
    : Number(recipientId) > 0 && Boolean(message);
  function close() {
    setOpen(false);
    setQuestion("");
    setRecipient("");
    setSelectedKinds([]);
    setHandoverQuestions({});
    setError("");
  }
  async function ask(e) {
    e.preventDefault();
    if (!canSend || busy) return;
    setBusy(true);
    setError("");
    try {
      const id = inquiryData.report.report_id;
      const version = inquiryData.report.version;
      const result = sendingToBoth
        ? await reviewApi.askBoth(id, { version, questions: verificationQuestions })
        : await reviewApi.ask(id, {
            version,
            ...(structured ? verificationQuestions[0] : { recipientId: Number(recipientId), question: message }),
          });
      onSaved(result);
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
          <form className={structured ? "rr-stack rr-verification-form" : "rr-stack"} onSubmit={ask}>
            {structured ? <>
              <strong>Nội dung cần xác minh</strong>
              <div className="rr-verification-options" role="group" aria-label="Nội dung cần xác minh">
                {options.map(option => {
                  const checked = selected.includes(option);
                  const label = option.label || verificationLabels[option.kind];
                  return <div className="rr-verification-option" data-selected={checked} key={option.kind}>
                    <label className="rr-check">
                      <input type="checkbox" checked={checked} disabled={busy || Boolean(option.reason)}
                        onChange={event => {
                          const nextChecked = event.target.checked;
                          setSelectedKinds(values => nextChecked ? [...values, option.kind] : values.filter(value => value !== option.kind));
                        }} />
                      <span>
                        <strong>{label}</strong>
                        {option.person && <small>Gửi đến <b>{option.person.full_name}</b> · {option.role}</small>}
                        {option.reason && <small>{option.reason}</small>}
                      </span>
                    </label>
                    {handoverVerification && <label className="rr-verification-question">
                      Câu hỏi xác minh{checked && " *"}
                      <textarea required={checked} disabled={busy || Boolean(option.reason)} maxLength={5000} rows={3}
                        value={handoverQuestion(option)}
                        onChange={event => {
                          const value = event.target.value;
                          setHandoverQuestions(questions => ({ ...questions, [option.kind]: value }));
                        }} />
                    </label>}
                  </div>;
                })}
              </div>
            </> : <>
              <p className="rr-muted">Người nhận có 12 giờ làm việc để trả lời. Hết hạn, hồ sơ về mục Cần xử lý để bạn kết luận dựa trên căn cứ hiện có.</p>
              <label>Người nhận *
                <select autoFocus required disabled={busy} value={recipientId}
                  onChange={e => setRecipient(e.target.value)}>
                  <option value="">Chọn người liên quan</option>
                  {recipients.map(person => (
                    <option key={person.id} value={person.id}>
                      {[...participantRoles(inquiryData, person.id), person.full_name, person.email].filter(Boolean).join(" · ")}
                    </option>
                  ))}
                </select>
              </label>
            </>}
            {!handoverVerification && <label>{structured ? "Câu hỏi xác minh" : "Nội dung cần làm rõ"} *
              <textarea required disabled={busy} maxLength={5000} rows={structured ? 3 : 5} value={question}
                placeholder={singleOption?.kind === "DISCREPANCY" ? "Ví dụ: Bạn có thể mô tả vị trí vết nứt trên máy?" : undefined}
                onChange={e => setQuestion(e.target.value)} />
            </label>}
            {structured && selected.length > 0 && <small>Hạn phản hồi 12 giờ làm việc.</small>}
            <ErrorBox>{error}</ErrorBox>
            <button type="submit" className="rr-primary" disabled={busy || !canSend}>
              {busy ? "Đang gửi…" : sendingToBoth ? "Gửi yêu cầu cho cả hai" : "Gửi yêu cầu"}
            </button>
          </form>
        </Dialog>
      )}
    </section>
  );
}
