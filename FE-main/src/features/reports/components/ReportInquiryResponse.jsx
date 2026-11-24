import { useState } from "react";
import { reviewApi } from "@/features/report-review/api";
import { formatReportDate } from "../utils/reportUtils";
import useImageUploads from '@/hooks/useImageUploads';
import ImageUploadButton from "@/components/common/ImageUploadButton";
import LocalImagePreview from "@/components/common/LocalImagePreview";
import { verificationLabels } from "@/features/report-review/format";
import { Send, ShieldCheck } from "lucide-react";
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
export default function ReportInquiryResponse({ inquiry: i, canReply, onSaved }) {
  const custody = i.verification_kind === "CUSTODY";
  const replyOpen = canReply && !i.responded_at && !i.closed_at;
  const [sent, setSent] = useState(false);
  const [response, setResponse] = useState(""),
    [files, setFiles] = useState([]),
    [submitting, setBusy] = useState(false),
    [error, setError] = useState("");
  const { prepare, processing } = useImageUploads();
  const busy = submitting || processing;
  async function submit(e) {
    e.preventDefault();
    if (busy || sent) return;
    setBusy(true);
    setError("");
    try {
      await reviewApi.reply(i.inquiry_id, response.trim(), files);
      setSent(true);
      onSaved();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function pick(e) {
    const values = [...e.target.files];
    e.target.value = "";
    if (busy) return;
    if (files.length + values.length > 5) {
      setError("Chọn tối đa 5 ảnh.");
      return;
    }
    setError("");
    try {
      const prepared = await prepare(values);
      if (prepared) setFiles(current => [...current,...prepared]);
    } catch (problem) { setError(problem.message); }
  }
  return (
    <article className="report-inquiry" tabIndex={-1} data-pending={replyOpen && !sent}>
      <div className="report-note">
        <h3>{verificationLabels[i.verification_kind] || "Yêu cầu từ quản lý"}</h3>
        <time dateTime={i.created_at}>{formatReportDate(i.created_at)}</time>
        <p>{i.question}</p>
        {i.response_due_at && replyOpen && <p>
          {i.overdue ? "Đã quá hạn phản hồi" : "Hạn phản hồi"} · {formatReportDate(i.response_due_at)}
        </p>}
      </div>
      {i.responded_at ? (
        <>
          <h3>Thông tin bạn đã gửi</h3>
          <p>{i.response}</p>
          <time dateTime={i.responded_at}>{formatReportDate(i.responded_at)}</time>
          {i.evidence?.length > 0 && <div className="report-evidence">
            {i.evidence.map(image => <a href={image.url} key={image.evidence_id} target="_blank" rel="noreferrer">
              <img src={image.url} alt={image.original_name || 'Ảnh đối chiếu'} loading="lazy" />
            </a>)}
          </div>}
        </>
      ) : sent ? <p role="status">Đã gửi phản hồi.</p> : replyOpen ? (
        <form className="report-reply-form" onSubmit={submit}>
          {custody ? <fieldset className="report-custody-choices">
            <legend>Tình trạng món đồ</legend>
            {["Tôi vẫn giữ đồ", "Tôi đã giao đồ"].map(value => <label key={value}>
              <input type="radio" required name={"custody-" + i.inquiry_id} value={value} checked={response === value}
                disabled={busy} onChange={() => setResponse(value)} /> {value}
            </label>)}
          </fieldset> : <>
          <label>
            Phản hồi của bạn *
            <textarea
              required
              maxLength={5000}
              rows={3}
              value={response}
              disabled={busy}
              onChange={(e) => setResponse(e.target.value)}
            />
          </label>
          <div className="report-reply-upload">
            <span>Ảnh đối chiếu <small>Không bắt buộc</small></span>
            <ImageUploadButton label="Thêm ảnh đối chiếu" disabled={busy || files.length >= 5} onChange={pick}/>
            <small>{files.length}/5 ảnh · Tối đa 5 MB/ảnh</small><small>{IMAGE_FORMATS}</small>
          </div>
          <ImageProcessingStatus active={processing} />
          {files.map((f, index) => (
            <div className="report-reply-file" key={index}>
              <LocalImagePreview file={f} />
              <span>{f.name}</span>
              <button
                type="button"
                aria-label={`Bỏ ảnh ${f.name}`}
                disabled={busy}
                onClick={() =>
                  setFiles((old) => old.filter((_, n) => n !== index))
                }
              >
                Bỏ ảnh
              </button>
            </div>
          ))}
          </>}
          {error && <p className="report-error" role="alert">{error}</p>}
          <footer className="report-reply-submit">
            <p><ShieldCheck size={16} aria-hidden="true" /> Phản hồi chỉ được gửi một lần.</p>
            <button className="btn btn-accent" type="submit" disabled={busy || !response.trim()}>
              <Send size={16} aria-hidden="true" />{busy ? "Đang gửi…" : "Gửi thông tin bổ sung"}
            </button>
          </footer>
        </form>
      ) : (
        <p>{i.verified_custody === "HELD" ? "Quản lý đã xác minh người nhặt vẫn giữ món đồ."
          : i.verified_custody === "DELIVERED" ? "Quản lý đã xác minh món đồ đã được giao."
          : "Yêu cầu bổ sung này đã đóng."}</p>
      )}
    </article>
  );
}
