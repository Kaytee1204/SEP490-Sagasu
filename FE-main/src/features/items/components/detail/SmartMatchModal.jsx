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
  Sparkles,
  ZoomIn,
  Images,
  ScanSearch,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import ImageViewer from '@/components/common/ImageViewer';
import { stripPostCode } from '@/services/apiClient';
import { getSmartMatches } from '../../api/postApi';
import SavePostButton from '../saved-posts/SavePostButton';
import './SmartMatchModal.css';

const PAGE_SIZE = 5;

const LOCAL_ITEM_PATTERNS = [
  { label: 'Balo / Túi xách', rx: /(balo|ba lô|túi xách|túi đeo|cặp sách|backpack|tuixach)/i },
  { label: 'Ví da / Bóp tiền', rx: /(ví|bóp|wallet|vida|bopda)/i },
  { label: 'Thẻ sinh viên / CCCD / Giấy tờ', rx: /(thẻ sinh viên|cccd|cmnd|căn cước|giấy tờ|thẻ atm|bằng lái|thesinhvien)/i },
  { label: 'Tai nghe / Hộp sạc không dây', rx: /(tai nghe|airpod|earbuds|headphone|tainghe)/i },
  { label: 'Điện thoại di động', rx: /(điện thoại|iphone|samsung|xiaomi|oppo|smartphone|dienthoai)/i },
  { label: 'Laptop / Máy tính xách tay', rx: /(laptop|macbook|máy tính xách tay|thinkpad|dell|asus)/i },
  { label: 'Củ sạc / Dây sạc / Sạc dự phòng', rx: /(sạc|adapter|dây sạc|củ sạc|cục sạc|charger)/i },
  { label: 'Kính mắt / Kính cận / Kính râm', rx: /(kính|mắt kính|glasses|matkinh)/i },
  { label: 'Chìa khóa / Móc khóa', rx: /(chìa khóa|chìa khoá|khóa xe|móc khóa|chiakhoa)/i },
  { label: 'Bình giữ nhiệt / Bình nước', rx: /(bình nước|bình giữ nhiệt|ly giữ nhiệt|cốc giữ nhiệt|bottle)/i },
  { label: 'Đồng hồ đeo tay / Smartwatch', rx: /(đồng hồ|smartwatch|apple watch|casio|dongho)/i },
  { label: 'Mũ bảo hiểm', rx: /(mũ bảo hiểm|nón bảo hiểm|helmet)/i },
  { label: 'Ô / Dù cầm tay', rx: /(ô dù|cây dù|umbrella)/i },
  { label: 'Sách / Vở / Sổ tay', rx: /(sách|giáo trình|vở|sổ tay|notebook)/i },
  { label: 'Chuột máy tính', rx: /(chuột|mouse|logitech)/i },
  { label: 'Máy tính cầm tay', rx: /(máy tính cầm tay|máy tính bỏ túi|calculator)/i },
];

const LOCAL_COLOR_PATTERNS = [
  { label: 'Màu đen', rx: /(màu đen|\bđen\b|baloden|mauden|black)/i },
  { label: 'Màu trắng', rx: /(màu trắng|\btrắng\b|mautrang|white)/i },
  { label: 'Màu nâu', rx: /(màu nâu|\bnâu\b|da bò|brown)/i },
  { label: 'Màu xám / bạc', rx: /(màu xám|màu bạc|\bxám\b|\bbạc\b|gray|silver)/i },
  { label: 'Màu xanh dương', rx: /(xanh dương|xanh lam|xanh navy|\bxanh\b|blue)/i },
  { label: 'Màu xanh lá', rx: /(xanh lá|xanh rêu|green)/i },
  { label: 'Màu đỏ', rx: /(màu đỏ|\bđỏ\b|red)/i },
  { label: 'Màu hồng', rx: /(màu hồng|\bhồng\b|pink)/i },
  { label: 'Màu vàng', rx: /(màu vàng|\bvàng\b|yellow|gold)/i },
  { label: 'Màu tím', rx: /(màu tím|\btím\b|purple)/i },
  { label: 'Màu cam', rx: /(màu cam|\bcam\b|orange)/i },
  { label: 'Màu be / kem', rx: /(màu be|màu kem|\bbe\b|beige)/i },
];

function inferDetectedItemFallback(postObj) {
  if (!postObj) return 'Đồ vật cá nhân';
  const text = `${postObj.title || ''} ${postObj.description || ''}`.trim();
  const foundItem = LOCAL_ITEM_PATTERNS.find((p) => p.rx.test(text))?.label
    || postObj.categoryName
    || postObj.category?.name
    || postObj.title
    || 'Đồ vật cá nhân';
  const foundColor = LOCAL_COLOR_PATTERNS.find((p) => p.rx.test(text))?.label || '';
  return foundColor ? `${foundItem} • ${foundColor}` : foundItem;
}

