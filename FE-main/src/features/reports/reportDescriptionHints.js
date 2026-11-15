// Review integration: help reporters identify what needs checking without assuming guilt.
const hints = {
  POST_FALSE_INFO: 'Thông tin nào trên bài đăng chưa đúng? Bạn có thông tin gì để đối chiếu?',
  POST_SCAM: 'Điều gì khiến bạn nghi ngờ lừa đảo? Nêu nội dung trao đổi hoặc hành vi cụ thể.',
  USER_IMPERSONATION: 'Tài khoản đang giả mạo ai? Nêu dấu hiệu giúp quản lý đối chiếu.',
  USER_HARASSMENT: 'Hành vi nào đã xảy ra, vào lúc nào và ở đâu? Có thể chỉ rõ tin nhắn liên quan.',
  USER_SCAM: 'Người này đã yêu cầu hoặc thực hiện việc gì khiến bạn nghi ngờ? Nêu thời điểm nếu nhớ.',
  USER_PRIVACY: 'Thông tin cá nhân nào bị tiết lộ và ở đâu? Không cần chép lại toàn bộ thông tin nhạy cảm.',
  CLAIM_FALSE_OWNERSHIP: 'Chi tiết nào khiến bạn nghi ngờ người yêu cầu không phải chủ đồ?',
  CLAIM_FALSE_EVIDENCE: 'Bằng chứng nào có dấu hiệu giả hoặc không liên quan? Giải thích điểm cần kiểm tra.',
  CLAIM_CONFLICTING_INFO: 'Những thông tin nào đang mâu thuẫn? Chỉ rõ các chi tiết cần đối chiếu.',
  HANDOVER_NOT_RECEIVED: 'Việc giao nhận thực tế diễn ra thế nào? Bạn chưa nhận được đồ ở bước nào?',
  HANDOVER_WRONG_ITEM: 'Bạn nhận được món gì và khác món cần nhận ở điểm nào?',
  HANDOVER_MISSING_ITEMS: 'Thiếu món đồ hoặc phụ kiện nào so với thông tin bàn giao?',
  HANDOVER_CONDITION: 'Tình trạng thực tế khác mô tả ở điểm nào? Bạn phát hiện khi nào?',
};

export function reportDescriptionHint(code) {
  return hints[code] || 'Nêu ngắn gọn sự việc và chi tiết bạn muốn quản lý kiểm tra.';
}
