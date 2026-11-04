import { useState } from "react";
import { Link } from "react-router-dom";
import { Package, RefreshCw, ArrowUpRight } from "lucide-react";
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
import "../Office.css";

export default function PostAdministration() {
  const [filters, setFilters] = useState({
    page: 1,
    q: "",
    type: "ALL",
    status: "ALL",
    moderation: "ALL",
    custody: "ALL",
    category: "",
  });
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
    <div className="office-page">
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
        <div className="of-toolbar">
          <SearchBox
            value={filters.q}
            onChange={(v) => change("q", v)}
            placeholder="Tìm tiêu đề hoặc nội dung bài đăng…"
          />
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
        <p className="of-muted">
          Ẩn bài chỉ thay đổi khả năng hiển thị. Trạng thái tìm chủ và trao trả
          được giữ nguyên.
        </p>
      </div>
      <LoadState state={state}>
        {state.data && (
          <>
            {!state.data.content.length ? (
              <Empty />
            ) : (
              <div className="of-grid">
                {state.data.content.map((post) => (
                  <article className="of-card" key={post.id}>
                    {post.images[0] ? (
                      <img
                        className="of-thumbnail"
                        src={post.images[0]}
                        alt={post.title}
                        loading="lazy"
                      />
                    ) : (
                      <div className="of-image-placeholder">
                        <Package size={34} />
                      </div>
                    )}
                    <div className="of-card-body">
                      <div className="of-badges">
                        <Badge value={post.postType} />
                        <Badge value={post.moderationStatus} />
                      </div>
                      <h2>
                        <Link to={`/admin/items/${post.id}`}>{post.title}</Link>
                      </h2>
                      <p className="of-muted">
                        #{post.id} · {post.category}
                        <br />
                        {post.author} · {date(post.createdAt)}
                      </p>
                      <div className="of-badges">
                        <Badge value={post.status} />
                        <Badge value={post.custodyMode} />
                      </div>
                      <div className="of-card-footer">
                        <span className="of-muted">{post.location}</span>
                        <Link
                          className="of-button"
                          to={`/admin/items/${post.id}`}
                        >
                          Chi tiết
                          <ArrowUpRight size={15} />
                        </Link>
                      </div>
                    </div>
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