function getItemImages(item) {
  const urls = [];
  if (Array.isArray(item?.imageUrls)) {
    for (const u of item.imageUrls) {
      if (typeof u === 'string' && u.trim() && !urls.includes(u.trim())) {
        urls.push(u.trim());
      }
    }
  }
  if (typeof item?.imageUrl === 'string' && item.imageUrl.trim() && !urls.includes(item.imageUrl.trim())) {
    urls.push(item.imageUrl.trim());
  }
  return urls;
}

function buildMetrics(breakdown) {
  if (!breakdown) return [];
  const metrics = [];
  if (breakdown.visual != null) {
    metrics.push({ key: 'visual', label: 'Độ tương đồng hình ảnh', value: Number(breakdown.visual), primary: true });
  }
  if (breakdown.color != null) {
    metrics.push({ key: 'color', label: 'Độ phù hợp màu sắc', value: Number(breakdown.color), primary: true });
  }
  if (breakdown.text != null) {
    metrics.push({ key: 'text', label: 'Độ tương đồng mô tả', value: Number(breakdown.text) });
  }
  if (breakdown.crossModal != null && Number(breakdown.crossModal) > 0) {
    metrics.push({ key: 'crossModal', label: 'Ảnh khớp mô tả', value: Number(breakdown.crossModal) });
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
  // Lưu index ảnh đang chọn xem cho từng bài trong danh sách ranking (id -> index)
  const [activeImgByPost, setActiveImgByPost] = useState({});
  // Lightbox phóng to ảnh trực tiếp ngay trên màn hình hiện tại (không cần chuyển trang)
  const [lightboxState, setLightboxState] = useState(null);

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
    () =>
      state.matches.map((item, idx) => ({
        ...item,
        title: stripPostCode(item.title),
        rank: idx + 1,
      })),
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

  const openLightboxForItem = (item, startIndex = 0, e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    const urls = getItemImages(item);
    if (!urls.length) return;
    setLightboxState({
      title: item.title || 'Ảnh bài đăng',
      initialIndex: Math.min(startIndex, urls.length - 1),
      images: urls.map((url, i) => ({
        id: `${item.id}-img-${i}`,
        url,
        name: `${item.title || 'Ảnh bài đăng'} (${i + 1}/${urls.length})`,
      })),
    });
  };

  const sourceDetectedItemLabel =
    rankedMatches[0]?.sourceDetectedItem ||
    rankedMatches[0]?.matchBreakdown?.sourceDetectedItem ||
    inferDetectedItemFallback(post);

  const renderRowHeader = (item, isExpanded) => {
    const itemImages = getItemImages(item);
    const candDetected =
      item.candDetectedItem ||
      item.matchBreakdown?.candDetectedItem ||
      inferDetectedItemFallback(item);

    return (
      <div
        role="button"
        tabIndex={0}
        className="sm-row-trigger"
        aria-expanded={isExpanded}
        onClick={() => toggleExpand(item.id)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            toggleExpand(item.id);
          }
        }}
      >
        <RankBadge rank={item.rank} />

        <div
          className="sm-thumb"
          title={itemImages.length ? 'Bấm để phóng to ảnh bài đăng này' : 'Không có ảnh'}
          onClick={(e) => {
            if (itemImages.length) {
              openLightboxForItem(item, 0, e);
            }
          }}
        >
          {itemImages.length > 0 ? (
            <>
              <img src={itemImages[0]} alt={item.title} loading="lazy" />
              <span className="sm-thumb-zoom" aria-hidden="true">
                <ZoomIn size={12} />
              </span>
              {itemImages.length > 1 && (
                <span className="sm-thumb-count">{itemImages.length}</span>
              )}
            </>
          ) : (
            <span className="sm-thumb-empty">Không có ảnh</span>
          )}
        </div>

        <div className="sm-info">
          <div className="sm-title-row">
            <h4 className="sm-title" title={item.title}>{item.title}</h4>
          </div>
          {candDetected && (
            <div className="sm-ai-item-tag">
              <Sparkles size={11} aria-hidden="true" />
              <span>AI nhận diện ảnh: <strong>{candDetected}</strong></span>
            </div>
          )}
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
      </div>
    );
  };

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
              <h3 id="sm-modal-title">Bảng xếp hạng kết quả</h3>
            </div>
          </div>
          <button type="button" className="sm-close-btn" onClick={onClose} aria-label="Đóng">
            <X size={18} />
          </button>
        </header>

        {/* AI Target Item Recognition Banner */}
        <div className="sm-ai-target-banner">
          <div className="sm-ai-target-left">
            <span className="sm-ai-target-icon" aria-hidden="true">
              <ScanSearch size={17} />
            </span>
            <div className="sm-ai-target-text">
              <span className="sm-ai-target-label">AI nhận diện đồ cần tìm:</span>
              <strong className="sm-ai-target-value">{sourceDetectedItemLabel}</strong>
            </div>
          </div>
        </div>

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
                  const itemImages = getItemImages(item);
                  const currentImgIdx = Math.min(
                    activeImgByPost[item.id] ?? 0,
                    Math.max(0, itemImages.length - 1),
                  );
                  const currentImgUrl = itemImages[currentImgIdx] || null;
                  const candDetected =
                    item.candDetectedItem ||
                    item.matchBreakdown?.candDetectedItem ||
                    inferDetectedItemFallback(item);

                  return (
                    <article key={item.id} className="sm-item-card is-expanded">
                      {renderRowHeader(item, true)}

                      <div className="sm-detail">
                        {/* Xem trực tiếp hình ảnh & thông tin bài đăng ngay trong danh sách Ranking */}
                        <div className="sm-inline-preview">
                          <div className="sm-inline-media">
                            {currentImgUrl ? (
                              <div className="sm-inline-stage">
                                <img
                                  src={currentImgUrl}
                                  alt={item.title}
                                  className="sm-inline-main-img"
                                  onClick={(e) => openLightboxForItem(item, currentImgIdx, e)}
                                />
                                <button
                                  type="button"
                                  className="sm-inline-zoom-btn"
                                  onClick={(e) => openLightboxForItem(item, currentImgIdx, e)}
                                >
                                  <ZoomIn size={14} />
                                  <span>Phóng to ảnh</span>
                                </button>
                                {itemImages.length > 1 && (
                                  <div className="sm-inline-thumbs">
                                    {itemImages.map((u, idx) => (
                                      <button
                                        key={`${item.id}-thumb-${idx}`}
                                        type="button"
                                        className={`sm-inline-thumb-btn ${idx === currentImgIdx ? 'active' : ''}`}
                                        onClick={() =>
                                          setActiveImgByPost((prev) => ({ ...prev, [item.id]: idx }))
                                        }
                                        aria-label={`Xem ảnh ${idx + 1}`}
                                      >
                                        <img src={u} alt="" />
                                      </button>
                                    ))}
                                  </div>
                                )}
                              </div>
                            ) : (
                              <div className="sm-inline-no-img">Bài đăng này không có hình ảnh</div>
                            )}
                          </div>

                          <div className="sm-inline-meta-panel">
                            <div className="sm-inline-ai-box">
                              <span className="sm-inline-ai-head">
                                <Sparkles size={14} />
                                Kết quả AI nhận diện bài đăng này
                              </span>
                              <div className="sm-inline-ai-val">{candDetected}</div>
                            </div>

                            {item.description && (
                              <div className="sm-inline-desc-box">
                                <span className="sm-inline-desc-label">Mô tả bài đăng:</span>
                                <p className="sm-inline-desc-text">{item.description}</p>
                              </div>
                            )}

                            <div className="sm-inline-tags">
                              {item.categoryName && (
                                <span className="sm-inline-tag">Danh mục: <strong>{item.categoryName}</strong></span>
                              )}
                              {item.authorName && (
                                <span className="sm-inline-tag">Người đăng: <strong>{item.authorName}</strong></span>
                              )}
                            </div>
                          </div>
                        </div>

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
                            {itemImages.length > 0 && (
                              <button
                                type="button"
                                className="sm-btn-outline"
                                onClick={(e) => openLightboxForItem(item, currentImgIdx, e)}
                              >
                                <Images size={13} aria-hidden="true" />
                                Xem ảnh lớn ({itemImages.length})
                              </button>
                            )}
                            <SavePostButton postId={item.id} />
                            <Link
                              to={`/chi-tiet/${item.id}`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="sm-btn-outline"
                            >
                              Xem bài đăng (Trang riêng)
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
        {lightboxState && (
          <ImageViewer
            images={lightboxState.images}
            initialIndex={lightboxState.initialIndex}
            title={lightboxState.title}
            onClose={() => setLightboxState(null)}
          />
        )}
      </div>
    </div>
  );
}
