import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getVisiblePosts } from '@/features/items/api/postApi';
import Pagination from '@/components/common/Pagination';
import SearchMatchCard from '../components/SearchMatchCard';
import './SearchPage.css';

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [reload, setReload] = useState(0);
  const rawPage = params.get('page') || '1';
  const page = /^[1-9]\d*$/.test(rawPage) && Number(rawPage) <= 2147483647 ? Number(rawPage) : 1;
  return <PostList key={`${page}:${reload}`} page={page}
    onReload={() => setReload(value => value + 1)}
    onPageChange={next => { setParams(next === 1 ? {} : { page: String(next) }); }} />;
}

function PostList({ page, onReload, onPageChange }) {
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let cancelled = false;
    getVisiblePosts(page).then(data => { if (!cancelled) setResult(data); })
      .catch(err => { if (!cancelled) setError(err.message || 'Không tải được danh sách bài đăng.'); });
    return () => { cancelled = true; };
  }, [page]);

  return <main className="container post-list-page">
    <header className="post-list-heading">
      <div><h1 className="text-display-md">Danh sách bài đăng</h1>
        <p>Những bài Lost/Found trong trường, mới nhất trước.</p>
      </div>
      <div className="post-list-actions">
        <button type="button" className="btn btn-ghost" onClick={onReload}>Tải lại</button>
        <Link className="btn btn-primary" to="/dang-tin">Đăng tin mất đồ</Link>
      </div>
    </header>
    {error ? <div className="post-list-message" role="alert"><h2>Không tải được bài đăng</h2><p>{error}</p>
      <button className="btn btn-primary" onClick={onReload}>Thử lại</button></div>
      : !result ? <p className="post-list-message" role="status">Đang tải danh sách…</p>
      : <>
        <p className="post-list-count" role="status">{result.totalElements} bài đăng
          {result.totalPages > 0 && ` · Trang ${result.page}/${result.totalPages}`}</p>
        {result.content.length ? <div className="post-list-grid">
          {result.content.map(post => <SearchMatchCard key={post.id} item={post} />)}
        </div> : <div className="post-list-message">
          <h2>{result.totalElements ? 'Trang này không có bài đăng' : 'Chưa có bài đăng hiển thị'}</h2>
          <p>{result.totalElements ? 'Vui lòng trở về trang đầu.' : 'Bạn có thể đăng tin mất đồ để cộng đồng hỗ trợ tìm kiếm.'}</p>
          {page > 1 && <button type="button" className="btn btn-primary" onClick={() => onPageChange(1)}>Về trang đầu</button>}
        </div>}
        {result.content.length > 0 && <Pagination className="post-list-pagination" currentPage={page}
          totalPages={result.totalPages} onPageChange={onPageChange} />}
      </>}
  </main>;
}
