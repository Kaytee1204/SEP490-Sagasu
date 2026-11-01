import { Camera, Plus, X, ImageOff } from 'lucide-react';
import { useRef, useState } from 'react';

export function ReceiptImage({ src, alt, className = '' }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) return <div className={`flex items-center justify-center bg-slate-100 text-slate-600 ${className}`} title="Ảnh chưa tải được. Tải lại phiếu để lấy liên kết mới."><ImageOff size={22} /></div>;
  return <img src={src} alt={alt} className={className} onError={() => setFailed(true)} />;
}

export default function ReceiptImagePicker({ kept, files, onAdd, onRemoveOld, onRemoveNew, error, disabled }) {
  const input = useRef(null);
  const total = kept.length + files.length;
  return <div className="relative">
    <div className="mb-4 flex items-start justify-between gap-3">
      <div><p className="text-base font-semibold text-slate-800">Ảnh hiện trường & đồ vật <span className="text-rose-600">*</span></p><p className="mt-1 text-sm leading-5 text-slate-600">1–5 ảnh JPG/PNG, tối đa 5 MB/ảnh. Chụp rõ toàn cảnh và các hư hỏng nếu có.</p></div>
      <span className="shrink-0 rounded-md bg-slate-100 px-2 py-1 text-sm font-semibold text-slate-600">{total}/5 ảnh</span>
    </div>
    <input ref={input} type="file" accept="image/jpeg,image/png" multiple disabled={disabled} className="sr-only" aria-label="Chọn ảnh đồ vật"
      onChange={(event) => { onAdd(Array.from(event.target.files || [])); event.target.value = ''; }} />
    {total > 0 && <div className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
      {kept.map((image, index) => <div key={image.id} className="group relative overflow-hidden rounded-xl border border-slate-200">
        <a href={image.url} target="_blank" rel="noreferrer"><ReceiptImage key={image.url} src={image.url} alt={`Ảnh đã lưu ${index + 1}`} className="aspect-[4/3] w-full object-cover" /></a>
        <span className="absolute bottom-2 left-2 rounded bg-black/60 px-2 py-1 text-xs font-medium text-white">Đã lưu</span>
        <button type="button" disabled={disabled} aria-label={`Bỏ ảnh đã lưu ${index + 1}`} onClick={() => onRemoveOld(image.id)} className="absolute right-2 top-2 rounded-full bg-white p-1.5 text-slate-700 shadow hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"><X size={15} /></button>
      </div>)}
      {files.map((image, index) => <div key={image.preview} className="relative overflow-hidden rounded-xl border border-teal-200">
        <img src={image.preview} alt={`Ảnh mới ${index + 1}`} className="aspect-[4/3] w-full object-cover" />
        <span className="absolute bottom-2 left-2 rounded bg-teal-800 px-2 py-1 text-xs font-medium text-white">Ảnh mới</span>
        <button type="button" disabled={disabled} aria-label={`Bỏ ảnh mới ${index + 1}`} onClick={() => onRemoveNew(index)} className="absolute right-2 top-2 rounded-full bg-white p-1.5 text-slate-700 shadow hover:bg-rose-50 hover:text-rose-700 disabled:opacity-50"><X size={15} /></button>
      </div>)}
    </div>}
    {total < 5 && <button type="button" disabled={disabled} onClick={() => input.current?.click()}
      className={`flex w-full flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-slate-200 bg-slate-50/70 p-5 text-slate-600 transition hover:border-teal-400 hover:bg-teal-50 hover:text-teal-700 disabled:opacity-50 ${total === 0 ? 'min-h-40' : ''}`}>
      {total ? <Plus size={22} /> : <Camera size={30} strokeWidth={1.5} />}<span className="text-base font-semibold">{total ? 'Thêm ảnh' : 'Chọn ảnh từ thiết bị'}</span>
      {!total && <span className="text-sm">Có thể chọn nhiều ảnh cùng lúc</span>}
    </button>}
    {error && <p role="alert" className="mt-2 text-base text-rose-700">{error}</p>}
  </div>;
}
