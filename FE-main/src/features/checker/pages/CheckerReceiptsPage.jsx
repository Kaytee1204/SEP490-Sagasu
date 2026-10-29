import { Link } from 'react-router-dom';
import { Plus, Search, RefreshCw, ClipboardList, ArrowUpRight, MapPin, Pencil } from 'lucide-react';
import Pagination from '@/components/common/Pagination';
import { useCheckerReceipts } from '../hooks/useCheckerReceipts';
import ReceiptStatusBadge from '../components/ReceiptStatusBadge';
import { ReceiptImage } from '../components/ReceiptImagePicker';
import { ReceiptError, ReceiptLoading } from '../components/ReceiptFeedback';
import { RECEIPT_FILTERS, canEditReceipt, receiptDate } from '../receiptUtils';

function Actions({ receipt }) {
  return <div className="flex items-center gap-2">
    <Link to={`/checker/receipts/${receipt.id}`} className="inline-flex items-center gap-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-600 hover:border-teal-300 hover:text-teal-700">Xem phiếu<ArrowUpRight size={14} /></Link>
    {canEditReceipt(receipt) && <Link to={`/checker/receipts/${receipt.id}/edit`} aria-label={`Sửa ${receipt.title}`} className="rounded-lg p-2 text-slate-600 hover:bg-teal-50 hover:text-teal-700"><Pencil size={16} /></Link>}
  </div>;
}

function StorageNote({ receipt }) {
  return <p className={`mt-2 max-w-48 text-sm leading-5 ${receipt.storageLocationName ? 'text-slate-600' : 'text-amber-700'}`}>{receipt.storageLocationName ? `Lưu giữ: ${receipt.storageLocationName}` : 'Chưa cập nhật vị trí'}</p>;
}

