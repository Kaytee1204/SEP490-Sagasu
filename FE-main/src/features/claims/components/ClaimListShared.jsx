import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowUpRight, ChevronDown, Clock3, History, UserRound } from 'lucide-react';
import { CLAIM_STATUS, HISTORY_STATUSES, claimDate, claimDetailPath, evidenceUrl } from '../utils/claimUtils';

export function ClaimPostThumbnail({ url, title }) {
  const [failed, setFailed] = useState(false);
  const source = evidenceUrl(url);
  return (
    <span className="claim-post-thumbnail">
      {source && !failed ? (
        <img src={source} alt={`Ảnh bài đăng: ${title}`} loading="lazy" onError={() => setFailed(true)} />
      ) : (
        <span>{failed ? 'Không tải được ảnh' : 'Chưa có ảnh'}</span>
      )}
    </span>
  );
}

export function ClaimRow({ claim, incoming, showPost = false }) {
  const location = useLocation();
  const actionLabel = incoming && claim.status === 'PENDING'
    ? 'Xem căn cứ & xét duyệt'
    : claim.status === 'APPROVED'
      ? 'Theo dõi nhận đồ'
      : 'Xem hồ sơ';

  return (
    <Link
      className={`claim-group-row ${claim.status === 'PENDING' ? 'is-pending' : ''}`}
      to={claimDetailPath(claim.postId, claim.id)}
      state={{ claimListReturn: location.pathname + location.search }}
    >
      {showPost ? (
        <ClaimPostThumbnail url={claim.postThumbnailUrl} title={claim.postTitle} />
      ) : (
        <span className="claim-row-avatar" aria-hidden="true"><UserRound size={21} /></span>
      )}
      <div className="claim-row-content">
        {showPost && <strong className="claim-row-post-title">{claim.postTitle}</strong>}
        <div className="claim-group-row-heading">
          <strong>
            {incoming
              ? claim.claimantName
              : claim.claimType === 'OFFICE'
                ? 'Văn phòng tiếp nhận'
                : `Người nhặt: ${claim.finderName}`}
          </strong>
          <span className={`claim-status claim-status-${claim.status}`}>
            {claim.status === 'PENDING' && claim.claimType === 'P2P'
              ? (incoming ? 'Chờ bạn xét duyệt' : 'Chờ người nhặt phản hồi')
              : CLAIM_STATUS[claim.status]}
          </span>
        </div>
        {claim.descriptionPreview && <p className="workflow-preview">{claim.descriptionPreview}</p>}
        {claim.closeReason === 'ITEM_RETURNED' && <p className="claims-muted">Đã đóng vì bài được trao trả.</p>}
        <p className="claim-row-date">
          <Clock3 size={14} aria-hidden="true" />
          <time dateTime={claim.createdAt}>{claimDate(claim.createdAt)}</time>
        </p>
      </div>
      <span className="claim-row-action">
        {actionLabel}
        <ArrowUpRight size={16} aria-hidden="true" />
      </span>
    </Link>
  );
}

export function ClaimRows({ claims, incoming, collapseHistory = true, latestOnly = false, showPost = false }) {
  const sorted = [...claims].sort((a, b) => (Date.parse(b.createdAt) - Date.parse(a.createdAt)) || b.id - a.id);
  const active = latestOnly
    ? sorted.slice(0, 1)
    : sorted
      .filter(c => !HISTORY_STATUSES.includes(c.status))
      .sort((a, b) => Number(b.status === 'PENDING') - Number(a.status === 'PENDING'));
  const activeIds = new Set(active.map(c => c.id));
  const history = sorted.filter(c => !activeIds.has(c.id));

  if (!collapseHistory) {
    return (
      <div className="claim-row-list">
        {sorted.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
      </div>
    );
  }

  return (
    <div className="claim-row-list">
      {active.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
      {history.length > 0 && (
        <details className="claim-group-history">
          <summary>
            <History size={17} aria-hidden="true" />
            <span>Lịch sử yêu cầu</span>
            <small>{history.length} hồ sơ</small>
            <ChevronDown size={17} aria-hidden="true" />
          </summary>
          <div className="claim-row-list">
            {history.map(claim => <ClaimRow key={claim.id} claim={claim} incoming={incoming} showPost={showPost} />)}
          </div>
        </details>
      )}
    </div>
  );
}
