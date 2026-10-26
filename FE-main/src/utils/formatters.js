// src/utils/formatters.js
// Shared formatting utilities — date, status labels, trust score, etc.

/**
 * Format an ISO date string to Vietnamese locale date
 * @param {string} isoDate - e.g. '2025-09-18T14:30:00Z'
 * @returns {string} - e.g. '18/09/2025'
 */
export function formatDate(isoDate) {
  if (!isoDate) return '';
  return new Date(isoDate).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  });
}

/**
 * Format an ISO date string to Vietnamese locale time
 * @param {string} isoDate
 * @returns {string} - e.g. '14:30'
 */
export function formatTime(isoDate) {
  if (!isoDate) return '';
  return new Date(isoDate).toLocaleTimeString('vi-VN', {
    hour: '2-digit', minute: '2-digit'
  });
}

/**
 * Get display config for an item type (lost / found)
 * @param {'lost'|'found'} type
 * @returns {{ label: string, badgeClass: string }}
 */
export function getItemTypeConfig(type) {
  return type === 'found'
    ? { label: 'Nhặt được', badgeClass: 'badge-found' }
    : { label: 'Cần tìm', badgeClass: 'badge-lost' };
}

/**
 * Get display config for an item status
 * @param {'active'|'matched'|'hidden'|'resolved'} status
 * @returns {{ label: string, color: string }}
 */
export function getItemStatusConfig(status) {
  const map = {
    active:   { label: 'Đang hiển thị', color: 'var(--status-found)' },
    matched:  { label: 'Đã khớp', color: 'var(--accent)' },
    hidden:   { label: 'Đã gỡ', color: 'var(--text-muted)' },
    resolved: { label: 'Đã giải quyết', color: 'var(--status-done)' },
    discussing: { label: 'Đang trao đổi', color: 'var(--status-warning)' },
  };
  return map[status] ?? { label: status, color: 'var(--text-muted)' };
}

/**
 * Get display config for a report status
 * @param {'pending'|'resolved'|'dismissed'} status
 * @returns {{ label: string, bg: string, color: string, border: string, dot: string }}
 */
export function getReportStatusConfig(status) {
  const map = {
    resolved:  { label: 'Đã giải quyết', bg: '#ecfdf5', color: '#059669', border: '#a7f3d0', dot: '#10b981' },
    dismissed: { label: 'Đã bác bỏ',     bg: '#f1f5f9', color: '#64748b', border: '#e2e8f0', dot: '#94a3b8' },
    pending:   { label: 'Chờ xử lý',     bg: '#fff1f2', color: '#e11d48', border: '#fecdd3', dot: '#e11d48' },
  };
  return map[status] ?? map.pending;
}

/**
 * Get display config for report severity
 * @param {'critical'|'high'|'medium'} severity
 * @returns {{ label: string, bg: string, color: string, border: string }}
 */
export function getReportSeverityConfig(severity) {
  const map = {
    critical: { label: 'Khẩn cấp', bg: '#fef2f2', color: '#dc2626', border: '#fecaca' },
    high:     { label: 'Mức độ cao', bg: '#fff7ed', color: '#ea580c', border: '#fed7aa' },
    medium:   { label: 'Mức độ thường', bg: '#fefce8', color: '#ca8a04', border: '#fef08a' },
  };
  return map[severity] ?? map.medium;
}

/**
 * Get color for a trust score value
 * @param {number} score - 0 to 100
 * @returns {string} CSS color
 */
export function getTrustScoreColor(score) {
  if (score >= 80) return '#10b981';
  if (score >= 50) return '#f59e0b';
  return '#ef4444';
}

/**
 * Truncate text to a given character limit
 * @param {string} text
 * @param {number} limit
 * @returns {string}
 */
export function truncate(text, limit = 80) {
  if (!text) return '';
  return text.length > limit ? `${text.slice(0, limit)}...` : text;
}
