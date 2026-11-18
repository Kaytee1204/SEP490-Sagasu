import { ArrowLeft, RefreshCw } from "lucide-react";
import { useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import useLoad from "../office/hooks/useLoad";
import { reviewApi } from "./api";
import ParticipantInquiry from "./components/ParticipantInquiry";
import { ErrorBox, Empty } from "./components/Shared";

import "./ReportReview.css";
export default function ReportExchangesPage() {
  const [reload, setReload] = useState(0);
  const [params, setParams] = useSearchParams();
  const tab = ["history", "results"].includes(params.get("tab"))
    ? params.get("tab")
    : "pending";
  const {data,error}=useLoad(reviewApi.mine,reload);
  const pending = (data || []).filter(
    (i) => !i.responded_at && ["OPEN", "IN_REVIEW"].includes(i.status),
  );
  const visible =
    tab === "pending"
      ? pending
      : (data || []).filter((i) => !pending.includes(i));
  if(tab==="results") return <Navigate replace to="/ket-qua-bao-cao"/>;
  return (
    <main className="container rr rr-public report-exchanges">
      <Link className="exchange-back" to="/bao-cao-cua-toi"><ArrowLeft size={15}/> Báo cáo của tôi</Link>
      <header className="rr-header">
        <div>
          <h1>Trao đổi về báo cáo</h1>
          <p>
            Bổ sung thông tin được yêu cầu và xem kết quả những vụ việc liên
            quan đến bạn.
          </p>
        </div>

      </header>
      <ErrorBox>{error}</ErrorBox>
      <section className="exchange-controls"><div className="exchange-controls-heading"><span>Trao đổi & kết quả xử lý</span><button type="button" onClick={() => setReload((x) => x + 1)}><RefreshCw size={15}/> Làm mới danh sách</button></div><span className="exchange-filter-label">Trạng thái trao đổi</span>
      <nav className="rr-tabs" aria-label="Trao đổi và kết quả báo cáo">
        {[
          ["pending", `Cần phản hồi (${pending.length})`],
          ["history", "Thông tin đã trao đổi"],
        ].map(([value, label]) => (
          <button
            key={value}
            className={tab === value ? "active" : ""}
            aria-current={tab === value ? "page" : undefined}
            onClick={() => setParams(value === "pending" ? {} : { tab: value })}
          >
            {label}
          </button>
        ))}
        <Link to="/ket-qua-bao-cao">Kết quả xử lý</Link>
      </nav></section>
      {!data && !error && <Empty>Đang tải…</Empty>}
      {data &&
        (visible.length === 0) && (
          <Empty>
            {tab === "pending"
              ? "Bạn không có yêu cầu nào cần phản hồi."
              : tab === "results"
                ? "Chưa có kết quả xử lý liên quan đến bạn."
                : "Chưa có thông tin đã trao đổi."}
          </Empty>
        )}
      <div className="rr-stack exchange-list">
        {visible.map((i) => (
            <ParticipantInquiry
              key={`${i.inquiry_id}:${i.responded_at}`}
              inquiry={i}
              onSaved={() => {
                setReload((x) => x + 1);
                setParams({ tab: "history" });
              }}
            />
          ))}
      </div>

    </main>
  );
}
