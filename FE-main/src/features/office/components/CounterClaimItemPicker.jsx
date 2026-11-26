import { useEffect, useState } from "react";
import { Package, Plus } from "lucide-react";
import { officeApi } from "../api/officeApi";
import useLoad from "../hooks/useLoad";
import { date } from "../officeUtils";
import LoadState from "./LoadState";
import SearchBox from "./SearchBox";

function ItemPreview({ item }) {
  return <article className="of-counter-claim-preview" aria-label="Tài sản đã chọn">
    <div className="of-counter-claim-image">
      <Package size={30} aria-hidden="true" />
      {item.imageUrl && <img src={item.imageUrl} alt={item.title}
        onError={(event) => { event.currentTarget.hidden = true; }} />}
    </div>
    <div className="of-counter-claim-details">
      <span className="of-counter-claim-ready">Đang nhận yêu cầu</span>
      <h3>{item.title}</h3>
      <dl className="of-facts">
        <div><dt>Mã phiếu tiếp nhận</dt><dd>{item.code}</dd></div>
        <div><dt>Bài đăng</dt><dd>#{item.postId} · {item.category}</dd></div>
        <div><dt>Địa điểm nhặt được</dt><dd>{item.foundLocation || "—"}</dd></div>
        <div><dt>Thời điểm nhặt được</dt><dd>{date(item.foundAt)}</dd></div>
        <div><dt>Nơi lưu giữ</dt><dd>{item.storageLocation || "—"}</dd></div>
        <div><dt>Thời điểm tiếp nhận</dt><dd>{date(item.receivedAt)}</dd></div>
      </dl>
      {item.description && <div className="of-counter-claim-description">
        <h4>Mô tả bài đăng</h4>
        <p className="of-prose">{item.description}</p>
      </div>}
    </div>
  </article>;
}

export default function CounterClaimItemPicker({ selected, postId, onSelect, disabled }) {
  const [query, setQuery] = useState("");
  const [keyword, setKeyword] = useState("");
  const typed = query.trim();
  useEffect(() => {
    const timer = setTimeout(() => setKeyword(typed), 250);
    return () => clearTimeout(timer);
  }, [typed]);

  const matches = useLoad(
    () => keyword.length >= 2 ? officeApi.counterItems(keyword) : Promise.resolve([]),
    keyword,
  );

  return <div className="of-counter-claim-picker">
    <SearchBox value={query} onChange={setQuery} disabled={disabled}
      placeholder="Tìm tên đồ vật hoặc mã phiếu tiếp nhận…" />
    {typed.length < 2 && <p className="of-muted">
      {selected.data ? "Để chọn món khác, nhập tên đồ vật hoặc mã phiếu." : "Nhập ít nhất 2 ký tự để tìm tài sản còn nhận yêu cầu."}
    </p>}
    {typed.length >= 2 && (typed !== keyword ? (
      <p className="of-muted" role="status">Đang tìm tài sản…</p>
    ) : <LoadState state={matches}>
      {matches.data?.length === 0 && <p className="of-muted" role="status">
        Không tìm thấy tài sản còn nhận yêu cầu. Vui lòng kiểm tra tên đồ vật hoặc mã phiếu.
      </p>}
      {!!matches.data?.length && <ul className="of-counter-claim-results" aria-label="Tài sản phù hợp">
        {matches.data.map(item => <li key={item.postId}>
          <button type="button" className="of-button" disabled={disabled}
            onClick={() => { onSelect(item.postId); setQuery(""); }}>
            <span><strong>{item.title}</strong><small>Mã phiếu {item.code} · Bài #{item.postId}</small></span>
            <Plus size={16} aria-hidden="true" />
          </button>
        </li>)}
      </ul>}
      {matches.data?.length === 10 && <p className="of-muted">Hiển thị tối đa 10 kết quả. Nhập thêm từ khóa để thu hẹp tìm kiếm.</p>}
    </LoadState>)}
    {postId && <LoadState state={selected}>
      {selected.data && <ItemPreview key={selected.data.postId} item={selected.data} />}
    </LoadState>}
  </div>;
}
