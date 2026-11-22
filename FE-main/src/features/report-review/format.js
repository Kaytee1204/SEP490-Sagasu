export const reportTargets = {
  POST: "Bài đăng",
  CLAIM: "Yêu cầu nhận đồ",
  MESSAGE: "Tin nhắn",
  USER: "Tài khoản",
};

export const reviewKinds = {
  GENERAL: "Vi phạm",
  HANDOVER_ISSUE: "Sự cố bàn giao",
  CLAIM_APPEAL: "Tranh chấp",
};

export const labels = {
  PRIORITY_HIGH: "Đặt ưu tiên cao",
  PRIORITY_NORMAL: "Đặt ưu tiên bình thường",
  PRIORITY_AUTO: "Ưu tiên theo nguy cơ giao nhận",
  CASE_LINK: "Liên kết vụ việc",
  CASE_UNLINK: "Tách khỏi vụ việc",
  DISPUTE_RESOLVE: "Kết luận tranh chấp",
  OPEN: "Chờ xử lý",
  IN_REVIEW: "Đang xem xét",
  RESOLVED: "Đã giải quyết",
  REJECTED: "Đã bác bỏ",
  GENERAL: "Báo cáo vi phạm",
  HANDOVER_ISSUE: "Sự cố bàn giao",
  CLAIM_APPEAL: "Tranh chấp kết quả nhận đồ",
  PENDING: "Chờ xử lý",
  NEEDS_INFO: "Cần bổ sung",
  APPROVED: "Đã duyệt",
  COMPLETED: "Hoàn tất",
  CANCELLED: "Đã hủy",
  CLOSED: "Đã đóng",
  DISPUTED: "Đang tranh chấp",
  WAITING_CONFIRMATION: "Chờ xác nhận",
  RETURNED: "Đã trả đồ",
  RESERVED: "Đang trao trả",
  VISIBLE: "Đang hiển thị",
  HIDDEN: "Đã ẩn",
  OFFICE: "Văn phòng",
  PERSONAL: "Người nhặt",
  P2P: "Giữa người dùng",
  ACTIVE: "Hoạt động",
  SUSPENDED: "Đình chỉ",
  DELETED: "Đã xóa",
  CONFIRMED: "Đã đồng ý",
  DECLINED: "Không thể đến",
  EXPIRED: "Hết hạn",
  SCHEDULED: "Đã hẹn",
  PASSED: "Đạt",
  FAILED: "Chưa đạt",
  RECEIVED: "Đã tiếp nhận",
  IN_STORAGE: "Đang lưu kho",
  ONLINE: "Trực tuyến",
  COUNTER: "Tại quầy",
  FOUND: "Nhặt được",
  LOST: "Thất lạc",
  USER: "Người dùng",
  APPROVER: "Staff",
  MANAGER: "Quản lý",
  ADMIN: "Quản trị viên",
  TAKE: "Nhận xử lý",
  NOTE: "Ghi chú nội bộ",
  HOLD: "Tạm dừng giao nhận",
  RELEASE: "Cho phép giao nhận",
  HIDE_POST: "Ẩn bài đăng",
  WARN_ACCOUNT: "Cảnh báo tài khoản",
  SUSPEND_ACCOUNT: "Đình chỉ tài khoản",
  DISMISS: "Bác bỏ báo cáo",
  RESOLVE: "Giải quyết báo cáo",
  VIOLATION: "Kết luận có vi phạm",
  NO_VIOLATION: "Không phát hiện vi phạm",
  INSUFFICIENT_EVIDENCE: "Chưa đủ căn cứ",
  REQUEST_POST_CORRECTION: "Yêu cầu chỉnh sửa bài đăng",
  SETTLED: "Ghi nhận vấn đề đã giải quyết",
  UNSUBSTANTIATED: "Chưa đủ căn cứ xác định vi phạm",
  HANDOVER_COMPLETED: "Xác nhận đã trả đồ",
  HANDOVER_CONTINUE: "Tiếp tục trao trả",
  HANDOVER_RETRY: "Hủy lượt giao và hẹn giao lại",
  HANDOVER_REOPEN: "Thu hồi chấp thuận để xét lại",
  CLAIM_REJECTED: "Hủy bàn giao và mở lại xét duyệt",
  CLAIM_VIOLATION_REJECT: "Bác yêu cầu nhận đồ vi phạm",
  REQUEST_ITEM_RECOVERY: "Yêu cầu thu hồi món đồ",
  CONFIRM_ITEM_RECOVERY: "Xác nhận đã thu hồi món đồ",
  APPEAL_KEEP: "Giữ người đã duyệt",
  APPEAL_INSUFFICIENT: "Giữ người đã duyệt · Chưa đủ căn cứ",
  APPEAL_REJECT_CLAIM: "Yêu cầu nhận đồ sai · Chặn gửi lại",
  APPEAL_REQUIRE_REVIEW: "Yêu cầu xét lại người nhận",
  APPEAL_WRONG_RECIPIENT: "Xác nhận duyệt nhầm người",
  APPEAL_ROUND_CLOSED: "Đã kết thúc tranh chấp của lượt duyệt cũ",
  APPEAL_REOPEN: "Thu hồi chấp thuận",
  ASK_INFORMATION: "Yêu cầu bổ sung",
  REPLY: "Nhận phản hồi",
  VIEW_CHAT: "Đọc hội thoại",
  CREATE: "Gửi báo cáo",
  SUBMIT: "Gửi yêu cầu",
  APPROVE: "Duyệt yêu cầu",
  REJECT: "Từ chối yêu cầu",
  REQUEST_INFO: "Đề nghị bổ sung",
  RESUBMIT: "Bổ sung thông tin",
  HANDOVER: "Ghi nhận bàn giao",
  CONFIRM_RECEIPT: "Xác nhận nhận đồ",
  AUTO_CLOSE: "Đóng sau trao trả",
  REPORT_ISSUE: "Báo sự cố",
  TRANSFER_OFFICE: "Văn phòng tiếp nhận",
  INVITE_CONFIRM: "Đồng ý lịch hẹn",
  INVITE_DECLINE: "Từ chối lịch hẹn",
  INVITE_EXPIRE: "Lịch hẹn hết hạn",
  OFFICE_CANCEL: "Văn phòng hủy",
  RESCHEDULE: "Hẹn lại",
};

