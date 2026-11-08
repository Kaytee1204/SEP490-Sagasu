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

export const progressLabels = {
  REVIEW: "Chờ xét duyệt", NEEDS_INFO: "Chờ bổ sung thông tin", EMAIL_UNSENT: "Cần gửi lịch hẹn",
  AWAITING_REPLY: "Chờ đồng ý lịch hẹn", REPLY_EXPIRED: "Hết hạn phản hồi lịch", COUNTER_VERIFICATION: "Chờ đến xác minh", VERIFICATION_FAILED: "Cần đối chiếu thêm",
  READY_TO_HANDOVER: "Sẵn sàng trao trả", OFFICE_COMPLETION: "Cần xác nhận hoàn tất", COMPLETED: "Đã trao trả",
  DISPUTED: "Có phản ánh cần xử lý", REJECTED: "Đã từ chối", CANCELLED: "Đã hủy", CLOSED: "Đã kết thúc",
};
