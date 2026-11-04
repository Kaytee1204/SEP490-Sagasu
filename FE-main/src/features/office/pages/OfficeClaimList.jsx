import { useState } from "react";
import { Link } from "react-router-dom";
import { Plus } from "lucide-react";
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

export default function OfficeClaimList({ mine = false }) {
  const [filters, setFilters] = useState({ page: 1, q: "", status: "ALL" });
  const state = useLoad(
    () => officeApi.claims(filters, mine),
    JSON.stringify(filters) + mine,
  );
  const base = mine ? "/van-phong/claims" : "/admin/office-claims";
  const change = (key, value) =>
    setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  return (
    <div className="office-page">
      <Header
        eyebrow={
          mine ? "Văn phòng · Hồ sơ của bạn" : "Văn phòng · Xét duyệt hồ sơ"
        }
        title={mine ? "Yêu cầu nhận đồ của tôi" : "Yêu cầu nhận đồ"}
        description={
          mine
            ? "Theo dõi các yêu cầu bạn đã gửi đến văn phòng, bổ sung thông tin và xem lịch hẹn nhận đồ."
            : "Đối chiếu bằng chứng sở hữu, xử lý yêu cầu và hẹn người nhận đến văn phòng."
        }
      >
        {!mine && (
          <Link className="of-button of-primary" to={`${base}/new`}>
            <Plus size={18} />
            Lập yêu cầu tại quầy
          </Link>
        )}
        {mine && (
          <Link className="of-button" to="/claim-cua-toi?view=sent">
            Yêu cầu gửi người nhặt
          </Link>
        )}
      </Header>
      <div className="of-panel">
        <div className="of-toolbar">
          <SearchBox
            value={filters.q}
            onChange={(v) => change("q", v)}
            placeholder={
              mine
                ? "Tìm đồ vật trong yêu cầu của bạn…"
                : "Tìm đồ vật hoặc người yêu cầu…"
            }
          />
          <Select
            label="Trạng thái hồ sơ"
            value={filters.status}
            onChange={(v) => change("status", v)}
            options={[
              "ALL",
              "PENDING",
              "NEEDS_INFO",
              "APPROVED",
              "REJECTED",
              "CANCELLED",
            ].map((v) => [v, v === "ALL" ? "Tất cả" : labels[v]])}
          />
        </div>
        <LoadState state={state}>
          {state.data && (
            <>
              {!state.data.content.length ? (
                <Empty
                  text={
                    filters.q.trim() ||
                    filters.status !== "ALL" ||
                    filters.page > 1
                      ? "Không tìm thấy yêu cầu phù hợp"
                      : mine
                        ? "Bạn chưa gửi yêu cầu nhận đồ"
                        : "Chưa có yêu cầu nhận đồ"
                  }
                  description={
                    filters.q.trim() ||
                    filters.status !== "ALL" ||
                    filters.page > 1
                      ? "Thử thay đổi từ khóa hoặc bỏ bộ lọc để xem lại danh sách."
                      : mine
                        ? "Khi tìm thấy đồ của mình trong một bài đăng do văn phòng tiếp nhận, bạn có thể gửi yêu cầu nhận lại đồ."
                        : "Các yêu cầu gửi trực tuyến và hồ sơ được lập tại quầy sẽ hiển thị tại đây."
                  }
                >
                  {filters.q.trim() ||
                  filters.status !== "ALL" ||
                  filters.page > 1 ? (
                    <button
                      type="button"
                      className="of-button"
                      onClick={() =>
                        setFilters({ page: 1, q: "", status: "ALL" })
                      }
                    >
                      Xóa bộ lọc
                    </button>
                  ) : mine ? (
                    <Link className="of-button of-primary" to="/tim-kiem">
                      Tìm đồ thất lạc
                    </Link>
                  ) : (
                    <Link className="of-button of-primary" to={`${base}/new`}>
                      Lập yêu cầu tại quầy
                    </Link>
                  )}
                </Empty>
              ) : (
                <div className="of-table-wrap">
                  <table className="of-table">
                    <thead>
                      <tr>
                        <th>Hồ sơ / Đồ vật</th>
                        <th>Người khai</th>
                        <th>Trạng thái</th>
                        <th>Ngày gửi</th>
                        <th></th>
                      </tr>
                    </thead>
                    <tbody>
                      {state.data.content.map((c) => (
                        <tr key={c.id}>
                          <td>
                            <Link to={`${base}/${c.id}`}>{c.postTitle}</Link>
                            <small>
                              Hồ sơ #{c.id} · Bài #{c.postId}
                            </small>
                          </td>
                          <td>
                            {c.claimantName}
                            <small>
                              <Badge value={c.channel} />
                              {c.channel === "COUNTER" &&
                                ` · Nhập bởi ${c.createdBy}`}
                            </small>
                          </td>
                          <td>
                            <Badge value={c.status} />
                          </td>
                          <td>{date(c.createdAt)}</td>
                          <td>
                            <Link className="of-button" to={`${base}/${c.id}`}>
                              {!mine && c.status === "PENDING"
                                ? "Xét duyệt"
                                : "Chi tiết"}{" "}
                              →
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
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
    </div>
  );
}
