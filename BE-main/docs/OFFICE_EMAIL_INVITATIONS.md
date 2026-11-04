# Email lịch hẹn văn phòng

Phần này giữ local để review, không tự commit/push. Không đổi tài khoản SMTP, cấu hình mail, OTP hoặc đăng nhập hiện có.

## Cài đặt

1. Review rồi chạy `db/15_office_email_invitation.sql` trên DB đã có `09_office_claim_and_appointment.sql`. Script tương thích với các cột của bản thử nghiệm 12 giờ trước đây. Lịch cũ giữ `LEGACY`, không tự gửi email hay tự từ chối hồi tố.
2. Đặt biến môi trường `OFFICE_FRONTEND_BASE_URL` thành URL frontend mà người nhận truy cập được, ví dụ `https://sagasu.example.com`. Mặc định localhost:5173 chỉ dành cho thử trên cùng máy.
3. Khởi động lại backend. Tác vụ hết hạn chạy mỗi 60 giây (cấu hình `office.appointments.expiry-check-ms`). Frontend cần hỗ trợ SPA fallback cho `/van-phong/lich-hen`.

## Luồng

- Sau commit duyệt, gửi email HTML kèm bản chữ thuần qua `JavaMailSender` hiện có. Hai nút mở trang riêng `/van-phong/lich-hen` với mã trong URL fragment; mã không xuất hiện trong URL request API hay referrer.
- Mở thư/trang chỉ xem thông tin lịch. Người nhận chọn lựa chọn rồi bấm xác nhận; không đăng nhập Sagasu. Ai có liên kết có thể phản hồi, vì vậy không chuyển tiếp thư.
- Hai API POST công khai: `/api/office-invitations/preview` và `/respond`. Cả hai bắt buộc mã ngẫu nhiên 256 bit; DB chỉ giữ SHA-256. Phản hồi công khai không chứa tên/email người nhận, bằng chứng hoặc ghi chú nội bộ. Không mở quyền cho API hồ sơ khác.
- Gửi thành công (SMTP chấp nhận) mới bắt đầu 12 giờ. Đây không phải xác nhận email đã vào inbox. Gửi lỗi không phát hành mã hay bắt đầu đồng hồ.
- Gửi lại thành công thay mã, giữ nguyên hạn cũ; gửi lại lỗi giữ mã trước đó. Dùng thư mới nhất. Sau khi đã đồng ý không gửi lại lời mời.
- Đồng ý giữ hồ sơ APPROVED và đồ RESERVED. Từ chối hoặc quá hạn chuyển hồ sơ REJECTED, hủy lịch và mở lại đồ đang RESERVED; không tự duyệt người tiếp theo.
- Thao tác hết hạn, phản hồi và gửi lại khóa theo thứ tự post → claim → appointment. Phản hồi ở hoặc sau deadline không được đồng ý, kể cả scheduler chưa chạy. Mã đã xử lý chỉ trả kết quả đã ghi, không đổi lại lựa chọn.
- Lịch phải sau ít nhất 12 giờ và 1 phút; lỗi gửi thư được báo riêng dù quyết định duyệt đã lưu. Nếu gửi lần đầu quá trễ so với giờ hẹn, cần văn phòng bố trí lại lịch. Chưa bổ sung UI đổi lịch hoặc xử lý người đã đồng ý nhưng không đến.
- Nếu server tắt, scheduler xử lý các lịch đã quá hạn sau khi chạy lại. Tối đa 100 lịch/lượt.
- Tác vụ hết hạn dùng người tạo lịch làm actor liên kết (schema bắt buộc), action `INVITE_EXPIRE` hiển thị “Hệ thống”.
- Chưa triển khai outbox: nếu SMTP đã chấp nhận nhưng tiến trình chết trước khi DB commit, liên kết gửi ra có thể chưa hoạt động; văn phòng gửi lại để khôi phục. Không tự gửi email thật khi khởi động.

## Kiểm tra

`mvn -Dtest=OfficeEmailInvitationTest,AppointmentHtmlEmailTest test`: token, quyền theo mã, HTML escaping, preview không ghi dữ liệu, xác nhận một lần, từ chối/quá hạn, gửi lỗi, gửi lại và giữ hạn.

Frontend: build, lint phần office và kiểm tra trình duyệt bằng API giả lập (không đăng nhập, mở link không ghi trạng thái, đồng ý/từ chối, hết hạn, liên kết sai, mobile). Migration thử trên DB schema sao chép riêng, không áp dụng tự động lên DB đang dùng.

Điểm tích hợp ngoài feature: một route trong `App.jsx` và hai POST permitAll trong `SecurityConfig.java`. Không sửa `LoginForm.jsx`, OTP hoặc luồng P2P.
