import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, ClipboardList, MapPin, ShieldCheck, Save, Check, LoaderCircle } from 'lucide-react';
import { approverApi } from '../api/approverApi';
import { useReceipt } from '@/features/receipt/hooks/useIntakeReceipts';
import { vietnamInput } from '@/features/receipt/receiptUtils';
import { ReceiptLoading, ReceiptError } from '@/features/receipt/components/ReceiptFeedback';
import ReceiptImagePicker from '@/features/receipt/components/ReceiptImagePicker';
import StorageLocationSelect from '@/features/receipt/components/StorageLocationSelect';
import useImageUploads from '@/hooks/useImageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';

const inputStyle = 'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-base text-slate-800 outline-none transition placeholder:text-slate-600 focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-50';

function Field({ name, label, required, hint, error, children }) {
  return <div><label htmlFor={name} className="mb-2 block text-base font-semibold text-slate-700">{label}{required && <span className="ml-1 text-rose-600">*</span>}</label>{children}{hint && !error && <p className="mt-1.5 text-sm leading-5 text-slate-600">{hint}</p>}{error && <p id={`${name}-error`} className="mt-1.5 text-sm text-rose-700">{error}</p>}</div>;
}

function Section({ number, title, subtitle, children }) {
  return <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-6"><div className="mb-5 flex items-center gap-3"><span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-teal-50 text-sm font-bold text-teal-700">{number}</span><div><h2 className="font-semibold text-slate-800">{title}</h2>{subtitle && <p className="mt-0.5 text-sm text-slate-600">{subtitle}</p>}</div></div>{children}</section>;
}

