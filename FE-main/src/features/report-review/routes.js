import { reviewKinds } from "./format";

export const reportReviewRoot = "/admin/reports";
export const reportReviewPages = {
  GENERAL: "vi-pham",
  HANDOVER_ISSUE: "su-co-ban-giao",
  CLAIM_APPEAL: "khang-cao",
};

export function reportReviewListPath(kind) {
  const page = Object.hasOwn(reportReviewPages, kind) ? reportReviewPages[kind] : reportReviewPages.GENERAL;
  return `${reportReviewRoot}/${page}`;
}

export const reportReviewNavItems = Object.keys(reportReviewPages).map(kind => ({
  kind, label: reviewKinds[kind], path: reportReviewListPath(kind),
}));
