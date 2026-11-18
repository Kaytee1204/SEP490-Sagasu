import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { ArrowLeft, ArrowRight, Plus } from "lucide-react";
import { officeApi } from "../api/officeApi";
import { date, labels, progressLabels } from "../officeUtils";
import useLoad from "../hooks/useLoad";
import Badge from "../components/Badge";
import Header from "../components/Header";
import LoadState from "../components/LoadState";
import Empty from "../components/Empty";
import SearchBox from "../components/SearchBox";
import Select from "../components/Select";
import Pager from "../components/Pager";
import ClaimProgress from "../components/ClaimProgress";
import PersonalOfficeHeader from '../components/PersonalOfficeHeader';
import PersonalClaimCards from '../components/PersonalClaimCards';
import "../Office.css";
import '../PersonalOffice.css';

export default function OfficeClaimList({ mine = false }) {
  const location = useLocation();
  const returnTo = location.state?.officeClaimReturn;
  const backPath = typeof returnTo === "string" && (returnTo === "/claim-cua-toi" || returnTo.startsWith("/claim-cua-toi?"))
    ? returnTo : "/claim-cua-toi?view=sent";
  const [filters, setFilters] = useState(() => { try { return JSON.parse(sessionStorage.getItem(`office-claims-filters:${mine}`)) || { page: 1, q: "", status: "ALL", stage: "ALL" }; } catch { return { page: 1, q: "", status: "ALL", stage: "ALL" }; } });
  useEffect(() => { sessionStorage.setItem(`office-claims-filters:${mine}`, JSON.stringify(filters)); }, [filters, mine]);
  const state = useLoad(
    () => officeApi.claims(mine ? {...filters, stage: "ALL"} : filters, mine),
    JSON.stringify(filters) + mine,
  );
  const base = mine ? "/van-phong/claims" : "/approver/office-claims";
  const change = (key, value) =>
    setFilters((f) => ({ ...f, [key]: value, page: 1 }));
  return (
    <div className={`office-page ${mine?'office-personal':''}`}>
      {mine?<PersonalOfficeHeader active="claims" backPath={backPath}/>:<Header
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
          <Link className="of-button" to="/van-phong/chuyen-giao">Theo dõi giao đồ cho văn phòng</Link>
        )}
        {mine && (
          <Link className="of-button" to={backPath}>
            <ArrowLeft size={18} />
            Quay lại
          </Link>
        )}
      </Header>}
      <div className={`of-panel${mine ? "" : " of-staff-list"}`}>
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
          {!mine && <Select
            label="Tiến trình xử lý"
            value={filters.stage}
            onChange={(v) => change("stage", v)}
            options={[["ALL", "Tất cả"], ...Object.entries(progressLabels)]}
          />}
          <Select
            label={mine ? "Trạng thái yêu cầu" : "Trạng thái hồ sơ"}
            value={filters.status}
            onChange={(v) => change("status", v)}
            options={[
              "ALL",
              "PENDING",
              "NEEDS_INFO",
              "APPROVED",
              "REJECTED",
              "CANCELLED",
              "COMPLETED",
              "CLOSED",
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
                    filters.status !== "ALL" || filters.stage !== "ALL" ||
                    filters.page > 1
                      ? "Không tìm thấy yêu cầu phù hợp"
                      : mine
                        ? "Bạn chưa gửi yêu cầu nhận đồ"
                        : "Chưa có yêu cầu nhận đồ"
                  }
                  description={
                    filters.q.trim() ||
                    filters.status !== "ALL" || filters.stage !== "ALL" ||
                    filters.page > 1
                      ? "Thử thay đổi từ khóa hoặc bỏ bộ lọc để xem lại danh sách."
                      : mine
                        ? "Khi tìm thấy đồ của mình trong một bài đăng do văn phòng tiếp nhận, bạn có thể gửi yêu cầu nhận lại đồ."
                        : "Các yêu cầu gửi trực tuyến và hồ sơ được lập tại quầy sẽ hiển thị tại đây."
                  }
                >
                  {filters.q.trim() ||
                  filters.status !== "ALL" || filters.stage !== "ALL" ||
                  filters.page > 1 ? (
                    <button
                      type="button"
                      className="of-button"
                      onClick={() =>
                        setFilters({ page: 1, q: "", status: "ALL", stage: "ALL" })
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
              ) : mine ? <PersonalClaimCards claims={state.data.content}/> : (
                <div className="of-table-wrap">
                  <table className="of-table of-claim-table">
                    <thead>
                      <tr>
                        <th>Yêu cầu / Đồ vật</th>
                        <th>Người yêu cầu</th>
                        <th>Tiến trình xử lý</th>
                        <th>Ngày gửi</th>
                        <th scope="col">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {state.data.content.map((c) => (
                        <tr key={c.id}>
                          <td data-label="Đồ vật">
                            <Link to={`${base}/${c.id}`}>{c.postTitle}</Link>
                            <small>
                              Yêu cầu #{c.id} · Bài #{c.postId}
                            </small>
                          </td>
                          <td data-label="Người yêu cầu">
                            {c.claimantName}
                            <small>
                              <Badge value={c.channel} />
                              {c.channel === "COUNTER" &&
                                ` · Nhập bởi ${c.createdBy}`}
                            </small>
                          </td>
                          <td data-label="Tiến trình">
                            <ClaimProgress stage={c.stage} scheduledAt={c.collectionAt} />
                          </td>
                          <td data-label="Ngày gửi">{date(c.createdAt)}</td>
                          <td data-label="Thao tác">
                            <Link className={`of-button of-worklist-action${!c.ownRequest && c.status === "PENDING" ? " of-primary" : ""}`} to={c.ownRequest ? `/van-phong/claims/${c.id}` : `${base}/${c.id}`}>
                              {!c.ownRequest && c.status === "PENDING" ? "Xét duyệt" : "Xem chi tiết"}
                              <ArrowRight size={16} aria-hidden="true" />
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
