import { AlertCircle, LoaderCircle, RotateCcw } from 'lucide-react';

export function ReceiptLoading() {
  return <div role="status" className="flex min-h-64 items-center justify-center gap-3 text-base text-slate-600"><LoaderCircle size={20} className="animate-spin text-teal-600" />Đang tải hồ sơ…</div>;
}

export function ReceiptError({ message, onRetry }) {
  return <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-base text-rose-800">
    <div className="flex items-start gap-2"><AlertCircle size={18} className="mt-0.5 shrink-0" /><p>{message}</p></div>
    {onRetry && <button type="button" onClick={onRetry} className="mt-3 inline-flex items-center gap-2 font-semibold underline underline-offset-4"><RotateCcw size={14} />Tải lại dữ liệu</button>}
  </div>;
}
