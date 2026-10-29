import { useRef, useState } from 'react';
import { MapPin } from 'lucide-react';
import { receiptApi } from '@/features/receipt/api/receiptApi';
import StorageLocationSelect from '@/features/receipt/components/StorageLocationSelect';
import { checkerApi } from '../api/checkerApi';
import { ReceiptError } from './ReceiptFeedback';

export default function ReceiptLocationCard({ receipt, onSaved }) {
  const [editing, setEditing] = useState(false);
  const [options, setOptions] = useState([]);
  const [value, setValue] = useState(String(receipt.storageLocationId || ''));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(false);
  const allowed = !receipt.receivedAt && ['DRAFT', 'SUBMITTED'].includes(receipt.status) && ['PENDING', 'NEEDS_INFO', 'ACCEPTED'].includes(receipt.reviewStatus);
  const edit = async () => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try { setOptions(await receiptApi.locations()); setEditing(true); }
    catch (failure) { setError(failure.message); }
    finally { pending.current = false; setBusy(false); }
  };
  const save = async () => {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try { await checkerApi.updateLocation(receipt.id, value ? Number(value) : null, receipt.updatedAt); setEditing(false); onSaved(); }
    catch (failure) { setError(failure.message); }
    finally { pending.current = false; setBusy(false); }
  };
  return <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <h2 className="flex items-center gap-2 font-semibold text-slate-800"><MapPin size={18} className="text-teal-600" />Vị trí lưu giữ</h2>
    {editing ? <><StorageLocationSelect options={options} current={receipt} value={value} onChange={setValue} disabled={busy} /><div className="flex flex-wrap gap-2"><button type="button" disabled={busy} onClick={save} className="rounded-lg bg-teal-700 px-4 py-2 text-base font-semibold text-white disabled:opacity-50">{busy ? 'Đang lưu…' : 'Lưu vị trí'}</button><button type="button" disabled={busy} onClick={() => { setEditing(false); setError(''); setValue(String(receipt.storageLocationId || '')); }} className="rounded-lg border border-slate-200 px-4 py-2 text-base">Hủy</button></div></>
      : <><p className={`text-base leading-6 ${receipt.storageLocationId ? 'text-slate-700' : 'text-amber-700'}`}>{receipt.storageLocationName || 'Chưa cập nhật vị trí'}</p>{receipt.storageLocationId && !receipt.storageLocationActive && <p className="text-sm text-amber-700">Vị trí này đã ngừng sử dụng.</p>}{allowed && <button type="button" disabled={busy} onClick={edit} className="text-base font-semibold text-teal-700 hover:underline disabled:opacity-50">{busy ? 'Đang tải…' : 'Cập nhật vị trí'}</button>}</>}
    {error && <ReceiptError message={error} />}
  </section>;
}
