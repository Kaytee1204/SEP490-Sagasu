import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { requestOfficeHandover } from '../../api/postApi';
import InfoHint from '@/features/office/components/InfoHint';
import Dialog from '@/features/office/components/Dialog';
import '@/features/office/Office.css';

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
      onTransferred({
        ...post, custodyMode: 'P2P', moderationStatus: result.moderationStatus,
        status: result.status, canRequestOfficeHandover: false
      });
    } catch (failure) {
      setError(failure.message || 'Chưa gửi được yêu cầu tiếp nhận.');
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }

  if (!post.canRequestOfficeHandover) return (post.isOwner || post.canEditOwnPost) ? <Link to="/van-phong/chuyen-giao" className="btn btn-ghost">Theo dõi giao đồ cho văn phòng</Link> : null;
  // DaiHV integration: explain and confirm the request at the post action itself.
  return <section aria-label="Gửi đồ cho văn phòng" className="space-y-3">
    <div className="flex items-center gap-3"><button type="button" className="btn btn-ghost" onClick={() => setConfirming(true)}>Nhờ văn phòng giữ đồ</button><InfoHint label="Giải thích việc gửi đồ cho văn phòng">Văn phòng sẽ liên hệ để tiếp nhận. Bạn giữ đồ cho đến khi giao trực tiếp; sau đó văn phòng tiếp tục xử lý các yêu cầu nhận lại đồ.</InfoHint></div>
    {confirming && <Dialog title="Gửi yêu cầu tiếp nhận đồ?" busy={busy} onClose={() => { setConfirming(false); setError(''); }}>
      <p><strong>{post.title}</strong></p><p>Văn phòng sẽ nhận được yêu cầu và sắp xếp việc giao đồ với bạn. Bạn vẫn giữ đồ cho đến khi văn phòng xác nhận đã nhận tại quầy.</p>
      {error && <p role="alert" className="text-red-700">{error}</p>}
      <div className="of-actions"><button type="button" className="of-button" disabled={busy} onClick={() => setConfirming(false)}>Để sau</button><button type="button" className="of-button of-primary" disabled={busy} onClick={sendRequest}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu'}</button></div>
    </Dialog>}
  </section>;
}
