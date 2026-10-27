import { useState } from 'react';
import CategoryDialog from './CategoryDialog';

export default function CategoryFormModal({ category, busy, onClose, onSave }) {
  const [name, setName] = useState(category?.name || '');
  const [error, setError] = useState('');
  const submit = async (event) => {
    event.preventDefault();
    const normalized = name.trim().replace(/\s+/gu, ' ');
    if (!normalized || normalized.length > 100) {
      setError('Tên danh mục phải có từ 1 đến 100 ký tự');
      return;
    }
    setError('');
    try { if (await onSave(category, normalized)) onClose(); }
    catch (err) { setError(err.message); }
  };
  return <CategoryDialog title={category ? 'Đổi tên danh mục' : 'Thêm danh mục'} busy={busy} onClose={onClose}>
    <form onSubmit={submit} className="space-y-4">
      <div>
        <label htmlFor="category-name" className="mb-2 block text-sm font-medium">Tên danh mục <span className="text-red-600">*</span></label>
        <input id="category-name" value={name} onChange={(event) => { setName(event.target.value); setError(''); }}
          disabled={busy} maxLength={100} required aria-invalid={!!error} aria-describedby={error ? 'category-form-error' : undefined}
          className="w-full rounded-lg border border-zinc-300 px-3 py-2.5 focus:outline-teal-600" placeholder="Ví dụ: Điện thoại" />
        {error && <p id="category-form-error" role="alert" className="mt-2 text-sm text-red-600">{error}</p>}
      </div>
      {!category && <p className="text-sm text-zinc-500">Danh mục mới sẽ được kích hoạt.</p>}
      <div className="flex justify-end gap-3">
        <button type="button" disabled={busy} onClick={onClose} className="rounded-lg border border-zinc-200 px-4 py-2 disabled:opacity-50">Hủy</button>
        <button type="submit" disabled={busy} className="rounded-lg bg-teal-700 px-4 py-2 text-white hover:bg-teal-800 disabled:opacity-50">
          {busy ? 'Đang lưu…' : category ? 'Lưu thay đổi' : 'Tạo danh mục'}
        </button>
      </div>
    </form>
  </CategoryDialog>;
}