function ReceiptEditor({ receipt, categories, locations }) {
  const navigate = useNavigate();
  const [form, setForm] = useState(() => ({
    title: receipt?.title || '', categoryId: String(receipt?.categoryId || ''), description: receipt?.description || '',
    conditionAtReceipt: receipt?.conditionAtReceipt || '', foundLocation: receipt?.foundLocation || '',
    foundAt: vietnamInput(receipt?.foundAt || new Date().toISOString()),
    privateIdentifiers: receipt?.privateIdentifiers || '', note: receipt?.note || '',
    storageLocationId: String(receipt?.storageLocationId || ''),
  }));
  const [kept, setKept] = useState(receipt?.images || []);
  const [files, setFiles] = useState([]);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setBusy] = useState(false);
  const { prepare, processing } = useImageUploads();
  const busy = saving || processing;
  const [dirty, setDirty] = useState(false);
  const pending = useRef(false);
  const previewUrls = useRef(new Set());
  const formElement = useRef(null);
  const errorElement = useRef(null);
  const isEdit = !!receipt;

  useEffect(() => {
    const urls = previewUrls.current;
    return () => { urls.forEach((url) => URL.revokeObjectURL(url)); urls.clear(); };
  }, []);

  useEffect(() => {
    const beforeUnload = (event) => { if (dirty || busy) { event.preventDefault(); event.returnValue = ''; } };
    const beforeLink = (event) => {
      const link = event.target.closest('a[href]');
      if (!link || link.target === '_blank' || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      if (link.origin !== window.location.origin || link.pathname === window.location.pathname) return;
      if (busy || (dirty && !window.confirm('Bạn có thay đổi chưa lưu. Rời trang và bỏ các thay đổi?'))) {
        event.preventDefault(); event.stopPropagation();
      }
    };
    window.addEventListener('beforeunload', beforeUnload);
    document.addEventListener('click', beforeLink, true);
    return () => { window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('click', beforeLink, true); };
  }, [dirty, busy]);

  const change = (name, value) => {
    setForm((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: '' }));
    setDirty(true);
  };
  const control = (name) => ({
    id: name, name, value: form[name], onChange: (event) => change(name, event.target.value),
    'aria-invalid': !!errors[name], 'aria-describedby': errors[name] ? `${name}-error` : undefined,
    className: `${inputStyle} ${errors[name] ? 'border-rose-400 bg-rose-50/30' : ''}`,
  });
  const addFiles = async (selected) => {
    if (!selected.length || busy) return;
    if (selected.length + files.length + kept.length > 5) {
      setErrors((current) => ({ ...current, images: 'Tối đa 5 ảnh. Bỏ bớt ảnh trước khi thêm.' })); return;
    }
    setErrors((current) => ({ ...current, images: '' }));
    try {
      const prepared = await prepare(selected);
      if (!prepared) return;
      const next = prepared.map((file) => {
        const preview = URL.createObjectURL(file); previewUrls.current.add(preview); return { file, preview };
      });
      setFiles((current) => [...current, ...next]); setDirty(true);
    } catch (problem) { setErrors((current) => ({ ...current, images: problem.message })); }
  };
  const removeNew = (index) => {
    URL.revokeObjectURL(files[index].preview); previewUrls.current.delete(files[index].preview);
    setFiles((current) => current.filter((_, i) => i !== index)); setDirty(true); setErrors((current) => ({ ...current, images: '' }));
  };
  const cancel = () => {
    if (busy || (dirty && !window.confirm('Bỏ các thay đổi chưa lưu?'))) return;
    navigate(receipt ? `/approver/receipts/${receipt.id}` : '/approver/receipts');
  };
  const save = async (event) => {
    event.preventDefault();
    if (pending.current || processing) return;
    const nextErrors = {};
    if (!form.title.trim()) nextErrors.title = 'Nhập tên đồ vật để nhận diện hồ sơ.';
    if (!categories.some((category) => String(category.categoryId) === form.categoryId)) nextErrors.categoryId = 'Chọn một danh mục đang hoạt động.';
    if (!form.description.trim()) nextErrors.description = 'Mô tả màu sắc, đặc điểm hoặc thương hiệu của đồ vật.';
    if (!form.conditionAtReceipt.trim()) nextErrors.conditionAtReceipt = 'Ghi rõ tình trạng đồ vật khi văn phòng tiếp nhận.';
    if (!form.foundLocation.trim()) nextErrors.foundLocation = 'Nhập vị trí tìm thấy đồ vật.';
    const foundAt = form.foundAt ? new Date(`${form.foundAt}:00+07:00`) : null;
    if (!foundAt || !Number.isFinite(foundAt.getTime()) || foundAt.getTime() > Date.now()) nextErrors.foundAt = 'Thời gian phải hợp lệ và không ở tương lai.';
    if (!kept.length && !files.length) nextErrors.images = 'Thêm ít nhất một ảnh thực tế để lưu hồ sơ.';
    setErrors(nextErrors); setError('');
    if (Object.keys(nextErrors).length) {
      formElement.current.querySelector(`[name="${Object.keys(nextErrors)[0]}"]`)?.focus();
      if (Object.keys(nextErrors)[0] === 'images') document.getElementById('receipt-images')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    pending.current = true; setBusy(true);
    try {
      const body = { ...form, title: form.title.trim(), categoryId: Number(form.categoryId), storageLocationId: form.storageLocationId ? Number(form.storageLocationId) : null, foundAt: foundAt.toISOString(),
        keptImageIds: kept.map((image) => image.id), expectedUpdatedAt: receipt?.updatedAt || null };
      const result = receipt ? await approverApi.update(receipt.id, body, files.map((image) => image.file)) : await approverApi.create(body, files.map((image) => image.file));
      setDirty(false);
      navigate(`/approver/receipts/${result.id}`, { replace: true, state: { receiptNotice: isEdit ? 'Đã lưu thay đổi của bạn.' : 'Đã lưu phiếu nháp. Kiểm tra thông tin trước khi xác nhận tiếp nhận.' } });
    } catch (failure) {
      setError(failure.message);
      requestAnimationFrame(() => errorElement.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }));
    } finally { pending.current = false; setBusy(false); }
  };

  const complete = [!!form.title.trim(), !!form.categoryId, !!form.description.trim(), !!form.conditionAtReceipt.trim(), !!form.foundLocation.trim(), !!form.foundAt, kept.length + files.length > 0].filter(Boolean).length;
  return <div className="mx-auto w-full max-w-6xl p-4 sm:p-6 lg:p-8">
    <Link to={receipt ? `/approver/receipts/${receipt.id}` : '/approver/receipts'} className="mb-5 inline-flex items-center gap-2 text-base font-medium text-slate-600 hover:text-teal-700"><ArrowLeft size={16} />{receipt ? 'Về chi tiết phiếu' : 'Phiếu tiếp nhận'}</Link>
    <div className="mb-6"><p className="mb-2 text-sm font-bold tracking-wide text-teal-700">Tiếp nhận tại văn phòng</p><h1 className="text-2xl font-bold text-slate-900 sm:text-3xl">{receipt ? 'Hoàn thiện phiếu nháp' : 'Lập phiếu tiếp nhận'}</h1><p className="mt-2 text-base leading-6 text-slate-600">Ghi nhận tài sản đã được mang đến văn phòng. Lưu nháp, xem lại thông tin và xác nhận tiếp nhận để đăng bài.</p></div>
    <form ref={formElement} onSubmit={save} noValidate>
      <fieldset disabled={busy} className="min-w-0">
        <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="min-w-0 space-y-5">
            <Section number="01" title="Thông tin đồ vật" subtitle="Các trường có dấu * là bắt buộc để lưu hồ sơ.">
              <div className="space-y-5">
                <Field name="title" label="Tên đồ vật" required error={errors.title}><input {...control('title')} maxLength={200} placeholder="Ví dụ: Ví da màu nâu, điện thoại Samsung màu đen" /></Field>
                <Field name="categoryId" label="Danh mục" required error={errors.categoryId}><select {...control('categoryId')}><option value="">Chọn loại đồ vật</option>{receipt && !categories.some((category) => category.categoryId === receipt.categoryId) && <option value={receipt.categoryId} disabled>{receipt.categoryName} — đã ngừng sử dụng</option>}{categories.map((category) => <option key={category.categoryId} value={category.categoryId}>{category.name}</option>)}</select></Field>
                <Field name="description" label="Mô tả nhận diện" required error={errors.description} hint="Ghi đặc điểm nhìn thấy được. Thông tin bí mật có ô riêng bên dưới."><textarea {...control('description')} rows={3} maxLength={5000} placeholder="Màu sắc, kích thước, thương hiệu, phụ kiện đi kèm…" /></Field>
                <Field name="conditionAtReceipt" label="Tình trạng khi tiếp nhận" required error={errors.conditionAtReceipt}><textarea {...control('conditionAtReceipt')} rows={3} maxLength={2000} placeholder="Ví dụ: Vỏ trầy nhẹ, màn hình nứt góc phải, không lên nguồn." /></Field>
              </div>
            </Section>
            <Section number="02" title="Nơi & thời gian nhặt" subtitle="Thông tin giúp đối chiếu với người báo mất.">
              <div className="space-y-5"><Field name="foundLocation" label="Địa điểm cụ thể" required error={errors.foundLocation}><input {...control('foundLocation')} maxLength={500} placeholder="Ví dụ: Ghế đá cạnh cổng tòa Alpha, tầng 1" /></Field>
                <Field name="foundAt" label="Thời gian nhặt được" required error={errors.foundAt} hint="Giờ Việt Nam (UTC+7). Không chọn thời gian ở tương lai."><input {...control('foundAt')} type="datetime-local" max={vietnamInput(new Date().toISOString())} /></Field>
                <StorageLocationSelect value={form.storageLocationId} onChange={(value) => change('storageLocationId', value)} options={locations} current={receipt} disabled={busy} intake /></div>
            </Section>
            <Section number="03" title="Thông tin nội bộ" subtitle="Không bắt buộc · Chỉ dùng trong hồ sơ nghiệp vụ.">
              <div className="space-y-5"><Field name="privateIdentifiers" label="Đặc điểm nhận dạng riêng tư" hint="Dành cho thông tin cần giữ kín để xác minh chủ sở hữu."><textarea {...control('privateIdentifiers')} rows={2} maxLength={2000} placeholder="Số serial, ký hiệu khắc riêng, chi tiết bên trong…" /></Field>
                <Field name="note" label="Ghi chú nội bộ"><textarea {...control('note')} rows={2} maxLength={2000} placeholder="Thông tin bổ sung hoặc cách bạn đã bảo quản đồ vật…" /></Field></div>
            </Section>
          </div>
          <aside className="space-y-4 xl:sticky xl:top-4">
            <section id="receipt-images" className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"><ReceiptImagePicker kept={kept} files={files} onAdd={addFiles} onRemoveNew={removeNew} onRemoveOld={(id) => { setKept((current) => current.filter((image) => image.id !== id)); setDirty(true); setErrors((current) => ({ ...current, images: '' })); }} disabled={busy} error={errors.images} /><ImageProcessingStatus active={processing} /></section>
            <p className="rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-900">Ảnh và mô tả sẽ xuất hiện trên bài đăng. Tránh chụp số giấy tờ, thông tin liên hệ hoặc đặc điểm dùng để xác minh chủ sở hữu.</p><div className="rounded-2xl border border-teal-100 bg-teal-50/70 p-5"><div className="mb-3 flex items-center gap-2 text-base font-semibold text-teal-900"><ClipboardList size={17} />Trước khi tiếp nhận</div><div className="space-y-3 text-sm leading-5 text-teal-800"><p className="flex gap-2"><Check size={15} className="mt-0.5 shrink-0" />Thông tin rõ ràng, đúng với đồ vật thực tế.</p><p className="flex gap-2"><MapPin size={15} className="mt-0.5 shrink-0" />Địa điểm và thời gian đủ để đối chiếu.</p><p className="flex gap-2"><ShieldCheck size={15} className="mt-0.5 shrink-0" />Lưu nháp để xem lại. Bài chỉ được đăng khi bạn xác nhận đã nhận tài sản.</p></div></div>
          </aside>
        </div>
      </fieldset>
      <div ref={errorElement} className={error ? 'mt-5' : ''}>{error && <ReceiptError message={error} />}</div>
      <div className="sticky bottom-0 z-10 mt-6 flex flex-wrap items-center justify-between gap-3 rounded-t-xl border-t border-slate-200 bg-white/95 px-3 py-4 backdrop-blur-sm sm:px-5">
        <p className="text-sm text-slate-600"><span className="font-semibold text-teal-700">{complete}/7</span> mục bắt buộc đã điền{dirty && <span className="ml-2 hidden sm:inline">· Có thay đổi chưa lưu</span>}</p>
        <div className="flex gap-3"><button type="button" disabled={busy} onClick={cancel} className="rounded-xl border border-slate-200 px-4 py-2.5 text-base font-semibold text-slate-600 disabled:opacity-50">Hủy</button><button type="submit" disabled={busy} className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-2.5 text-base font-semibold text-white hover:bg-teal-800 disabled:opacity-50">{busy ? <LoaderCircle size={17} className="animate-spin" /> : <Save size={17} />}{busy ? 'Đang lưu hồ sơ…' : receipt ? 'Lưu & xem lại' : 'Lưu nháp & xem lại'}</button></div>
      </div>
    </form>
  </div>;
}

