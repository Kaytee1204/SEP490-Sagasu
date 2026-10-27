import { useState } from 'react';
import { Plus, Tags } from 'lucide-react';
import Pagination from '@/components/common/Pagination';
import { useCategories } from '../hooks/useCategories';
import CategoryFilterBar from '../components/CategoryFilterBar';
import CategoryTable from '../components/CategoryTable';
import CategoryFormModal from '../components/CategoryFormModal';
import CategoryStatusModal from '../components/CategoryStatusModal';

export default function CategoryManagementPage() {
  const data = useCategories();
  const [form, setForm] = useState(null);
  const [statusCategory, setStatusCategory] = useState(null);
  const filtered = !!data.filters.keyword.trim() || data.filters.active !== '';
  return <div className="space-y-5 p-4 text-zinc-900 lg:p-7">
    {data.toast && <div role="status" className="fixed right-5 top-16 z-[60] max-w-sm rounded-xl bg-teal-800 p-4 text-sm text-white shadow-lg">{data.toast}</div>}
    <div className="flex flex-wrap items-center justify-between gap-4">
      <div><h1 className="text-2xl font-bold">Quản lý danh mục</h1><p className="mt-1 text-sm text-zinc-500">Quản lý danh mục đồ vật sử dụng trong hệ thống.</p></div>
      <button type="button" onClick={() => setForm({ category: null })} className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"><Plus size={17} />Thêm danh mục</button>
    </div>
    <CategoryFilterBar filters={data.filters} onChange={data.changeFilter} />
    <div className="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm" aria-busy={data.loading}>
      {data.loading ? <div role="status" className="space-y-4 p-5"><span className="sr-only">Đang tải danh mục</span>{[1, 2, 3, 4].map((key) => <div key={key} className="h-10 animate-pulse rounded bg-zinc-100" />)}</div>
        : data.error ? <div role="alert" className="space-y-3 p-8 text-center"><p className="text-red-700">{data.error}</p><button type="button" onClick={data.reload} className="text-sm font-semibold text-teal-700 underline">Thử lại</button></div>
          : data.content.length ? <CategoryTable categories={data.content} onEdit={(category) => setForm({ category })} onStatus={setStatusCategory} />
            : <div className="space-y-3 p-10 text-center"><Tags className="mx-auto text-zinc-400" size={32} /><p className="text-zinc-500">{filtered ? 'Không tìm thấy danh mục phù hợp' : 'Chưa có danh mục'}</p>
              <button type="button" className="text-sm font-semibold text-teal-700" onClick={() => {
                if (filtered) { data.changeFilter('keyword', ''); data.changeFilter('active', ''); }
                else setForm({ category: null });
              }}>{filtered ? 'Xóa bộ lọc' : 'Thêm danh mục'}</button></div>}
    </div>
    {!data.loading && !data.error && <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-zinc-500">{data.totalElements ? `Hiển thị ${(data.filters.page - 1) * 10 + 1}–${Math.min(data.filters.page * 10, data.totalElements)} / ${data.totalElements} danh mục` : '0 danh mục'}</p>
      <Pagination currentPage={data.filters.page} totalPages={data.totalPages} onPageChange={data.setPage} />
    </div>}
    {form && <CategoryFormModal category={form.category} busy={data.submitting} onClose={() => setForm(null)} onSave={data.save} />}
    {statusCategory && <CategoryStatusModal category={statusCategory} busy={data.submitting} onClose={() => setStatusCategory(null)} onConfirm={data.changeStatus} />}
  </div>;
}
