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
