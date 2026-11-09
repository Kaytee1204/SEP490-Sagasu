import { useEffect,useState } from 'react';
import { useChat } from '@/features/chat/ChatProvider';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { getHandover } from './handoverApi';
import RecordHandoverDialog from './RecordHandoverDialog';
import ConfirmReceiptDialog from './ConfirmReceiptDialog';
import ReportHandoverIssueDialog from './ReportHandoverIssueDialog';
import './Handover.css';
const statusLabels={NOT_RECORDED:'Chưa ghi nhận giao đồ',WAITING_CONFIRMATION:'Chờ người nhận xác nhận',COMPLETED:'Đã nhận đồ',DISPUTED:'Có sự cố — chờ Manager xử lý'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});
export default function HandoverPanel({claim,onChanged}) {
  const {revision}=useChat();
  const [data,setData]=useState(null),[error,setError]=useState(''),[image,setImage]=useState(null),[refresh,setRefresh]=useState(0);
  const [recordOpen,setRecordOpen]=useState(false);
  const [confirmOpen,setConfirmOpen]=useState(false);
  const [issueOpen,setIssueOpen]=useState(false);
  useEffect(()=>{const refetch=()=>setRefresh(n=>n+1);window.addEventListener('focus',refetch);return()=>window.removeEventListener('focus',refetch);},[]);
  useEffect(()=>{let cancelled=false;getHandover(claim.id).then(value=>{if(!cancelled){setData(value);setError('');}}).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[claim.id,claim.version,revision,refresh]);
  const evidence=(data?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh giao đồ'}));
  const issueEvidence=(data?.issue?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh sự cố'}));
  const gallery=[...evidence.map(item=>({...item,id:`h:${item.id}`})),...issueEvidence.map(item=>({...item,id:`r:${item.id}`}))];
  return <section id="handover" className="claim-panel handover-panel"><h2>Tiến trình nhận đồ</h2>
    {error&&<p role="alert" className="claim-alert">{error}</p>}{!data&&!error&&<p>Đang tải bàn giao…</p>}
    {data&&<><p className="claim-notice">{statusLabels[data.status]}</p>{data.recordedAt&&<details className="workflow-disclosure"><summary>Chi tiết giao đồ và bằng chứng</summary><p>Người nhặt ghi nhận giao lúc {date(data.recordedAt)}</p>{data.note&&<p className="claim-prose">{data.note}</p>}
    <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} type="button" onClick={()=>setImage(i)} aria-label="Xem ảnh giao đồ"><img src={item.url} alt={item.name}/></button>)}</div>
    </details>}
    {data.canRecord&&<button type="button" className="btn btn-accent" onClick={()=>setRecordOpen(true)}>Tôi đã giao đồ</button>}
    {data.status==='NOT_RECORDED'&&!data.canRecord&&<p className="claims-muted">Chờ người nhặt ghi nhận đã giao đồ sau khi claim được chấp thuận.</p>}
    {data.canConfirm&&<div className="handover-actions"><button type="button" className="btn btn-accent" onClick={()=>setConfirmOpen(true)}>Tôi đã nhận đúng đồ</button></div>}
    {data.canReportIssue&&<button type="button" className="btn btn-ghost" onClick={()=>setIssueOpen(true)}>Báo sự cố bàn giao</button>}
    {data.issue&&<section className="claim-notice"><h3>Báo cáo sự cố #{data.issue.id}</h3><p className="claim-prose">{data.issue.description}</p><p>Đang chờ Manager xử lý. Hai bên vẫn chat được; không thể hoàn tất hoặc hủy claim.</p>
      <div className="report-evidence">{issueEvidence.map((item,i)=><button type="button" key={item.id} onClick={()=>setImage(evidence.length+i)} aria-label="Xem ảnh sự cố"><img src={item.url} alt={item.name}/></button>)}</div></section>}
    {data.confirmedAt&&<p className="claim-notice">Người nhận xác nhận lúc {date(data.confirmedAt)}. Bài đã trao trả; chat chỉ đọc.</p>}
    </>}
    {recordOpen&&data?.canRecord&&<RecordHandoverDialog claim={claim} onClose={()=>setRecordOpen(false)}
      onRecorded={result=>{setData(result);setRecordOpen(false);setError('');onChanged();}}/>}
    {confirmOpen&&data?.canConfirm&&<ConfirmReceiptDialog claim={claim} handover={data}
      onClose={()=>setConfirmOpen(false)} onConfirmed={result=>{setData(result);setConfirmOpen(false);setError('');onChanged();}}
      onReportIssue={data.canReportIssue?()=>{setConfirmOpen(false);setIssueOpen(true);}:undefined}/>}
    {issueOpen&&data?.canReportIssue&&<ReportHandoverIssueDialog claim={claim} handover={data}
      onClose={()=>setIssueOpen(false)} onReported={result=>{setData(result);setIssueOpen(false);setError('');onChanged();}}/>}
    {image!==null&&<ClaimEvidencePreview images={gallery} initialIndex={image} onClose={()=>setImage(null)}/>}
  </section>;
}
