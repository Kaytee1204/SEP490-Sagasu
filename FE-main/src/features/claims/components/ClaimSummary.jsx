import { useEffect, useId, useState } from 'react';
import { AlignLeft, ChevronUp, RefreshCw } from 'lucide-react';
import { getClaimSummary } from '../api/claimApi';
import { summarizeClaimAnswers } from '../utils/claimAnswerDigest';
import './ClaimSummary.css';

export default function ClaimSummary({ postId, page, status, claims, heading, disabled = false }) {
  const messageId = useId();
  const [expanded, setExpanded] = useState(false);
  const [request, setRequest] = useState(0);
  const [snapshot, setSnapshot] = useState({ data: null, error: '' });
  useEffect(() => {
    if (!request) return;
    let cancelled = false;
    getClaimSummary(postId, page, status).then(data => {
      if (cancelled) return;
      const matches = data?.rows?.length === claims.length && data.rows.every((row, i) => row.claimId === claims[i].id);
      setSnapshot({ data: matches ? data : null, error: matches ? '' : 'Danh sách vừa thay đổi. Vui lòng tải lại trang để cập nhật bản tóm tắt.' });
    }).catch(() => {
      if (!cancelled) setSnapshot({ data: null, error: 'Chưa tải được bản tóm tắt. Bạn vẫn có thể đối chiếu câu trả lời bên dưới.' });
    });
    return () => { cancelled = true; };
  }, [postId, page, status, claims, request]);

  function toggleSummary() {
    setExpanded(value => !value);
    if (!request) setRequest(1);
  }
  function retry() {
    setSnapshot({ data: null, error: '' });
    setRequest(value => value + 1);
  }
  const loading = !snapshot.data && !snapshot.error;
  const generated = snapshot.data?.mode === 'GEMINI';
  return <div className="claim-summary">
    <header className="claim-comparison-heading">{heading}
    <button type="button" className="btn btn-ghost claim-summary-toggle" aria-expanded={expanded}
      aria-controls={messageId} onClick={toggleSummary} disabled={disabled}>
      {expanded ? <ChevronUp size={16} aria-hidden="true" /> : <AlignLeft size={16} aria-hidden="true" />}
      {expanded ? 'Ẩn tóm tắt' : 'Tóm tắt yêu cầu'}
    </button>
    </header>
    {expanded && <section id={messageId} className="claim-summary-message" aria-label="Tóm tắt yêu cầu nhận đồ" aria-busy={loading}>
      <header><strong>Tóm tắt câu trả lời</strong>
        {snapshot.data && <span className={`claim-summary-mode${generated ? ' is-generated' : ''}`}>
          {generated ? 'Bản tóm tắt' : 'Trích theo mẫu'}
        </span>}
      </header>
      {loading ? <p role="status">Đang tóm tắt yêu cầu…</p> : snapshot.error ? <div>
        <p role="status">{snapshot.error}</p>
        <button type="button" className="btn btn-ghost" onClick={retry}><RefreshCw size={14} aria-hidden="true" />Thử lại tóm tắt</button>
      </div> : <div className="claim-summary-users">
        {snapshot.data.rows.map((row, i) => <article className="claim-summary-user" key={row.claimId}
          aria-label={`Tóm tắt của ${claims[i].claimantName}, hồ sơ ${row.claimId}`}>
          <header><a href={`#claim-compare-person-${postId}-${row.claimId}`} title={`Đối chiếu hồ sơ #${row.claimId}`}>
            <strong>{claims[i].claimantName}</strong>
          </a><small>Hồ sơ #{row.claimId}</small></header>
          <p>Người gửi {summarizeClaimAnswers(claims[i], row)}.</p>
        </article>)}
      </div>}
    </section>}
  </div>;
}
