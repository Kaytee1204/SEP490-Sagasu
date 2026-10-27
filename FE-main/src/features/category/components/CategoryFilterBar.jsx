export default function CategoryFilterBar({ filters, onChange }) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row">
      <input type="search" aria-label="Tìm theo tên danh mục" placeholder="Tìm theo tên danh mục..."
        value={filters.keyword} onChange={(event) => onChange('keyword', event.target.value)}
        className="w-full sm:max-w-md rounded-lg border border-zinc-200 bg-white px-4 py-2.5 text-sm focus:outline-teal-600" />
      <select aria-label="Lọc trạng thái" value={filters.active} onChange={(event) => onChange('active', event.target.value)}
        className="rounded-lg border border-zinc-200 bg-white px-4 py-2.5 text-sm">
        <option value="">Tất cả trạng thái</option>
        <option value="true">Đang hoạt động</option>
        <option value="false">Ngừng hoạt động</option>
      </select>
    </div>
  );
}
