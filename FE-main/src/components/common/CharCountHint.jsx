export default function CharCountHint({ value = '', max, className = '' }) {
  const length = typeof value === 'string' ? value.length : 0;
  const ratio = max > 0 ? length / max : 0;
  const colorClass =
    ratio >= 1
      ? 'text-rose-600 font-semibold'
      : ratio >= 0.9
        ? 'text-amber-600 font-medium'
        : 'text-slate-400';

  return (
    <div
      className={`mt-1 flex justify-end text-[12px] tabular-nums ${colorClass} ${className}`.trim()}
      aria-live="polite"
    >
      <span>
        {length} / {max} ký tự
      </span>
    </div>
  );
}
