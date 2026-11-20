export function handoverVerificationHint(answers = []) {
  const template = answers.find(a => a.templateCode !== 'COMMON')?.templateCode;
  if (['PHONE', 'LAPTOP', 'ELECTRONICS'].includes(template)) return 'Trước khi giao, nhờ người nhận tự mở khóa thiết bị hoặc đối chiếu thông tin sở hữu với máy thực tế. Không yêu cầu cung cấp mật khẩu, PIN hay OTP.';
  if (['WALLET', 'BAG'].includes(template)) return 'Trước khi giao, đối chiếu mô tả đồ bên trong và dấu riêng đã khai. Để người nhận tự mô tả, không gợi ý đáp án.';
  if (template === 'ID_CARD') return 'Trước khi giao, đối chiếu thông tin và ảnh trên giấy tờ với người đến nhận; không chỉ dựa vào việc họ biết tên trên thẻ.';
  if (template === 'KEYS') return 'Trước khi giao, đối chiếu chùm chìa và dấu riêng; khi có điều kiện, nhờ người nhận chứng minh sử dụng được với khóa hoặc phương tiện tương ứng.';
  return 'Trước khi giao, đối chiếu dấu riêng hoặc bằng chứng sở hữu với món đồ thực tế. Nếu chưa đủ căn cứ, tiếp tục xác minh trước khi trao.';
}
