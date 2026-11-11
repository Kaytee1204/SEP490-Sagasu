import '@/features/workspace/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { getPostForEdit, getPostCategories, updatePost } from '../api/postApi';
import StepDetailsForm from '../components/post/StepDetailsForm';
import '../../profile/pages/MyPostsPage.css';

function toVietnamInputs(value) {
  if (!value) return {date:'', time:''};
  const parts = new Intl.DateTimeFormat('sv-SE', {timeZone:'Asia/Ho_Chi_Minh', year:'numeric', month:'2-digit',
    day:'2-digit', hour:'2-digit', minute:'2-digit', hourCycle:'h23'}).formatToParts(new Date(value));
  const fields = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return {date:`${fields.year}-${fields.month}-${fields.day}`, time:`${fields.hour}:${fields.minute}`};
}

export default function EditPostPage() {
  const { id }=useParams();
  const [attempt,setAttempt]=useState(0);
  return <Editor key={`${id}:${attempt}`} id={id} onReload={()=>setAttempt(n=>n+1)} />;
}

function Editor({ id, onReload, embedded = false, onClose, onSaved, onDirty, onBusy }) {
  const location = useLocation();
  const backUrl = location.state?.from || '/tin-dang-cua-toi';
  const [loaded,setLoaded]=useState(null);
  const [form,setForm]=useState(null);
  const [kept,setKept]=useState([]);
  const [files,setFiles]=useState([]);
  const [error,setError]=useState('');
  const [saving,setSaving]=useState(false);
  const [saved,setSaved]=useState(null);
  const previews=useRef([]);
  const initialForm=useRef(null);
  const inFlight=useRef(false);
  useEffect(()=>{
    let cancelled=false;
    Promise.all([getPostForEdit(id),getPostCategories()]).then(([post,categories])=>{
      if(cancelled)return;
      const eventInputs=toVietnamInputs(post.eventAt);
      const initial = {itemName:post.title,description:post.description,categoryId:String(post.categoryId),
        location:post.locationText,date:eventInputs.date,time:eventInputs.time};
      initialForm.current=JSON.stringify(initial); setForm(initial);
      setKept(post.images);
      setLoaded({post,categories});
    }).catch(err=>{if(!cancelled)setError(err.message);});
    return ()=>{cancelled=true;};
  },[id]);
  useEffect(()=>()=>previews.current.forEach(url=>URL.revokeObjectURL(url)),[]);

  useEffect(()=>{onDirty?.(Boolean(loaded && (JSON.stringify(form)!==initialForm.current || files.length || kept.length!==loaded.post.images.length)));},[form,files,kept,loaded,onDirty]);
  useEffect(()=>{onBusy?.(saving);},[saving,onBusy]);
  const Wrapper=embedded?'div':'main';
  function upload(event) {
    const added=Array.from(event.target.files||[]); event.target.value='';
    if(kept.length+files.length+added.length>5){setError('Tổng ảnh cũ và mới tối đa 5 ảnh.');return;}
    if(added.some(file=>!['image/jpeg','image/png'].includes(file.type)||!file.size||file.size>5*1024*1024)){
      setError('Ảnh phải là JPG hoặc PNG, tối đa 5 MB/ảnh.');return;
    }
    const next=added.map(file=>({file,preview:URL.createObjectURL(file)}));
    previews.current.push(...next.map(item=>item.preview)); setFiles(current=>[...current,...next]); setError('');
  }
  function removeNew(index){
    URL.revokeObjectURL(files[index].preview);
    previews.current=previews.current.filter(url=>url!==files[index].preview);
    setFiles(current=>current.filter((_,i)=>i!==index));
  }
  async function submit(event){
    event.preventDefault(); if(inFlight.current)return;
    if(!form.itemName.trim()||!form.description.trim()||!form.location.trim()){
      setError('Vui lòng nhập đầy đủ thông tin.');return;
    }
    const eventAt=`${form.date}T${form.time}:00+07:00`;
    if(!Number.isFinite(new Date(eventAt).getTime())||new Date(eventAt).getTime()>Date.now()){
      setError('Thời gian xảy ra phải hợp lệ và không ở tương lai (giờ Việt Nam).');return;
    }
    inFlight.current=true;setSaving(true);setError('');
    try{
      const result=await updatePost(id,{title:form.itemName.trim(),description:form.description.trim(),
        categoryId:Number(form.categoryId),locationText:form.location.trim(),eventAt,
        expectedUpdatedAt:loaded.post.updatedAt,keptImageIds:kept.map(image=>image.id)},files.map(item=>item.file));
      if(onSaved) onSaved(result); else setSaved(result);
    }catch(err){setError(err.message);}
    finally{setSaving(false);inFlight.current=false;}
  }

  if(saved)return <main className="container-sm my-posts-page"><h1>Đã lưu bài đăng #{saved.id}</h1>
    <p role="status">{saved.moderationStatus==='VISIBLE'?'Bài đăng đã được cập nhật và hiển thị.':'Bài đăng đã được gửi lại để Approver duyệt.'}</p>
    <div className="my-post-actions"><Link className="btn btn-primary" to={backUrl}>Về tin đăng của tôi</Link>
      <Link className="btn btn-ghost" to={`/chi-tiet/${id}`} state={{ from: backUrl }}>Xem bài đăng</Link></div></main>;
  return <Wrapper className={embedded ? "post-editor-body" : "container-sm my-posts-page"}>

    {!embedded && <header className="my-posts-heading"><h1>Sửa bài đăng</h1><Link to={backUrl}>Về tin đăng của tôi</Link></header>}
    {error&&<div role="alert"><p>{error}</p>{!loaded && <button type="button" className="btn btn-ghost" disabled={saving} onClick={onReload}>Tải lại bài đăng</button>}</div>}
    {!loaded?!error&&<p role="status">Đang tải bài đăng…</p>:<form onSubmit={submit}>
      <fieldset disabled={saving} style={{border:0,padding:0,minWidth:0}}>
        <p>Bài phải còn mở và chưa bị ẩn để chỉnh sửa. Bài Found giao văn phòng trường và bài bị từ chối sẽ cần duyệt lại.</p>
        <div className="card" style={{padding:24,marginBlock:20}}>
          <StepDetailsForm formData={form} onChange={setForm} postType={loaded.post.postType.toLowerCase()}
            categories={loaded.categories} />
          {!loaded.categories.some(category=>String(category.id)===form.categoryId)&&
            <p role="alert">Danh mục cũ đã ngừng hoạt động. Vui lòng chọn danh mục khác.</p>}
        </div>
        <section className="card" style={{padding:24,marginBlock:20}}><h2>Ảnh bài đăng ({kept.length+files.length}/5)</h2>
          <p>Có thể xóa ảnh cũ và thêm ảnh mới. Thay đổi chỉ áp dụng khi bấm “Lưu thay đổi”. JPG/PNG, tối đa 5 MB/ảnh.</p>
          <div className="edit-post-images">
            {kept.map(image=><figure key={image.id}>{image.url?<img src={image.url} alt="Ảnh hiện có"/>:<span>Không tải được ảnh</span>}
              <button type="button" className="btn btn-ghost" onClick={()=>setKept(current=>current.filter(item=>item.id!==image.id))}>Xóa ảnh này</button></figure>)}
            {files.map((item,index)=><figure key={item.preview}><img src={item.preview} alt={`Ảnh mới ${index+1}`}/>
              <button type="button" className="btn btn-ghost" onClick={()=>removeNew(index)}>Xóa ảnh mới {index+1}</button></figure>)}
          </div>
          <label className="input-label" htmlFor="edit-post-files">Thêm ảnh mới</label>
          <input id="edit-post-files" type="file" accept="image/jpeg,image/png" multiple onChange={upload}/>
        </section>
        {!embedded && <p>Ghi chú xác minh riêng tư (không bắt buộc) được quản lý tại <Link to={`/chi-tiet/${id}`}>trang chi tiết của bạn</Link>.</p>}
        <div className="my-post-actions post-editor-actions"><button className="btn btn-primary" disabled={saving}>{saving?'Đang lưu…':'Lưu thay đổi'}</button>
          {!saving&&(embedded ? <button type="button" className="btn btn-ghost" onClick={onClose}>Hủy</button> : <Link className="btn btn-ghost" to="/tin-dang-cua-toi">Hủy</Link>)}</div>
      </fieldset>
    </form>}
  </Wrapper>;
}

export function EditPostDialog({ id, onClose, onSaved }) {
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
    <div hidden={discard}><Editor key={`${id}:${attempt}`} id={id} embedded onClose={close} onSaved={onSaved}
      onDirty={setDirty} onBusy={setBusy} onReload={()=>setAttempt(value=>value+1)}/></div>
  </dialog>;
}
