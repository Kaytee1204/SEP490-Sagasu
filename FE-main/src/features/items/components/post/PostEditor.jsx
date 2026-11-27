import '@/styles/Workspace.css';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { getPostForEdit, getPostCategories, updatePost } from '../../api/postApi';
import StepDetailsForm from './StepDetailsForm';
import LivePostPreview from './LivePostPreview';
import './PostEditor.css';
import '../../styles/MyPosts.css';
import useImageUploads from '@/hooks/useImageUploads';
import { IMAGE_FORMATS } from '@/utils/imageUploads';
import ImageProcessingStatus from '@/components/common/ImageProcessingStatus';
import ImageUploadButton from '@/components/common/ImageUploadButton';

function toVietnamInputs(value) {
  if (!value) return {date:'', time:''};
  const parts = new Intl.DateTimeFormat('sv-SE', {timeZone:'Asia/Ho_Chi_Minh', year:'numeric', month:'2-digit',
    day:'2-digit', hour:'2-digit', minute:'2-digit', hourCycle:'h23'}).formatToParts(new Date(value));
  const fields = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return {date:`${fields.year}-${fields.month}-${fields.day}`, time:`${fields.hour}:${fields.minute}`};
}

export default function PostEditor({ id, onReload }) {
  const { user } = useAuth();
  const location = useLocation();
  const from = location.state?.from;
  const backUrl = typeof from === 'string' && /^\/(?!\/)/.test(from) ? from : '/tin-dang-cua-toi';
  const backLabel = location.state?.fromLabel || 'Bài đăng của tôi';
  const [loaded,setLoaded]=useState(null);
  const [form,setForm]=useState(null);
  const [kept,setKept]=useState([]);
  const [files,setFiles]=useState([]);
  const [error,setError]=useState('');
  const [saving,setSaving]=useState(false);
  const {prepare,processing}=useImageUploads();
  const [saved,setSaved]=useState(null);
  const previews=useRef([]);
  const [initialForm, setInitialForm]=useState('');
  const inFlight=useRef(false);
  useEffect(()=>{
    let cancelled=false;
    Promise.all([getPostForEdit(id),getPostCategories()]).then(([post,categories])=>{
      if(cancelled)return;
      const eventInputs=toVietnamInputs(post.eventAt);
      const initial = {itemName:post.title,description:post.description,categoryId:String(post.categoryId),
        location:post.locationText,date:eventInputs.date,time:eventInputs.time};
      setInitialForm(JSON.stringify(initial)); setForm(initial);
      setKept(post.images);
      setLoaded({post,categories});
    }).catch(err=>{if(!cancelled)setError(err.message);});
    return ()=>{cancelled=true;};
  },[id]);
  useEffect(()=>()=>previews.current.forEach(url=>URL.revokeObjectURL(url)),[]);

  const dirty = Boolean(loaded && (JSON.stringify(form) !== initialForm || files.length || kept.length !== loaded.post.images.length));
  useEffect(() => {
    if (!dirty || saved) return;
    const warn = event => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, saved]);
  async function upload(event) {
    const added=Array.from(event.target.files||[]); event.target.value='';
    if(saving||processing)return;
    if(kept.length+files.length+added.length>5){setError('Tổng ảnh cũ và mới tối đa 5 ảnh.');return;}
    setError('');
    try {
      const prepared=await prepare(added);
      if(!prepared)return;
      const next=prepared.map(file=>({file,preview:URL.createObjectURL(file)}));
      previews.current.push(...next.map(item=>item.preview)); setFiles(current=>[...current,...next]);
    } catch(problem) {
      setError(problem.message);
    }
  }
  function removeNew(index){
    URL.revokeObjectURL(files[index].preview);
    previews.current=previews.current.filter(url=>url!==files[index].preview);
    setFiles(current=>current.filter((_,i)=>i!==index));
  }
  async function submit(event){
    event.preventDefault(); if(inFlight.current||processing)return;
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
      setSaved(result);
    }catch(err){setError(err.message);}
    finally{setSaving(false);inFlight.current=false;}
  }

  if(saved)return <main className="container-sm my-posts-page"><h1>Đã lưu bài đăng #{saved.id}</h1>
    <p role="status">{saved.moderationStatus==='VISIBLE'?'Bài đăng đã được cập nhật và hiển thị.':'Bài đăng đã được gửi lại để Quản lý duyệt.'}</p>
    <div className="my-post-actions"><Link className="btn btn-primary" to={backUrl}>{backLabel}</Link>
      <Link className="btn btn-ghost" to={`/chi-tiet/${id}`} state={{ from: backUrl }}>Xem bài đăng</Link></div></main>;
  const review = form && {
    ...form,
    images: [...kept.map(image => image.url).filter(Boolean), ...files.map(image => image.preview)],
    categoryName: loaded.categories.find(category => String(category.id) === form.categoryId)?.name || loaded.post.categoryName,
  };
  return <main className="post-create-page post-create-compact post-edit-page">
    <div className="container post-edit-container">
      <header className="post-edit-heading">
        <Link className="post-edit-back" to={backUrl}><ArrowLeft size={17} aria-hidden="true" />{backLabel}</Link>
        <h1 className="post-create-title">Sửa bài đăng</h1>
      </header>
      {error && <div className="post-edit-alert" role="alert"><p>{error}</p>{!loaded &&
        <button type="button" className="btn btn-ghost" onClick={onReload}>Tải lại bài đăng</button>}</div>}
      {!loaded ? !error && <p role="status">Đang tải bài đăng…</p> : <form onSubmit={submit} className="card post-edit-card">
        <fieldset disabled={saving || processing} className="post-edit-fields">
          <div className="post-edit-workspace">
            <div className="post-edit-inputs">
              <section className="post-create-details" aria-label="Thông tin bài đăng">
                <StepDetailsForm formData={form} onChange={setForm} postType={loaded.post.postType.toLowerCase()} categories={loaded.categories} />
                {!loaded.categories.some(category => String(category.id) === form.categoryId) &&
                  <p className="post-edit-category-warning" role="alert">Danh mục cũ đã ngừng hoạt động. Vui lòng chọn danh mục khác.</p>}
              </section>
              <section className="post-edit-photos" aria-labelledby="edit-post-photos-title">
                <div className="post-edit-upload-heading"><h2 id="edit-post-photos-title">Ảnh bài đăng ({kept.length + files.length}/5)</h2>
                  <small>{IMAGE_FORMATS} · Tối đa 5 MB/ảnh</small></div>
                <div className="post-edit-images">
                  {kept.map((image, index) => <figure key={image.id}>
                    {image.url ? <img src={image.url} alt={`Ảnh hiện có ${index + 1}`} /> : <span>Không tải được ảnh</span>}
                    <button type="button" onClick={() => setKept(current => current.filter(item => item.id !== image.id))}
                      aria-label={`Xóa ảnh hiện có ${index + 1}`}>Xóa ảnh</button>
                  </figure>)}
                  {files.map((item, index) => <figure key={item.preview}><img src={item.preview} alt={`Ảnh mới ${index + 1}`} />
                    <button type="button" onClick={() => removeNew(index)} aria-label={`Xóa ảnh mới ${index + 1}`}>Xóa ảnh mới</button></figure>)}
                </div>
                <ImageUploadButton id="edit-post-files" label="Thêm ảnh mới" disabled={saving || processing || kept.length + files.length >= 5} onChange={upload} />
                <ImageProcessingStatus active={processing} />
              </section>
            </div>
            <aside className="post-edit-preview" aria-label="Xem trước nội dung đang sửa">
              <LivePostPreview formData={review} postType={loaded.post.postType.toLowerCase()} authorName={user?.fullName || user?.username} />
            </aside>
          </div>
          <footer className="post-create-actions post-edit-actions">
            <Link className="btn btn-ghost" to={backUrl} aria-disabled={saving || processing}
              onClick={event => { if (saving || processing) event.preventDefault(); }}>Hủy chỉnh sửa</Link>
            <button type="submit" className="btn btn-primary" disabled={saving || processing}>
              {processing ? 'Đang xử lý ảnh…' : saving ? 'Đang lưu…' : 'Lưu thay đổi'}</button>
          </footer>
        </fieldset>
      </form>}
    </div>
  </main>;
}
