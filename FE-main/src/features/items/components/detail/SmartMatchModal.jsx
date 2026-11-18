import { useState, useEffect } from 'react';
import {
  X,
  SearchCheck,
  MapPin,
  Calendar,
  ExternalLink,
  ArrowRight,
  ChevronDown,
  ChevronUp,
  AlertCircle,
  Trophy,
  Award,
  Sparkles,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { getSmartMatches } from '../../api/postApi';
import SavePostButton from '../saved-posts/SavePostButton';

function getRankTheme(rank, score) {
  if (rank === 1) {
    return {
      rankLabel: 'TOP 1 • Đề xuất tốt nhất',
      rankGradient: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
      rankShadow: '0 4px 12px rgba(245, 158, 11, 0.35)',
      cardBorder: '2px solid #f59e0b',
      cardBg: 'linear-gradient(180deg, #fffbeb 0%, #ffffff 52%)',
      cardShadow: '0 10px 24px -6px rgba(245, 158, 11, 0.22)',
      accentColor: '#d97706',
      scoreBg: '#fef3c7',
      scoreBorder: '#f59e0b',
      scoreText: '#b45309',
      scoreLabel: `${score}% Khớp Hạng 1`,
      thumbBorder: '2px solid #fbbf24',
      ctaBg: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)',
      ctaBorder: '#d97706',
    };
  }
  if (rank === 2) {
    return {
      rankLabel: 'TOP 2 • Tương đồng cao',
      rankGradient: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
      rankShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
      cardBorder: '2px solid #818cf8',
      cardBg: 'linear-gradient(180deg, #eef2ff 0%, #ffffff 52%)',
      cardShadow: '0 8px 20px -6px rgba(99, 102, 241, 0.18)',
      accentColor: '#4f46e5',
      scoreBg: '#e0e7ff',
      scoreBorder: '#818cf8',
      scoreText: '#3730a3',
      scoreLabel: `${score}% Khớp Hạng 2`,
      thumbBorder: '2px solid #a5b4fc',
      ctaBg: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)',
      ctaBorder: '#4338ca',
    };
  }
  if (rank === 3) {
    return {
      rankLabel: 'TOP 3 • Khả năng khớp',
      rankGradient: 'linear-gradient(135deg, #ea580c 0%, #be123c 100%)',
      rankShadow: '0 4px 12px rgba(234, 88, 12, 0.28)',
      cardBorder: '2px solid #fb923c',
      cardBg: 'linear-gradient(180deg, #fff7ed 0%, #ffffff 52%)',
      cardShadow: '0 8px 20px -6px rgba(234, 88, 12, 0.16)',
      accentColor: '#ea580c',
      scoreBg: '#ffedd5',
      scoreBorder: '#fb923c',
      scoreText: '#c2410c',
      scoreLabel: `${score}% Khớp Hạng 3`,
      thumbBorder: '2px solid #fdba74',
      ctaBg: 'linear-gradient(135deg, #ea580c 0%, #c2410c 100%)',
      ctaBorder: '#c2410c',
    };
  }
  if (rank === 4) {
    return {
      rankLabel: 'TOP 4 • Gợi ý mở rộng',
      rankGradient: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
      rankShadow: '0 3px 8px rgba(2, 132, 199, 0.22)',
      cardBorder: '1.5px solid #7dd3fc',
      cardBg: 'linear-gradient(180deg, #f0f9ff 0%, #ffffff 52%)',
      cardShadow: '0 4px 14px -4px rgba(2, 132, 199, 0.12)',
      accentColor: '#0284c7',
      scoreBg: '#e0f2fe',
      scoreBorder: '#7dd3fc',
      scoreText: '#0369a1',
      scoreLabel: `${score}% Tương đồng`,
      thumbBorder: '1.5px solid #bae6fd',
      ctaBg: '#0284c7',
      ctaBorder: '#0284c7',
    };
  }
  return {
    rankLabel: `HẠNG #${rank} • Tiềm năng`,
    rankGradient: 'linear-gradient(135deg, #475569 0%, #334155 100%)',
    rankShadow: '0 2px 6px rgba(51, 65, 85, 0.18)',
    cardBorder: '1px solid #cbd5e1',
    cardBg: '#ffffff',
    cardShadow: '0 2px 8px rgba(15, 23, 42, 0.05)',
    accentColor: '#0d9488',
    scoreBg: '#f1f5f9',
    scoreBorder: '#cbd5e1',
    scoreText: '#334155',
    scoreLabel: `${score}% Tiềm năng`,
    thumbBorder: '1px solid #e2e8f0',
    ctaBg: '#0d9488',
    ctaBorder: '#0d9488',
  };
}

