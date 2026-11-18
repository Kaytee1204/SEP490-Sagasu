import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Plus, MapPin, Pencil } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { officeApi } from "../api/officeApi";
import { date } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import LocationDialog from "./LocationDialog";
import Badge from "./Badge";
import Header from "./Header";
import Notice from "./Notice";
import LoadState from "./LoadState";
import Empty from "./Empty";
import SearchBox from "./SearchBox";
import Select from "./Select";
import Pager from "./Pager";
import "../Office.css";

export default function StorageWorkspace({ locationsOnly=false }) {
  const { isManager } = useAuth();
  const tab=locationsOnly?"locations":"items";
  const [filters,setFilters]=useState(()=>{try{return JSON.parse(sessionStorage.getItem('office-storage-filters'))||{page:1,q:'',locationId:''};}catch{return {page:1,q:'',locationId:''};}});
  useEffect(()=>{try{sessionStorage.setItem('office-storage-filters',JSON.stringify(filters));}catch{/* Storage is optional. */}},[filters]);
  const items = useLoad(
    () => locationsOnly ? Promise.resolve(null) : officeApi.stored(filters),
    JSON.stringify(filters)+locationsOnly,
  );
  const locations = useLoad(officeApi.locations, "locations");
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState("");
  const change = (key, value) =>
    setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  return (
    <div className="office-page of-storage-workspace">
      <Header
        title={locationsOnly ? "Vị trí lưu trữ" : "Kho đồ thất lạc"}
        description={locationsOnly
          ? "Quản lý các vị trí và khu vực lưu giữ đồ của văn phòng."
          : "Tìm món đồ theo tên, mã tiếp nhận hoặc vị trí trong kho."}
      >
        {isManager && tab === "locations" && (
          <button
            className="of-button of-primary"
            onClick={() => setEditing({})}
          >
            <Plus size={17} />
            Thêm vị trí
          </button>
        )}
      </Header>
      <Notice>{message}</Notice>
      {isManager && (
        <nav className="of-tabs" aria-label="Quản lý kho">
          <Link className={`of-button ${!locationsOnly ? "active" : ""}`} aria-current={!locationsOnly ? "page" : undefined} to="/admin/storage">Đồ đang lưu giữ</Link>
          <Link className={`of-button ${locationsOnly ? "active" : ""}`} aria-current={locationsOnly ? "page" : undefined} to="/admin/storage/locations">Vị trí lưu trữ</Link>
        </nav>
      )}
      {tab === "items" ? (
        <>
          <div className="of-toolbar">
            <SearchBox
              value={filters.q}
              onChange={(v) => change("q", v)}
              placeholder="Tìm tên đồ vật hoặc mã tiếp nhận…"
            />
            <Select
              label="Vị trí (gồm vị trí con)"
              value={filters.locationId}
              onChange={(v) => change("locationId", v)}
              options={[
                ["", "Tất cả vị trí"],
                ...(locations.data || []).map((l) => [
                  l.id,
                  `${l.code} · ${l.name}`,
                ]),
              ]}
            />
          </div>
          <LoadState state={items}>
            {items.data && (
              <>
                {!items.data.content.length ? (
                  <Empty text="Không có đồ đang lưu giữ phù hợp" />
                ) : (
                  <div className="of-storage-list" aria-label="Đồ đang lưu giữ">
                    {items.data.content.map((item) => (
                      <article key={item.id} className="of-storage-row">
                        <div className="of-storage-item">
                          <h2>{item.title}</h2>
                          <div className="of-badges">
                            <Badge value={item.status} />
                            {item.code && <small className="of-muted">Mã tiếp nhận: {item.code}</small>}
                          </div>
                          {item.condition && <p className="of-muted">Tình trạng: {item.condition}</p>}
                        </div>
                        <div className="of-storage-location">
                          <small className="of-muted">Vị trí lưu giữ</small>
                          <div className="of-storage-location-name">
                            <MapPin size={16} aria-hidden="true" />
                            <strong>{item.location || "Chưa bố trí vị trí"}</strong>
                          </div>
                          {item.receivedAt && <small className="of-muted">Tiếp nhận: {date(item.receivedAt)}{item.receivedBy && ` · ${item.receivedBy}`}</small>}
                        </div>
                        <div className="of-storage-actions">
                            {isManager ? (
                              <>
                                <Link
                                  className="of-button"
                                  to={`/admin/items/${item.postId}`}
                                >
                                  Xem bài đăng
                                </Link>
                              </>
                            ) : (
                              <><Link
                                className="of-button"
                                to={`/approver/receipts/${item.id}`}
                              >
                                Xem hồ sơ tiếp nhận
                              </Link><Link className="of-inline-link" to={`/approver/office-claims/new?postId=${item.postId}`}>Lập yêu cầu nhận đồ →</Link></>
                            )}
                        </div>
                      </article>
                    ))}
                  </div>
                )}
                <Pager
                  data={items.data}
                  onChange={(page) => setFilters((f) => ({ ...f, page }))}
                />
              </>
            )}
          </LoadState>
        </>
      ) : (
        <LoadState state={locations}>
          {locations.data && (
            <div className="of-panel">
              <p className="of-muted">
                Sắp xếp theo tòa nhà, phòng, tủ hoặc ngăn. Chỉ ngừng sử dụng khi
                vị trí và các vị trí con không còn đồ.
              </p>
              <div className="of-table-wrap">
                <table className="of-table of-storage-location-table">
                  <thead>
                    <tr>
                      <th>Vị trí</th>
                      <th>Thuộc vị trí</th>
                      <th>Trạng thái</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {locations.data.map((l) => (
                      <tr key={l.id}>
                        <td data-label="Vị trí">
                          <strong>{l.name}</strong>
                          <small>{l.code}</small>
                        </td>
                        <td data-label="Thuộc vị trí">
                          {locations.data.find((p) => p.id === l.parentId)
                            ?.name || "Vị trí gốc"}
                        </td>
                        <td data-label="Trạng thái">
                          <Badge
                            value={l.active ? "Đang sử dụng" : "Ngừng sử dụng"}
                          />
                        </td>
                        <td>
                          <button
                            className="of-button"
                            onClick={() => setEditing(l)}
                            aria-label={`Chỉnh sửa ${l.name}`}
                          >
                            <Pencil size={15} />
                            Sửa
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {!locations.data.length && (
                <Empty text="Chưa có vị trí lưu trữ" />
              )}
            </div>
          )}
        </LoadState>
      )}
      {editing && (
        <LocationDialog
          key={editing.id || "new"}
          initial={editing}
          locations={locations.data || []}
          onClose={() => setEditing(null)}
          onDone={() => {
            setEditing(null);
            setMessage("Đã lưu vị trí kho.");
            locations.reload();
            items.reload();
          }}
        />
      )}
    </div>
  );
}
