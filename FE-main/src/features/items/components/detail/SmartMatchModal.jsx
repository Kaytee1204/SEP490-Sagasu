import React, { useState, useEffect } from 'react';
import { X, SearchCheck, MapPin, Calendar, ExternalLink, ArrowRight, CheckCircle2, ChevronDown, ChevronUp, AlertCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getSmartMatches } from '../../api/postApi';

export default function SmartMatchModal({ isOpen, onClose, post }) {
  const [loading, setLoading] = useState(false);
  const [matches, setMatches] = useState([]);
  const [error, setError] = useState('');
  const [expandedId, setExpandedId] = useState(null);

  useEffect(() => {
    if (!isOpen || !post?.id) return;

    let isMounted = true;
    setLoading(true);
    setError('');

    getSmartMatches(post.id)
      .then((data) => {
        if (isMounted) {
          setMatches(Array.isArray(data) ? data : []);
        }
      })
      .catch((err) => {
        if (isMounted) {
          setError(err.message || 'Không thể tải kết quả đối sánh vào lúc này.');
        }
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isOpen, post?.id]);

  if (!isOpen) return null;

  const isLostPost = post.postType === 'LOST';
  const targetLabel = isLostPost ? 'đồ nhặt được (FOUND)' : 'đồ thất lạc (LOST)';

  const getScoreBadge = (score) => {
    if (score >= 80) {
      return {
        bg: '#ecfdf5',
        border: '#a7f3d0',
        text: '#047857',
        label: `${score}% Trùng khớp cao`,
      };
    }
    if (score >= 65) {
      return {
        bg: '#f0fdfa',
        border: '#99f6e4',
        text: '#0f766e',
        label: `${score}% Tương đồng`,
      };
    }
    return {
      bg: '#f8fafc',
      border: '#e2e8f0',
      text: '#475569',
      label: `${score}% Tiềm năng`,
    };
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1050,
        background: 'rgba(15, 23, 42, 0.5)',
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
          borderRadius: '16px',
          width: '100%',
          maxWidth: '720px',
          maxHeight: '90vh',
          display: 'flex',
          flexDirection: 'column',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
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
                width: 40,
                height: 40,
                borderRadius: '10px',
                background: '#f0fdfa',
                color: '#0d9488',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: '1px solid #ccfbf1',
              }}
            >
              <SearchCheck size={22} />
            </div>
            <div>
              <h3
                style={{
                  margin: 0,
                  fontSize: '1.15rem',
                  fontWeight: 700,
                  color: '#0f172a',
                  letterSpacing: '-0.01em',
                }}
              >
                Gợi ý đồ vật trùng khớp
              </h3>
              <p style={{ margin: '2px 0 0', fontSize: '0.82rem', color: '#64748b' }}>
                Đối sánh tự động với các bài đăng {targetLabel} đang mở
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
              width: 32,
              height: 32,
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

        {/* Content Body */}
        <div style={{ padding: '20px 24px', overflowY: 'auto', flex: 1 }}>
          {loading && (
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

          {!loading && error && (
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

          {!loading && !error && matches.length === 0 && (
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

          {!loading && !error && matches.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              <div style={{ fontSize: '0.82rem', color: '#64748b', marginBottom: '2px' }}>
                Tìm thấy <strong style={{ color: '#0f172a' }}>{matches.length} bài đăng triển vọng nhất</strong> được xếp hạng theo mức độ tương đồng:
              </div>

              {matches.map((item) => {
                const badge = getScoreBadge(item.matchScore);
                const isExpanded = expandedId === item.id;
                const eventDate = item.eventAt
                  ? new Date(item.eventAt).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
                  : 'Chưa cập nhật';

                return (
                  <div
                    key={item.id}
                    style={{
                      background: '#ffffff',
                      border: '1px solid #e2e8f0',
                      borderRadius: '12px',
                      padding: '16px',
                      transition: 'border-color 150ms, box-shadow 150ms',
                    }}
                  >
                    <div style={{ display: 'flex', gap: '16px' }}>
                      {/* Image Thumbnail */}
                      <div
                        style={{
                          width: 88,
                          height: 88,
                          borderRadius: '8px',
                          overflow: 'hidden',
                          background: '#f1f5f9',
                          flexShrink: 0,
                          border: '1px solid #e2e8f0',
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
                      </div>

                      {/* Info & Details */}
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px' }}>
                          <span
                            style={{
                              fontSize: '0.72rem',
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: '4px',
                              background: item.postType === 'FOUND' ? '#ecfdf5' : '#eff6ff',
                              color: item.postType === 'FOUND' ? '#059669' : '#2563eb',
                              border: `1px solid ${item.postType === 'FOUND' ? '#a7f3d0' : '#bfdbfe'}`,
                            }}
                          >
                            {item.postType === 'FOUND' ? 'NHẶT ĐƯỢC' : 'THẤT LẠC'}
                          </span>

                          <span
                            style={{
                              fontSize: '0.78rem',
                              fontWeight: 700,
                              padding: '3px 10px',
                              borderRadius: '20px',
                              background: badge.bg,
                              border: `1px solid ${badge.border}`,
                              color: badge.text,
                            }}
                          >
                            {badge.label}
                          </span>
                        </div>

                        <h4
                          style={{
                            margin: '6px 0 4px',
                            fontSize: '0.98rem',
                            fontWeight: 700,
                            color: '#1e293b',
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
                            color: '#64748b',
                            margin: '4px 0 6px',
                          }}
                        >
                          {item.locationText && (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                              <MapPin size={13} style={{ color: '#94a3b8' }} /> {item.locationText}
                            </span>
                          )}
                          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <Calendar size={13} style={{ color: '#94a3b8' }} /> {eventDate}
                          </span>
                        </div>

                        {item.matchReason && (
                          <div style={{ fontSize: '0.78rem', color: '#0d9488', fontWeight: 500, margin: '4px 0' }}>
                            ✓ {item.matchReason}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Breakdown accordion toggle */}
                    {item.matchBreakdown && (
                      <div style={{ marginTop: '10px', borderTop: '1px solid #f1f5f9', paddingTop: '8px' }}>
                        <button
                          type="button"
                          onClick={() => setExpandedId(isExpanded ? null : item.id)}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#64748b',
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            cursor: 'pointer',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '4px',
                            padding: '2px 0',
                          }}
                        >
                          {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          {isExpanded ? 'Thu gọn tiêu chí chi tiết' : 'Xem chi tiết các tiêu chí đối sánh'}
                        </button>

                        {isExpanded && (
                          <div
                            style={{
                              marginTop: '8px',
                              display: 'grid',
                              gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))',
                              gap: '8px',
                              background: '#f8fafc',
                              padding: '10px 12px',
                              borderRadius: '8px',
                              fontSize: '0.75rem',
                            }}
                          >
                            <div>
                              <span style={{ color: '#64748b' }}>Hình ảnh: </span>
                              <strong style={{ color: '#1e293b' }}>{item.matchBreakdown.visual ?? 0}%</strong>
                            </div>
                            <div>
                              <span style={{ color: '#64748b' }}>Màu sắc: </span>
                              <strong style={{ color: '#1e293b' }}>{item.matchBreakdown.color ?? 0}%</strong>
                            </div>
                            <div>
                              <span style={{ color: '#64748b' }}>Mô tả: </span>
                              <strong style={{ color: '#1e293b' }}>{item.matchBreakdown.text ?? 0}%</strong>
                            </div>
                            <div>
                              <span style={{ color: '#64748b' }}>Địa điểm & Ngày: </span>
                              <strong style={{ color: '#1e293b' }}>{item.matchBreakdown.spatioTemporal ?? 0}%</strong>
                            </div>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Action buttons */}
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '12px' }}>
                      <Link
                        to={`/chi-tiet/${item.id}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          padding: '7px 14px',
                          borderRadius: '6px',
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
                            borderRadius: '6px',
                            background: '#0d9488',
                            border: '1px solid #0d9488',
                            color: '#ffffff',
                            fontSize: '0.8rem',
                            fontWeight: 600,
                            textDecoration: 'none',
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
