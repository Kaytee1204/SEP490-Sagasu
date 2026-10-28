import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { getMyPosts, deletePost } from '@/features/items/api/postApi';
import Pagination from '@/components/common/Pagination';
import MyPostCard from '../components/MyPostCard';
import MyPostsEmptyState from '../components/MyPostsEmptyState';
import './MyPostsPage.css';

const FILTERS = [['ALL','Tất cả'],['VISIBLE','Đang hiển thị'],['PENDING','Chờ duyệt'],
  ['REJECTED','Bị từ chối'],['HIDDEN','Bị ẩn']];

export default function MyPostsPage({ embedded = false }) {
  const { user } = useAuth();
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState('ALL');
  const [attempt, setAttempt] = useState(0);
  const Wrapper = embedded ? 'section' : 'main';
  return <Wrapper className={`my-posts-page ${embedded ? 'my-posts-embedded' : 'container'}`}>
    <header className="my-posts-heading"><div><h1>Tin đăng của tôi</h1>
      <p>Theo dõi kiểm duyệt và trạng thái xử lý các bài bạn đã đăng.</p></div>
      <div className="my-post-actions"><button className="btn btn-ghost" onClick={()=>setAttempt(n=>n+1)}>Tải lại</button>
        <Link className="btn btn-primary" to="/dang-tin">Đăng tin mới</Link></div></header>
    <div className="my-posts-filters" aria-label="Lọc trạng thái bài đăng">
      {FILTERS.map(([key,label]) => <button key={key} className={`chip ${filter===key ? 'active' : ''}`}
        aria-pressed={filter===key} onClick={() => {setFilter(key); setPage(1);}}>{label}</button>)}
    </div>
    <MyPostList key={`${user?.id}:${page}:${filter}:${attempt}`} page={page} filter={filter} onPage={setPage}
      onReload={() => setAttempt(n=>n+1)} />
  </Wrapper>;
}

function MyPostList({ page, filter, onPage, onReload }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [postToDelete, setPostToDelete] = useState(null);
  useEffect(() => {
    let cancelled = false;
    getMyPosts(page, filter).then(data => { if (!cancelled) setResult(data); })
      .catch(err => {if (!cancelled) setError(err.message);});
    return () => {cancelled=true;};
  }, [page, filter]);

  async function afterDelete() {
    setPostToDelete(null); setNotice('Đã xóa bài đăng.');
    try {
      const data=await getMyPosts(page, filter);
      if (!data.content.length && page>1) onPage(page-1);
      else setResult(data);
    } catch (err) {setError(err.message);}
  }

  return <>
    {notice && <p role="status">{notice}</p>}
    {error ? <div role="alert"><p>{error}</p><button className="btn btn-ghost" onClick={onReload}>Tải lại</button></div>
      : !result ? <p role="status">Đang tải bài đăng…</p> : <>
        <p>{result.totalElements} bài đăng</p>
        {result.content.length ? result.content.map(post => <MyPostCard key={`${post.id}:${post.updatedAt}`} post={post}
          onDelete={setPostToDelete} />) : <MyPostsEmptyState />}
        <Pagination currentPage={page} totalPages={result.totalPages} onPageChange={onPage} />
      </>}
    {postToDelete && <DeletePostDialog post={postToDelete} onCancel={()=>setPostToDelete(null)} onSuccess={afterDelete} />}
  </>;
}

function DeletePostDialog({ post, onCancel, onSuccess }) {
  const [reason,setReason]=useState('');
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const inFlight=useRef(false);
  const firstField=useRef(null);
  useEffect(()=>{firstField.current?.focus();},[]);
  async function submit(event) {
    event.preventDefault();
    if(inFlight.current) return;
    if(!reason.trim()) {setError('Vui lòng nhập lý do.'); return;}
    inFlight.current=true; setBusy(true); setError('');
    try {await deletePost(post.id,reason.trim()); await onSuccess();}
    catch(err){setError(err.message);setBusy(false);inFlight.current=false;}
  }
  return <div className="my-post-dialog-backdrop" onKeyDown={e=>{if(e.key==='Escape'&&!busy)onCancel();}}>
    <section role="dialog" aria-modal="true" aria-labelledby="delete-post-title" className="card my-post-dialog">
      <h2 id="delete-post-title">Xóa bài: {post.title}</h2>
      <p>Bài sẽ không còn xuất hiện trong danh sách và trang chi tiết. Hệ thống giữ dữ liệu lịch sử.</p>
      <form onSubmit={submit}>
        <label htmlFor="deletion-reason" className="input-label">Lý do xóa</label>
        <textarea ref={firstField} id="deletion-reason" className="input" required maxLength={1000} rows={4}
          value={reason} disabled={busy} onChange={e=>setReason(e.target.value)} />
        {error&&<p role="alert">{error}</p>}
        <div className="my-post-actions"><button type="button" className="btn btn-ghost" disabled={busy} onClick={onCancel}>Hủy</button>
          <button className="btn btn-primary" disabled={busy}>{busy?'Đang xử lý…':'Xóa bài'}</button></div>
      </form>
    </section>
  </div>;
}
