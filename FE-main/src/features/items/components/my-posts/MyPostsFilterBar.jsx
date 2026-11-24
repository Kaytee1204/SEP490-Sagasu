export default function MyPostsFilterBar({
  filter,
  onSelectFilter,
  filters,
  postType,
  postTypes,
  onSelectPostType,
  statusCounts = {},
}) {
  return <div className="my-posts-filter-panel">
    <div className="my-posts-filter-row">
      <span className="my-posts-filter-label">Trạng thái</span>
      <div className="my-posts-filters" role="group" aria-label="Lọc trạng thái bài đăng">
        {filters.map(option => {
          const count = statusCounts[option.key];
          const isPendingHighlight = option.key === 'PENDING' && count > 0;
          return <button key={option.key} type="button" data-attention={isPendingHighlight || undefined}
            aria-pressed={filter === option.key} onClick={() => onSelectFilter(option.key)}>
            {option.label}{typeof count === 'number' && ` (${count})`}
          </button>;
        })}
      </div>
    </div>
    <label className="my-posts-type-filter">
      <span className="my-posts-filter-label">Loại bài</span>
      <select value={postType} onChange={event => onSelectPostType(event.target.value)}>
        {postTypes.map(option => <option key={option.key} value={option.key}>{option.label}</option>)}
      </select>
    </label>
  </div>;
}
