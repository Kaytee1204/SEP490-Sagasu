import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
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

function Editor({ id, onReload }) {
  const [loaded,setLoaded]=useState(null);
  const [form,setForm]=useState(null);
  const [kept,setKept]=useState([]);
  const [files,setFiles]=useState([]);
  const [error,setError]=useState('');
  const [saving,setSaving]=useState(false);
  const [saved,setSaved]=useState(null);
  const previews=useRef([]);
  const inFlight=useRef(false);
  useEffect(()=>{
    let cancelled=false;
    Promise.all([getPostForEdit(id),getPostCategories()]).then(([post,categories])=>{
      if(cancelled)return;
      const eventInputs=toVietnamInputs(post.eventAt);
      const handoverInputs=toVietnamInputs(post.handoverRequestedAt);
      setForm({itemName:post.title,description:post.description,categoryId:String(post.categoryId),
        location:post.locationText,date:eventInputs.date,time:eventInputs.time,custodyMode:post.custodyMode,
        handoverDate:handoverInputs.date,handoverTime:handoverInputs.time});
      setKept(post.images);
      setLoaded({post,categories});
    }).catch(err=>{if(!cancelled)setError(err.message);});
    return ()=>{cancelled=true;};
  },[id]);
  useEffect(()=>()=>previews.current.forEach(url=>URL.revokeObjectURL(url)),[]);

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
    const officeHandover=loaded.post.postType==='FOUND'&&form.custodyMode==='OFFICE';
    const handoverRequestedAt=officeHandover?`${form.handoverDate}T${form.handoverTime}:00+07:00`:null;
    if(officeHandover&&(!form.handoverDate||!form.handoverTime||!Number.isFinite(new Date(handoverRequestedAt).getTime())
      ||new Date(handoverRequestedAt).getTime()<=Date.now())){
      setError('Vui lòng đề xuất ngày/giờ giao đồ trong tương lai (giờ Việt Nam).');return;
    }
    inFlight.current=true;setSaving(true);setError('');
    try{
      const result=await updatePost(id,{title:form.itemName.trim(),description:form.description.trim(),
        categoryId:Number(form.categoryId),locationText:form.location.trim(),eventAt,custodyMode:form.custodyMode,
        expectedUpdatedAt:loaded.post.updatedAt,keptImageIds:kept.map(image=>image.id),handoverRequestedAt},files.map(item=>item.file));
      setSaved(result);
    }catch(err){setError(err.message);}
    finally{setSaving(false);inFlight.current=false;}
  }

  if(saved)return <main className="container-sm my-posts-page"><h1>Đã lưu bài đăng #{saved.id}</h1>
    <p role="status">{saved.moderationStatus==='VISIBLE'?'Bài đăng đã được cập nhật và hiển thị.':'Bài đăng đã được gửi lại để Approver duyệt.'}</p>
    <div className="my-post-actions"><Link className="btn btn-primary" to="/tin-dang-cua-toi">Về tin đăng của tôi</Link>
      <Link className="btn btn-ghost" to={`/chi-tiet/${id}`}>Xem bài đăng</Link></div></main>;
  return <main className="container-sm my-posts-page">
    <header className="my-posts-heading"><h1>Sửa bài đăng</h1><Link to="/tin-dang-cua-toi">Về tin đăng của tôi</Link></header>
    {error&&<div role="alert"><p>{error}</p><button type="button" className="btn btn-ghost" disabled={saving} onClick={onReload}>Tải lại bài đăng</button></div>}
    {!loaded?!error&&<p role="status">Đang tải bài đăng…</p>:<form onSubmit={submit}>
      <fieldset disabled={saving} style={{border:0,padding:0,minWidth:0}}>
        <p>Bài phải còn mở và chưa bị ẩn để chỉnh sửa. Bài Found giao Approver, bài của Checker/người ngoài trường và bài bị từ chối sẽ cần duyệt lại.</p>
        <div className="card" style={{padding:24,marginBlock:20}}>
          <StepDetailsForm formData={form} onChange={setForm} postType={loaded.post.postType.toLowerCase()}
            categories={loaded.categories} showPrivate={false} />
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
        <p>Dấu hiệu nhận dạng riêng tư được quản lý riêng tại <Link to={`/chi-tiet/${id}`}>trang chi tiết của bạn</Link>.</p>
        <div className="my-post-actions"><button className="btn btn-primary" disabled={saving}>{saving?'Đang lưu…':'Lưu thay đổi'}</button>
          {!saving&&<Link className="btn btn-ghost" to="/tin-dang-cua-toi">Hủy</Link>}</div>
      </fieldset>
    </form>}
  </main>;
}
