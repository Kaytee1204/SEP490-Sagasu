import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import useReviewResource from "./hooks/useReviewResource";
import ParticipantInquiry from "./components/ParticipantInquiry";
import { Badge, ErrorBox, Empty } from "./components/Shared";
import { date } from "./format";
import "./ReportReview.css";
export default function ReportExchangesPage() {
  const [reload, setReload] = useState(0);
  const [params, setParams] = useSearchParams();
  const tab = ["history", "results"].includes(params.get("tab"))
    ? params.get("tab")
    : "pending";
  const { data: resource, error } = useReviewResource(
    "exchanges",
    null,
    reload,
  );
  const data = resource?.[0],
    results = resource?.[1] || [];
  const pending = (data || []).filter(
    (i) => !i.responded_at && ["OPEN", "IN_REVIEW"].includes(i.status),
  );
  const visible =
    tab === "pending"
      ? pending
      : (data || []).filter((i) => !pending.includes(i));
  return (
    <main className="rr rr-public">
      <Link to="/bao-cao-cua-toi">← Báo cáo của tôi</Link>
      <header className="rr-header">
        <div>
          <h1>Trao đổi về báo cáo</h1>
          <p>
            Bổ sung thông tin được yêu cầu và xem kết quả những vụ việc liên
            quan đến bạn.
          </p>
        </div>
        <button onClick={() => setReload((x) => x + 1)}>Làm mới</button>
      </header>
      <ErrorBox>{error}</ErrorBox>
      <nav className="rr-tabs" aria-label="Trao đổi và kết quả báo cáo">
        {[
          ["pending", `Cần phản hồi (${pending.length})`],
          ["history", "Thông tin đã trao đổi"],
          ["results", "Kết quả xử lý"],
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
      </nav>
      {!data && !error && <Empty>Đang tải…</Empty>}
      {data &&
        (tab === "results" ? results.length === 0 : visible.length === 0) && (
          <Empty>
            {tab === "pending"
              ? "Bạn không có yêu cầu nào cần phản hồi."
              : tab === "results"
                ? "Chưa có kết quả xử lý liên quan đến bạn."
                : "Chưa có thông tin đã trao đổi."}
          </Empty>
        )}
      <div className="rr-stack">
        {tab !== "results" &&
          visible.map((i) => (
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
      {tab === "results" && results.length > 0 && (
        <section className="rr-card">
          <h2>Kết quả xử lý</h2>
          {results.map((r) => (
            <article className="rr-record" key={r.report_id}>
              <div className="rr-row">
                <h3>Báo cáo #{r.report_id}</h3>
                <Badge value={r.status} />
              </div>
              <small>{date(r.resolved_at)}</small>
              <p>{r.resolution_note}</p>
            </article>
          ))}
        </section>
      )}
    </main>
  );
}
