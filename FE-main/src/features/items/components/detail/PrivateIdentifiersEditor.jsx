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
      if (!cancelled) { setValue(data.privateIdentifiers || ''); setLoaded(true); setError(''); }
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
      setMessage('Đã lưu ghi chú. Nội dung chỉ hiển thị cho bạn.');
    } catch (err) { setError(err.message); }
    finally { setSaving(false); inFlight.current = false; }
  }

  return <section className="card" style={{ padding: 20, marginTop: 24 }} aria-label="Ghi chú xác minh riêng tư">
    <h2>Ghi chú xác minh riêng tư</h2>
    <p>Chỉ bạn đọc và sửa được. Ghi lại đặc điểm thực tế của món đồ để đối chiếu khi xét duyệt claim. Nội dung không hiển thị trong bài đăng hoặc form claim.</p>
    {error && <p role="alert">{error}</p>}
    {!loaded ? <>
      {!error && <p role="status">Đang tải ghi chú…</p>}
      {error && <button className="btn btn-ghost" onClick={() => setAttempt(n => n + 1)}>Thử lại</button>}
    </> : <form onSubmit={save}>
      <label className="input-label" htmlFor="private-identifiers-edit">Nội dung ghi chú (không bắt buộc, tối đa 5.000 ký tự)</label>
      <textarea id="private-identifiers-edit" className="input" rows={5} maxLength={5000}
        disabled={saving} value={value} onChange={e => { setValue(e.target.value); setMessage(''); }} />
      <p>Để trống và lưu nếu muốn xóa ghi chú.</p>
      <button className="btn btn-primary" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu ghi chú'}</button>
      {message && <p role="status">{message}</p>}
    </form>}
  </section>;
}
