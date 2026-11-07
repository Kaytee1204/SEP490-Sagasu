import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getPostDetail } from '@/features/items/api/postApi';
import { getClaimOnPost, getLatestClaimOnPost, getClaimQuestionnaire, submitClaim } from '../api/claimApi';
import { validClaimId, claimDetailPath, canSubmitPostClaim, postClaimType } from '../claimUtils';
import { serializeQuestionnaireAnswers } from '../questionnaireUtils';
import ClaimQuestionnaireFields from '../components/ClaimQuestionnaireFields';
import '../Claims.css';

export default function SubmitClaimPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const retryId = params.get('retryOf');
  const { user } = useAuth();
  if (!validClaimId(id) || (retryId !== null && !validClaimId(retryId))) return <main className="container claims-page"><p role="alert">Mã bài đăng hoặc claim không hợp lệ.</p></main>;
  return <ClaimForm key={`${user?.id}:${id}:${retryId}`} postId={id} retryId={retryId} />;
}
function ClaimForm({ postId, retryId }) {
  const navigate = useNavigate();
  const [loaded, setLoaded] = useState(null);
  const [description, setDescription] = useState('');
  const [evidenceNote, setEvidenceNote] = useState('');
  const [answers, setAnswers] = useState({});
  const [files, setFiles] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
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
        let problem = !canSubmitPostClaim(post) ? 'Bài chưa nhận claim: cần là bài P2P hoặc đồ đã được phòng ban xác nhận tiếp nhận, đang hiển thị và còn mở.' : '';
        if (post.isOwner || post.canEditOwnPost) problem = 'Bạn không thể claim bài đăng của chính mình.';
        if (old && (String(old.postId) !== postId || !old.canRetry)) problem = 'Hồ sơ này chưa được phép gửi lại, hoặc bài không còn nhận claim.';
        setLoaded({ post, old, questionnaire, problem });
      });
    }
    load().catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [postId, retryId, navigate]);
  useEffect(() => () => previews.current.forEach(url => URL.revokeObjectURL(url)), []);
  function upload(event) {
    const added = Array.from(event.target.files || []); event.target.value = '';
    if (files.length + added.length > 5) { setError('Mỗi hồ sơ tối đa 5 ảnh.'); return; }
    if (added.some(file => !['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024)) {
      setError('Chỉ chọn ảnh JPG/PNG từ 1 byte đến 5 MB.'); return;
    }
    const entries = added.map(file => { const url = URL.createObjectURL(file); previews.current.push(url); return { id: crypto.randomUUID(), file, url }; });
    setFiles(current => [...current, ...entries]); setError('');
  }
  function remove(entry) {
    URL.revokeObjectURL(entry.url); previews.current = previews.current.filter(url => url !== entry.url);
    setFiles(current => current.filter(item => item.id !== entry.id));
  }
  async function send(event) {
    event.preventDefault();
    if (inFlight.current || !loaded || loaded.problem) return;
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
  const evidenceField = <>
    <label>Ảnh bằng chứng<input type="file" multiple accept="image/jpeg,image/png" disabled={busy} onChange={upload} /></label>
    <p className="claims-muted">Tối đa 5 ảnh JPG/PNG, mỗi ảnh tối đa 5 MB. Có thể gửi căn cứ bằng chữ mà không đính kèm ảnh.</p>
    <div className="claim-evidence">{files.map(entry => <div key={entry.id}><img src={entry.url} alt={entry.file.name} /><small>{entry.file.name}</small>
      <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => remove(entry)} aria-label={`Xóa ảnh ${entry.file.name}`}>Xóa ảnh</button></div>)}</div>
    <label>Ghi chú bằng chứng (không bắt buộc)<textarea maxLength={5000} value={evidenceNote} disabled={busy}
      onChange={event => setEvidenceNote(event.target.value)} placeholder="Ghi chú về ảnh cũ, hóa đơn hoặc tài liệu đính kèm…" /></label>
  </>;
  return <main className="container claims-page">
    <header className="claims-heading"><h1>{retryId ? 'Gửi claim mới' : 'Xác minh quyền sở hữu'}</h1><Link className="btn btn-ghost" to={`/chi-tiet/${postId}`}>Về bài đăng</Link></header>
    {error && <div className="claim-alert" role="alert">{error}</div>}
    {!loaded ? <p role="status">{error ? 'Không tải được thông tin bài đăng. Vui lòng mở lại trang.' : 'Đang tải bài đăng…'}</p>
      : loaded.problem ? <div className="claim-panel" role="alert">{loaded.problem}</div> : <>
        <section className="claim-panel"><h2>{loaded.post.title}</h2><p>{loaded.post.locationText}</p>
          <p className="claims-muted">{postClaimType(loaded.post) === 'OFFICE'
            ? 'Yêu cầu được gửi cho phòng ban tiếp nhận. Bằng chứng chỉ dành cho bạn và cán bộ xét duyệt; luồng này không có chat P2P.'
            : 'Căn cứ và ảnh bằng chứng chỉ dành cho bạn và người nhặt xem xét, không hiển thị trên bài đăng.'}</p></section>
        {loaded.old && <div className="claim-notice">Hồ sơ #{loaded.old.id} {loaded.old.status === 'CANCELLED' ? 'đã hủy' : 'đã bị từ chối'}. Lần gửi này tạo hồ sơ mới; nội dung, bằng chứng và lịch sử cũ vẫn được giữ lại.
          <Link to={claimDetailPath(loaded.old.postId, loaded.old.id)}> Xem hồ sơ cũ</Link></div>}
        <form className="claim-panel claim-form" onSubmit={send}>
          <ClaimQuestionnaireFields questionnaire={loaded.questionnaire} answers={answers}
            onAnswer={(code, value) => setAnswers(current => ({ ...current, [code]: value }))}
            description={description} onDescription={setDescription} evidenceField={evidenceField} disabled={busy} />
          <button className="btn btn-accent" disabled={busy} type="submit">{busy ? 'Đang gửi…' : postClaimType(loaded.post) === 'OFFICE' ? 'Gửi claim cho phòng ban' : 'Gửi claim'}</button>
        </form>
      </>}
  </main>;
}
