export const labels = {
  OPEN: "Đang tìm chủ",
  RESERVED: "Đã có yêu cầu được duyệt",
  RETURNED: "Đã trao trả",
  CLOSED: "Đã đóng",
  COMPLETED: "Hoàn tất trao trả",
  VISIBLE: "Đang hiển thị",
  HIDDEN: "Đã ẩn",
  PENDING: "Chờ xử lý",
  NEEDS_INFO: "Chờ bổ sung",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã hủy",
  IN_STORAGE: "Đang lưu kho",
  FOUND: "Nhặt được",
  LOST: "Bị mất",
  P2P: "Người nhặt giữ",
  OFFICE: "Văn phòng",
  COUNTER: "Tại quầy",
  ONLINE: "Trực tuyến",
};
export const date = (value) =>
  value
    ? new Date(value).toLocaleString("vi-VN", {
        timeZone: "Asia/Bangkok",
        dateStyle: "medium",
        timeStyle: "short",
      })
    : "—";

export function claimActivityReason(activity, hideVerificationNote = false) {
  const { action, reason } = activity;
  if (action === "AUTO_CLOSE" && reason === "ITEM_RETURNED") {
    return "Đồ vật đã được trao trả cho người nhận.";
  }
  if (hideVerificationNote && action === "OFFICE_CANCEL" && reason?.startsWith("Không đạt xác minh người nhận tại quầy:")) {
    return "Không đạt xác minh người nhận tại quầy.";
  }
  return reason;
}

export function earliestAppointment() {
  const time = new Date(Date.now() + (12 * 60 + 2) * 60_000);
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(time).replace(' ', 'T');
}

export const progressLabels = {
  APPOINTMENT_REVIEW: "Lịch hẹn đã qua — cần hẹn lại",
  REVIEW: "Chờ xét duyệt", NEEDS_INFO: "Chờ bổ sung thông tin", EMAIL_UNSENT: "Cần gửi lịch hẹn",
  AWAITING_REPLY: "Chờ đồng ý lịch hẹn", REPLY_EXPIRED: "Hết hạn phản hồi lịch", COUNTER_VERIFICATION: "Chờ đến xác minh", VERIFICATION_FAILED: "Không đạt xác minh",
  READY_TO_HANDOVER: "Sẵn sàng trao trả", OFFICE_COMPLETION: "Cần xác nhận hoàn tất", COMPLETED: "Đã trao trả",
  DISPUTED: "Có phản ánh cần xử lý", REJECTED: "Đã từ chối", CANCELLED: "Đã hủy", CLOSED: "Đã kết thúc",
};
