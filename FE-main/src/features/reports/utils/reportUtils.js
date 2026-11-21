export const formatReportDate = value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});

export const reportsPath = '/bao-cao-cua-toi';
export const reportDetailPath = id => `/bao-cao/${id}`;

export function reportSubject(report) {
  const label = report.report_type === 'USER' ? 'Tài khoản liên quan' : 'Bài đăng liên quan';
  const value = report.report_subject || (report.report_subject_id ? `#${report.report_subject_id}` : '');
  return { label, value };
}

export const appealSteps = {
  WAIT_APPEALS: 'Chờ xử lý tranh chấp của bài đăng',
  VERIFY_CUSTODY: 'Chờ xác minh điều kiện bàn giao',
  READY_TO_REOPEN: 'Cần mở lại xét duyệt người nhận',
};

export function appealInstruction(step) {
  switch (step) {
    case 'WAIT_APPEALS': return 'Quản lý xử lý các tranh chấp còn lại của bài đăng. Món đồ tiếp tục được giữ để đối chiếu.';
    case 'VERIFY_CUSTODY': return 'Quản lý kiểm tra người đang giữ đồ và các điều kiện còn vướng trước khi mở lại xét duyệt.';
    case 'READY_TO_REOPEN': return 'Người nhặt cần xác nhận vẫn giữ món đồ, sau đó mở lại xét duyệt và quyết định người nhận.';
    default: return '';
  }
}