export default function IntakeFormPage() {
  const { id } = useParams();
  const loaded = useReceipt(id, true, approverApi);
  if (loaded.loading) return <ReceiptLoading />;
  if (loaded.error) return <div className="mx-auto max-w-3xl space-y-4 p-6"><Link to="/approver/receipts" className="text-base text-teal-700">← Phiếu tiếp nhận</Link><ReceiptError message={loaded.error} onRetry={loaded.reload} /></div>;
  if (loaded.receipt && !loaded.receipt.canProcess) return <div className="mx-auto max-w-3xl space-y-4 p-6"><ReceiptError message="Phiếu này đã chuyển xử lý và không còn được chỉnh sửa." /><Link to={`/approver/receipts/${id}`} className="text-base font-semibold text-teal-700">Xem chi tiết phiếu →</Link></div>;
  if (!loaded.categories.length) return <div className="mx-auto max-w-3xl space-y-4 p-6"><Link to="/approver/receipts" className="text-base text-teal-700">← Phiếu tiếp nhận</Link><ReceiptError message="Chưa có danh mục đang hoạt động. Vui lòng liên hệ người quản lý để bổ sung danh mục." onRetry={loaded.reload} /></div>;
  return <ReceiptEditor key={`${id || 'new'}:${loaded.receipt?.updatedAt || ''}`} receipt={loaded.receipt} categories={loaded.categories} locations={loaded.locations || []} />;
}
