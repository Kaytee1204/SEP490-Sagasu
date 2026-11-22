import { Navigate, useSearchParams } from "react-router-dom";
import ReportQueue from "./components/ReportQueue";
import { reviewKinds } from "./format";
import { reportReviewListPath, reportReviewRoot } from "./routes";
import "./ReportReview.css";
import "./ReportWorkspace.css";

export default function ReportReviewPage({ kind }) {
  const [params] = useSearchParams();
  const id = params.get("id");
  const next = new URLSearchParams(params);
  if (id) {
    next.delete("id");
    return <Navigate replace to={`${reportReviewRoot}/${id}${next.size ? `?${next}` : ""}`} />;
  }
  if (!kind) {
    const path = reportReviewListPath(next.get("kind"));
    next.delete("kind");
    return <Navigate replace to={`${path}${next.size ? `?${next}` : ""}`} />;
  }

  next.set("kind", kind);
  return <section className="rr rr-workspace" aria-labelledby="rr-queue-title">
    <header className="rr-workspace-heading">
      <h1 id="rr-queue-title">{reviewKinds[kind]}</h1>
    </header>
    <ReportQueue key={kind} kind={kind} reportPath={value => `${reportReviewRoot}/${value}?${next}`} />
  </section>;
}
