import { useRef, useState } from 'react';
import { requestOfficeHandover } from '../../api/postApi';

export default function OfficeHandoverAction({ post, onTransferred }) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const inFlight = useRef(false);

  async function sendRequest() {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await requestOfficeHandover(post.id);
      onTransferred({ ...post, custodyMode: 'OFFICE', moderationStatus: result.moderationStatus,
        status: result.status, canRequestOfficeHandover: false });
    } catch (failure) {
      setError(failure.message || 'Chưa gửi được yêu cầu tiếp nhận.');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  if (!post.canRequestOfficeHandover) return null;
  return <section aria-label="Giao cho bên tiếp nhận" className="space-y-3">
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {!confirming ? <button type="button" className="btn btn-ghost" onClick={() => setConfirming(true)}>Giao cho bên tiếp nhận</button>
      : <div className="space-y-3 rounded-2xl border border-teal-200 bg-teal-50 p-4">
        <p>Bài sẽ chuyển sang chờ xét duyệt và tạm ẩn khỏi danh sách công khai. Yêu cầu này chưa xác nhận bạn đã bàn giao đồ.</p>
        <div className="flex flex-wrap gap-3">
          <button type="button" className="btn btn-primary" disabled={busy} onClick={sendRequest}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu tiếp nhận'}</button>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => { setConfirming(false); setError(''); }}>Để sau</button>
        </div>
      </div>}
  </section>;
}
