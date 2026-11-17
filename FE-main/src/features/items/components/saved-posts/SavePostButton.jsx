import { Bookmark, BookmarkCheck, LoaderCircle, RotateCw } from 'lucide-react';
import { useSavedPosts } from '../../hooks/useSavedPosts';

export default function SavePostButton({ postId, compact = false, className = '' }) {
  const state = useSavedPosts();
  if (!state) return null;
  const saved = state.ids.has(postId);
  const busy = state.pending.has(postId);
  const Icon = state.error ? RotateCw : busy ? LoaderCircle : saved ? BookmarkCheck : Bookmark;
  const label = state.error ? 'Thử lại trạng thái lưu' : busy ? 'Đang cập nhật…' : saved ? 'Bỏ lưu bài đăng' : 'Lưu bài đăng';
  return <button type="button" className={`save-post-button${compact ? ' is-compact' : ''}${saved ? ' is-saved' : ''} ${className}`}
    aria-label={label} aria-pressed={saved} title={state.error || label}
    disabled={busy || (!state.ready && !state.error)} aria-busy={busy}
    onClick={() => state.error ? state.reload() : state.toggle(postId)}>
    <Icon size={compact ? 19 : 18} aria-hidden="true" className={busy ? 'save-post-spinner' : ''} />
    {!compact && <span>{state.error ? 'Thử lại' : saved ? 'Đã lưu bài đăng' : 'Lưu bài đăng'}</span>}
  </button>;
}
