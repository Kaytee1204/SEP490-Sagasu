import { useEffect, useState } from 'react';
import { ArrowLeft, RefreshCw, FolderOpen } from 'lucide-react';
import { reviewApi } from '../api';
import { Badge, ErrorBox, Empty, Gallery } from './Shared';
import { date, labels } from '../format';
import useLoad from '../../office/hooks/useLoad';
import ConclusionDialog from './ConclusionDialog';
import ReportedContent from './ReportedContent';

const workStages={NEW:'Chưa nhận xử lý',MINE:'Tôi đang xử lý',WAITING:'Chờ phản hồi',UPDATED:'Có thông tin mới',DONE:'Đã kết thúc',ALL:'Tất cả'};
export default function ReportWorkbench({ target, onTarget, onOpen }) {
  const [stage,setStage]=useState('NEW'),[search,setSearch]=useState(''),[q,setQ]=useState(''),[page,setPage]=useState(1);
  const [selected,setSelected]=useState([]),[decision,setDecision]=useState(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [notice,setNotice]=useState('');
  const [initialOutcome,setInitialOutcome]=useState('');
  useEffect(()=>{const timer=setTimeout(()=>{setQ(search);setPage(1);},300);return()=>clearTimeout(timer);},[search]);
  const state=useLoad(()=>target?reviewApi.targetReports(target,page):reviewApi.workQueue({stage,q,page}),`${target}:${stage}:${q}:${page}`);
  const data=state.data;
  const checked=(data?.content||[]).filter(row=>selected.includes(row.report_id));
  async function conclude(outcome='') {
    setBusy(true);setError('');
    try {const detail=await reviewApi.detail(checked[0].report_id);setInitialOutcome(outcome);setDecision(detail);}catch(e){setError(e.message);}finally{setBusy(false);}
  }
  async function takeSelected(){setBusy(true);setError('');try{const result=await reviewApi.concludeSelected(checked.map(r=>({id:r.report_id,version:r.version})),{version:0,action:'TAKE'});setNotice(`Đã nhận xử lý ${result.completed} báo cáo.`);setSelected([]);state.reload();}catch(e){setError(e.message);}finally{setBusy(false);}}
  return <div className="rr-stack">
    {target ? <div className="rr-row"><button onClick={()=>{onTarget(null);setPage(1);setSelected([]);}}><ArrowLeft size={16}/> Hàng đợi xử lý</button><span className="rr-case-tag">{target}</span></div> : <nav className="rr-tabs" aria-label="Công việc xử lý báo cáo">{Object.entries(workStages).map(([value,label])=><button key={value} aria-current={stage===value?'page':undefined} className={stage===value?'active':''} onClick={()=>{setStage(value);setPage(1);}}>{label}</button>)}</nav>}
    <section className="rr-card">
      <div className="rr-row"><div><h2>{target?'Xem xét nội dung bị báo cáo':'Nội dung cần xem xét'}</h2><p className="rr-muted">{target?'Đọc nội dung, đối chiếu các phản ánh rồi chọn kết quả. Nếu cần hỏi thêm hoặc xác minh trao trả, mở phản ánh tương ứng.':'Mở một mục để xem nội dung và các phản ánh tại cùng một nơi.'}</p></div><button disabled={state.loading} onClick={state.reload}><RefreshCw size={16}/> Làm mới</button></div>
      {!target&&<label>Tìm nội dung, tên đồ vật hoặc mã báo cáo<input type="search" maxLength={200} value={search} onChange={e=>setSearch(e.target.value)}/></label>}
      <ErrorBox>{state.error||error}</ErrorBox>
      {notice&&<p className="rr-callout" role="status">{notice}</p>}
      {state.loading?<Empty>Đang tải công việc…</Empty>:data&&!data.content.length?<Empty>Không có mục phù hợp trong hàng đợi này.</Empty>:null}
      {data&&<><p className="rr-muted">{data.totalElements} {target?'báo cáo':'đối tượng'} · Trang {data.page}/{data.totalPages}</p>
      <div className={target?'rr-moderation-layout':''}>
      {target&&<ReportedContent context={data.context}/>}
      <div className="rr-moderation-reports">
      {target&&<div className="rr-row"><h2>Các phản ánh</h2><button disabled={busy} onClick={()=>setSelected(data.content.filter(r=>r.kind==='GENERAL'&&['OPEN','IN_REVIEW'].includes(r.status)).map(r=>r.report_id))}>Chọn các phản ánh chưa kết thúc trên trang</button></div>}
      <div className="rr-work-items">{data.content.map(row=>target?<article className="rr-work-item" key={row.report_id}>
        <div className="rr-row"><div><h3>Báo cáo #{row.report_id}</h3><p className="rr-muted">{row.reporter} · {date(row.created_at)}</p></div><Badge value={row.status}/></div>
        <strong>{row.reason_label_snapshot||labels[row.kind]}</strong><p>{row.reason_label_snapshot&&row.description.startsWith(row.reason_label_snapshot)?row.description.slice(row.reason_label_snapshot.length).trim():row.description}</p><Gallery images={row.evidence}/>
        <p className="rr-muted">{workStages[row.stage]||'Đang xác minh'} · {row.assignee||'Chưa có người phụ trách'}</p>
        {row.resolution_note&&<div className="rr-callout"><strong>Kết quả</strong><p>{row.resolution_note}</p></div>}
        <div className="rr-row">{row.kind==='GENERAL'&&['OPEN','IN_REVIEW'].includes(row.status)&&<label className="rr-check"><input type="checkbox" disabled={busy} checked={selected.includes(row.report_id)} onChange={e=>setSelected(old=>e.target.checked?[...old,row.report_id]:old.filter(id=>id!==row.report_id))}/>Chọn báo cáo #{row.report_id}</label>}<button onClick={()=>onOpen(row.report_id)}>{row.kind==='HANDOVER_ISSUE'?'Xác minh tranh chấp trao trả':'Hỏi thêm & xem lịch sử'} →</button></div>
      </article>:<article className="rr-work-item" key={row.target_key}>
        <div className="rr-row"><h3><FolderOpen size={18} aria-hidden="true"/> {row.title||({P:'Bài đăng',C:'Yêu cầu nhận đồ',M:'Tin nhắn',U:'Tài khoản'}[row.target_key[0]]+' #'+row.target_key.slice(2))}</h3><span className="rr-case-tag">{row.report_count} phản ánh</span></div>
        {row.dispute&&<p className="rr-callout">Có vướng mắc trao trả cần xác minh.</p>}
        <p>{row.reasons.filter(Boolean).join(' · ')}</p><p className="rr-muted">Chờ từ {date(row.oldest_at)} · Cập nhật {date(row.latest_at)}</p>
        <button className="rr-primary" onClick={()=>{setPage(1);setSelected([]);setNotice('');onTarget(row.target_key);}}>Xem xét nội dung →</button>
      </article>)}</div></div></div>
      {target&&<section className="rr-moderation-actions" aria-label="Kết quả xem xét"><strong>{checked.length ? `Đang xử lý ${checked.length} phản ánh đã chọn` : 'Chọn phản ánh sau khi đối chiếu nội dung'}</strong><p className="rr-muted">Kết quả chỉ áp dụng cho phản ánh được chọn. Vướng mắc trao trả cần xác minh riêng.</p><div className="rr-actions"><button disabled={!checked.length||busy} onClick={()=>conclude('UNSUBSTANTIATED')}>Giữ nguyên · Chưa đủ căn cứ vi phạm</button><button disabled={!checked.length||busy} className="rr-primary" onClick={()=>conclude('VIOLATION')}>Có vi phạm · Chọn biện pháp</button><button disabled={!checked.length||busy} onClick={()=>conclude('SETTLED')}>Vấn đề đã được giải quyết</button></div><details><summary>Theo dõi xử lý</summary><div className="rr-actions"><button disabled={!checked.length||busy||checked.some(r=>r.assigned_to)} onClick={takeSelected}>Nhận xử lý để xác minh tiếp</button><button disabled={!checked.length||busy} onClick={()=>setSelected([])}>Bỏ chọn</button></div></details></section>}
      <nav className="rr-pagination" aria-label="Trang công việc"><button disabled={page<=1} onClick={()=>{setSelected([]);setPage(page-1);}}>Trước</button><span>{data.page}/{data.totalPages}</span><button disabled={page>=data.totalPages} onClick={()=>{setSelected([]);setPage(page+1);}}>Sau</button></nav></>}
    </section>
    {decision&&<ConclusionDialog data={decision} initialOutcome={initialOutcome} selectedReports={checked} onClose={()=>setDecision(null)} onSaved={result=>{setNotice(`Đã kết luận ${result.completed} báo cáo. Các phản ánh chưa chọn vẫn giữ nguyên.`);setSelected([]);setDecision(null);state.reload();}}/>}
  </div>;
}
