import { useState, useEffect, useMemo } from 'react';
import {
  X,
  Trophy,
  Crown,
  MapPin,
  Calendar,
  ExternalLink,
  ArrowRight,
  ChevronRight,
  ChevronLeft,
  ChevronUp,
  BarChart3,
  Info,
  AlertCircle,
  SearchCheck,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { getSmartMatches } from '../../api/postApi';
import SavePostButton from '../saved-posts/SavePostButton';
import './SmartMatchModal.css';

const PAGE_SIZE = 5;

function buildMetrics(breakdown) {
  if (!breakdown) return [];
  const metrics = [];
  if (breakdown.text != null) {
    metrics.push({ key: 'text', label: 'Độ tương đồng nội dung', value: Number(breakdown.text) });
  }
  if (breakdown.visual != null) {
    metrics.push({ key: 'visual', label: 'Độ tương đồng hình ảnh', value: Number(breakdown.visual) });
  }
  if (breakdown.color != null) {
    metrics.push({ key: 'color', label: 'Độ phù hợp màu sắc', value: Number(breakdown.color) });
  }
  if (breakdown.location != null) {
    metrics.push({ key: 'location', label: 'Độ phù hợp vị trí', value: Number(breakdown.location) });
  }
  if (breakdown.time != null) {
    metrics.push({ key: 'time', label: 'Độ phù hợp thời gian', value: Number(breakdown.time) });
  }
  if (breakdown.location == null && breakdown.time == null && breakdown.spatioTemporal != null) {
    metrics.push({ key: 'spatioTemporal', label: 'Vị trí & thời gian', value: Number(breakdown.spatioTemporal) });
  }
  return metrics;
}

function formatScore(val) {
  const num = Number(val ?? 0);
  if (Number.isNaN(num)) return '0.0%';
  return `${num.toFixed(1)}%`;
}

function formatDate(val) {
  if (!val) return 'Chưa cập nhật';
  return new Date(val).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' });
}

function RankBadge({ rank }) {
  if (rank === 1) {
    return (
      <span className="sm-rank sm-rank-1" aria-label="Hạng 1">
        <Crown size={15} fill="#eab308" color="#ca8a04" />
        <span>1</span>
      </span>
    );
  }
  const cls = rank === 2 ? 'sm-rank-2' : rank === 3 ? 'sm-rank-3' : 'sm-rank-other';
  return (
    <span className={`sm-rank ${cls}`} aria-label={`Hạng ${rank}`}>
      {rank}
    </span>
  );
}

export default function SmartMatchModal({ isOpen, onClose, post }) {
  const [state, setState] = useState({
    loading: false,
    matches: [],
    error: '',
    loadedKey: null,
  });
  const [expandedId, setExpandedId] = useState(null);
  const [hasInteracted, setHasInteracted] = useState(false);
  const [showDisclaimer, setShowDisclaimer] = useState(true);
  const [page, setPage] = useState(1);

  const requestKey = isOpen && post?.id ? `${post.id}` : null;
  const isLoading = Boolean(requestKey) && (state.loading || state.loadedKey !== requestKey);

  useEffect(() => {
    if (!requestKey) return;

    let isMounted = true;
    getSmartMatches(post.id)
      .then((data) => {
        if (isMounted) {
          const list = Array.isArray(data) ? data : [];
          setState({
            loading: false,
            matches: list,
            error: '',
            loadedKey: requestKey,
          });
          setPage(1);
          setHasInteracted(false);
          setExpandedId(list[0]?.id ?? null);
          setShowDisclaimer(list.length > 0);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setState({
            loading: false,
            matches: [],
            error: err.message || 'Không thể tải kết quả đối sánh vào lúc này.',
            loadedKey: requestKey,
          });
        }
      });

    return () => {
      isMounted = false;
    };
  }, [requestKey, post?.id]);

  const rankedMatches = useMemo(
    () => state.matches.map((item, idx) => ({ ...item, rank: idx + 1 })),
    [state.matches],
  );

  if (!isOpen) return null;

  const isLostPost = post.postType === 'LOST';
  const sourceTypeLabel = isLostPost ? 'LOST' : 'FOUND';
  const targetTypeLabel = isLostPost ? 'FOUND' : 'LOST';
  const total = rankedMatches.length;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const startIdx = (safePage - 1) * PAGE_SIZE;
  const pageItems = rankedMatches.slice(startIdx, startIdx + PAGE_SIZE);
  const activeExpandedId = hasInteracted ? expandedId : (safePage === 1 ? pageItems[0]?.id ?? null : expandedId);

  const toggleExpand = (id) => {
    setHasInteracted(true);
    setExpandedId(activeExpandedId === id ? null : id);
  };

  const renderRowHeader = (item, isExpanded) => (
    <button
      type="button"
      className="sm-row-trigger"
      aria-expanded={isExpanded}
      onClick={() => toggleExpand(item.id)}
    >
      <RankBadge rank={item.rank} />

      <div className="sm-thumb">
        {item.imageUrl ? (
          <img src={item.imageUrl} alt={item.title} loading="lazy" />
        ) : (
          <span className="sm-thumb-empty">Không có ảnh</span>
        )}
      </div>

      <div className="sm-info">
        <h4 className="sm-title" title={item.title}>{item.title}</h4>
        <div className="sm-meta">
          {item.locationText && (
            <span className="sm-meta-item">
              <MapPin size={13} aria-hidden="true" />
              <span>{item.locationText}</span>
            </span>
          )}
          {item.locationText && <span className="sm-meta-sep">|</span>}
          <span className="sm-meta-item">
            <Calendar size={13} aria-hidden="true" />
            <span>{formatDate(item.eventAt)}</span>
          </span>
        </div>
      </div>

      <div className="sm-score-box">
        <span className="sm-score-val">{formatScore(item.matchScore)}</span>
        <span className="sm-score-label">Độ phù hợp</span>
      </div>

      <span className="sm-chevron" aria-hidden="true">
        {isExpanded ? <ChevronUp size={18} /> : <ChevronRight size={18} />}
      </span>
    </button>
  );

  // Split pageItems into clean standalone expanded card(s) and contiguous compact groups (matches mockup)
  const segments = [];
  for (const item of pageItems) {
    const isExpanded = activeExpandedId === item.id;
    if (isExpanded) {
      segments.push({ type: 'expanded', item });
    } else {
      const last = segments[segments.length - 1];
      if (last && last.type === 'group') last.items.push(item);
      else segments.push({ type: 'group', items: [item] });
    }
  }

  return (
    <div className="sm-backdrop" onClick={onClose}>
      <div className="sm-modal" role="dialog" aria-modal="true" aria-labelledby="sm-modal-title" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <header className="sm-header">
          <div className="sm-header-left">
            <div className="sm-header-icon" aria-hidden="true">
              <Trophy size={21} />
            </div>
            <div className="sm-header-text">
              <h3 id="sm-modal-title">Bảng xếp hạng kết quả tìm kiếm</h3>
              <p>
                Các bài đăng {targetTypeLabel} được sắp xếp theo mức độ tương đồng với bài đăng {sourceTypeLabel}.
              </p>
            </div>
          </div>
          <button type="button" className="sm-close-btn" onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </header>

        {/* Legend Strip */}
        {!isLoading && !state.error && total > 0 && (
          <div className="sm-legend-wrap">
            <div className="sm-legend" aria-label="Chú thích thứ hạng">
              <span className="sm-legend-item"><span className="sm-dot sm-dot-1" />Top 1 (Vàng)</span>
              <span className="sm-legend-item"><span className="sm-dot sm-dot-2" />Top 2 (Tím)</span>
              <span className="sm-legend-item"><span className="sm-dot sm-dot-3" />Top 3 (Cam)</span>
              <span className="sm-legend-item"><span className="sm-dot sm-dot-4" />Top 4+</span>
            </div>
          </div>
        )}

        {/* Body */}
        <div className="sm-body">
          {isLoading && (
            <div className="sm-state-box">
              <div className="sm-spinner" />
              <div style={{ fontWeight: 650, fontSize: '0.94rem', color: '#1e293b' }}>
                Đang đối chiếu hình ảnh và mô tả bài đăng…
              </div>
              <div style={{ fontSize: '0.82rem', color: '#94a3b8', marginTop: 4 }}>
                Đang so sánh nội dung, hình ảnh, màu sắc, vị trí và thời gian
              </div>
            </div>
          )}

          {!isLoading && state.error && (
            <div className="sm-error-box" role="alert">
              <AlertCircle size={18} />
              <span>{state.error}</span>
            </div>
          )}

          {!isLoading && !state.error && total === 0 && (
            <div className="sm-state-box" style={{ background: '#f8fafc', borderRadius: 14, border: '1px dashed #cbd5e1' }}>
              <SearchCheck size={34} style={{ color: '#94a3b8', margin: '0 auto 10px' }} />
              <div style={{ fontWeight: 650, fontSize: '0.95rem', color: '#334155' }}>
                Chưa tìm thấy bài đăng tương đồng
              </div>
              <p style={{ margin: '6px auto 0', fontSize: '0.82rem', color: '#64748b', maxWidth: 420 }}>
                Hệ thống sẽ tự động cập nhật gợi ý khi có thêm bài đăng {targetTypeLabel} phù hợp.
              </p>
            </div>
          )}

          {!isLoading && !state.error && total > 0 && (
            <>
              <div className="sm-toolbar">
                <div className="sm-count-text">
                  Tìm thấy <strong>{total}</strong> bài đăng phù hợp
                </div>
                <button
                  type="button"
                  className="sm-disclaimer-trigger"
                  onClick={() => setShowDisclaimer(true)}
                >
                  <Info size={14} aria-hidden="true" />
                  <span>Lưu ý về danh sách gợi ý</span>
                </button>
              </div>

              <div className="sm-list">
                {segments.map((seg, sIdx) => {
                  if (seg.type === 'group') {
                    return (
                      <div key={`group-${sIdx}`} className="sm-compact-group">
                        {seg.items.map((item) => (
                          <div key={item.id}>{renderRowHeader(item, false)}</div>
                        ))}
                      </div>
                    );
                  }

                  const item = seg.item;
                  const metrics = buildMetrics(item.matchBreakdown);

                  return (
                    <article key={item.id} className="sm-item-card is-expanded">
                      {renderRowHeader(item, true)}

                      <div className="sm-detail">
                        {metrics.length > 0 && (
                          <div className="sm-breakdown-box">
                            <div className="sm-breakdown-head">
                              <span className="sm-breakdown-title">
                                <BarChart3 size={15} aria-hidden="true" />
                                Chi tiết điểm đánh giá
                              </span>
                              {item.matchReason && (
                                <span className="sm-breakdown-reason">{item.matchReason}</span>
                              )}
                            </div>

                            <div className="sm-metrics-grid">
                              {metrics.map((metric) => (
                                <div key={metric.key} className="sm-metric-col">
                                  <span className="sm-metric-name">{metric.label}</span>
                                  <div className="sm-metric-bottom">
                                    <span className="sm-metric-val">{formatScore(metric.value)}</span>
                                    <div className="sm-bar-track">
                                      <div
                                        className="sm-bar-fill"
                                        style={{ width: `${Math.min(100, Math.max(0, metric.value))}%` }}
                                      />
                                    </div>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        <div className="sm-detail-footer">
                          <button
                            type="button"
                            className="sm-collapse-btn"
                            onClick={() => toggleExpand(item.id)}
                          >
                            <ChevronUp size={15} aria-hidden="true" />
                            Thu gọn chi tiết
                          </button>

                          <div className="sm-detail-actions">
                            <SavePostButton postId={item.id} />
                            <Link
                              to={`/chi-tiet/${item.id}`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="sm-btn-outline"
                            >
                              Xem chi tiết
                              <ExternalLink size={13} aria-hidden="true" />
                            </Link>
                            {item.postType === 'FOUND' && (
                              <Link to={`/xac-minh/${item.id}`} className="sm-btn-primary">
                                Xác minh nhận đồ
                                <ArrowRight size={13} aria-hidden="true" />
                              </Link>
                            )}
                          </div>
                        </div>
                      </div>
                    </article>
                  );
                })}
              </div>
            </>
          )}
        </div>

        {/* Pagination Footer */}
        {!isLoading && !state.error && total > 0 && (
          <footer className="sm-footer">
            <span className="sm-page-info">
              Hiển thị {startIdx + 1} – {Math.min(startIdx + PAGE_SIZE, total)} trong {total} kết quả
            </span>
            {totalPages > 1 ? (
              <nav className="sm-pagination" aria-label="Phân trang kết quả">
                <button
                  type="button"
                  className="sm-page-btn"
                  disabled={safePage <= 1}
                  onClick={() => setPage(safePage - 1)}
                  aria-label="Trang trước"
                >
                  <ChevronLeft size={15} />
                </button>
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
                  <button
                    key={p}
                    type="button"
                    className={`sm-page-btn ${p === safePage ? 'active' : ''}`}
                    aria-current={p === safePage ? 'page' : undefined}
                    onClick={() => setPage(p)}
                  >
                    {p}
                  </button>
                ))}
                <button
                  type="button"
                  className="sm-page-btn"
                  disabled={safePage >= totalPages}
                  onClick={() => setPage(safePage + 1)}
                  aria-label="Trang sau"
                >
                  <ChevronRight size={15} />
                </button>
              </nav>
            ) : (
              <button type="button" className="sm-btn-outline" onClick={onClose}>
                Đóng
              </button>
            )}
          </footer>
        )}
        {/* AI Suggestion Disclaimer Popup */}
        {!isLoading && !state.error && total > 0 && showDisclaimer && (
          <div className="sm-disclaimer-overlay" onClick={() => setShowDisclaimer(false)}>
            <div
              className="sm-disclaimer-popup"
              role="alertdialog"
              aria-modal="true"
              aria-labelledby="sm-disclaimer-title"
              aria-describedby="sm-disclaimer-desc"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="sm-disclaimer-icon" aria-hidden="true">
                <Info size={20} />
              </div>
              <h4 id="sm-disclaimer-title">Lưu ý về danh sách gợi ý</h4>
              <p id="sm-disclaimer-desc">
                Danh sách này chỉ mang tính chất <strong>gợi ý tham khảo</strong> do hệ thống AI tự động xếp hạng và <strong>có thể xảy ra sai sót</strong>. Vui lòng kiểm tra kỹ hình ảnh, mô tả, vị trí và tự đối chiếu thực tế trước khi gửi yêu cầu nhận đồ hoặc trao trả.
              </p>
              <div className="sm-disclaimer-actions">
                <button
                  type="button"
                  className="sm-btn-primary"
                  onClick={() => setShowDisclaimer(false)}
                >
                  Tôi đã hiểu
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
