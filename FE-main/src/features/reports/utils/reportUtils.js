export const formatReportDate = value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});

export const reportsPath = '/bao-cao-cua-toi';
export const reportDetailPath = id => `/bao-cao/${id}`;

export function reportSubject(report) {
  const label = report.report_type === 'USER' ? 'Tài khoản liên quan' : 'Bài đăng liên quan';
  const value = report.report_subject || (report.report_subject_id ? `#${report.report_subject_id}` : '');
  return { label, value };
}
