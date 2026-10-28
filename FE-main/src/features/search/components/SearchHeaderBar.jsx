import { Search, X } from 'lucide-react';

export default function SearchHeaderBar({ toolbarRef, values, categories, categoryError, onChange, onSearch, onClearQuery }) {
  return <div className="post-search-toolbar" ref={toolbarRef}>
    <div className="container">
      <form className="search-bar post-search-bar" onSubmit={onSearch} aria-label="Tìm kiếm bài đăng">
        <Search size={19} className="post-search-icon" aria-hidden="true" />
        <input className="search-input" type="search" name="keyword" aria-label="Từ khóa"
          value={values.keyword} onChange={onChange} maxLength={200}
          placeholder="Nhập từ khóa đồ thất lạc (vd: Ví Pedro, iPhone, CCCD...)" />
        {values.keyword && <button type="button" className="post-search-clear" onClick={onClearQuery} aria-label="Xóa từ khóa">
          <X size={16} aria-hidden="true" />
        </button>}
        <div className="search-divider" aria-hidden="true" />
        <select className="search-category" name="categoryId" aria-label="Danh mục"
          value={values.categoryId} onChange={onChange}>
          <option value="">Tất cả danh mục</option>
          {categories.map(category => <option key={category.id} value={category.id}>{category.name}</option>)}
        </select>
        <button className="search-btn" type="submit"><Search size={15} aria-hidden="true" />Tìm kiếm</button>
      </form>
      {categoryError && <p className="post-search-category-error" role="alert">{categoryError}</p>}
    </div>
  </div>;
}
