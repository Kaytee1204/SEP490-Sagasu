import { officeApi } from '../api/officeApi';
import useLoad from '../hooks/useLoad';
import Notice from './Notice';
import { date } from '../officeUtils';

export default function SupplementHistory({ claim }) {
  const state = useLoad(() => officeApi.supplements(claim.id), `${claim.id}:${claim.version}`);
  return <section className="of-panel">
    <h2>Thông tin đã bổ sung</h2>
    {state.error ? <><Notice error>{state.error}</Notice><button className="of-button" onClick={state.reload}>Tải lại phần bổ sung</button></> : !state.data ? <p role="status">Đang tải thông tin bổ sung…</p> : !state.data.length ? <p className="of-muted">Chưa có lần bổ sung nào.</p> :
      <ol className="of-timeline">{state.data.map((entry, index) => <li key={entry.activity_id}>
        <h3>Lần bổ sung {state.data.length - index}</h3>
        <p className="of-muted">{entry.actor} · {date(entry.created_at)}</p>
        {entry.question && <div className="of-callout"><strong>Văn phòng cần làm rõ</strong><p className="of-prose">{entry.question}</p></div>}
        <p className="of-prose">{entry.information}</p>
        <div className="of-gallery">{entry.images.map(image => <a key={image.image_id} href={image.url} target="_blank" rel="noreferrer"><img src={image.url} alt={image.original_name || 'Ảnh bổ sung'} loading="lazy" /></a>)}</div>
      </li>)}</ol>}
  </section>;
}
