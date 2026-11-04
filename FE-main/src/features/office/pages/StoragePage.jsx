import { useState } from "react";
import { Link } from "react-router-dom";
import { Plus, MapPin, Pencil } from "lucide-react";
import { useAuth } from "@/context/AuthContext";
import { officeApi } from "../api/officeApi";
import { date } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import LocationDialog from "../components/LocationDialog";
import Badge from "../components/Badge";
import Header from "../components/Header";
import Notice from "../components/Notice";
import LoadState from "../components/LoadState";
import Empty from "../components/Empty";
import SearchBox from "../components/SearchBox";
import Select from "../components/Select";
import Pager from "../components/Pager";
import "../Office.css";

export default function StoragePage() {
  const { isManager } = useAuth();
  const [tab, setTab] = useState("items");
  const [filters, setFilters] = useState({ page: 1, q: "", locationId: "" });
  const items = useLoad(
    () => officeApi.stored(filters),
    JSON.stringify(filters),
  );
  const locations = useLoad(officeApi.locations, "locations");
  const [editing, setEditing] = useState(null);
  const [message, setMessage] = useState("");
  const change = (key, value) =>
    setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  return (
    <div className="office-page">
      <Header
        title="Kho đồ thất lạc"
        description="Tra cứu đồ đang được văn phòng lưu giữ và vị trí để tìm đúng món đồ khi cần."
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
          <button
            className={`of-button ${tab === "items" ? "active" : ""}`}
            onClick={() => setTab("items")}
          >
            Đồ đang lưu giữ
          </button>
          <button
            className={`of-button ${tab === "locations" ? "active" : ""}`}
            onClick={() => setTab("locations")}
          >
            Vị trí lưu trữ
          </button>
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
                  <div className="of-grid">
                    {items.data.content.map((item) => (
                      <article key={item.id} className="of-card">
                        <div className="of-card-body">
                          <div className="of-badges">
                            <Badge value={item.status} />
                            <span className="of-muted">{item.code}</span>
                          </div>
                          <h2>{item.title}</h2>
                          <p className="of-actions">
                            <MapPin size={16} />
                            {item.location || "Chưa bố trí vị trí"}
                          </p>
                          <p className="of-muted">
                            {item.condition}
                            <br />
                            Tiếp nhận: {date(item.receivedAt)}
                            <br />
                            {item.receivedBy}
                          </p>
                          <div className="of-card-footer">
                            {isManager ? (
                              <>
                                <Link
                                  className="of-button"
                                  to={`/admin/items/${item.postId}`}
                                >
                                  Xem bài đăng
                                </Link>
                                <Link
                                  className="of-inline-link"
                                  to={`/admin/office-claims/new?postId=${item.postId}`}
                                >
                                  Lập yêu cầu →
                                </Link>
                              </>
                            ) : (
                              <Link
                                className="of-button"
                                to={`/approver/receipts/${item.id}`}
                              >
                                Xem hồ sơ tiếp nhận
                              </Link>
                            )}
                          </div>
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
                <table className="of-table">
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
                        <td>
                          <strong>{l.name}</strong>
                          <small>{l.code}</small>
                        </td>
                        <td>
                          {locations.data.find((p) => p.id === l.parentId)
                            ?.name || "Vị trí gốc"}
                        </td>
                        <td>
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
