function CategoryActions({ category, onEdit, onStatus }) {
  return <div className="flex flex-wrap gap-2">
    <button type="button" onClick={() => onEdit(category)} aria-label={`Đổi tên ${category.name}`}
      className="rounded-lg border border-zinc-200 px-3 py-1.5 text-sm hover:bg-zinc-50">Sửa</button>
    <button type="button" onClick={() => onStatus(category)}
      aria-label={`${category.active ? 'Ngừng hoạt động' : 'Kích hoạt'} ${category.name}`}
      className={`rounded-lg border px-3 py-1.5 text-sm ${category.active ? 'border-amber-200 text-amber-800 hover:bg-amber-50' : 'border-teal-200 text-teal-700 hover:bg-teal-50'}`}>
      {category.active ? 'Ngừng hoạt động' : 'Kích hoạt'}
    </button>
  </div>;
}

function Status({ active }) {
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${active ? 'bg-emerald-50 text-emerald-700' : 'bg-zinc-100 text-zinc-600'}`}>
    {active ? 'Đang hoạt động' : 'Ngừng hoạt động'}
  </span>;
}

const date = (value) => value ? new Date(value).toLocaleString('vi-VN') : '—';

export default function CategoryTable({ categories, onEdit, onStatus }) {
  return <>
    <div className="hidden md:block overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-zinc-200 bg-zinc-50 text-zinc-500">
          <tr>{['Tên danh mục', 'Trạng thái', 'Cập nhật', 'Thao tác'].map((label) => <th key={label} scope="col" className="px-5 py-3 font-medium">{label}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-zinc-100">{categories.map((category) => <tr key={category.categoryId}>
          <td className="max-w-xs break-words px-5 py-4 font-medium">{category.name}</td>
          <td className="px-5 py-4"><Status active={category.active} /></td>
          <td className="px-5 py-4 text-zinc-500">{date(category.updatedAt)}</td>
          <td className="px-5 py-4"><CategoryActions category={category} onEdit={onEdit} onStatus={onStatus} /></td>
        </tr>)}</tbody>
      </table>
    </div>
    <div className="divide-y divide-zinc-100 md:hidden">{categories.map((category) => <article key={category.categoryId} className="space-y-3 p-4">
      <h2 className="break-words font-semibold">{category.name}</h2>
      <Status active={category.active} />
      <p className="text-xs text-zinc-500">Cập nhật: {date(category.updatedAt)}</p>
      <CategoryActions category={category} onEdit={onEdit} onStatus={onStatus} />
    </article>)}</div>
  </>;
}
