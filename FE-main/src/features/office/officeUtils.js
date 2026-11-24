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

export const closedAfterReturn = claim => claim.status === "CLOSED" && claim.closeReason === "ITEM_RETURNED";

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

export const pickupScheduleHint = "Đặt lịch từ thứ Hai đến thứ Năm, trong ca 08:00–12:00 hoặc 13:00–17:00 (giờ Việt Nam).";

export function pickupScheduleError(value) {
  if (!value) return "";
  const [day, time] = value.split("T");
  const weekday = new Date(day).getUTCDay();
  const inWorkingHours = (time >= "08:00" && time < "12:00") || (time >= "13:00" && time < "17:00");
  return weekday >= 1 && weekday <= 4 && inWorkingHours ? "" : pickupScheduleHint;
}

export function validateRequiredText(form) {
  const textInputs = 'input[required]:enabled:is(:not([type]), [type="text"]), textarea[required]:enabled';
  const blank = Array.from(form.querySelectorAll(textInputs)).find(field => !field.value.trim());
  if (!blank) return true;

  const clearError = () => {
    blank.setCustomValidity("");
    blank.removeEventListener("input", clearError);
    blank.removeEventListener("blur", clearError);
  };
  blank.setCustomValidity("Nhập nội dung cho trường này.");
  blank.addEventListener("input", clearError);
  blank.addEventListener("blur", clearError);
  blank.reportValidity();
  return false;
}

export const progressLabels = {
  APPEAL_WAIT: "Trong thời hạn gửi tranh chấp",
  APPEAL_REVIEW: "Đang xử lý tranh chấp",
  APPEAL_REASSESS: "Cần xét duyệt lại",
  SCHEDULE_REQUIRED: "Chờ lập lịch hẹn",
  APPOINTMENT_REVIEW: "Lịch hẹn đã qua — cần hẹn lại",
  REVIEW: "Chờ xét duyệt", NEEDS_INFO: "Chờ bổ sung thông tin", EMAIL_UNSENT: "Cần gửi lịch hẹn",
  AWAITING_REPLY: "Chờ đồng ý lịch hẹn", REPLY_EXPIRED: "Hết hạn phản hồi lịch", COUNTER_VERIFICATION: "Chờ đến xác minh", VERIFICATION_FAILED: "Không đạt xác minh",
  READY_TO_HANDOVER: "Sẵn sàng trao trả", OFFICE_COMPLETION: "Cần xác nhận hoàn tất", COMPLETED: "Đã trao trả",
  DISPUTED: "Có phản ánh cần xử lý", REJECTED: "Đã từ chối", CANCELLED: "Đã hủy", CLOSED: "Đã kết thúc",
};
