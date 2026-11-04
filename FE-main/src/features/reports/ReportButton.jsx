import { useEffect,useId,useRef,useState } from 'react';
import { Flag,X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { createReport } from './reportApi';
import './Reports.css';
export default function ReportButton({type,id,label='Báo cáo'}) {
  const [open,setOpen]=useState(false),[sent,setSent]=useState(null);
  return <><button type="button" className="report-trigger" onClick={()=>setOpen(true)} title={label} aria-label={label}><Flag size={15}/><span>{label}</span></button>
    {sent && <Link className="claims-muted" role="status" to={`/bao-cao/${sent}`}>Đã gửi báo cáo #{sent} — Xem hồ sơ</Link>}
    {open && <ReportForm type={type} id={id} close={()=>setOpen(false)} done={result=>{setSent(result.id);setOpen(false);}}/>}</>;
}
function ReportForm({type,id,close,done}) {
  const dialog=useRef(null),title=useId(),sending=useRef(false),retry=useRef(null);
  const [reason,setReason]=useState(''),[files,setFiles]=useState([]),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  useEffect(()=>{const el=dialog.current,focus=document.activeElement,overflow=document.body.style.overflow;el.showModal();document.body.style.overflow='hidden';return()=>{el.close();document.body.style.overflow=overflow;focus?.isConnected&&focus.focus();};},[]);
  async function send(event) {
    event.preventDefault(); if(sending.current)return;
    sending.current=true;setBusy(true);setError('');
    retry.current ||= {targetType:type,targetId:Number(id),description:reason.trim(),clientRequestId:crypto.randomUUID()};
    try {done(await createReport(retry.current,files));}catch(problem){setError(problem.message);}finally{sending.current=false;setBusy(false);}
  }
  return <dialog ref={dialog} className="report-dialog" aria-labelledby={title} onCancel={e=>{e.preventDefault();if(!sending.current)close();}}>
    <header><h2 id={title}>Gửi báo cáo</h2><button disabled={busy} type="button" aria-label="Đóng" onClick={close}><X/></button></header>
    <form onSubmit={send}><p>Báo cáo được gửi đến Manager để xem xét. Nội dung đã gửi được giữ nguyên.</p>
      <label>Lý do báo cáo *<textarea required maxLength={5000} value={reason} disabled={busy} onChange={e=>{setReason(e.target.value);retry.current=null;}}/></label>
      <label>Ảnh bằng chứng (tùy chọn, tối đa 5 ảnh JPG/PNG, 5 MB/ảnh)<input type="file" accept="image/jpeg,image/png" multiple disabled={busy} onChange={e=>{const picked=[...e.target.files];e.target.value='';if(picked.length>5||picked.some(f=>!['image/jpeg','image/png'].includes(f.type)||!f.size||f.size>5242880)){setError('Chọn tối đa 5 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.');return;}setFiles(picked);retry.current=null;setError('');}}/></label>
      {files.map((file,i)=><div key={i}>{file.name}<button type="button" disabled={busy} onClick={()=>{setFiles(old=>old.filter((_,n)=>n!==i));retry.current=null;}}>Bỏ ảnh</button></div>)}
      {error&&<p className="report-error" role="alert">{error}</p>}
      <footer><button className="btn btn-ghost" type="button" disabled={busy} onClick={close}>Hủy</button><button className="btn btn-accent" disabled={busy||!reason.trim()}>{busy?'Đang gửi…':'Gửi báo cáo'}</button></footer>
    </form></dialog>;
}
