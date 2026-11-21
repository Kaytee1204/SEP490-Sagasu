import { useState } from "react";
import { reviewApi } from "@/features/report-review/api";
import { formatReportDate } from "../utils/reportUtils";
import useImageUploads from '@/hooks/useImageUploads';
import ImageUploadButton from "@/components/common/ImageUploadButton";
import { Send, ShieldCheck } from "lucide-react";
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
export default function ReportInquiryResponse({ inquiry: i, canReply, onSaved }) {
  const replyOpen = canReply && !i.closed_at;
  const [response, setResponse] = useState(""),
    [files, setFiles] = useState([]),
    [submitting, setBusy] = useState(false),
    [error, setError] = useState("");
  const { prepare, processing } = useImageUploads();
  const busy = submitting || processing;
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
    <article className="report-inquiry">
      <div className="report-note">
        <h3>Yêu cầu từ quản lý</h3>
        <time dateTime={i.created_at}>{formatReportDate(i.created_at)}</time>
        <p>{i.question}</p>
        {i.response_due_at && !i.responded_at && replyOpen && <p>Hạn phản hồi · {formatReportDate(i.response_due_at)}</p>}
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
      ) : replyOpen ? (
        <form className="report-reply-form" onSubmit={submit}>
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
          {error && <p className="report-error" role="alert">{error}</p>}
          <footer className="report-reply-submit">
            <p><ShieldCheck size={16} aria-hidden="true" /> Phản hồi chỉ được gửi một lần.</p>
            <button className="btn btn-accent" type="submit" disabled={busy || !response.trim()}>
              <Send size={16} aria-hidden="true" />{busy ? "Đang gửi…" : "Gửi thông tin bổ sung"}
            </button>
          </footer>
        </form>
      ) : (
        <p>Vụ việc đã kết thúc, không cần gửi thêm thông tin.</p>
      )}
    </article>
  );
}
