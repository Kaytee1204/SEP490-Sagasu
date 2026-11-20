export const reportTargetType = report => report.reportType || (report.kind === 'HANDOVER_ISSUE' ? 'HANDOVER_ISSUE' : report.targetType);
export const formatReportDate = value=>new Date(value).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
