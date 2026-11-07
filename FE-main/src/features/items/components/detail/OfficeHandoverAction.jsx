import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
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
      // DaiHV integration: request only; office staff confirm physical custody later.
      onTransferred({ ...post, custodyMode: 'P2P', moderationStatus: result.moderationStatus,
        status: result.status, canRequestOfficeHandover: false });
    } catch (failure) {
      setError(failure.message || 'Chưa gửi được yêu cầu tiếp nhận.');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  if (!post.canRequestOfficeHandover) return (post.isOwner || post.canEditOwnPost) ? <Link to="/van-phong/chuyen-giao" className="btn btn-ghost">Theo dõi giao đồ cho văn phòng</Link> : null;
  return <section aria-label="Giao cho bên tiếp nhận" className="space-y-3">
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {!confirming ? <button type="button" className="btn btn-ghost" onClick={() => setConfirming(true)}>Giao cho bên tiếp nhận</button>
      : <div className="space-y-3 rounded-2xl border border-teal-200 bg-teal-50 p-4">
        <p>Văn phòng sẽ tiếp nhận yêu cầu. Bạn vẫn giữ đồ cho đến khi nhân viên xác nhận đã nhận. Các hồ sơ liên quan sẽ chuyển sang văn phòng và hồ sơ đã duyệt sẽ được xét lại khi tiếp nhận đồ.</p>
        <div className="flex flex-wrap gap-3">
          <button type="button" className="btn btn-primary" disabled={busy} onClick={sendRequest}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu tiếp nhận'}</button>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => { setConfirming(false); setError(''); }}>Để sau</button>
        </div>
      </div>}
  </section>;
}
