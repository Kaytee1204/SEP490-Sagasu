import { useEffect, useRef, useState } from 'react';
import { getPrivateIdentifiers, updatePrivateIdentifiers } from '../../api/postApi';

export default function PrivateIdentifiersEditor({ postId }) {
  const [value, setValue] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [attempt, setAttempt] = useState(0);
  const inFlight = useRef(false);

  useEffect(() => {
    let cancelled = false;
    getPrivateIdentifiers(postId).then(data => {
      if (!cancelled) { setValue(data.privateIdentifiers); setLoaded(true); setError(''); }
    }).catch(err => { if (!cancelled) setError(err.message); });
    return () => { cancelled = true; };
  }, [postId, attempt]);

  async function save(event) {
    event.preventDefault();
    if (inFlight.current) return;
    inFlight.current = true;
    setSaving(true); setError(''); setMessage('');
    try {
      await updatePrivateIdentifiers(postId, value.trim());
      setValue(value.trim());
      setMessage('Đã lưu. Thông tin này chỉ hiển thị cho bạn.');
    } catch (err) { setError(err.message); }
    finally { setSaving(false); inFlight.current = false; }
  }

  return <section className="card" style={{ padding: 20, marginTop: 24 }} aria-label="Dấu hiệu riêng tư">
    <h2>Dấu hiệu nhận dạng riêng tư</h2>
    <p>Chỉ chủ bài đọc và sửa được. Nhập số seri, dấu vết hoặc chi tiết để xác minh; không đưa vào mô tả hay ảnh công khai.</p>
    {error && <p role="alert">{error}</p>}
    {!loaded ? <>
      {!error && <p role="status">Đang tải thông tin riêng tư…</p>}
      {error && <button className="btn btn-ghost" onClick={() => setAttempt(n => n + 1)}>Thử lại</button>}
    </> : <form onSubmit={save}>
      <label className="input-label" htmlFor="private-identifiers-edit">Thông tin riêng tư (tối đa 5.000 ký tự)</label>
      <textarea id="private-identifiers-edit" className="input" rows={5} maxLength={5000}
        disabled={saving} value={value} onChange={e => { setValue(e.target.value); setMessage(''); }} />
      <p>Để trống và lưu nếu muốn xóa thông tin đã nhập.</p>
      <button className="btn btn-primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu dấu hiệu riêng tư'}</button>
      {message && <p role="status">{message}</p>}
    </form>}
  </section>;
}