export default function CheckerReceiptsPage() {
  const data = useCheckerReceipts();
  const filtered = data.filters.keyword.trim() || data.filters.filter !== 'ALL';
  return <div className="mx-auto w-full max-w-7xl space-y-6 p-4 sm:p-6 lg:p-8">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div><p className="mb-2 text-sm font-bold tracking-wide text-teal-700">Tuần tra · Ghi nhận đồ nhặt được</p><h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">Phiếu của tôi</h1><p className="mt-2 max-w-xl text-base leading-6 text-slate-600">Lưu hồ sơ tại hiện trường, hoàn thiện thông tin và theo dõi kết quả xét duyệt.</p></div>
      <Link to="/checker/receipts/new" className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-3 text-base font-semibold text-white shadow-sm hover:bg-teal-800"><Plus size={18} />Lập phiếu mới</Link>
    </div>
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="space-y-4 border-b border-slate-100 p-4 sm:p-5">
        <div className="flex gap-3"><div className="relative flex-1 sm:max-w-md"><Search size={17} className="absolute left-3.5 top-3 text-slate-600" /><input type="search" value={data.filters.keyword} onChange={(e) => data.change('keyword', e.target.value)} maxLength={200} aria-label="Tìm phiếu" placeholder="Tìm tên đồ vật, mã phiếu hoặc địa điểm…" className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-3 text-base outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100" /></div>
          <button type="button" disabled={data.loading} onClick={data.reload} aria-label="Tải lại danh sách" className="rounded-xl border border-slate-200 px-3 text-slate-600 hover:bg-slate-50 disabled:opacity-40"><RefreshCw size={17} className={data.loading ? 'animate-spin' : ''} /></button></div>
        <div className="flex flex-wrap gap-2" aria-label="Lọc trạng thái">{RECEIPT_FILTERS.map(([value, label]) => <button key={value} type="button" aria-pressed={data.filters.filter === value} onClick={() => data.change('filter', value)} className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition ${data.filters.filter === value ? 'bg-teal-700 text-white' : 'bg-slate-50 text-slate-600 hover:bg-slate-100'}`}>{label}</button>)}</div>
      </div>
      {data.loading ? <ReceiptLoading /> : data.error ? <div className="p-5"><ReceiptError message={data.error} onRetry={data.reload} /></div> : !data.content.length ? <div className="flex flex-col items-center px-5 py-16 text-center"><div className="mb-4 rounded-2xl bg-teal-50 p-4 text-teal-600"><ClipboardList size={32} strokeWidth={1.5} /></div><h2 className="font-semibold text-slate-800">{filtered ? 'Không tìm thấy phiếu phù hợp' : 'Bắt đầu với phiếu đầu tiên'}</h2><p className="mb-5 mt-2 max-w-sm text-base leading-6 text-slate-600">{filtered ? 'Thử từ khóa khác hoặc bỏ bộ lọc để xem lại danh sách.' : 'Ghi lại đồ vật nhặt được cùng ảnh và tình trạng để chuyển cho người xét duyệt.'}</p>{filtered ? <button type="button" onClick={data.reset} className="text-base font-semibold text-teal-700">Xóa bộ lọc</button> : <Link to="/checker/receipts/new" className="text-base font-semibold text-teal-700">+ Lập phiếu mới</Link>}</div> : <>
        <div className="hidden overflow-x-auto lg:block"><table className="w-full text-left text-base"><thead className="border-b border-slate-100 bg-slate-50/70 text-sm text-slate-600"><tr>{['Hồ sơ đồ vật', 'Nơi & thời gian nhặt', 'Trạng thái', ''].map((label) => <th key={label} className="px-5 py-3.5 font-medium" scope="col">{label}</th>)}</tr></thead><tbody className="divide-y divide-slate-100">{data.content.map((receipt) => <tr key={receipt.id} className="hover:bg-slate-50/60">
          <td className="px-5 py-4"><Link to={`/checker/receipts/${receipt.id}`} className="flex items-center gap-3"><ReceiptImage key={receipt.thumbnailUrl} src={receipt.thumbnailUrl} alt="" className="h-14 w-14 shrink-0 rounded-xl object-cover" /><div className="min-w-0"><p className="max-w-64 truncate font-semibold text-slate-800">{receipt.title}</p><p className="mt-1 text-sm text-slate-600">Phiếu #{receipt.id} · {receipt.categoryName}</p></div></Link></td>
          <td className="px-5 py-4"><p className="max-w-52 truncate text-slate-600" title={receipt.foundLocation}>{receipt.foundLocation}</p><p className="mt-1 text-sm text-slate-600">{receiptDate(receipt.foundAt)}</p></td><td className="px-5 py-4"><ReceiptStatusBadge receipt={receipt} /><StorageNote receipt={receipt} /></td><td className="px-5 py-4"><Actions receipt={receipt} /></td>
        </tr>)}</tbody></table></div>
        <div className="divide-y divide-slate-100 lg:hidden">{data.content.map((receipt) => <article key={receipt.id} className="space-y-4 p-4"><div className="flex gap-3"><ReceiptImage key={receipt.thumbnailUrl} src={receipt.thumbnailUrl} alt="" className="h-16 w-16 shrink-0 rounded-xl object-cover" /><div className="min-w-0"><Link to={`/checker/receipts/${receipt.id}`} className="block break-words font-semibold text-slate-800">{receipt.title}</Link><p className="mb-2 mt-1 text-sm text-slate-600">#{receipt.id} · {receipt.categoryName}</p><ReceiptStatusBadge receipt={receipt} /><StorageNote receipt={receipt} /></div></div><div className="flex gap-2 text-sm text-slate-600"><MapPin size={15} className="shrink-0" /><div><p className="break-words">{receipt.foundLocation}</p><p className="mt-1">{receiptDate(receipt.foundAt)}</p></div></div><Actions receipt={receipt} /></article>)}</div>
      </>}
    </div>
    {!data.loading && !data.error && <div className="flex flex-wrap items-center justify-between gap-4 px-1"><p className="text-sm text-slate-600">{data.totalElements ? `Hiển thị ${(data.filters.page - 1) * 10 + 1}–${Math.min(data.filters.page * 10, data.totalElements)} trong ${data.totalElements} phiếu` : '0 phiếu'}</p><Pagination currentPage={data.filters.page} totalPages={data.totalPages} onPageChange={data.setPage} /></div>}
  </div>;
}
