import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Package, RefreshCw, ArrowUpRight, SlidersHorizontal } from "lucide-react";
import { getPostCategories } from "@/features/items/api/postApi";
import { officeApi } from "../api/officeApi";
import { date, labels } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import Badge from "../components/Badge";
import Header from "../components/Header";
import LoadState from "../components/LoadState";
import Empty from "../components/Empty";
import SearchBox from "../components/SearchBox";
import Select from "../components/Select";
import Pager from "../components/Pager";
import PostAuthor from "../components/PostAuthor";
import "../Office.css";

const initialFilters = { page: 1, q: "", type: "ALL", status: "ALL", moderation: "ALL", custody: "ALL", category: "" };

export default function PostAdministration() {
  const [filters, setFilters] = useState(initialFilters);
  const [search, setSearch] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const activeFilters = ["type", "status", "moderation", "custody", "category"]
    .filter(key => filters[key] !== initialFilters[key]).length;
  useEffect(() => {
    if (search === filters.q) return;
    const timer = setTimeout(() => setFilters((f) => ({ ...f, q: search, page: 1 })), 300);
    return () => clearTimeout(timer);
  }, [search, filters.q]);
  const state = useLoad(
    () => officeApi.posts(filters),
    JSON.stringify(filters),
  );
  const categories = useLoad(getPostCategories, "categories");
  const change = (key, value) =>
    setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  const options = (values) => [
    ["ALL", "Tất cả"],
    ...values.map((v) => [v, labels[v]]),
  ];
  return (
    <div className="office-page of-post-workspace">
      <Header
        title="Quản lý bài đăng"
        description="Theo dõi bài đăng, kiểm tra hồ sơ tiếp nhận và xử lý nội dung vi phạm."
      >
        <button
          className="of-button"
          onClick={state.reload}
          disabled={state.loading}
        >
          <RefreshCw size={16} />
          Tải lại
        </button>
      </Header>
      <div className="of-panel">
        <div className="of-toolbar of-post-toolbar">
          <SearchBox
            value={search}
            onChange={setSearch}
            placeholder="Tìm bài hoặc người đăng…"
          />
          <button className="of-button of-post-filter-toggle" aria-expanded={showFilters} aria-controls="post-admin-filters" onClick={() => setShowFilters(!showFilters)}>
            <SlidersHorizontal size={16} aria-hidden="true" /> Bộ lọc {activeFilters > 0 && `(${activeFilters})`}
          </button>
          <div id="post-admin-filters" className={`of-post-filters${showFilters ? " is-open" : ""}`}>
            <Select
              label="Loại bài"
              value={filters.type}
              onChange={(v) => change("type", v)}
              options={options(["LOST", "FOUND"])}
            />
            <Select
              label="Hiển thị"
              value={filters.moderation}
              onChange={(v) => change("moderation", v)}
              options={options(["VISIBLE", "HIDDEN", "PENDING", "REJECTED"])}
            />
            <Select
              label="Xử lý đồ"
              value={filters.status}
              onChange={(v) => change("status", v)}
              options={options(["OPEN", "RESERVED", "RETURNED", "CLOSED"])}
            />
            <Select
              label="Bên giữ đồ"
              value={filters.custody}
              onChange={(v) => change("custody", v)}
              options={options(["P2P", "OFFICE"])}
            />
            <Select
              label="Danh mục"
              value={filters.category}
              onChange={(v) => change("category", v)}
              options={[
                ["", "Tất cả"],
                ...(categories.data || []).map((c) => [
                  c.id ?? c.categoryId,
                  c.name,
                ]),
              ]}
            />
          </div>
        </div>
      </div>
      <LoadState state={state}>
        {state.data && (
          <>
            <div className="of-post-results">
              <strong role="status">{state.data.totalElements} bài đăng</strong>
              {(search || Object.keys(initialFilters).some((key) => key !== "page" && filters[key] !== initialFilters[key])) && (
                <button className="of-button" onClick={() => { setSearch(""); setFilters(initialFilters); }}>Xóa bộ lọc</button>
              )}
            </div>
            {!state.data.content.length ? (
              <Empty text="Không tìm thấy bài đăng phù hợp" />
            ) : (
              <div className="of-post-list">
                <div className="of-post-row of-post-column-head" aria-hidden="true">
                  <span>Bài đăng</span><span>Người đăng</span><span>Hiển thị và trao trả</span><span />
                </div>
                {state.data.content.map((post) => (
                  <article className="of-post-row" key={post.id}>
                    <div className="of-post-summary">
                    {post.images?.[0] ? (
                      <img
                        className="of-post-thumb"
                        src={post.images[0]}
                        alt=""
                        loading="lazy"
                      />
                    ) : (
                      <div className="of-post-thumb of-post-thumb-empty">
                        <Package size={25} aria-hidden="true" />
                      </div>
                    )}
                    <div>
                      <div className="of-post-meta">
                        <span>#{post.id}</span>
                        <Badge value={post.postType} />
                      </div>
                      <h2>
                        <Link to={`/admin/items/${post.id}`}>{post.title}</Link>
                      </h2>
                      <small>{[post.category, post.location].filter(Boolean).join(" · ")}</small>
                      <small>Ngày đăng {date(post.createdAt)}</small>
                    </div>
                    </div>
                    <div className="of-post-person">
                      <span className="of-post-mobile-label">Người đăng</span>
                      <PostAuthor post={post} />
                    </div>
                    <div className="of-post-state">
                      <span className="of-post-mobile-label">Hiển thị và trao trả</span>
                      <Badge value={post.moderationStatus} />
                      {post.postType === "FOUND" && <div className="of-badges">
                        <Badge value={post.status} />
                        <Badge value={post.custodyMode} />
                      </div>}
                    </div>
                    <Link className="of-button" to={`/admin/items/${post.id}`}>
                      Chi tiết <ArrowUpRight size={15} aria-hidden="true" />
                    </Link>
                  </article>
                ))}
              </div>
            )}
            <Pager
              data={state.data}
              onChange={(page) => setFilters((f) => ({ ...f, page }))}
            />
          </>
        )}
      </LoadState>
    </div>
  );
}
