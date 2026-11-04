import { useEffect,useRef,useState } from 'react';
import { useChat } from '@/features/chat/ChatProvider';
import ClaimEvidencePreview from '@/features/claims/components/ClaimEvidencePreview';
import { getHandover,uploadHandover } from './handoverApi';
import './Handover.css';
const statusLabels={NOT_RECORDED:'Chưa ghi nhận giao đồ',WAITING_CONFIRMATION:'Chờ người nhận xác nhận',COMPLETED:'Đã nhận đồ',DISPUTED:'Có sự cố — chờ Manager xử lý'};
const date=value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'});
export default function HandoverPanel({claim,onChanged}) {
  const {revision}=useChat();
  const [data,setData]=useState(null),[error,setError]=useState(''),[note,setNote]=useState(''),[files,setFiles]=useState([]),[busy,setBusy]=useState(false),[image,setImage]=useState(null),[refresh,setRefresh]=useState(0);
  const inFlight=useRef(false),request=useRef(null);
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
  return <section className="claim-panel handover-panel"><h2>Bàn giao đồ P2P</h2>
    {error&&<p role="alert" className="claim-alert">{error}</p>}{!data&&!error&&<p>Đang tải bàn giao…</p>}
    {data&&<><p className="claim-notice">{statusLabels[data.status]}</p>{data.recordedAt&&<p>Người nhặt ghi nhận giao lúc {date(data.recordedAt)}</p>}{data.note&&<p className="claim-prose">{data.note}</p>}
    <div className="report-evidence">{evidence.map((item,i)=><button key={item.id} type="button" onClick={()=>setImage(i)} aria-label="Xem ảnh giao đồ"><img src={item.url} alt={item.name}/></button>)}</div>
    {data.canRecord&&<form className="claim-form" onSubmit={record}>
      <label>Ghi chú giao đồ (tùy chọn)<textarea maxLength={2000} disabled={busy} value={note} onChange={e=>{setNote(e.target.value);request.current=null;}}/></label>
      <label>Ảnh bàn giao (tối đa 5 JPG/PNG, 5 MB/ảnh)<input type="file" accept="image/jpeg,image/png" multiple disabled={busy} onChange={e=>{const selected=[...e.target.files];e.target.value='';if(selected.length>5||selected.some(f=>!['image/jpeg','image/png'].includes(f.type)||!f.size||f.size>5242880)){setError('Chọn tối đa 5 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.');return;}setFiles(selected);request.current=null;}}/></label>
      {files.map((file,i)=><p key={i}>{file.name}<button type="button" disabled={busy} onClick={()=>{setFiles(old=>old.filter((_,n)=>n!==i));request.current=null;}}> — Bỏ ảnh</button></p>)}
      <button className="btn btn-accent" disabled={busy}>{busy?'Đang ghi nhận…':'Ghi nhận đã giao đồ'}</button>
    </form>}
    {data.status==='NOT_RECORDED'&&!data.canRecord&&<p className="claims-muted">Chờ người nhặt ghi nhận đã giao đồ sau khi claim được chấp thuận.</p>}
    {data.status==='WAITING_CONFIRMATION'&&<p className="claims-muted">Ghi nhận giao đồ đã được lưu. Chờ người nhận phản hồi; chưa phải xác nhận đã nhận đồ.</p>}
    </>}
    {image!==null&&<ClaimEvidencePreview images={evidence} initialIndex={image} onClose={()=>setImage(null)}/>}
  </section>;
}
