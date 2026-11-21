import { useEffect, useId, useRef } from 'react';
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
}) {
  const dialogRef = useRef(null);
  const titleId = useId();
  const descId = useId();

  useEffect(() => {
    const el = dialogRef.current;
    if (!el) return;
    if (open && !el.open) {
      try {
        el.showModal();
      } catch {
        // Fallback if already open
      }
    } else if (!open && el.open) {
      el.close();
    }
    return () => {
      if (el.open) el.close();
    };
  }, [open]);

  if (!open) return null;

  const cfg = VARIANT_CONFIG[variant] || VARIANT_CONFIG.warning;
  const IconComponent = cfg.Icon;

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      aria-describedby={description ? descId : undefined}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose?.();
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current && !busy) {
          onClose?.();
        }
      }}
      style={{
        margin: 'auto',
        padding: 0,
        border: 'none',
        background: 'transparent',
        maxWidth: '92vw',
        width: '390px',
        overflow: 'visible',
      }}
      className="backdrop:bg-slate-900/45 backdrop:backdrop-blur-[2px]"
    >
      <div
        className="relative w-full rounded-[22px] border border-slate-100 bg-white px-6 pb-6 pt-7 text-center shadow-[0_24px_60px_rgba(15,23,42,0.18)] font-sans"
        style={{ backgroundColor: '#ffffff' }}
      >
        {/* Top-right close X */}
        <button
          type="button"
          disabled={busy}
          onClick={() => !busy && onClose?.()}
          aria-label="Đóng"
          className="absolute right-4 top-4 inline-flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-700 disabled:opacity-50 cursor-pointer"
        >
          <X size={18} />
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
            onClick={onConfirm}
            className="inline-flex items-center justify-center rounded-xl bg-[#111827] px-4 py-2.5 text-[14px] font-semibold text-white shadow-xs transition-all hover:bg-[#1f2937] disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
          >
            {busy ? 'Đang xử lý…' : confirmLabel}
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={() => !busy && onClose?.()}
            className="inline-flex items-center justify-center rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-[14px] font-semibold text-slate-700 transition-all hover:bg-slate-50 hover:border-slate-300 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer"
          >
            {cancelLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
}
