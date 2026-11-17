# Tóm tắt mô tả món đồ bằng Gemini

## Bật tính năng

Tính năng đã có giao diện và hoạt động ngay bằng bản **Trích theo mẫu**. Backend chỉ gọi Gemini khi cả hai biến môi trường được cấu hình:

| Biến môi trường của backend | Giá trị |
| --- | --- |
| `CLAIM_SUMMARY_ENABLED` | `true` |
| `GEMINI_API_KEY` | Key tạo trong Google AI Studio; giữ trong cấu hình chạy riêng của backend |
| `GEMINI_MODEL` | Mặc định `gemini-3.5-flash-lite`; có thể thay bằng model hỗ trợ structured output mà tài khoản được dùng |
| `GEMINI_TIMEOUT_MS` | Mặc định `20000`; giới hạn 1–30 giây |

Sau khi đặt biến môi trường, khởi động lại backend. Không đặt key trong biến `VITE_*`, frontend, tài liệu, commit hoặc chat. Không cần đổi database. Khi chưa có key, không có yêu cầu nào được gửi tới Google.

Mở **Yêu cầu nhận đồ → Người khác yêu cầu → mở nhóm món đồ → Tóm tắt yêu cầu**. Chỉ khi bấm nút mới tải bản tóm tắt. Kết quả được tách thành từng người: mỗi hồ sơ một khung riêng có tên người gửi và một đoạn tóm tắt dựa trên câu trả lời gốc trong hồ sơ chi tiết, gồm ngày/nơi mất, mô tả món đồ, các câu tùy chọn, phần chưa rõ/chưa nêu, căn cứ nhận đồ và số ảnh. Tên người gửi được gắn vào câu tại frontend; bấm tên để tới hồ sơ gốc, hoặc bấm **Ẩn tóm tắt** để thu gọn. Nhãn **Bản tóm tắt** chỉ xuất hiện sau khi nhận và kiểm tra thành công kết quả Gemini; thiếu cấu hình, hết hạn mức, timeout, kết quả không hợp lệ hoặc không có dữ liệu phù hợp đều dùng bản trích theo mẫu.

## Phạm vi và dữ liệu

`POST /api/posts/{postId}/claims/summary?page=1&size=5&status=ALL`

- Chỉ chủ bài FOUND/P2P, tài khoản còn được phép sử dụng, được gọi API. Kiểm tra quyền lại ở mọi lần gọi, kể cả khi có cache.
- Cùng phân trang, bộ lọc và thứ tự với `/claims/comparison`; tối đa 10 hồ sơ/lần. Tóm tắt chỉ áp dụng các hồ sơ trên trang hiện tại, không phải toàn bộ các trang.
- Phần Gemini chỉ xử lý ba tiêu chí cố định: **Loại đồ / Hãng**, **Màu sắc / Chất liệu**, **Phụ kiện đi kèm**. Không tính điểm sở hữu, không tự chấp thuận/từ chối và không thay đổi tỷ lệ hoàn thành câu hỏi.
- Backend chỉ gửi nhãn tạm `R1`, `R2`…, mã ý `F0`…, khóa tiêu chí và các giá trị trong từ điển cố định. Không gửi ID thật, tên người yêu cầu, nhãn câu hỏi, nội dung bài, ngày/địa điểm mất, căn cứ nhận đồ, ảnh, URL, ghi chú bằng chứng hoặc câu trả lời nguyên văn.
- COMMON, ID_CARD, KEYS và các câu hỏi về giấy tờ trong ví, serial, hình nền, đồ bên trong túi, tên/chữ khắc hoặc dấu riêng được loại khỏi đường gửi AI. Chỉ một số câu mô tả vật trong các bộ câu hỏi phiên bản 1 có chính sách cho phép. Phiên bản/câu hỏi mới mặc định không được gửi cho tới khi được rà soát.
- Với câu được phép, gặp dấu hiệu thông tin riêng (CCCD, CVV, PIN, OTP, serial, email, mật khẩu, địa chỉ…) thì bỏ câu đó. Sau đó chỉ trích **giá trị từ điển**, không xóa vài mẫu nhạy cảm rồi gửi phần văn bản còn lại. Cách này chủ động bỏ qua các hãng/mô tả chưa biết và một số thông tin hữu ích; không phải công cụ ẩn danh toàn bộ văn bản.
- Gemini chỉ chọn tối đa ba mã ý trong từng tiêu chí của đúng hồ sơ. Backend từ chối mã ngoài nguồn, trộn hồ sơ, tiêu chí sai, lặp ý, nội dung tự sinh hoặc kết quả thiếu. Văn bản hiển thị do backend lấy từ các ý gốc đã chuẩn hóa. Bấm tên hồ sơ trong tin nhắn tóm tắt để đối chiếu nguyên văn trong danh sách đầy đủ bên dưới.


- Frontend tạo đoạn tóm tắt đầy đủ từ cùng các câu trả lời đã được phân quyền trên trang chi tiết, bao gồm câu tùy chọn. Ngày/nơi mất, căn cứ riêng và câu trả lời nguyên văn được xử lý tại ứng dụng; không thêm vào request Gemini. Trường nhạy cảm như số giấy tờ, serial và mật khẩu không được lặp lại trong đoạn tóm tắt. Ảnh chỉ được đếm, không được phân tích. Chỉ có nút tóm tắt ở danh sách yêu cầu; trang chi tiết giữ nguyên phần câu trả lời gốc, không có nút tóm tắt riêng. Dữ liệu từ API đối chiếu đã bao gồm cùng các câu trả lời của hồ sơ chi tiết, nên không cần mở từng hồ sơ hoặc gọi thêm một API chi tiết cho mỗi người.

## Giới hạn và vận hành

- Cache trong bộ nhớ: tối đa 128 nhóm kết quả; 30 phút cho Gemini, 60 giây cho bản trích. Thay đổi hồ sơ/ý mô tả làm đổi cache. Khởi động lại backend sẽ xóa cache.
- Gộp các yêu cầu đồng thời có cùng nội dung. Mỗi tiến trình tối đa 2 cuộc gọi Gemini cùng lúc, 3 lần/phút và 100 lần/ngày UTC; không tự thử lại khi lỗi. Đây là giới hạn của ứng dụng, không bảo đảm tương ứng hạn mức miễn phí Google cấp cho tài khoản.
- Không ghi request/response của nhà cung cấp, key hoặc chi tiết lỗi nhà cung cấp vào log/trả về frontend. Không bật logging HTTP body/header cho đường gọi Gemini trong môi trường thật.
- Đã kiểm thử trực tiếp với Gemini 3.5 Flash-Lite ngày 17/11/2026: yêu cầu mẫu trả JSON hợp lệ và giao diện tóm tắt nhóm ba hồ sơ nhận kết quả Gemini thành công. Đồng thời có kiểm thử hợp đồng HTTP giả lập, chuẩn hóa dữ liệu, kiểm tra nguồn, quyền trước cache, lỗi/hạn mức, cache và gộp yêu cầu.

Tham khảo chính thức: [API generateContent](https://ai.google.dev/api/generate-content), [structured output](https://ai.google.dev/gemini-api/docs/structured-output), [giá và gói miễn phí](https://ai.google.dev/gemini-api/docs/pricing), [điều khoản dữ liệu dịch vụ miễn phí](https://aistudio.google.com/docs/terms#data-use-unpaid). Cần kiểm tra model và hạn mức thực tế trong AI Studio khi cấu hình key.
