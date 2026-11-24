import { Link } from 'react-router-dom';
import { Package, Plus } from 'lucide-react';

const emptyTitles = {
  ACTIVE: 'Chưa có bài đang hoạt động',
  PENDING: 'Chưa có bài chờ duyệt',
  HISTORY: 'Chưa có bài trong lịch sử',
};

export default function MyPostsEmptyState({ filter, postType, onReset }) {
  return <div className="my-posts-empty">
    <span className="my-posts-empty-icon"><Package size={30} strokeWidth={1.5} aria-hidden="true" /></span>
    <h3>{emptyTitles[filter]}</h3>
    {postType !== 'ALL' ? <button type="button" className="btn btn-ghost" onClick={onReset}>Xem mọi loại bài trong mục này</button>
      : filter === 'ACTIVE' && <Link to="/dang-tin" className="btn btn-accent"><Plus size={16} aria-hidden="true" />Đăng bài mới</Link>}
  </div>;
}
