export default function MyPostsFilterBar({ filter, onSelectFilter, filters, postType, postTypes, onSelectPostType }) {
  return <div className="my-posts-filter-panel">
    <div className="my-posts-filter-row my-posts-type-row">
      <span className="my-posts-filter-label">Loại bài</span>
      <div className="my-post-type-tabs" role="group" aria-label="Lọc loại bài đăng">
        {postTypes.map(option => {
          const Icon = option.icon;
          return <button key={option.key} type="button" data-type={option.key}
            className={postType === option.key ? 'active' : ''} aria-pressed={postType === option.key}
            onClick={() => onSelectPostType(option.key)}><Icon size={16} aria-hidden="true" />{option.label}</button>;
        })}
      </div>
    </div>
    <div className="my-posts-filter-row">
      <span className="my-posts-filter-label">Trạng thái</span>
      <div className="my-posts-filters" role="group" aria-label="Lọc trạng thái bài đăng">
        {filters.map(option => {
          const Icon = option.icon;
          return <button key={option.key} type="button" className={filter === option.key ? 'active' : ''}
            aria-pressed={filter === option.key} onClick={() => onSelectFilter(option.key)}>
            <Icon size={16} aria-hidden="true" />{option.label}
          </button>;
        })}
      </div>
    </div>
  </div>;
}
