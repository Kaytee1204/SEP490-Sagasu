import { useState } from 'react';
import { FolderOpen, Link2 } from 'lucide-react';
import { reviewApi } from '../api';
import useLoad from '../../office/hooks/useLoad';
import { Badge, Dialog, ErrorBox } from './Shared';
import { date } from '../format';

export default function ReportCasePanel({ report, onChanged, onOpen }) {
  const [page, setPage] = useState(1);
  const state = useLoad(() => reviewApi.caseDetail(report.report_id,page), `${report.report_id}:${report.version}:${page}`);
  const [mode, setMode] = useState(null);
  const [title, setTitle] = useState('');
  const [reason, setReason] = useState('');
  const [caseId, setCaseId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [discard, setDiscard] = useState(false);
  const data = state.data;
  const current = data?.case;
  const cases = [...new Map((data?.candidates || []).filter(row => row.case_id).map(row => [row.case_id, row])).values()];
  const remaining = data?.pendingMembers || 0;
  function open(value) { setMode(value); setTitle(''); setReason(''); setCaseId(''); setError(''); setDiscard(false); }
  function close() { if (busy) return; if(title || reason || caseId) setDiscard(true); else setMode(null); }
  async function submit(event) {
    event.preventDefault();
    if(busy) return;
    setBusy(true); setError('');
    try {
      const body = { version:report.version, reason:reason.trim() };
      if(mode === 'unlink') await reviewApi.unlinkCase(report.report_id,body);
      else await reviewApi.linkCase(report.report_id,{...body,caseId:mode === 'link' ? Number(caseId) : null,title:title.trim()});
      setMode(null); state.reload(); onChanged();
    } catch(problem) { setError(problem.message); } finally { setBusy(false); }
  }
  return <section className="rr-card rr-case-panel">
    <h3><FolderOpen size={20} aria-hidden="true" /> Vụ việc liên quan</h3>
    {state.error ? <><ErrorBox>{state.error}</ErrorBox><button onClick={state.reload}>Thử lại</button></> : !data ? <p role="status">Đang đối chiếu các báo cáo…</p> : <>
      {current.case_id ? <>
        <div className="rr-callout"><strong>#{current.case_id} · {current.title}</strong><p>Phụ trách: {current.owner} · {remaining ? `${remaining}/${data.totalMembers} báo cáo chưa kết thúc` : 'Tất cả báo cáo hiện có đã kết thúc'}</p></div>
        <p className="rr-muted">Xem bằng chứng và kết luận riêng của từng báo cáo. Liên kết không làm thay đổi nội dung người gửi.</p>
        <ul className="rr-case-members">{data.members.map(row => <li key={row.report_id}>
          <div><strong>#{row.report_id} · {row.reason_label_snapshot || 'Phản ánh'}</strong><p className="rr-excerpt">{row.description}</p><small>{date(row.created_at)}</small></div>
          <Badge value={row.status} />
          {row.report_id === report.report_id ? <span className="rr-muted">Đang xem</span> : <button onClick={() => onOpen(row.report_id)}>Xem báo cáo #{row.report_id}</button>}
        </li>)}</ul>
        {data.totalPages>1 && <nav className="rr-pagination" aria-label="Trang báo cáo trong vụ việc"><button disabled={data.page<=1} onClick={()=>setPage(data.page-1)}>Trang trước</button><span>{data.page}/{data.totalPages}</span><button disabled={data.page>=data.totalPages} onClick={()=>setPage(data.page+1)}>Trang sau</button></nav>}
        {data.canUnlink && <button onClick={() => open('unlink')}>Tách báo cáo này khỏi vụ việc</button>}
      </> : <>
        <p className="rr-muted">Báo cáo này chưa thuộc vụ việc nào. Chỉ liên kết khi đã đối chiếu và xác định cùng vấn đề cần xử lý.</p>
        {data.canLink && <div className="rr-actions"><button onClick={() => open('create')}><FolderOpen size={16} aria-hidden="true" /> Tạo vụ việc từ báo cáo này</button>{cases.length > 0 && <button onClick={() => open('link')}><Link2 size={16} aria-hidden="true" /> Liên kết vụ việc có sẵn</button>}</div>}
      </>}
      {!!data.candidates.length && <details className="rr-case-candidates"><summary>Báo cáo có thể liên quan ({data.candidates.length}{data.candidates.length===50 ? '+' : ''})</summary>
        <p className="rr-muted">Gợi ý theo đối tượng hoặc đồ vật liên quan. Cùng bài đăng chưa chắc là cùng vụ việc; báo cáo đã kết thúc chỉ dùng để tham khảo.</p>
        {data.candidates.map(row => <div className="rr-record" key={row.report_id}><strong>#{row.report_id} · {row.reason_label_snapshot || 'Phản ánh'}</strong><p className="rr-excerpt">{row.description}</p><div className="rr-row"><Badge value={row.status} /><small>{date(row.created_at)}{row.case_id ? ` · Vụ việc #${row.case_id} · ${row.owner}` : ' · Chưa liên kết'}</small><button onClick={() => onOpen(row.report_id)}>Đối chiếu #{row.report_id}</button></div></div>)}
      </details>}
    </>}
    {mode && <Dialog title={mode==='create' ? 'Tạo vụ việc' : mode==='link' ? 'Liên kết vụ việc' : 'Tách báo cáo khỏi vụ việc'} onClose={close} busy={busy}>
      {discard && <div className="rr-callout" role="alert"><strong>Bỏ nội dung chưa lưu?</strong><div className="rr-actions"><button onClick={() => setDiscard(false)}>Tiếp tục nhập</button><button onClick={() => setMode(null)}>Bỏ thay đổi</button></div></div>}
      <form className="rr-stack" onSubmit={submit} style={discard ? {display:'none'} : undefined}>
        <p>Áp dụng cho báo cáo <strong>#{report.report_id}</strong>. Bằng chứng và kết luận không bị gộp hay xóa.</p>
        {mode==='create' && <label>Tên vụ việc *<input autoFocus required maxLength={200} value={title} disabled={busy} onChange={e => setTitle(e.target.value)} placeholder="Nêu vấn đề và đồ vật cần đối chiếu" /><small>Bạn sẽ phụ trách vụ việc và nhận xử lý báo cáo này.</small></label>}
        {mode==='link' && <label>Vụ việc cần liên kết *<select required disabled={busy} value={caseId} onChange={e => setCaseId(e.target.value)}><option value="">Chọn vụ việc liên quan</option>{cases.map(row => <option key={row.case_id} value={row.case_id}>#{row.case_id} · {row.case_title} · {row.owner}</option>)}</select><small>Chỉ người phụ trách vụ việc được thêm báo cáo. Báo cáo do quản lý khác nhận xử lý sẽ không bị tự chuyển.</small></label>}
        <label>{mode==='unlink' ? 'Lý do tách' : 'Căn cứ xác định cùng vụ việc'} *<textarea required maxLength={2000} rows={3} disabled={busy} value={reason} onChange={e => setReason(e.target.value)} /><small>Chỉ quản lý xem được phần căn cứ này.</small></label>
        <ErrorBox>{error}</ErrorBox>
        <footer className="rr-actions"><button type="button" disabled={busy} onClick={close}>Quay lại</button><button className="rr-primary" disabled={busy || !reason.trim() || (mode==='create' && !title.trim()) || (mode==='link' && !caseId)}>{busy ? 'Đang lưu…' : mode==='unlink' ? 'Xác nhận tách' : 'Xác nhận liên kết'}</button></footer>
      </form>
    </Dialog>}
  </section>;
}
