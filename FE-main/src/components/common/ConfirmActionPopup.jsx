import { useEffect, useId } from 'react';
import { createPortal } from 'react-dom';
import { AlertCircle, AlertTriangle, Check, Info, X } from 'lucide-react';

const VARIANT_CONFIG = {
  warning: {
    outerBg: 'bg-amber-50/80 shadow-[0_0_28px_rgba(245,158,11,0.22)]',
    innerBg: 'bg-amber-100/90 text-amber-500',
    Icon: AlertCircle,
  },
  danger: {
    outerBg: 'bg-rose-50/80 shadow-[0_0_28px_rgba(239,68,68,0.22)]',
    innerBg: 'bg-rose-100/90 text-rose-500',
    Icon: AlertTriangle,
  },
  success: {
    outerBg: 'bg-emerald-50/80 shadow-[0_0_28px_rgba(16,185,129,0.22)]',
    innerBg: 'bg-emerald-100/90 text-emerald-600',
    Icon: Check,
  },
  info: {
    outerBg: 'bg-sky-50/80 shadow-[0_0_28px_rgba(14,165,233,0.22)]',
    innerBg: 'bg-sky-100/90 text-sky-500',
    Icon: Info,
  },
};

export default function ConfirmActionPopup({
  open = true,
  variant = 'warning',
  title = 'Bạn có chắc chắn?',
  description,
  confirmLabel = 'Xác nhận',
  cancelLabel = 'Hủy bỏ',
  busy = false,
  error = '',
  children,
  onConfirm,
  onClose,
  inline = false,
}) {
  const titleId = useId();
  const descId = useId();

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && !busy) {
        e.preventDefault();
        e.stopPropagation();
        onClose?.();
      }
    };
    window.addEventListener('keydown', handleKeyDown, true);
    return () => window.removeEventListener('keydown', handleKeyDown, true);
  }, [open, busy, onClose]);

  if (!open) return null;

  const cfg = VARIANT_CONFIG[variant] || VARIANT_CONFIG.warning;
  const IconComponent = cfg.Icon;

  const handleClose = (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    if (!busy) {
      onClose?.();
    }
  };

  const handleConfirm = (e) => {
    if (e) {
      e.preventDefault();
      e.stopPropagation();
    }
    if (!busy) {
      onConfirm?.();
    }
  };

  const modalNode = (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      aria-describedby={description ? descId : undefined}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          handleClose(e);
        }
      }}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 10050,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '16px',
        backgroundColor: 'rgba(15, 23, 42, 0.48)',
        backdropFilter: 'blur(2px)',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-[390px] rounded-[22px] border border-slate-100 bg-white px-6 pb-6 pt-7 text-center shadow-[0_24px_60px_rgba(15,23,42,0.18)]"
        style={{
          backgroundColor: '#ffffff',
          fontFamily: 'var(--font-body)',
        }}
      >
        {/* Top-right close X — isolated via Portal so parent modal CSS never breaks it */}
        <button
          type="button"
          disabled={busy}
          onClick={handleClose}
          aria-label="Đóng"
          style={{
            position: 'absolute',
            top: '14px',
            right: '14px',
            zIndex: 10,
            width: '32px',
            height: '32px',
            padding: 0,
            border: 'none',
            borderRadius: '8px',
            backgroundColor: 'transparent',
            color: '#64748b',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: busy ? 'not-allowed' : 'pointer',
          }}
          className="transition-colors hover:!bg-slate-100 hover:!text-slate-800 disabled:opacity-50"
        >
          <X size={18} strokeWidth={2.2} />
        </button>

        {/* Glowing circular icon badge */}
        <div className={`mx-auto flex h-14 w-14 items-center justify-center rounded-full ${cfg.outerBg}`}>
          <div className={`flex h-10 w-10 items-center justify-center rounded-full ${cfg.innerBg}`}>
            <IconComponent size={20} strokeWidth={2.3} />
          </div>
        </div>

        {/* Title */}
        <h3
          id={titleId}
          className="mt-4 text-[18.5px] font-bold leading-snug text-slate-900"
          style={{ color: '#0f172a' }}
        >
          {title}
        </h3>

        {/* Description */}
        {description && (
          <p
            id={descId}
            className="mt-2 text-[14px] leading-relaxed text-slate-500"
          >
            {description}
          </p>
        )}

        {/* Custom children */}
        {children && <div className="mt-3 text-left">{children}</div>}

        {/* Error alert */}
        {error && (
          <p
            role="alert"
            className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-[13px] font-medium text-rose-700"
          >
            {error}
          </p>
        )}

        {/* Side-by-side action buttons */}
        <div className="mt-6 grid grid-cols-2 gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={handleConfirm}
            className="inline-flex items-center justify-center rounded-xl bg-[#111827] px-4 py-2.5 text-[14px] font-semibold text-white shadow-xs transition-all hover:bg-[#1f2937] disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
          >
            {busy ? 'Đang xử lý…' : confirmLabel}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={handleClose}
            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-[14px] font-semibold text-slate-700 transition-all hover:bg-slate-50 hover:border-slate-300 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
          >
            {cancelLabel}
          </button>
        </div>
      </div>
    </div>
  );

  return !inline && typeof document !== 'undefined' ? createPortal(modalNode, document.body) : modalNode;
}
