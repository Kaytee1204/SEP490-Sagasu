import { useState } from "react";
import { officeApi } from "../api/officeApi";
import Notice from "./Notice";
import Dialog from "./Dialog";

export default function LocationDialog({
  initial,
  locations,
  onClose,
  onDone,
}) {
  const [form, setForm] = useState({
    code: initial.code || "",
    name: initial.name || "",
    parentId: initial.parentId || "",
    active: initial.active ?? true,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const change = (key, value) => setForm((f) => ({ ...f, [key]: value }));
  const excluded = new Set(initial.id ? [initial.id] : []);
  let changed;
  do {
    changed = false;
    locations.forEach((l) => {
      if (excluded.has(l.parentId) && !excluded.has(l.id)) {
        excluded.add(l.id);
        changed = true;
      }
    });
  } while (changed);
  async function save(e) {
    e.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await officeApi.saveLocation(initial.id, {
        ...form,
        code: form.code.trim(),
        name: form.name.trim(),
        parentId: form.parentId ? Number(form.parentId) : null,
      });
      onDone();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog
      title={initial.id ? "Chỉnh sửa vị trí" : "Thêm vị trí lưu trữ"}
      onClose={onClose}
      busy={busy}
    >
      <form className="of-form" onSubmit={save}>
        <Notice error>{error}</Notice>
        <label>
          Mã vị trí *
          <input
            autoFocus
            required
            maxLength={40}
            value={form.code}
            disabled={busy}
            onChange={(e) => change("code", e.target.value)}
            placeholder="VD: A-101-TU01"
          />
        </label>
        <label>
          Tên vị trí *
          <input
            required
            maxLength={150}
            value={form.name}
            disabled={busy}
            onChange={(e) => change("name", e.target.value)}
            placeholder="VD: Tủ 01, phòng tiếp nhận"
          />
        </label>
        <label>
          Vị trí cha
          <select
            value={form.parentId}
            disabled={busy}
            onChange={(e) => change("parentId", e.target.value)}
          >
            <option value="">Không có — vị trí gốc</option>
            {locations
              .filter((l) => l.active && !excluded.has(l.id))
              .map((l) => (
                <option key={l.id} value={l.id}>
                  {l.code} · {l.name}
                </option>
              ))}
          </select>
        </label>
        <label className="of-checkbox">
          <input
            type="checkbox"
            checked={form.active}
            disabled={busy}
            onChange={(e) => change("active", e.target.checked)}
          />
          Đang sử dụng
        </label>
        {!form.active && (
          <Notice>
            Vị trí ngừng sử dụng sẽ không được chọn cho hồ sơ tiếp nhận mới. Các
            vị trí con cần ngừng sử dụng trước.
          </Notice>
        )}
        <div className="of-actions">
          <button
            type="button"
            className="of-button"
            onClick={onClose}
            disabled={busy}
          >
            Hủy
          </button>
          <button className="of-button of-primary" disabled={busy}>
            {busy ? "Đang lưu…" : "Lưu vị trí"}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
