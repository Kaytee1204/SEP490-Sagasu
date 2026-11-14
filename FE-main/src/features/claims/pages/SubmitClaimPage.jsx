import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ChevronDown, Images, MapPin, Package, Send, ShieldCheck, TriangleAlert, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { getPostDetail } from '@/features/items/api/postApi';
import { getClaimOnPost, getLatestClaimOnPost, getClaimQuestionnaire, submitClaim } from '../api/claimApi';
import { validClaimId, claimDetailPath, canSubmitPostClaim, postClaimType, evidenceUrl } from '../claimUtils';
import { serializeQuestionnaireAnswers } from '../questionnaireUtils';
import ClaimQuestionnaireFields from '../components/ClaimQuestionnaireFields';
import '../Claims.css';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
import ImageUploadButton from '@/components/common/ImageUploadButton';
import ImageViewer from '@/components/common/ImageViewer';
import './SubmitClaimPage.css';

export default function SubmitClaimPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const retryId = params.get('retryOf');
  const { user } = useAuth();
  const [attempt, setAttempt] = useState(0);
  if (!validClaimId(id) || (retryId !== null && !validClaimId(retryId))) return <main className="container claims-page"><p role="alert">Mã bài đăng hoặc yêu cầu nhận đồ không hợp lệ.</p></main>;
  return <ClaimForm key={`${user?.id}:${id}:${retryId}:${attempt}`} postId={id} retryId={retryId} onRetry={() => setAttempt(value => value + 1)} />;
}
function ClaimForm({ postId, retryId, onRetry }) {
  const navigate = useNavigate();
  const [loaded, setLoaded] = useState(null);
  const [description, setDescription] = useState('');
  const [evidenceNote, setEvidenceNote] = useState('');
  const [answers, setAnswers] = useState({});
  const [files, setFiles] = useState([]);
  const [error, setError] = useState('');
  const [sending, setBusy] = useState(false);
  const [previewId, setPreviewId] = useState(null);
  const { prepare, processing } = useImageUploads();
  const busy = sending || processing;
  const inFlight = useRef(false);
  const previews = useRef([]);
  useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!retryId) {
        const previous = await getLatestClaimOnPost(postId);
        if (cancelled) return;
        if (previous) { navigate(claimDetailPath(postId, previous.id), { replace: true }); return; }
      }
      return Promise.all([getPostDetail(postId), retryId ? getClaimOnPost(postId, retryId) : Promise.resolve(null), getClaimQuestionnaire(postId)])
      .then(([post, old, questionnaire]) => {
        if (cancelled) return;
        let problem = !canSubmitPostClaim(post) ? 'Bài đăng hiện không tiếp nhận yêu cầu nhận đồ mới.' : '';
        if (post.isOwner || post.canEditOwnPost) problem = 'Bạn không thể gửi yêu cầu nhận đồ trên bài đăng của chính mình.';
        if (old && (String(old.postId) !== postId || !old.canRetry)) problem = 'Hồ sơ này chưa được phép gửi lại, hoặc bài không còn tiếp nhận yêu cầu nhận đồ.';
        setLoaded({ post, old, questionnaire, problem });
      });
    }
    load().catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [postId, retryId, navigate]);
  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), []);
  async function upload(event) {
    const added = Array.from(event.target.files || []); event.target.value = '';
    if (busy) return;
    if (files.length + added.length > 5) { setError('Mỗi hồ sơ tối đa 5 ảnh.'); return; }
    setError('');
    try {
      const prepared = await prepare(added);
      if (!prepared) return;
      const entries = prepared.map(file => { const url = URL.createObjectURL(file); previews.current.push(url); return { id: crypto.randomUUID(), file, url }; });
      setFiles(current => [...current, ...entries]);
    } catch (problem) {
      setError(problem.message);
    }
  }
  function remove(entry) {
    URL.revokeObjectURL(entry.url); previews.current = previews.current.filter(url => url !== entry.url);
    setFiles(current => current.filter(item => item.id !== entry.id));
  }
  async function send(event) {
    event.preventDefault();
    if (busy || inFlight.current || !loaded || loaded.problem) return;
    if (!description.trim()) { setError('Vui lòng mô tả căn cứ nhận đồ.'); return; }
    inFlight.current = true; setBusy(true); setError('');
    try {
      const result = await submitClaim(postId, { description: description.trim(), evidenceNote: evidenceNote.trim() || null,
        questionnaireVersion: loaded.questionnaire.version, answers: serializeQuestionnaireAnswers(loaded.questionnaire, answers) },
      files.map(entry => entry.file), retryId);
      navigate(claimDetailPath(result.postId, result.id), { replace: true, state: { submitted: true } });
    } catch (err) { setError(err.message); }
    finally { inFlight.current = false; setBusy(false); }
  }
  const previewIndex = files.findIndex(entry => entry.id === previewId);
  return <main className="container claims-page claim-submit-page">
    <header className="claim-submit-heading"><h1>{retryId ? 'Gửi lại yêu cầu nhận đồ' : 'Yêu cầu nhận đồ'}</h1>
      <Link to={`/chi-tiet/${postId}`}><ArrowLeft size={16} aria-hidden="true" />Về bài đăng</Link></header>
    {!loaded ? <div className="claim-submit-loading" role={error ? 'alert' : 'status'}>
      {error ? <><TriangleAlert size={26} aria-hidden="true" /><h2>Chưa tải được thông tin</h2><p>{error}</p>
        <button type="button" className="btn btn-ghost" onClick={onRetry}>Thử lại</button></> : <p>Đang tải thông tin món đồ…</p>}
    </div> : loaded.problem ? <div className="claim-submit-loading" role="alert"><TriangleAlert size={26} aria-hidden="true" /><p>{loaded.problem}</p></div> : <>
        <ClaimPostContext post={loaded.post} />
        {loaded.old && <div className="claim-submit-retry">Yêu cầu #{loaded.old.id} {loaded.old.status === 'CANCELLED' ? 'đã hủy' : 'đã bị từ chối'}. Bạn đang gửi một yêu cầu mới.
          <Link to={claimDetailPath(loaded.old.postId, loaded.old.id)}>Xem yêu cầu cũ</Link></div>}
        <form className="claim-form claim-submit-form" onSubmit={send}>
          <ClaimQuestionnaireFields questionnaire={loaded.questionnaire} answers={answers}
            onAnswer={(code, value) => setAnswers(current => ({ ...current, [code]: value }))}
            description={description} onDescription={setDescription} disabled={busy} />
          <aside className="claim-submit-sidebar" aria-label="Bằng chứng bổ sung">
            <section className="claim-submit-section claim-submit-evidence">
              <header className="claim-submit-section-heading"><Images size={19} aria-hidden="true" /><h2>Ảnh bằng chứng</h2></header>
              <p className="claim-submit-optional">Không bắt buộc</p>
              <p className="claim-submit-evidence-help">Ảnh cũ, hóa đơn hoặc tài liệu giúp xác minh món đồ của bạn.</p>
              <ImageUploadButton label="Thêm ảnh bằng chứng" disabled={busy || files.length >= 5} onChange={upload} />
              <div className="claim-submit-upload-limit"><span>{files.length}/5 ảnh</span><span>Tối đa 5 MB/ảnh</span></div>
              <p className="claim-submit-image-formats">{IMAGE_FORMATS}</p>
              <ImageProcessingStatus active={processing} />
              {files.length > 0 && <div className="claim-submit-photos">{files.map(entry => <figure key={entry.id}>
                <button type="button" className="claim-submit-photo-preview" disabled={busy} onClick={() => setPreviewId(entry.id)} aria-label={`Xem ảnh ${entry.file.name}`}>
                  <img src={entry.url} alt={entry.file.name} />
                </button>
                <figcaption title={entry.file.name}>{entry.file.name}</figcaption>
                <button type="button" className="claim-submit-photo-remove" disabled={busy} onClick={() => remove(entry)} aria-label={`Bỏ ảnh ${entry.file.name}`}>
                  <X size={12} aria-hidden="true" />Bỏ ảnh
                </button>
              </figure>)}</div>}
              <details className="claim-submit-evidence-note"><summary>Thêm ghi chú<ChevronDown size={15} aria-hidden="true" /></summary>
                <label>Ghi chú về bằng chứng<textarea rows={2} maxLength={5000} value={evidenceNote} disabled={busy}
                  onChange={event => setEvidenceNote(event.target.value)} placeholder="Bổ sung thông tin về ảnh hoặc tài liệu…" /></label>
              </details>
            </section>
            <footer className="claim-submit-actions">
              {error && <p className="claim-submit-error" role="alert"><TriangleAlert size={18} aria-hidden="true" />{error}</p>}
              <button className="btn btn-accent" disabled={busy} type="submit"><Send size={17} aria-hidden="true" />
                {processing ? 'Đang xử lý ảnh…' : sending ? 'Đang gửi…' : postClaimType(loaded.post) === 'OFFICE' ? 'Gửi yêu cầu cho phòng ban' : 'Gửi yêu cầu nhận đồ'}
              </button>
              <p><ShieldCheck size={17} aria-hidden="true" />{postClaimType(loaded.post) === 'OFFICE'
                ? 'Căn cứ chỉ dành cho bạn và cán bộ xét duyệt.' : 'Căn cứ chỉ dành cho bạn và người nhặt xem xét.'}</p>
            </footer>
            <p className="claim-submit-security"><ShieldCheck size={17} aria-hidden="true" />Không nhập mật khẩu, PIN, OTP, CVV hoặc đầy đủ số thẻ ngân hàng.</p>
          </aside>
        </form>
        {previewIndex >= 0 && <ImageViewer images={files.map(entry => ({ id: entry.id, url: entry.url, name: entry.file.name }))}
          initialIndex={previewIndex} title="Ảnh bằng chứng đã chọn" onClose={() => setPreviewId(null)} />}
      </>}
  </main>;
}

function ClaimPostContext({ post }) {
  const [imageFailed, setImageFailed] = useState(false);
  const cover = evidenceUrl(post.images?.[0]?.url);
  return <section className="claim-submit-post" aria-label="Món đồ muốn nhận lại">
    <div className="claim-submit-post-image">{cover && !imageFailed
      ? <img src={cover} alt="" onError={() => setImageFailed(true)} /> : <Package size={28} aria-hidden="true" />}</div>
    <div><h2>{post.title}</h2><p><MapPin size={14} aria-hidden="true" />Nhặt tại {post.locationText}</p></div>
  </section>;
}
