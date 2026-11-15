import { Link } from 'react-router-dom';
import { Package, Plus } from 'lucide-react';

export default function MyPostsEmptyState({ filter = 'ALL', postType = 'ALL', onReset }) {
  const filtered = filter !== 'ALL' || postType !== 'ALL';
  return <div className="my-posts-empty">
    <span className="my-posts-empty-icon"><Package size={30} strokeWidth={1.5} aria-hidden="true" /></span>
    <h3>{filtered ? 'Không có bài đăng phù hợp' : 'Bạn chưa có bài đăng nào'}</h3>
    <p>{filtered ? 'Thử đổi loại bài hoặc trạng thái để xem các bài đăng còn lại.' : 'Đăng tin mất đồ hoặc nhặt được để bắt đầu.'}</p>
    {filtered ? <button type="button" className="btn btn-ghost" onClick={onReset}>Xem tất cả bài đăng</button>
      : <Link to="/dang-tin" className="btn btn-accent"><Plus size={16} aria-hidden="true" />Đăng tin mới</Link>}
  </div>;
}