export default function SmartMatchModal({ isOpen, onClose, post }) {
  const [state, setState] = useState({
    loading: false,
    matches: [],
    error: '',
    loadedKey: null,
  });
  const [expandedId, setExpandedId] = useState(null);

  const requestKey = isOpen && post?.id ? `${post.id}` : null;
  const isLoading = Boolean(requestKey) && (state.loading || state.loadedKey !== requestKey);

  useEffect(() => {
    if (!requestKey) return;

    let isMounted = true;
    getSmartMatches(post.id)
      .then((data) => {
        if (isMounted) {
          setState({
            loading: false,
            matches: Array.isArray(data) ? data : [],
            error: '',
            loadedKey: requestKey,
          });
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

  if (!isOpen) return null;

  const isLostPost = post.postType === 'LOST';
  const targetLabel = isLostPost ? 'đồ nhặt được (FOUND)' : 'đồ thất lạc (LOST)';
  const matches = state.matches;
  const error = state.error;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1050,
        background: 'rgba(15, 23, 42, 0.55)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '20px',
      }}
      onClick={onClose}
    >
      <div
        style={{
          background: '#ffffff',
          borderRadius: '18px',
          width: '100%',
          maxWidth: '760px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.28)',
          border: '1px solid #e2e8f0',
          overflow: 'hidden',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '20px 24px',
            borderBottom: '1px solid #f1f5f9',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            background: '#ffffff',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: 42,
                height: 42,
                borderRadius: '11px',
                background: 'linear-gradient(135deg, #f0fdfa 0%, #ccfbf1 100%)',
                color: '#0d9488',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: '1px solid #99f6e4',
              }}
            >
              <SearchCheck size={22} />
            </div>
            <div>
              <h3
                style={{
                  margin: 0,
                  fontSize: '1.15rem',
                  fontWeight: 800,
                  color: '#0f172a',
                  letterSpacing: '-0.01em',
                }}
              >
                Bảng Xếp Hạng AI Smart Match
              </h3>
              <p style={{ margin: '2px 0 0', fontSize: '0.82rem', color: '#64748b' }}>
                Đối sánh đa phương thức & xếp hạng màu sắc theo Top 1, Top 2, Top 3 với các bài đăng {targetLabel}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            style={{
              background: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: '8px',
              width: 34,
              height: 34,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#64748b',
              cursor: 'pointer',
            }}
          >
            <X size={16} />
          </button>
        </div>

        {/* Tier Legend Bar */}
        {!isLoading && !error && matches.length > 0 && (
          <div
            style={{
              padding: '10px 24px',
              background: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: '10px',
              fontSize: '0.74rem',
              fontWeight: 700,
            }}
          >
            <span style={{ color: '#64748b', fontWeight: 600 }}>Phân hạng AI:</span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '2px 9px', borderRadius: 999, background: '#fef3c7', color: '#b45309', border: '1px solid #f59e0b' }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#f59e0b' }} /> Top 1 (Vàng Hoàng Kim)
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '2px 9px', borderRadius: 999, background: '#e0e7ff', color: '#3730a3', border: '1px solid #818cf8' }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#6366f1' }} /> Top 2 (Tím Indigo)
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '2px 9px', borderRadius: 999, background: '#ffedd5', color: '#c2410c', border: '1px solid #fb923c' }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#ea580c' }} /> Top 3 (Đồng Ánh Cam)
            </span>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '2px 9px', borderRadius: 999, background: '#e0f2fe', color: '#0369a1', border: '1px solid #7dd3fc' }}>
              <span style={{ width: 7, height: 7, borderRadius: '50%', background: '#0284c7' }} /> Top 4+ (Xanh Lam / Xám)
            </span>
          </div>
        )}

        {/* Content Body */}
        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
          {isLoading && (
            <div style={{ textAlign: 'center', padding: '48px 20px', color: '#64748b' }}>
              <div
                style={{
                  width: 36,
                  height: 36,
                  border: '3px solid #e2e8f0',
                  borderTopColor: '#0d9488',
                  borderRadius: '50%',
                  animation: 'spin 0.8s linear infinite',
                  margin: '0 auto 14px',
                }}
              />
              <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
              <div style={{ fontWeight: 600, fontSize: '0.92rem', color: '#1e293b' }}>
                Đang đối sánh đặc điểm hình ảnh và mô tả...
              </div>
              <div style={{ fontSize: '0.8rem', color: '#94a3b8', marginTop: '4px' }}>
                Hệ thống phân tích hình thể, màu sắc, vị trí và thời gian
              </div>
            </div>
          )}

          {!isLoading && error && (
            <div
              style={{
                padding: '16px',
                borderRadius: '10px',
                background: '#fef2f2',
                border: '1px solid #fecaca',
                color: '#dc2626',
                fontSize: '0.85rem',
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
              }}
            >
              <AlertCircle size={18} />
              <span>{error}</span>
            </div>
          )}

          {!isLoading && !error && matches.length === 0 && (
            <div
              style={{
                textAlign: 'center',
                padding: '48px 24px',
                background: '#f8fafc',
                borderRadius: '12px',
                border: '1px dashed #cbd5e1',
              }}
            >
              <SearchCheck size={36} style={{ color: '#94a3b8', margin: '0 auto 12px' }} />
              <div style={{ fontWeight: 600, fontSize: '0.95rem', color: '#334155' }}>
                Chưa có bài đăng nào có độ tương đồng cao
              </div>
              <p style={{ margin: '6px 0 0', fontSize: '0.82rem', color: '#64748b', maxWidth: '420px', marginInline: 'auto' }}>
                Hệ thống sẽ tự động đối soát và gợi ý khi có thêm bài đăng {targetLabel} mới được cập nhật.
              </p>
            </div>
          )}

          {!isLoading && !error && matches.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ fontSize: '0.82rem', color: '#64748b' }}>
                Tìm thấy <strong style={{ color: '#0f172a' }}>{matches.length} bài đăng triển vọng nhất</strong> được xếp hạng từ cao xuống thấp:
              </div>

              {matches.map((item, idx) => {
                const rank = idx + 1;
                const theme = getRankTheme(rank, item.matchScore);
                const isExpanded = expandedId === item.id;
                const eventDate = item.eventAt
                  ? new Date(item.eventAt).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
                  : 'Chưa cập nhật';
                const RankIcon = rank === 1 ? Trophy : rank <= 3 ? Award : Sparkles;

                return (
                  <div
                    key={item.id}
                    style={{
                      position: 'relative',
                      background: theme.cardBg,
                      border: theme.cardBorder,
                      borderRadius: '14px',
                      padding: '16px',
                      boxShadow: theme.cardShadow,
                      transition: 'transform 150ms ease, box-shadow 150ms ease',
                    }}
                  >
                    {/* Top Rank Ribbon + Score Header */}
                    <div
                      style={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: '8px',
                        marginBottom: '12px',
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                            background: theme.rankGradient,
                            color: '#ffffff',
                            fontSize: '0.73rem',
                            fontWeight: 800,
                            padding: '4px 11px',
                            borderRadius: '999px',
                            boxShadow: theme.rankShadow,
                            letterSpacing: '0.02em',
                          }}
                        >
                          <RankIcon size={13} />
                          <span>{theme.rankLabel}</span>
                        </span>

                        <span
                          style={{
                            fontSize: '0.7rem',
                            fontWeight: 700,
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: item.postType === 'FOUND' ? '#ecfdf5' : '#eff6ff',
                            color: item.postType === 'FOUND' ? '#059669' : '#2563eb',
                            border: `1px solid ${item.postType === 'FOUND' ? '#a7f3d0' : '#bfdbfe'}`,
                          }}
                        >
                          {item.postType === 'FOUND' ? 'NHẶT ĐƯỢC' : 'THẤT LẠC'}
                        </span>
                      </div>

                      <span
                        style={{
                          fontSize: '0.8rem',
                          fontWeight: 800,
                          padding: '4px 12px',
                          borderRadius: '999px',
                          background: theme.scoreBg,
                          border: `1.5px solid ${theme.scoreBorder}`,
                          color: theme.scoreText,
                        }}
                      >
                        {theme.scoreLabel}
                      </span>
                    </div>

                    <div style={{ display: 'flex', gap: '16px' }}>
                      {/* Image Thumbnail */}
                      <div
                        style={{
                          position: 'relative',
                          width: 92,
                          height: 92,
                          borderRadius: '10px',
                          overflow: 'hidden',
                          background: '#f1f5f9',
                          flexShrink: 0,
                          border: theme.thumbBorder,
                        }}
                      >
                        {item.imageUrl ? (
                          <img
                            src={item.imageUrl}
                            alt={item.title}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                          />
                        ) : (
                          <div
                            style={{
                              width: '100%',
                              height: '100%',
                              display: 'flex',
                              alignItems: 'center',
                              justifyContent: 'center',
                              color: '#94a3b8',
                              fontSize: '0.75rem',
                            }}
                          >
                            Không có ảnh
                          </div>
                        )}
                        <div
                          style={{
                            position: 'absolute',
                            top: 6,
                            left: 6,
                            background: theme.rankGradient,
                            color: '#fff',
                            fontSize: '0.68rem',
                            fontWeight: 800,
                            padding: '1px 7px',
                            borderRadius: '6px',
                            boxShadow: '0 2px 6px rgba(0,0,0,0.25)',
                          }}
                        >
                          #{rank}
                        </div>
                      </div>

                      {/* Info & Details */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <h4
                          style={{
                            margin: '0 0 6px',
                            fontSize: '1.02rem',
                            fontWeight: 800,
                            color: '#0f172a',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                          }}
                        >
                          {item.title}
                        </h4>

                        <div
                          style={{
                            display: 'flex',
                            flexWrap: 'wrap',
                            alignItems: 'center',
                            gap: '12px',
                            fontSize: '0.8rem',
                            color: '#475569',
                            margin: '4px 0 6px',
                          }}
                        >
                          {item.locationText && (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                              <MapPin size={13} style={{ color: theme.accentColor }} /> {item.locationText}
                            </span>
                          )}
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <Calendar size={13} style={{ color: theme.accentColor }} /> {eventDate}
                          </span>
                        </div>

                        {item.matchReason && (
                          <div
                            style={{
                              fontSize: '0.78rem',
                              color: theme.scoreText,
                              fontWeight: 600,
                              margin: '6px 0 0',
                              background: 'rgba(255,255,255,0.75)',
                              padding: '5px 9px',
                              borderRadius: '7px',
                              border: `1px solid ${theme.scoreBorder}`,
                            }}
                          >
                            ✓ {item.matchReason}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Breakdown accordion toggle */}
                    {item.matchBreakdown && (
                      <div style={{ marginTop: '12px', borderTop: '1px solid rgba(148,163,184,0.22)', paddingTop: '10px' }}>
                        <button
                          type="button"
                          onClick={() => setExpandedId(isExpanded ? null : item.id)}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: theme.scoreText,
                            fontSize: '0.76rem',
                            fontWeight: 700,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            padding: '2px 0',
                          }}
                        >
                          {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          {isExpanded ? 'Thu gọn tiêu chí chi tiết' : 'Xem chi tiết điểm thành phần AI'}
                        </button>

                        {isExpanded && (
                          <div
                            style={{
                              marginTop: '8px',
                              display: 'grid',
                              gridTemplateColumns: 'repeat(auto-fit, minmax(135px, 1fr))',
                              gap: '10px',
                              background: '#ffffff',
                              padding: '10px 12px',
                              borderRadius: '10px',
                              border: '1px solid #e2e8f0',
                              fontSize: '0.75rem',
                            }}
                          >
                            {[
                              { label: 'Hình ảnh', val: item.matchBreakdown.visual ?? 0 },
                              { label: 'Màu sắc', val: item.matchBreakdown.color ?? 0 },
                              { label: 'Mô tả', val: item.matchBreakdown.text ?? 0 },
                              { label: 'Địa điểm & Ngày', val: item.matchBreakdown.spatioTemporal ?? 0 },
                            ].map((crit) => (
                              <div key={crit.label}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                                  <span style={{ color: '#64748b' }}>{crit.label}</span>
                                  <strong style={{ color: theme.scoreText }}>{crit.val}%</strong>
                                </div>
                                <div style={{ height: 5, borderRadius: 99, background: '#f1f5f9', overflow: 'hidden' }}>
                                  <div
                                    style={{
                                      width: `${Math.min(100, Math.max(0, crit.val))}%`,
                                      height: '100%',
                                      background: theme.rankGradient,
                                      borderRadius: 99,
                                    }}
                                  />
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}

                    {/* Action buttons */}
                    <div style={{ display: 'flex', justifyContent: 'flex-end', flexWrap: 'wrap', gap: '8px', marginTop: '12px' }}>
                      <SavePostButton postId={item.id} />
                      <Link
                        to={`/chi-tiet/${item.id}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '7px 14px',
                          borderRadius: '8px',
                          background: '#ffffff',
                          border: '1px solid #cbd5e1',
                          color: '#334155',
                          fontSize: '0.8rem',
                          fontWeight: 600,
                          textDecoration: 'none',
                        }}
                      >
                        <span>Xem chi tiết</span>
                        <ExternalLink size={13} />
                      </Link>

                      {item.postType === 'FOUND' && (
                        <Link
                          to={`/xac-minh/${item.id}`}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                            padding: '7px 14px',
                            borderRadius: '8px',
                            background: theme.ctaBg,
                            border: `1px solid ${theme.ctaBorder}`,
                            color: '#ffffff',
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            textDecoration: 'none',
                            boxShadow: theme.rankShadow,
                          }}
                        >
                          <span>Xác minh nhận đồ</span>
                          <ArrowRight size={13} />
                        </Link>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Footer */}
        <div
          style={{
            padding: '14px 24px',
            borderTop: '1px solid #f1f5f9',
            display: 'flex',
            justifyContent: 'flex-end',
            background: '#ffffff',
          }}
        >
          <button
            type="button"
            onClick={onClose}
            style={{
              padding: '8px 18px',
              borderRadius: '8px',
              background: '#f1f5f9',
              border: 'none',
              color: '#475569',
              fontSize: '0.85rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Đóng
          </button>
        </div>
      </div>
    </div>
  );
}
