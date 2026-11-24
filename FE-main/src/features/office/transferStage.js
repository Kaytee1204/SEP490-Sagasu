export function transferStage(transfer) {
  if (transfer.status !== "PENDING") return transfer.status;
  return transfer.appointment?.status === "SCHEDULED" ? "SCHEDULED" : "UNSCHEDULED";
}
