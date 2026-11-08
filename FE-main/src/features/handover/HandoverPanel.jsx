import { useEffect,useRef,useState } from 'react';
import { useChat } from '@/features/chat/ChatProvider';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { getHandover,uploadHandover,confirmHandover } from './handoverApi';
import './Handover.css';
const statusLabels={NOT_RECORDED:'Chưa ghi nhận giao đồ',WAITING_CONFIRMATION:'Chờ người nhận xác nhận',COMPLETED:'Đã nhận đồ',DISPUTED:'Có sự cố — chờ Manager xử lý'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});
export default function HandoverPanel({claim,onChanged}) {
  const {revision}=useChat();
  const [data,setData]=useState(null),[error,setError]=useState(''),[note,setNote]=useState(''),[files,setFiles]=useState([]),[busy,setBusy]=useState(false),[image,setImage]=useState(null),[refresh,setRefresh]=useState(0);
  const inFlight=useRef(false),request=useRef(null);
  const [issueOpen,setIssueOpen]=useState(false),[reason,setReason]=useState(''),[issueFiles,setIssueFiles]=useState([]);
  const issueRequest=useRef(null);
  useEffect(()=>{const refetch=()=>setRefresh(n=>n+1);window.addEventListener('focus',refetch);return()=>window.removeEventListener('focus',refetch);},[]);
  useEffect(()=>{let cancelled=false;getHandover(claim.id).then(value=>{if(!cancelled){setData(value);setError('');}}).catch(e=>{if(!cancelled)setError(e.message);});return()=>{cancelled=true;};},[claim.id,claim.version,revision,refresh]);
  async function record(event) {
    event.preventDefault();if(inFlight.current)return;
    if(!window.confirm('Bạn xác nhận đã giao đồ thực tế cho người nhận? Ghi nhận này không được sửa hoặc rút lại.'))return;
    inFlight.current=true;setBusy(true);setError('');
    request.current ||= {note:note.trim()||null,expectedClaimVersion:claim.version,clientRequestId:crypto.randomUUID()};
    try{setData(await uploadHandover(claim.id,request.current,files));setFiles([]);setNote('');request.current=null;onChanged();}
    catch(e){setError(e.message);setRefresh(n=>n+1);}finally{inFlight.current=false;setBusy(false);}
  }
  const evidence=(data?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh giao đồ'}));
  const issueEvidence=(data?.issue?.evidence||[]).map(item=>({...item,name:item.name||'Ảnh sự cố'}));
  const gallery=[...evidence.map(item=>({...item,id:`h:${item.id}`})),...issueEvidence.map(item=>({...item,id:`r:${item.id}`}))];
  async function reportIssue(event) {
    event.preventDefault();if(inFlight.current||!reason.trim())return;
    if(!window.confirm('Gửi báo cáo sự cố và khóa hoàn tất bàn giao để chờ Manager? Báo cáo không được sửa hoặc rút lại.'))return;
    inFlight.current=true;setBusy(true);setError('');
    issueRequest.current ||= {reason:reason.trim(),expectedVersion:data.version,clientRequestId:crypto.randomUUID()};
    try {setData(await uploadHandover(claim.id,issueRequest.current,issueFiles,'/issues'));setIssueOpen(false);setReason('');setIssueFiles([]);issueRequest.current=null;onChanged();}
    catch(e){setError(e.message);setRefresh(n=>n+1);}finally{inFlight.current=false;setBusy(false);}
  }
  async function confirmReceipt() {
    if(inFlight.current||!window.confirm('Bạn đã nhận đúng đồ và muốn hoàn tất? Sau xác nhận, không thể báo sự cố bàn giao tại đây.'))return;
    inFlight.current=true;setBusy(true);setError('');
    try{setData(await confirmHandover(claim.id,data.version));onChanged();}catch(e){setError(e.message);setRefresh(n=>n+1);}finally{inFlight.current=false;setBusy(false);}
  }
  return <section id="handover" className="claim-panel handover-panel"><h2>Tiến trình nhận đồ</h2>
    {error&&<p role="alert" className="claim-alert">{error}</p>}{!data&&!error&&<p>Đang tải bàn giao…</p>}
    {data&&<><p className="claim-notice">{statusLabels[data.status]}</p>{data.recordedAt&&<details className="workflow-disclosure"><summary>Chi tiết giao đồ và bằng chứng</summary><p>Người nhặt ghi nhận giao lúc {date(data.recordedAt)}</p>{data.note&&<p className="claim-prose">{data.note}</p>}
    <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} type="button" onClick={()=>setImage(i)} aria-label="Xem ảnh giao đồ"><img src={item.url} alt={item.name}/></button>)}</div>
    </details>}
    {data.canRecord&&<form className="claim-form" onSubmit={record}>
      <details className="workflow-disclosure"><summary>Thêm ghi chú hoặc ảnh bàn giao</summary><label>Ghi chú giao đồ (tùy chọn)<textarea maxLength={2000} disabled={busy} value={note} onChange={e=>{setNote(e.target.value);request.current=null;}}/></label>
      <label>Ảnh bàn giao (tối đa 5 JPG/PNG, 5 MB/ảnh)<input type="file" accept="image/jpeg,image/png" multiple disabled={busy} onChange={e=>{const selected=[...e.target.files];e.target.value='';if(selected.length>5||selected.some(f=>!['image/jpeg','image/png'].includes(f.type)||!f.size||f.size>5242880)){setError('Chọn tối đa 5 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.');return;}setFiles(selected);request.current=null;}}/></label>
      {files.map((file,i)=><p key={i}>{file.name}<button type="button" disabled={busy} onClick={()=>{setFiles(old=>old.filter((_,n)=>n!==i));request.current=null;}}> — Bỏ ảnh</button></p>)}
      </details><button className="btn btn-accent" disabled={busy}>{busy?'Đang ghi nhận…':'Tôi đã giao đồ'}</button>
    </form>}
    {data.status==='NOT_RECORDED'&&!data.canRecord&&<p className="claims-muted">Chờ người nhặt ghi nhận đã giao đồ sau khi claim được chấp thuận.</p>}
    {data.canConfirm&&<div className="handover-actions"><button type="button" className="btn btn-accent" disabled={busy} onClick={confirmReceipt}>Tôi đã nhận đúng đồ</button></div>}
    {data.canReportIssue&&<button type="button" className="btn btn-ghost" disabled={busy} onClick={()=>setIssueOpen(value=>!value)}>Báo sự cố bàn giao</button>}
    {data.canReportIssue&&issueOpen&&<form className="claim-form" onSubmit={reportIssue}>
      <label>Lý do sự cố *<textarea required maxLength={5000} disabled={busy} value={reason} onChange={e=>{setReason(e.target.value);issueRequest.current=null;}}/></label>
      <label>Ảnh sự cố (tùy chọn, tối đa 5 JPG/PNG, 5 MB/ảnh)<input type="file" multiple accept="image/jpeg,image/png" disabled={busy} onChange={e=>{const selected=[...e.target.files];e.target.value='';if(selected.length>5||selected.some(f=>!['image/jpeg','image/png'].includes(f.type)||!f.size||f.size>5242880)){setError('Chọn tối đa 5 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.');return;}setIssueFiles(selected);issueRequest.current=null;}}/></label>
      {issueFiles.map((file,i)=><p key={i}>{file.name}<button type="button" disabled={busy} onClick={()=>{setIssueFiles(old=>old.filter((_,n)=>n!==i));issueRequest.current=null;}}> — Bỏ ảnh</button></p>)}
      <button type="submit" className="btn btn-accent" disabled={busy||!reason.trim()}>{busy?'Đang gửi…':'Gửi báo cáo sự cố'}</button>
    </form>}
    {data.issue&&<section className="claim-notice"><h3>Báo cáo sự cố #{data.issue.id}</h3><p className="claim-prose">{data.issue.description}</p><p>Đang chờ Manager xử lý. Hai bên vẫn chat được; không thể hoàn tất hoặc hủy claim.</p>
      <div className="report-evidence">{issueEvidence.map((item,i)=><button type="button" key={item.id} onClick={()=>setImage(evidence.length+i)} aria-label="Xem ảnh sự cố"><img src={item.url} alt={item.name}/></button>)}</div></section>}
    {data.confirmedAt&&<p className="claim-notice">Người nhận xác nhận lúc {date(data.confirmedAt)}. Bài đã trao trả; chat chỉ đọc.</p>}
    </>}
    {image!==null&&<ClaimEvidencePreview images={gallery} initialIndex={image} onClose={()=>setImage(null)}/>}
  </section>;
}
