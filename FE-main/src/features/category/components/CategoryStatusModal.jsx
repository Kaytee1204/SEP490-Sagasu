import { useState } from 'react';
import CategoryDialog from './CategoryDialog';

export default function CategoryStatusModal({ category, busy, onClose, onConfirm }) {
  const [error, setError] = useState('');
  const label = category.active ? 'Ngừng hoạt động' : 'Kích hoạt';
  const confirm = async () => {
    setError('');
    try { if (await onConfirm(category)) onClose(); }
    catch (err) { setError(err.message); }
  };
  return <CategoryDialog title={`${label} danh mục?`} busy={busy} onClose={onClose}>
    <p className="break-words text-sm leading-6 text-zinc-600">
      <strong>{category.name}</strong>{category.active
        ? ' sẽ không còn được chọn cho bài đăng mới. Các bài đăng cũ vẫn được giữ nguyên.'
        : ' sẽ có thể được chọn lại cho bài đăng mới.'}
    </p>
    {error && <p role="alert" className="mt-3 text-sm text-red-600">{error}</p>}
    <div className="mt-5 flex justify-end gap-3">
      <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-zinc-200 px-4 py-2 disabled:opacity-50">Hủy</button>
      <button type="button" disabled={busy} onClick={confirm} className={`rounded-lg px-4 py-2 text-white disabled:opacity-50 ${category.active ? 'bg-amber-700 hover:bg-amber-800' : 'bg-teal-700 hover:bg-teal-800'}`}>
        {busy ? 'Đang lưu…' : label}
      </button>
    </div>
  </CategoryDialog>;
}