export const officeAppealActionHelp = {
  APPEAL_KEEP: "Giữ người đã duyệt. Chỉ chặn gửi lại khi đã xác minh yêu cầu nhận đồ sai.",
  APPEAL_WRONG_RECIPIENT: "Tài sản tiếp tục lưu kho. Văn phòng xét duyệt lại sau khi mọi tranh chấp của lần duyệt này đã được xử lý.",
};

export const appealActionHelp = {
  APPEAL_KEEP: "Giữ người đã duyệt. Chỉ chặn gửi lại khi đã xác minh yêu cầu nhận đồ sai.",
  APPEAL_WRONG_RECIPIENT: "Đưa hồ sơ đã được chấp thuận về chờ xét duyệt và báo cho người nhặt xét lại các hồ sơ còn hợp lệ.",
  REQUEST_ITEM_RECOVERY: "Dùng khi món đồ đã được giao và cần lấy lại. Hệ thống gửi hướng dẫn cho người đang giữ đồ.",
  CONFIRM_ITEM_RECOVERY: "Dùng sau khi đã xác minh món đồ được lấy lại.",
};
export const date = (value) =>
  value
    ? new Date(value).toLocaleString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" })
    : "Chưa ghi nhận";

export function participantRoles(data, accountId) {
  const { report, caseKind, approvedContext, appeal } = data;
  const isAppeal = caseKind === "CLAIM_APPEAL";
  const roles = [];
  if (accountId === report.reporter_id) roles.push(isAppeal ? "Người gửi tranh chấp" : "Người gửi báo cáo");
  if (isAppeal && accountId === approvedContext?.claim?.claimant_id) roles.push("Người được chấp thuận");
  if (isAppeal && appeal && accountId === appeal.finder_id) {
    roles.push(appeal.claim_type === "P2P" ? "Người đăng bài (người nhặt được)" : "Người đăng bài");
  }
  return roles;
}

export function availableRecipients(data) {
  const waiting = new Set(data.inquiries.filter(i => !i.responded_at && !i.closed_at).map(i => i.recipient_id));
  return data.current.participants.filter(person => !waiting.has(person.id));
}
