import { useEffect,useId,useRef,useState } from 'react';
import { Flag,X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { createReport } from './reportApi';
import './Reports.css';
export default function ReportButton({type,id,label='Báo cáo',targetLabel}) {
  const [open,setOpen]=useState(false),[sent,setSent]=useState(null);
  return <><button type="button" className="report-trigger" onClick={()=>setOpen(true)} title={label} aria-label={label}><Flag size={15}/><span>{label}</span></button>
    {sent && <Link className="claims-muted" role="status" to={`/bao-cao/${sent}`}>Đã gửi báo cáo — Theo dõi xử lý</Link>}
    {open && <ReportForm type={type} id={id} targetLabel={targetLabel} close={()=>setOpen(false)} done={result=>{setSent(result.id);setOpen(false);}}/>}</>;
}
function ReportForm({type,id,targetLabel,close,done}) {
  const dialog=useRef(null),title=useId(),sending=useRef(false),retry=useRef(null);
  const [preset,setPreset]=useState('');
  const [discard,setDiscard]=useState(false);
  const requestClose=()=>{if(sending.current)return;if(preset||reason.trim()||files.length)setDiscard(true);else close();};
  const [reason,setReason]=useState(''),[files,setFiles]=useState([]),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  useEffect(()=>{const el=dialog.current,focus=document.activeElement,overflow=document.body.style.overflow;el.showModal();document.body.style.overflow='hidden';return()=>{el.close();document.body.style.overflow=overflow;focus?.isConnected&&focus.focus();};},[]);
  async function send(event) {
    event.preventDefault(); if(sending.current||(!preset&&!reason.trim()))return;
    sending.current=true;setBusy(true);setError('');
    retry.current ||= {targetType:type,targetId:Number(id),description:[preset,reason.trim()].filter(Boolean).join(' — '),clientRequestId:crypto.randomUUID()};
    try {done(await createReport(retry.current,files));}catch(problem){setError(problem.message);}finally{sending.current=false;setBusy(false);}
  }
  return <dialog ref={dialog} className="report-dialog" aria-labelledby={title} onCancel={e=>{e.preventDefault();requestClose();}}>
    <header><h2 id={title}>Gửi báo cáo</h2><button disabled={busy} type="button" aria-label="Đóng" onClick={requestClose}><X/></button></header>
    <form onSubmit={send}><p>Đang báo cáo: <strong>{targetLabel || ({POST:'Bài đăng', CLAIM:'Yêu cầu nhận đồ', MESSAGE:'Tin nhắn', USER:'Tài khoản'}[type] + ' #' + id)}</strong> · Báo cáo được gửi đến quản lý để xem xét.</p>
      <fieldset className="report-reasons"><legend>Chọn nhanh lý do rồi bổ sung chi tiết</legend>
        {['Thông tin sai lệch', 'Nghi ngờ gian lận', 'Nội dung không phù hợp', 'Lý do khác'].map(value => <button type="button" key={value} disabled={busy} aria-pressed={preset === value || (value === 'Lý do khác' && !preset)} onClick={() => {setPreset(value === 'Lý do khác' ? '' : value);retry.current=null;}}>{value}</button>)}
      </fieldset>
      <label>{preset ? 'Mô tả thêm (không bắt buộc)' : 'Mô tả lý do *'}<textarea required={!preset} maxLength={4800} value={reason} disabled={busy} onChange={e=>{setReason(e.target.value);retry.current=null;}}/></label>
      <label>Ảnh bằng chứng (tùy chọn, tối đa 5 ảnh JPG/PNG, 5 MB/ảnh)<input type="file" accept="image/jpeg,image/png" multiple disabled={busy} onChange={e=>{const picked=[...e.target.files];e.target.value='';if(files.length+picked.length>5||picked.some(f=>!['image/jpeg','image/png'].includes(f.type)||!f.size||f.size>5242880)){setError('Chọn tối đa 5 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.');return;}setFiles(old=>[...old,...picked]);retry.current=null;setError('');}}/></label>
      {files.map((file,i)=><div key={i}>{file.name}<button type="button" disabled={busy} onClick={()=>{setFiles(old=>old.filter((_,n)=>n!==i));retry.current=null;}}>Bỏ ảnh</button></div>)}
      {error&&<p className="report-error" role="alert">{error}</p>}
      {discard ? <div role="alert"><p>Bỏ nội dung báo cáo đang nhập?</p><footer>
        <button type="button" className="btn btn-primary" onClick={()=>setDiscard(false)}>Tiếp tục nhập</button>
        <button type="button" className="btn btn-ghost" onClick={close}>Bỏ nội dung và đóng</button></footer></div> : <footer><button className="btn btn-ghost" type="button" disabled={busy} onClick={requestClose}>Hủy</button><button className="btn btn-accent" disabled={busy||(!preset&&!reason.trim())}>{busy?'Đang gửi…':'Gửi báo cáo'}</button></footer>}
    </form></dialog>;
}
