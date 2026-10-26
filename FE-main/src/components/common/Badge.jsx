// src/components/common/Badge.jsx
import React from 'react';

const STATUS_CONFIGS = {
  lost: {
    label: 'Thất lạc',
    className: 'bg-red-50 text-red-700 border-red-200',
  },
  found: {
    label: 'Nhặt được',
    className: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  },
  returned: {
    label: 'Đã trao trả',
    className: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  },
  pending: {
    label: 'Chờ duyệt',
    className: 'bg-amber-50 text-amber-700 border-amber-200',
  },
  active: {
    label: 'Đang hiển thị',
    className: 'bg-teal-50 text-teal-700 border-teal-200',
  },
  hidden: {
    label: 'Đã ẩn',
    className: 'bg-zinc-100 text-zinc-600 border-zinc-200',
  },
  rejected: {
    label: 'Từ chối',
    className: 'bg-rose-50 text-rose-700 border-rose-200',
  },
};

export default function Badge({
  status,
  label,
  variant = 'default',
  size = 'md',
  className = '',
  dot = false,
  children,
}) {
  const config = STATUS_CONFIGS[status?.toLowerCase()] || null;
  const displayLabel = children || label || config?.label || status;
  const colorClass = config?.className || 'bg-zinc-100 text-zinc-700 border-zinc-200';

  const sizeClass = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-xs';

  return (
    <span
      className={`inline-flex items-center gap-1.5 font-medium border rounded-full ${sizeClass} ${colorClass} ${className}`.trim()}
    >
      {dot && <span className="w-1.5 h-1.5 rounded-full bg-current shrink-0" />}
      {displayLabel}
    </span>
  );
}
