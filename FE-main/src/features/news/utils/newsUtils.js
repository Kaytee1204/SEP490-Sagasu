export const NEWS_CATEGORIES = [
  { key: 'UPDATE', label: 'Cập nhật hệ thống', shortLabel: 'Cập nhật' },
  { key: 'FEATURE', label: 'Tính năng mới', shortLabel: 'Tính năng' },
  { key: 'FEEDBACK', label: 'Cảm nhận & Phản hồi', shortLabel: 'Cảm nhận' },
  { key: 'GUIDE', label: 'Hướng dẫn', shortLabel: 'Hướng dẫn' },
  { key: 'ANNOUNCEMENT', label: 'Thông báo', shortLabel: 'Thông báo' },
];

export const NEWS_CATEGORY_META = {
  UPDATE: {
    label: 'Cập nhật hệ thống',
    shortLabel: 'Cập nhật',
    gradient: 'linear-gradient(135deg, #e6f6f0 0%, #c8ece0 55%, #a7e0ce 100%)',
    accent: '#0f766e',
    softBg: '#e6f6f0',
  },
  FEATURE: {
    label: 'Tính năng mới',
    shortLabel: 'Tính năng',
    gradient: 'linear-gradient(135deg, #eef2ff 0%, #d9e4ff 55%, #c2d4ff 100%)',
    accent: '#1d4ed8',
    softBg: '#eef2ff',
  },
  FEEDBACK: {
    label: 'Cảm nhận & Phản hồi',
    shortLabel: 'Cảm nhận',
    gradient: 'linear-gradient(135deg, #fff1f2 0%, #ffe4e6 55%, #fecdd3 100%)',
    accent: '#be123c',
    softBg: '#fff1f2',
  },
  GUIDE: {
    label: 'Hướng dẫn',
    shortLabel: 'Hướng dẫn',
    gradient: 'linear-gradient(135deg, #fef3c7 0%, #fde68a 55%, #fcd34d 100%)',
    accent: '#b45309',
    softBg: '#fef3c7',
  },
  ANNOUNCEMENT: {
    label: 'Thông báo',
    shortLabel: 'Thông báo',
    gradient: 'linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 55%, #cbd5e1 100%)',
    accent: '#334155',
    softBg: '#f1f5f9',
  },
};

export function getNewsCategoryMeta(category) {
  return NEWS_CATEGORY_META[category] || NEWS_CATEGORY_META.UPDATE;
}

export function formatNewsRelativeTime(isoString) {
  if (!isoString) return 'Vừa xong';
  const time = Date.parse(isoString);
  if (Number.isNaN(time)) return '';
  const diffSec = Math.max(0, Math.floor((Date.now() - time) / 1000));
  if (diffSec < 60) return 'Vừa xong';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${diffMin} phút trước`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour} giờ trước`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 30) return `${diffDay} ngày trước`;
  return new Date(time).toLocaleDateString('vi-VN');
}

export function formatNewsFullDate(isoString) {
  if (!isoString) return '';
  const time = Date.parse(isoString);
  if (Number.isNaN(time)) return '';
  return new Date(time).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
