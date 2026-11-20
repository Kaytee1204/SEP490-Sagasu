import { ArrowLeft, RefreshCw } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import useLoad from "../office/hooks/useLoad";
import { reviewApi } from "./api";
import { date } from "./format";
import ParticipantInquiry from "./components/ParticipantInquiry";
import ParticipantReportCard from "./components/ParticipantReportCard";
import { ErrorBox, Empty } from "./components/Shared";
import "./ReportReview.css";

const emptyMessages = {
  pending: "Bạn không có yêu cầu nào cần phản hồi.",
  history: "Chưa có thông tin đã trao đổi.",
  results: "Chưa có kết quả xử lý liên quan đến bạn.",
};

export default function ReportExchangesPage({ resultsView = false }) {
  const [params, setParams] = useSearchParams();
  const requestedTab = params.get("tab");
  const tab = resultsView || requestedTab === "results" ? "results"
    : requestedTab === "history" ? "history" : "pending";
  const isResults = tab === "results";
  const inquiries = useLoad(reviewApi.mine, "inquiries");
  const results = useLoad(() => isResults ? reviewApi.results() : null, isResults);

  const exchanges = inquiries.data ?? [];
  const needsReply = inquiry => !inquiry.responded_at && ["OPEN", "IN_REVIEW"].includes(inquiry.status);
  const pending = exchanges.filter(needsReply);
  const state = isResults ? results : inquiries;
  const visible = isResults ? (results.data ?? [])
    : tab === "pending" ? pending : exchanges.filter(inquiry => !needsReply(inquiry));
  const tabs = [
    { value: "pending", label: inquiries.data ? `Cần phản hồi (${pending.length})` : "Cần phản hồi", path: "/bao-cao-trao-doi" },
    { value: "history", label: "Thông tin đã trao đổi", path: "/bao-cao-trao-doi?tab=history" },
    { value: "results", label: "Kết quả xử lý", path: "/ket-qua-bao-cao" },
  ];

  function afterReply() {
    inquiries.reload();
    setParams({ tab: "history" });
  }

  return (
    <main className="container rr rr-public report-exchanges">
      <Link className="exchange-back" to="/bao-cao-cua-toi"><ArrowLeft size={15} aria-hidden="true" /> Báo cáo của tôi</Link>
      <header className="rr-header">
        <div>
          <h1>Trao đổi về báo cáo</h1>
          <p>Xem yêu cầu phản hồi và kết luận của các vụ việc bạn có tham gia, kể cả báo cáo do người khác gửi.</p>
        </div>
      </header>
      <section className="exchange-controls">
        <div className="exchange-controls-heading">
          <span>Trao đổi &amp; kết quả xử lý</span>
          <button type="button" onClick={state.reload} disabled={state.loading}>
            <RefreshCw size={15} aria-hidden="true" /> Làm mới danh sách
          </button>
        </div>
        <nav className="rr-tabs" aria-label="Trao đổi và kết quả báo cáo">
          {tabs.map(({ value, label, path }) => (
            <Link key={value} to={path} aria-current={tab === value ? "page" : undefined}>{label}</Link>
          ))}
        </nav>
      </section>
      <ErrorBox>{state.error}</ErrorBox>
      {state.loading && <Empty>{isResults ? "Đang tải kết quả xử lý…" : "Đang tải thông tin trao đổi…"}</Empty>}
      {state.data && !visible.length && <Empty>{emptyMessages[tab]}</Empty>}
      <div className="rr-stack exchange-list">
        {isResults ? visible.map(report => (
          <ParticipantReportCard key={report.report_id} report={report}
            timestamp={report.resolved_at && `Xử lý lúc ${date(report.resolved_at)}`}>
            <div className="rr-callout exchange-report-note">
              <h3>Kết luận xử lý</h3>
              <p>{report.resolution_note || "Chưa có ghi chú kết luận."}</p>
            </div>
          </ParticipantReportCard>
        )) : visible.map(inquiry => (
          <ParticipantInquiry key={`${inquiry.inquiry_id}:${inquiry.responded_at}`} inquiry={inquiry} onSaved={afterReply} />
        ))}
      </div>
    </main>
  );
}
