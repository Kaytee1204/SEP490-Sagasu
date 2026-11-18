export function transferStage(transfer) {
  if (transfer.status !== "PENDING") return transfer.status;
  const response = transfer.appointment?.responseStatus;
  return !response || ["DECLINED", "EXPIRED"].includes(response) ? "UNSCHEDULED" : response;
}
