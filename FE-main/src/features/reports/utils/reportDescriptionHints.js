// Review integration: help reporters identify what needs checking without assuming guilt.
const hints = {
  POST_FALSE_INFO: 'Thông tin nào trên bài đăng chưa đúng? Bạn có thông tin gì để đối chiếu?',
  POST_SCAM: 'Điều gì khiến bạn nghi ngờ lừa đảo? Nêu nội dung trao đổi hoặc hành vi cụ thể.',
  POST_PRIVACY: 'Chỉ rõ ảnh hoặc đoạn nội dung cần kiểm tra. Tránh chép lại số CCCD, số thẻ hoặc thông tin nhạy cảm.',
  USER_SAFETY_THREAT: 'Hành vi đe dọa xảy ra khi nào, ở đâu? Chỉ rõ căn cứ cần đối chiếu.',
  USER_IMPERSONATION: 'Tài khoản đang giả mạo ai? Nêu dấu hiệu giúp quản lý đối chiếu.',
  USER_HARASSMENT: 'Hành vi nào đã xảy ra, vào lúc nào và ở đâu? Có thể chỉ rõ tin nhắn liên quan.',
  USER_SCAM: 'Người này đã yêu cầu hoặc thực hiện việc gì khiến bạn nghi ngờ? Nêu thời điểm nếu nhớ.',
  USER_PRIVACY: 'Thông tin cá nhân nào bị tiết lộ và ở đâu? Không cần chép lại toàn bộ thông tin nhạy cảm.',
  CLAIM_FALSE_OWNERSHIP: 'Chi tiết nào khiến bạn nghi ngờ người yêu cầu không phải chủ đồ?',
  CLAIM_FALSE_EVIDENCE: 'Bằng chứng nào có dấu hiệu bị làm giả? Chỉ rõ điểm cần đối chiếu.',
  HANDOVER_APPOINTMENT_ISSUE: 'Nêu lịch hẹn, sự việc và ai đang giữ món đồ.',
  HANDOVER_WRONG_RECIPIENT: 'Nêu căn cứ cho thấy đồ đã đến tay người khác và ai đang giữ đồ nếu bạn biết.',
  HANDOVER_CONFIRMATION_ERROR: 'Bước nào được ghi nhận hoặc xác nhận nhầm? Nêu tình trạng thực tế của món đồ.',
  CLAIM_CONFLICTING_INFO: 'Những thông tin nào đang mâu thuẫn? Chỉ rõ các chi tiết cần đối chiếu.',
  P2P_HANDOVER_NO_CONTACT: 'Hai bên hẹn giao ở đâu, lúc nào? Bạn đã liên hệ lại bằng cách nào?',
  P2P_HANDOVER_NOT_RETURNED: 'Ai đang giữ đồ? Hai bên đã thống nhất gì và người giữ phản hồi thế nào?',
  P2P_HANDOVER_PAYMENT: 'Ai đã yêu cầu khoản tiền nào, vào lúc nào? Bạn có trao đổi gì để đối chiếu?',
  P2P_HANDOVER_THREAT: 'Điều gì xảy ra, vào lúc nào và bằng cách nào? Bạn có bằng chứng nào để đối chiếu?',
  HANDOVER_NOT_RECEIVED: 'Việc giao nhận thực tế diễn ra thế nào? Bạn chưa nhận được đồ ở bước nào?',
  HANDOVER_WRONG_ITEM: 'Bạn nhận được món gì và khác món cần nhận ở điểm nào?',
  HANDOVER_MISSING_ITEMS: 'Thiếu món đồ hoặc phụ kiện nào so với thông tin bàn giao?',
  HANDOVER_CONDITION: 'Tình trạng thực tế khác mô tả ở điểm nào? Bạn phát hiện khi nào?',
  APPEAL_NEW_EVIDENCE: 'Bạn có căn cứ bổ sung gì cho thấy món đồ là của bạn?',
  APPEAL_WRONG_RECIPIENT: 'Nêu điểm đặc trưng của món đồ hoặc tài liệu giúp đối chiếu người nhận.',
};

export function reportDescriptionHint(code) {
  return hints[code] || 'Nêu ngắn gọn sự việc và chi tiết bạn muốn quản lý kiểm tra.';
}
