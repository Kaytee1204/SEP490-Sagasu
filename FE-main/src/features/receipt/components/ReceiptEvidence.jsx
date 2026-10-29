import { MapPin, Clock, ShieldCheck } from 'lucide-react';
import { ReceiptImage } from '@/features/checker/components/ReceiptImagePicker';
import { receiptDate } from '@/features/checker/receiptUtils';

export function ReceiptText({ title, children }) {
  return <div><h3 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-600">{title}</h3><p className="whitespace-pre-wrap break-words text-base leading-7 text-slate-700">{children || 'Chưa ghi nhận'}</p></div>;
}

export default function ReceiptEvidence({ receipt }) {
  return <div className="min-w-0 space-y-5">
    <section className="space-y-6 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
      <h2 className="font-semibold text-slate-800">Thông tin ghi nhận</h2>
      <ReceiptText title="Mô tả đồ vật">{receipt.description}</ReceiptText>
      <div className="rounded-xl border border-amber-100 bg-amber-50/50 p-4"><ReceiptText title="Tình trạng khi nhặt">{receipt.conditionAtReceipt}</ReceiptText></div>
      <div className="grid gap-5 sm:grid-cols-2"><div className="flex items-start gap-3"><MapPin size={19} className="mt-1 shrink-0 text-teal-600" /><ReceiptText title="Nơi nhặt được">{receipt.foundLocation}</ReceiptText></div><div className="flex items-start gap-3"><Clock size={19} className="mt-1 shrink-0 text-teal-600" /><ReceiptText title="Thời gian nhặt · UTC+7">{receiptDate(receipt.foundAt)}</ReceiptText></div></div>
    </section>
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2"><h2 className="font-semibold text-slate-800">Ảnh hồ sơ</h2><span className="text-sm text-slate-600">{receipt.images.length} ảnh · Bấm để phóng to</span></div>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">{receipt.images.map((image, index) => <a key={image.id} href={image.url} target="_blank" rel="noreferrer" aria-label={`Xem ảnh ${index + 1}`} className="relative overflow-hidden rounded-xl border border-slate-200"><ReceiptImage key={image.url} src={image.url} alt={`Ảnh đồ vật ${index + 1}`} className="aspect-[4/3] w-full object-cover transition hover:scale-105" />{image.sensitive && <span className="absolute bottom-1 left-1 rounded bg-slate-900/80 px-2 py-1 text-sm text-white">Ảnh nội bộ</span>}</a>)}</div>
      {!receipt.images.length && <p className="text-base text-slate-600">Chưa có ảnh.</p>}
      <p className="mt-3 text-sm leading-5 text-slate-600">Nếu ảnh hết hạn hiển thị, dùng nút tải lại phiếu để xem tiếp.</p>
    </section>
    {(receipt.privateIdentifiers || receipt.note) && <section className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"><h2 className="flex items-center gap-2 font-semibold text-slate-800"><ShieldCheck size={18} className="text-teal-600" />Thông tin nội bộ</h2>{receipt.privateIdentifiers && <ReceiptText title="Đặc điểm nhận dạng riêng tư">{receipt.privateIdentifiers}</ReceiptText>}{receipt.note && <ReceiptText title="Ghi chú">{receipt.note}</ReceiptText>}</section>}
    {receipt.source === 'WALK_IN' && <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5"><h2 className="font-semibold text-slate-800">Người mang đồ đến</h2><ReceiptText title="Họ tên">{receipt.finderName}</ReceiptText><ReceiptText title="Thông tin liên hệ">{receipt.finderContact}</ReceiptText></section>}
  </div>;
}
