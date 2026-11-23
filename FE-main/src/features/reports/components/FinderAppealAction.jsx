import { useState } from "react";
import { createPortal } from "react-dom";
import { ArrowUpRight, RotateCcw } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { getClaimOnPost } from "@/features/claims/api/claimApi";
import RevokeClaimApprovalDialog from "@/features/claims/components/RevokeClaimApprovalDialog";
import { postClaimsPath } from "@/features/claims/utils/claimUtils";
import { getReportWorkspaceDetail } from "../api/reportApi";

export default function FinderAppealAction({ report, claimPath }) {
  const { finder_approved_claim_id: claimId, report_subject_id: postId, report_id: reportId } = report;
  const [claim, setClaim] = useState(null);
  const [busy, setBusy] = useState(false);
  const [fallback, setFallback] = useState(null);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const reviewPath = postClaimsPath(postId, "PENDING");

  async function openConfirmation() {
    setBusy(true);
    setError("");
    try {
      const currentClaim = await getClaimOnPost(postId, claimId);
      // Read the report after the claim to ensure it still points to this active approval round.
      const { report: currentReport } = await getReportWorkspaceDetail(reportId);
      const stillLinked = currentReport.needs_reopen && currentReport.finder_approved_claim_id === claimId;
      if (stillLinked && currentClaim.canRevokeApproval) {
        setClaim(currentClaim);
      } else {
        setFallback(currentClaim.status === "PENDING" && currentClaim.postStatus === "OPEN" ? "reopened" : "unavailable");
      }
    } catch (problem) {
      setError(problem.message || "Chưa kiểm tra được hồ sơ. Vui lòng thử lại.");
    } finally {
      setBusy(false);
    }
  }

  return <div className="report-appeal-next">
    <div>
      <h3>{fallback === "reopened" ? "Hồ sơ đã về chờ xét duyệt" : "Cần xét duyệt lại người nhận"}</h3>
      {fallback !== "reopened" && <p>
        {fallback === "unavailable"
          ? "Hồ sơ chưa thể thu hồi ở bước hiện tại. Kiểm tra tiến trình để biết cần chờ hay làm gì tiếp theo."
          : "Nếu vẫn giữ món đồ, hãy mở lại xét duyệt. Hồ sơ đã chấp thuận sẽ về chờ duyệt để bạn đối chiếu cùng các hồ sơ khác."}
      </p>}
      {error && <p className="report-error" role="alert">{error}</p>}
    </div>
    {fallback === "reopened" ? <Link className="btn btn-ghost" to={reviewPath}>Xem yêu cầu chờ duyệt<ArrowUpRight size={15} aria-hidden="true" /></Link>
      : fallback === "unavailable" ? <Link className="btn btn-ghost" to={`${claimPath}#handover`}>Theo dõi bàn giao<ArrowUpRight size={15} aria-hidden="true" /></Link>
        : <button className="btn btn-accent" type="button" onClick={openConfirmation} disabled={busy}>
          <RotateCcw size={15} aria-hidden="true" />{busy ? "Đang kiểm tra…" : "Mở lại xét duyệt"}
        </button>}
    {claim && createPortal(
      <RevokeClaimApprovalDialog claim={claim} initialReason={`Xét duyệt lại theo kết luận tranh chấp #${reportId}.`}
        cancelLabel="Quay lại" onClose={() => setClaim(null)}
        onRevoked={() => navigate(reviewPath, { state: { revokedClaimId: claimId } })} />,
      document.body,
    )}
  </div>;
}
