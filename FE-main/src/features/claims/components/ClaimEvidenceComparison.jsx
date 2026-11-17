import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { FileSearch, ListChecks, RefreshCw } from 'lucide-react';
import { useChat } from '@/features/chat/hooks/useChat';
import ImageViewer from '@/components/common/ImageViewer';
import { getClaimComparison } from '../api/claimApi';
import { CLAIM_STATUS, claimDate, claimDetailPath, evidenceUrl } from '../utils/claimUtils';
import { comparisonCriteria } from '../utils/reviewCriteria';
import './ClaimEvidenceComparison.css';

function AnswerText({ text }) {
  if (!text) return <span className="claim-compare-missing">Chưa cung cấp / Không áp dụng</span>;
  if (text.length <= 180) return <p className="claim-compare-answer">{text}</p>;
  return <details className="claim-compare-long"><summary>{text.slice(0, 160)}… <span>Đọc đầy đủ</span></summary><p className="claim-compare-answer">{text}</p></details>;
}
function CriterionField({ claim, criterion }) {
  const answer = (claim.answers || []).find(a => a.templateCode === criterion.templateCode
    && a.templateVersion === criterion.templateVersion && a.questionCode === criterion.questionCode);
  const text = criterion.description ? claim.description : answer?.answerText;
  const displayText = criterion.templateCode === 'COMMON' && criterion.questionCode === 'C1'
    ? text?.replace(/\s*\(giờ Việt Nam\)/giu, '') : text;
  return <div className="claim-compare-field"><dt>{criterion.label}<span className="claim-required"> *</span></dt>
    <dd><AnswerText text={displayText} /></dd></div>;
}
function EvidenceField({ claim }) {
  const [index, setIndex] = useState(null);
  const images = (claim.evidence || []).map(e => ({ url: evidenceUrl(e.url), name: e.originalName || 'Ảnh bằng chứng' })).filter(e => e.url);
  return <div className="claim-compare-field claim-compare-evidence"><dt>Ảnh bằng chứng</dt><dd><div className="claim-compare-images">{images.map((image, i) => <button type="button" key={`${image.url}:${i}`} onClick={() => setIndex(i)} aria-label={`Xem ảnh bằng chứng ${i + 1} của ${claim.claimantName}`}><img src={image.url} alt={image.name} loading="lazy" /></button>)}</div>
    <small className="claim-compare-missing">{images.length ? `${images.length} ảnh` : 'Chưa có ảnh xem được'}</small>
    {index !== null && <ImageViewer images={images} initialIndex={index} onClose={() => setIndex(null)} title={`Bằng chứng của ${claim.claimantName}`} />}
  </dd></div>;
}
export default function ClaimEvidenceComparison({ postId, status }) {
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const pageKey = `reviewPage_${postId}`;
  const rawPage = Number(params.get(pageKey));
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1;
  const { revision } = useChat();
  const [refresh, setRefresh] = useState(0);
  const [snapshot, setSnapshot] = useState({ key: '', data: null, error: '' });
  const key = `${postId}:${status}:${page}:${refresh}:${revision}`;
  useEffect(() => {
    const reload = () => setRefresh(value => value + 1);
    window.addEventListener('focus', reload);
    return () => window.removeEventListener('focus', reload);
  }, []);
  useEffect(() => {
    let cancelled = false;
    getClaimComparison(postId, page, status).then(data => { if (!cancelled) setSnapshot({ key, data, error: '' }); })
      .catch(error => { if (!cancelled) setSnapshot({ key, data: null, error: error.message || 'Không tải được căn cứ.' }); });
    return () => { cancelled = true; };
  }, [postId, page, status, key]);
  function changePage(next) {
    const updated = new URLSearchParams(params);
    if (next === 1) updated.delete(pageKey); else updated.set(pageKey, String(next));
    setParams(updated, { replace: true });
  }
  const loading = snapshot.key !== key;
  const rows = snapshot.data?.content || [];
  const criteria = comparisonCriteria(rows);
  return <section className="claim-comparison" aria-label="Đối chiếu căn cứ nhận đồ">
    <header className="claim-comparison-heading"><div><h4><FileSearch size={18} aria-hidden="true" />Căn cứ cần đối chiếu</h4><p>Ưu tiên hồ sơ trả lời đầy đủ các câu hỏi áp dụng. Mức độ hoàn thành không xác nhận quyền sở hữu.</p></div>
      <button type="button" className="btn btn-ghost" onClick={() => setRefresh(value => value + 1)} disabled={loading}><RefreshCw size={15} aria-hidden="true" />Tải lại</button></header>
    {loading ? <p role="status">Đang tải căn cứ…</p> : snapshot.error ? <p role="alert" className="claim-alert">{snapshot.error}</p> : !rows.length ? <div><p>Không còn yêu cầu đang xử lý ở trang này.</p>{page > 1 && <button type="button" className="btn btn-ghost" onClick={() => changePage(1)}>Về trang đầu</button>}</div> : <>
      <div className="claim-comparison-cards">
        {rows.map(claim => <article className="claim-compare-card" key={claim.id} aria-labelledby={`claim-compare-person-${postId}-${claim.id}`}>
          <header className="claim-compare-card-heading">
            <div className="claim-compare-person"><h5 id={`claim-compare-person-${postId}-${claim.id}`}>{claim.claimantName}</h5>
              <small>Hồ sơ #{claim.id} · {claimDate(claim.createdAt)}</small></div>
            <div className="claim-compare-card-actions">
              <span className={`claim-completion${claim.completion?.total > 0 && claim.completion.answered === claim.completion.total ? ' is-complete' : ''}`}
                title="Tính các câu hỏi áp dụng, gồm câu tùy chọn; ảnh bằng chứng hiển thị riêng. Chỉ đếm việc trả lời, không chấm đúng sai.">
                <ListChecks size={16} aria-hidden="true" />{claim.completion?.total > 0
                  ? <>Đã trả lời <strong>{claim.completion.answered}/{claim.completion.total}</strong> câu</>
                  : 'Chưa có dữ liệu câu hỏi'}
              </span>
              <span className={`claim-status claim-status-${claim.status}`}>{CLAIM_STATUS[claim.status]}</span>
              <Link className="btn btn-ghost" to={claimDetailPath(postId, claim.id)} state={{ claimListReturn: location.pathname + location.search }}>Xem hồ sơ & xử lý →</Link></div>
          </header>
          <dl className="claim-compare-fields">{criteria.map(c => <CriterionField key={c.key} claim={claim} criterion={c} />)}<EvidenceField claim={claim} /></dl>
        </article>)}
      </div>
      <footer className="claim-comparison-footer"><small>{snapshot.data.totalElements} yêu cầu đang xử lý · Chờ duyệt trước, đầy đủ trước; cùng mức thì mới nhất trước</small>
        {snapshot.data.totalPages > 1 && <nav className="claim-compare-pagination" aria-label="Trang đối chiếu">
          <button type="button" className="btn btn-ghost" disabled={page <= 1} onClick={() => changePage(page - 1)}>Trang trước</button>
          <span>Trang {page}/{snapshot.data.totalPages}</span>
          <button type="button" className="btn btn-ghost" disabled={page >= snapshot.data.totalPages} onClick={() => changePage(page + 1)}>Trang sau</button>
        </nav>}</footer>
    </>}
  </section>;
}
