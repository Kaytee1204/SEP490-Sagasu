export const counterConfirmationAnchor = "xac-nhan-tai-quay";

export function counterConfirmationPath(claimId) {
  return `/van-phong/claims/${claimId}#${counterConfirmationAnchor}`;
}

export function counterConfirmationReturn(path) {
  return typeof path === "string" && /^\/van-phong\/claims\/[1-9]\d*#xac-nhan-tai-quay$/.test(path) ? path : null;
}
