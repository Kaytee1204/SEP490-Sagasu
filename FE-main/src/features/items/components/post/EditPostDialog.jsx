import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import PostEditor from './PostEditor';

export default function EditPostDialog({ id, onClose, onSaved }) {
  const dialog=useRef(null);
  const [dirty,setDirty]=useState(false);
  const [busy,setBusy]=useState(false);
  const [discard,setDiscard]=useState(false);
  const [attempt,setAttempt]=useState(0);
  useEffect(()=>{
    const element=dialog.current, focus=document.activeElement, overflow=document.body.style.overflow;
    element.showModal(); document.body.style.overflow='hidden';
    return ()=>{element.close();document.body.style.overflow=overflow;if(focus?.isConnected)focus.focus();};
  },[]);
  const close=()=>{if(busy)return;if(dirty)setDiscard(true);else onClose();};
  return <dialog ref={dialog} className="post-editor-dialog" aria-labelledby="post-editor-title"
    onCancel={event=>{event.preventDefault();close();}}>
    <header><h2 id="post-editor-title">Sửa bài đăng</h2><button type="button" aria-label="Đóng sửa bài đăng" disabled={busy} onClick={close}><X size={22}/></button></header>
    {discard && <div className="post-editor-discard" role="alert"><p>Bỏ các thay đổi chưa lưu?</p><div className="my-post-actions">
      <button type="button" className="btn btn-primary" onClick={()=>setDiscard(false)}>Tiếp tục sửa</button>
      <button type="button" className="btn btn-ghost" onClick={onClose}>Bỏ thay đổi và đóng</button></div></div>}
    <div hidden={discard}><PostEditor key={`${id}:${attempt}`} id={id} embedded onClose={close} onSaved={onSaved}
      onDirty={setDirty} onBusy={setBusy} onReload={()=>setAttempt(value=>value+1)}/></div>
  </dialog>;
}
