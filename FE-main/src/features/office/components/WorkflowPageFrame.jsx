import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Dialog from './Dialog';

export default function WorkflowPageFrame({ title, onClose, busy, resetKey, children, hideBack=false, personal=false, className="" }) {
  const [edited,setEdited]=useState(null),[pending,setPending]=useState(null);
  const navigate=useNavigate();
  const dirty=edited?.key===resetKey && edited?.dirty, discard=!!pending && pending.key===resetKey;
  function guard(action=onClose){if(busy)return;if(dirty)setPending({key:resetKey,action});else action();}
  useEffect(()=>{if(!dirty)return;const prevent=e=>{e.preventDefault();e.returnValue='';};window.addEventListener('beforeunload',prevent);return()=>window.removeEventListener('beforeunload',prevent);},[dirty]);
  return <main className={`office-page ${personal ? "office-personal op-transfer-detail" : ""} ${className}`} onClickCapture={e=>{const link=e.target.closest('a');if(link&&link.getAttribute('href')?.startsWith('/')&&(busy||dirty)){e.preventDefault();e.stopPropagation();guard(()=>navigate(link.getAttribute('href')));}}}>{!hideBack&&<button className="of-button" disabled={busy} onClick={()=>guard()}><ArrowLeft size={16}/> Quay lại danh sách</button>}<header className="of-header"><h1>{title}</h1></header>
    {discard&&<Dialog title="Rời trang khi chưa lưu?" onClose={()=>setPending(null)}><div className="of-leave-confirm"><p>Những thông tin bạn vừa nhập sẽ bị mất. Bạn có muốn tiếp tục chỉnh sửa?</p><footer><button type="button" className="of-button of-leave-button" onClick={()=>{setEdited(null);const action=pending.action;setPending(null);action();}}>Bỏ thay đổi</button><button type="button" className="of-button of-primary" autoFocus onClick={()=>setPending(null)}>Tiếp tục chỉnh sửa</button></footer></div></Dialog>}
    <section className="of-panel" onChange={()=>setEdited({key:resetKey,dirty:true})}>{typeof children==='function'?children({guard}):children}</section>
  </main>;
}
