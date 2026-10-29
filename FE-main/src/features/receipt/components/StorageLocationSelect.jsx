export default function StorageLocationSelect({ id = 'storageLocationId', value, onChange, options, current, disabled }) {
  const historical = current?.storageLocationId && !options.some((option) => option.id === current.storageLocationId);
  return <div>
    <label htmlFor={id} className="mb-2 block text-base font-semibold text-slate-700">Vị trí lưu giữ</label>
    <select id={id} name="storageLocationId" value={value} onChange={(event) => onChange(event.target.value)} disabled={disabled}
      className="w-full min-w-0 rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-800 outline-none focus:border-teal-500 focus:ring-2 focus:ring-teal-100 disabled:bg-slate-50">
      <option value="">Chưa xác định</option>
      {historical && <option value={current.storageLocationId} disabled>{current.storageLocationName} — ngừng sử dụng</option>}
      {options.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
    </select>
    <p className="mt-2 text-sm leading-5 text-slate-600">{options.length ? 'Chọn nơi đồ đang được tập kết. Có thể để trống và bổ sung trước khi bàn giao kho.' : 'Chưa có vị trí đang hoạt động để chọn. Bạn vẫn có thể lưu và gửi phiếu.'}</p>
  </div>;
}
