import { useEffect, useState } from "react";
import { Search, RefreshCw } from "lucide-react";
import { Badge, ErrorBox, Empty } from "./Shared";
import { date, labels } from "../format";
import useReviewResource from "../hooks/useReviewResource";
export default function ReportQueue({ onOpen }) {
  const [filters, setFilters] = useState({
      page: 1,
      status: "ALL",
      kind: "ALL",
      target: "ALL",
      q: "",
    }),
    [search, setSearch] = useState(""),
    [reload, setReload] = useState(0);
  useEffect(() => {
    const timer = setTimeout(
      () => setFilters((old) => ({ ...old, q: search, page: 1 })),
      350,
    );
    return () => clearTimeout(timer);
  }, [search]);
  const { data, error } = useReviewResource("queue", filters, reload);
  const filter = (key, value) =>
    setFilters((old) => ({ ...old, [key]: value, page: 1 }));
  return (
    <section className="rr-card">
      <div className="rr-filters">
        <label className="rr-search">
          Tìm báo cáo
          <div>
            <Search size={18} />
            <input
              value={search}
              maxLength={200}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Mã báo cáo, người gửi hoặc nội dung…"
            />
          </div>
        </label>
        <label>
          Trạng thái
          <select
            value={filters.status}
            onChange={(e) => filter("status", e.target.value)}
          >
            <option value="ALL">Tất cả trạng thái</option>
            {["OPEN", "IN_REVIEW", "RESOLVED", "REJECTED"].map((s) => (
              <option key={s} value={s}>
                {labels[s]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Loại báo cáo
          <select
            value={filters.kind}
            onChange={(e) => filter("kind", e.target.value)}
          >
            <option value="ALL">Tất cả loại</option>
            <option value="GENERAL">Báo cáo vi phạm</option>
            <option value="HANDOVER_ISSUE">Vướng mắc trao trả</option>
          </select>
        </label>
        <label>
          Đối tượng
          <select
            value={filters.target}
            onChange={(e) => filter("target", e.target.value)}
          >
            {Object.entries({
              ALL: "Tất cả",
              POST: "Bài đăng",
              CLAIM: "Yêu cầu nhận đồ",
              MESSAGE: "Tin nhắn",
              USER: "Tài khoản",
            }).map(([v, t]) => (
              <option key={v} value={v}>
                {t}
              </option>
            ))}
          </select>
        </label>
      </div>
      <ErrorBox>{error}</ErrorBox>
      {error && (
        <button onClick={() => setReload((x) => x + 1)}>Thử lại</button>
      )}
      {!data && !error && <Empty>Đang tải báo cáo…</Empty>}
      {data && (
        <>
          <div className="rr-row rr-list-meta">
            <span>{data.totalElements} báo cáo</span>
            <button onClick={() => setReload((x) => x + 1)}>
              <RefreshCw size={15} /> Làm mới
            </button>
          </div>
          {!data.content.length ? (
            <Empty>Chưa có báo cáo phù hợp. Thử đổi bộ lọc hoặc từ khóa.</Empty>
          ) : (
            <div className="rr-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Báo cáo</th>
                    <th>Người gửi</th>
                    <th>Trạng thái</th>
                    <th>Người xử lý</th>
                    <th>Ngày gửi</th>
                    <th>
                      <span className="sr-only">Thao tác</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((r) => (
                    <tr key={r.report_id}>
                      <td>
                        <strong>
                          #{r.report_id} · {labels[r.kind]}
                        </strong>
                        <p className="rr-excerpt">{r.description}</p>
                      </td>
                      <td>{r.reporter}</td>
                      <td>
                        <Badge value={r.status} />
                      </td>
                      <td>{r.assignee || "Chưa nhận xử lý"}</td>
                      <td>{date(r.created_at)}</td>
                      <td>
                        <button onClick={() => onOpen(r.report_id)}>
                          Xem xét →
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <footer className="rr-pagination">
            <button
              disabled={data.page <= 1}
              onClick={() =>
                setFilters((old) => ({ ...old, page: data.page - 1 }))
              }
            >
              Trước
            </button>
            <span>
              Trang {data.page}/{data.totalPages}
            </span>
            <button
              disabled={data.page >= data.totalPages}
              onClick={() =>
                setFilters((old) => ({ ...old, page: data.page + 1 }))
              }
            >
              Sau
            </button>
          </footer>
        </>
      )}
    </section>
  );
}
