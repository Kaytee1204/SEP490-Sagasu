# Bộ câu hỏi claim và category Sagasu — bản duyệt 2

Ngày khảo sát và cập nhật: **03/11/2026**.

**Đã chốt theo yêu cầu:** dùng câu hỏi chung **C1, C2, C3, C4**; giữ toàn bộ câu hỏi riêng ở mục 5 của bản trước; thêm các category còn thiếu, ngoại trừ **Hokie P** và **Default**.

**Trạng thái hiện tại:** các phần còn lại đã được người dùng duyệt. Đã triển khai C1–C4, 16 bộ câu hỏi theo category và phần đọc câu trả lời trong hồ sơ claim; migration 10 tạo ba bảng phục vụ chức năng này. Ngày triển khai: **04/11/2026**. Chỉ kiểm tra build, không viết hoặc chạy test theo yêu cầu.

Nguồn: [Pixit — Virginia Tech SECL, Browse Items](https://campuslife.app.pixithq.com/public/#/squires/items).

## 1. Phạm vi và cách khảo sát

- Đã mở lần lượt **15/15 category** trong bộ lọc của Pixit. Tổng số đồ hiển thị theo các category tại thời điểm khảo sát là **256**.
- Với **13 category có đồ**, mở **một bài mẫu/category** và bấm nút mở form claim của bài đó.
- Hai category **Default** và **Water Bottle** không có đồ. Với mỗi category này, mở form claim chung rồi chọn đúng category để xem câu hỏi.
- Chỉ xem cấu trúc form; **không nhập thông tin cá nhân, không bấm gửi claim và không tạo yêu cầu nhận đồ thật**.
- Phần câu hỏi Pixit bên dưới được **diễn giải ngắn bằng tiếng Việt**. Phần đề xuất Sagasu là cách điều chỉnh cho nghiệp vụ và category hiện có, không phải bản sao nguyên văn của Pixit.
- Chỉ khảo sát một mẫu cho mỗi category, không khẳng định đã kiểm tra mọi món đồ hoặc mọi cấu hình của toàn bộ nền tảng Pixit.
- Lượt khảo sát đầu đọc 10 category từ PostgreSQL Docker. Trong bản duyệt 2, đã thêm 6 category theo yêu cầu; không đổi tên/ID các category cũ và không chuyển category của bài đăng hiện có.
- Lượt khảo sát ban đầu chỉ cập nhật dữ liệu danh mục và tài liệu. Sau khi người dùng duyệt, phần triển khai đã bổ sung form, lưu câu trả lời và ba bảng ở mục 9; giữ nguyên luồng xét duyệt claim và không làm luồng Approver.

## 2. Những điểm quan sát được trên Pixit

### 2.1. Tám trường chung xuất hiện trong các form đã xem

| STT | Nội dung trường/câu hỏi, diễn giải tiếng Việt | Cách nhập quan sát được |
| --- | --- | --- |
| 1 | Số điện thoại để liên hệ là gì? | Ô văn bản |
| 2 | Bạn mất loại đồ gì? | Ô văn bản |
| 3 | Bạn mất đồ vào ngày nào? | Ô văn bản; nhãn gợi ý định dạng tháng/ngày/năm |
| 4 | Đồ có thể bị mất ở đâu? | Ô văn bản |
| 5 | Những đặc điểm nào giúp xác minh bạn là chủ đồ, chẳng hạn dấu khắc hoặc dấu hiệu riêng? | Ô mô tả nhiều dòng |
| 6 | Email liên hệ của bạn | Ô email |
| 7 | Nhập lại email để xác nhận | Ô email |
| 8 | Họ tên người gửi claim | Ô văn bản |

Các trường này đều có dấu `*` trên giao diện. Đây là ghi nhận về nhãn giao diện; chưa xác minh quy tắc bắt buộc ở phía server vì không gửi form. Các ô câu hỏi bổ sung hầu hết là ô văn bản, kể cả câu hỏi có/không. Trường ngày sự kiện của Water Bottle là ngoại lệ có ô chọn ngày.

Form claim chung có thêm bộ chọn category. Khi claim một món cụ thể, category được xác định theo món đó. Trong các form đã khảo sát, không thấy trường tải ảnh bằng chứng; khả năng đính kèm ảnh đang có ở Sagasu là phần riêng của Sagasu.

### 2.2. Câu hỏi bổ sung của từng category Pixit

Mỗi dòng dưới đây là **ngoài tám trường chung** ở mục 2.1. Số lượng đồ chỉ phản ánh thời điểm khảo sát và có thể thay đổi.

| Category Pixit | ID | Số đồ | Câu hỏi bổ sung quan sát được | Form đã mở |
| --- | ---: | ---: | --- | --- |
| Bags & Backpacks | 81 | 14 | Thương hiệu gì?<br>Màu gì?<br>Trong túi/balo có những gì? | [Bài mẫu 91258](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91258) |
| Children Items | 88 | 1 | Thương hiệu gì?<br>Màu gì? | [Bài mẫu 90853](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/90853) |
| Clothing | 79 | 23 | Thương hiệu gì?<br>Màu gì?<br>Cỡ của đồ là bao nhiêu? | [Bài mẫu 91315](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91315) |
| Computers & Electronics | 87 | 70 | Hãng và màu của thiết bị?<br>Có túi đi kèm không, bên trong túi có gì?<br>Có vỏ/bao bảo vệ không, loại hoặc hãng nào?<br>Mã mở khóa thiết bị là gì?<br>Số serial của thiết bị? | [Bài mẫu 91314](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91314) |
| Default | 295 | 0 | Không thấy câu hỏi riêng sau khi chọn Default; còn tám trường chung. | [Form chung — chọn Default](https://campuslife.app.pixithq.com/public/#/squires/new-claim) |
| Eyewear | 86 | 13 | Kính có độ hay không có độ?<br>Thương hiệu gì?<br>Màu gì?<br>Chất liệu gì? | [Bài mẫu 91265](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91265) |
| Footwear | 89 | 1 | Thương hiệu gì?<br>Màu gì?<br>Cỡ giày/dép?<br>Chất liệu gì? | [Bài mẫu 91045](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91045) |
| Hokie P | 93 | 5 | Họ tên ghi trên thẻ?<br>Năm chữ số cuối trên thẻ? | [Bài mẫu 91313](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91313) |
| IDs & Cards | 80 | 33 | Tên ghi trên giấy tờ/thẻ?<br>Đơn vị phát hành?<br>Màu hoặc hình ở mặt trước thẻ? | [Bài mẫu 91317](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91317) |
| Keys | 83 | 28 | Có móc/vòng/dây đeo đi kèm không?<br>Móc/vòng/dây có hình dạng, thiết kế thế nào?<br>Có phải chìa dùng cho cơ sở của trường không?<br>Nếu là chìa xe: hãng xe gì?<br>Nếu là chìa nhà/văn phòng: hãng chìa/khóa gì? | [Bài mẫu 91293](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91293) |
| Miscellaneous | 85 | 23 | Thương hiệu gì?<br>Màu gì? | [Bài mẫu 91307](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91307) |
| Mobile Devices | 82 | 1 | Hãng và màu điện thoại?<br>Nhà mạng đang dùng?<br>Có ốp không, hãng/loại nào?<br>Mã mở khóa thiết bị?<br>Hình nền có nội dung gì?<br>Số serial thiết bị? | [Bài mẫu 90973](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/90973) |
| Wallets & Purses | 84 | 22 | Thương hiệu gì?<br>Màu gì?<br>Chất liệu gì?<br>Bên trong có những gì?<br>Tên ghi trên giấy tờ/thẻ ở trong ví? | [Bài mẫu 91312](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91312) |
| Watches & Jewelry | 90 | 22 | Thương hiệu gì?<br>Chất liệu gì?<br>Có đá hoặc hạt trang trí nào?<br>Còn đặc điểm nhận dạng nào khác? | [Bài mẫu 91273](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91273) |
| Water Bottle | 4967 | 0 | Ngày diễn ra sự kiện/lượt đặt chỗ?<br>Tên sự kiện/lượt đặt chỗ?<br>Mô tả đồ bị để quên?<br>Sự kiện/lượt đặt chỗ tổ chức ở đâu? | [Form chung — chọn Water Bottle](https://campuslife.app.pixithq.com/public/#/squires/new-claim) |

### 2.3. Các điểm cần điều chỉnh trước khi áp dụng

1. **Water Bottle lệch ngữ cảnh:** đã chọn đúng category này nhưng form hiển thị bốn câu hỏi về sự kiện/đặt chỗ. Chưa kết luận nguyên nhân cấu hình của Pixit. Không lấy bốn câu này làm bộ xác minh bình nước của Sagasu.
2. **Điện tử/điện thoại có hỏi mã mở khóa:** ghi nhận là nội dung có thật trên form nguồn, nhưng đề xuất **loại bỏ** khi áp dụng. Sagasu không nên yêu cầu mật khẩu, PIN, OTP hoặc mật khẩu tài khoản. Có thể để chủ đồ tự mở khóa trực tiếp khi bàn giao, không lưu mã.
3. **Hokie P là nhóm thẻ riêng của trang nguồn:** khi áp dụng cho Sagasu, chuyển ý tưởng đối chiếu tên/một phần mã thẻ sang nhánh thẻ sinh viên/thẻ nội bộ, không tạo category tên Hokie P.
4. **Thông tin liên hệ:** Pixit thu lại tên, email, xác nhận email và số điện thoại. Sagasu đã có tài khoản đăng nhập nên nên lấy tên/email từ tài khoản; không bắt người dùng nhập lại email hai lần trong mỗi claim.
5. **Câu hỏi có điều kiện:** Pixit hiển thị đồng thời các câu về chìa xe và chìa nhà/văn phòng. Sagasu nên hỏi loại chìa trước rồi chỉ hiện nhánh phù hợp.
6. **Một số tên category khá rộng:** danh sách Computers & Electronics có nhiều loại thiết bị; không đồng nhất mọi câu hỏi điện tử với mọi món đồ. Trong Sagasu cần phân biệt laptop, điện thoại và phụ kiện điện tử.

## 3. Category trong database sau khi bổ sung

Hiện có **16 category đang hoạt động**: 10 category cũ và 6 category mới. Các ID dưới đây là ID thực tế được PostgreSQL trả về sau khi thêm, không phải số thứ tự đề xuất.

| ID Sagasu | Tên trong DB | Trạng thái | Nhóm nguồn/cách dùng | Bộ câu hỏi |
| ---: | --- | --- | --- | --- |
| 1 | Điện thoại | Giữ nguyên | Mobile Devices; điện thoại tiếp tục chọn nhóm này. | 5.1 |
| 2 | Máy tính xách tay | Giữ nguyên | Laptop dùng bộ riêng; không đổi thành nhóm điện tử chung. | 5.2 |
| 3 | Ví tiền & Giấy tờ cá nhân | Giữ nguyên | Wallets & Purses; giữ cả nhánh giấy tờ để hỗ trợ các bài cũ. | 5.3 |
| 4 | Chìa khóa | Giữ nguyên | Keys. | 5.4 |
| 5 | Balo, Túi xách | Giữ nguyên | Bags & Backpacks. | 5.5 |
| 6 | Trang phục (Clothing & Apparel) | Giữ nguyên | Clothing; giữ bộ cũ có nhánh giày/dép cho các bài đã đăng. | 5.6 |
| 7 | Phụ kiện và Trang sức | Giữ nguyên | Watches & Jewelry; giữ bộ cũ có nhánh kính cho các bài đã đăng. | 5.7 |
| 8 | Văn phòng phẩm | Giữ nguyên | Nhóm riêng của Sagasu; câu hỏi sách/vở/bút đã đề xuất ở bản trước. | 5.8 |
| 9 | Đồ dùng cá nhân | Giữ nguyên | Giữ toàn bộ bộ câu hỏi cũ cho đồ cá nhân và các bài hiện có. | 5.9 |
| 10 | Đồ dùng khác | Giữ nguyên | Tương ứng Miscellaneous; không tạo category Default. | 5.10 |
| 67 | Đồ dùng trẻ em | **Đã thêm** | Children Items. | 5.11 |
| 68 | Máy tính & Thiết bị điện tử | **Đã thêm** | Computers & Electronics; dùng cho máy tính bảng, tai nghe và thiết bị điện tử khác; ưu tiên ID 1 cho điện thoại, ID 2 cho laptop. | 5.12 |
| 69 | Kính mắt | **Đã thêm** | Eyewear. | 5.13 |
| 70 | Giày dép | **Đã thêm** | Footwear. | 5.14 |
| 71 | Giấy tờ & Thẻ | **Đã thêm** | IDs & Cards; dùng cho giấy tờ/thẻ rời khi đăng mới. | 5.15 |
| 72 | Bình nước | **Đã thêm** | Water Bottle; dùng câu hỏi nhận dạng bình nước của Sagasu, không lấy câu hỏi sự kiện lệch ngữ cảnh ở nguồn. | 5.16 |

**Không thêm Hokie P và Default.** Hai tên này chỉ còn xuất hiện ở phần khảo sát nguồn/lịch sử tham khảo, không phải category đích của Sagasu.

### 3.1. Cách dùng khi category cũ và mới có phần giao nhau

- Giấy tờ/thẻ rời mới đăng: ưu tiên **71 — Giấy tờ & Thẻ**. Ví hoặc ví có giấy tờ bên trong: **3 — Ví tiền & Giấy tờ cá nhân**.
- Giày/dép mới đăng: ưu tiên **70 — Giày dép**. Quần áo: **6 — Trang phục**.
- Kính mới đăng: ưu tiên **69 — Kính mắt**. Đồng hồ, trang sức và phụ kiện khác: **7 — Phụ kiện và Trang sức**.
- Bình nước mới đăng: ưu tiên **72 — Bình nước**. Đồ trẻ em mới đăng: **67 — Đồ dùng trẻ em**.
- Điện thoại và laptop vẫn dùng **1** và **2**; tai nghe/thiết bị điện tử khác ưu tiên **68**. Bộ câu hỏi cũ của ID 9 vẫn được giữ để áp dụng cho bài cũ hoặc các đồ cá nhân phù hợp.
- Không tự động sửa category của bài đã có. Vì người dùng yêu cầu giữ tất cả câu hỏi ở mục 5 cũ, các nhánh cũ vẫn được giữ nguyên; nhóm mới có bộ riêng ở 5.11–5.16.

### 3.2. Phạm vi thay đổi đã thực hiện trong DB

- Thêm đúng 6 dòng mới vào bảng `category`, đều có `is_active = true`.
- Không sửa hoặc xóa 10 dòng cũ.
- Bước thêm category không đổi schema. Bước triển khai sau khi duyệt bổ sung ba bảng riêng qua migration 10, mô tả tại mục 9.
- Mẫu câu hỏi đã được lưu vào DB; không tự tạo câu trả lời hoặc thay đổi dữ liệu claim hiện có.

## 4. Bộ câu hỏi chung đề xuất cho Sagasu

**Chỉ dùng C1–C4**, áp dụng cho cả 16 category, sau đó bổ sung bộ câu hỏi riêng ở mục 5. C5 và C6 đã bỏ khỏi bộ câu hỏi claim.

| Mã | Câu hỏi/thông tin | Đề xuất nhập liệu | Mức bắt buộc |
| --- | --- | --- | --- |
| C1 | Bạn mất món đồ này vào ngày nào? Có nhớ khoảng thời gian không? | Chọn ngày, thời gian tùy chọn; hỗ trợ ngày gần đúng hoặc “Không nhớ rõ”. Đây là thời điểm mất đồ, **không phải lịch giao đồ**. | Bắt buộc trả lời; chấp nhận không nhớ rõ. |
| C2 | Bạn có thể đã làm mất hoặc để quên đồ ở đâu? | Khu vực và mô tả ngắn; có thể ghi không nhớ chính xác. | Bắt buộc trả lời. |
| C3 | Hãy mô tả ít nhất một dấu hiệu riêng hoặc căn cứ cho thấy đồ này là của bạn. | Mô tả nhiều dòng, ưu tiên chi tiết không có trong ảnh/nội dung công khai. | Bắt buộc; có thể tiếp tục dùng trường mô tả căn cứ của claim hiện tại. |
| C4 | Bạn có ảnh cũ, hóa đơn hoặc tài liệu nào để đối chiếu không? | Đính kèm ảnh theo khả năng gửi bằng chứng đã có ở Sagasu; có thể dùng ghi chú bằng chứng. | Không bắt buộc. |

Category lấy từ bài Found đang claim, không cho người claim đổi category của bài. Chỉ hỏi “loại đồ cụ thể” khi cần phân nhánh trong một category rộng.

## 5. Bộ câu hỏi riêng cho 16 category Sagasu

**Giữ nguyên toàn bộ 59 câu hỏi trong 10 bộ cũ ở 5.1–5.10.** Bổ sung 6 bộ ở 5.11–5.16 cho category mới; không thay thế hoặc lược bỏ câu hỏi của các bộ cũ.

**Cách đọc:** mọi nhóm dưới đây đều có thêm C1–C4 ở mục 4. “Bắt buộc” là đề xuất cho Sagasu, không phải khẳng định về kiểm tra server của Pixit. Với thông tin phụ như hãng, cỡ hoặc chất liệu, nên có lựa chọn “Không rõ/không áp dụng” thay vì buộc người dùng điền thông tin đoán.

### 5.1. ID 1 — Điện thoại

Tham khảo trực tiếp nhóm [Mobile Devices](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/90973). Các câu tách hãng/dòng máy, mô tả hư hỏng và cách dùng serial là điều chỉnh cho Sagasu.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Điện thoại của bạn thuộc hãng và dòng máy nào? | Luôn hiện. | Có. |
| Thân máy màu gì? Có dấu trầy, nứt hoặc đặc điểm nào dễ đối chiếu? | Luôn hiện. | Có; dấu hư hỏng có thể trả lời không có. |
| Có dùng ốp hoặc phụ kiện gắn trên máy không? Hãy mô tả màu/kiểu/hình trên ốp. | Luôn hỏi có/không; mô tả khi có. | Có/không bắt buộc; mô tả theo nhánh. |
| Hình nền hoặc một dấu hiệu ở màn hình khóa có gì đặc trưng? | Luôn hiện. | Không bắt buộc. |
| Máy đang dùng nhà mạng nào? | Luôn hiện. | Không bắt buộc. |
| Bạn có thể cung cấp một phần serial hoặc bằng chứng mua/sở hữu máy không? | Luôn hiện. | Không bắt buộc; không bắt người dùng biết serial mới được gửi claim. |

**Không áp dụng câu hỏi mã mở khóa của nguồn.** Không thu mật khẩu Apple/Google, PIN hay OTP. Việc tự mở khóa khi nhận đồ là bước đối chiếu trực tiếp sau này.

### 5.2. ID 2 — Máy tính xách tay

Tham khảo [Computers & Electronics](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91314), điều chỉnh riêng cho laptop; nhãn dán/hư hỏng là gợi ý nhận dạng bổ sung.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Laptop thuộc hãng, dòng máy nào? | Luôn hiện. | Có. |
| Vỏ máy màu gì, có nhãn dán hoặc dấu trầy/nứt nào? | Luôn hiện. | Có. |
| Máy có để trong túi/balo khi bị mất không? | Luôn hiện. | Có/không. |
| Túi đựng có đặc điểm gì và có những đồ gì bên trong? | Có túi/balo. | Có theo nhánh; có thể ghi không có đồ khác. |
| Có bao bảo vệ hoặc phụ kiện đi kèm như sạc, chuột, USB không? Hãy mô tả. | Luôn hiện. | Không bắt buộc. |
| Có phần serial, ảnh cũ hoặc chứng từ sở hữu nào để đối chiếu không? | Luôn hiện. | Không bắt buộc. |

Không yêu cầu mật khẩu đăng nhập laptop hoặc mật khẩu tài khoản.

### 5.3. ID 3 — Ví tiền & Giấy tờ cá nhân

Tham khảo [Wallets & Purses](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91312), [IDs & Cards](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91317) và [Hokie P](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91313). Đề xuất dùng hai nhánh trong category hiện tại.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Bạn đang nhận lại ví hay giấy tờ/thẻ rời? | Luôn hiện. | Có. |
| Ví màu gì, chất liệu và thương hiệu/kiểu dáng thế nào? | Nhánh ví. | Màu/chất liệu có; thương hiệu có thể không rõ. |
| Trong ví có những loại đồ, giấy tờ hoặc thẻ nào? | Nhánh ví. | Có. |
| Tên ghi trên giấy tờ/thẻ trong ví là gì? | Ví có giấy tờ/thẻ. | Có theo nhánh. |
| Đó là loại giấy tờ/thẻ gì, tên người đứng trên thẻ và đơn vị cấp là gì? | Nhánh giấy tờ/thẻ rời. | Có. |
| Mặt trước giấy tờ/thẻ có màu hoặc hình nhận dạng nào? | Nhánh giấy tờ/thẻ rời. | Không bắt buộc. |
| Có thể cung cấp vài ký tự cuối của mã thẻ để đối chiếu không? | Thẻ có mã và người claim biết. | Không bắt buộc; số ký tự cuối cần chốt theo loại thẻ. |

Không yêu cầu đủ số thẻ ngân hàng, CVV, PIN hoặc OTP. Với ảnh giấy tờ làm bằng chứng, hướng dẫn che thông tin không cần cho việc đối chiếu. Hỏi một phần mã thẻ là điều chỉnh từ ý tưởng của Hokie P, không sao chép định dạng mã riêng của trường nguồn.

### 5.4. ID 4 — Chìa khóa

Tham khảo [Keys](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91293); bổ sung câu về số lượng chìa và cách phân nhánh.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Đây là chìa xe, chìa nhà/văn phòng, chìa cơ sở trường hay loại khác? | Luôn hiện. | Có. |
| Chùm có khoảng bao nhiêu chìa? Có remote hoặc thẻ đi kèm không? | Luôn hiện. | Có. |
| Có móc, vòng, dây đeo hoặc thẻ tên đi kèm không? | Luôn hiện. | Có/không. |
| Móc/vòng/dây đeo có màu, hình hoặc chữ gì đặc trưng? | Có phụ kiện đi kèm. | Có theo nhánh. |
| Chìa dùng cho xe hãng nào? | Nhánh chìa xe. | Có theo nhánh. |
| Trên chìa hoặc khóa có tên hãng/chữ khắc nào không? | Nhánh nhà/văn phòng hoặc loại khác phù hợp. | Không bắt buộc. |

Không buộc cung cấp địa chỉ nhà hoặc chính xác phòng/cửa mà chìa mở được trong claim.

### 5.5. ID 5 — Balo, Túi xách

Tham khảo [Bags & Backpacks](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91258); bổ sung kiểu túi và đặc điểm ngăn/khóa kéo.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Đó là balo, túi xách, túi đeo chéo, túi laptop hay loại túi khác? | Luôn hiện. | Có. |
| Túi có thương hiệu/logo nào? | Luôn hiện. | Không bắt buộc nếu không biết. |
| Màu chính, họa tiết hoặc chất liệu túi như thế nào? | Luôn hiện. | Có. |
| Khi bị mất, bên trong túi có những đồ gì? | Luôn hiện. | Có; có thể ghi túi rỗng. |
| Có ngăn riêng, móc treo, khóa kéo, dấu rách hoặc chữ ghi nào đặc trưng? | Luôn hiện. | Không bắt buộc; có thể đưa vào C3. |

Nếu có giấy tờ trong túi, chỉ hỏi thông tin đủ để xác minh, không yêu cầu liệt kê toàn bộ mã giấy tờ/thẻ.

### 5.6. ID 6 — Trang phục (Clothing & Apparel)

Tham khảo [Clothing](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91315) và [Footwear](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91045). Giữ toàn bộ bộ câu hỏi cũ, gồm nhánh giày/dép, để hỗ trợ các bài hiện có. Giày/dép đăng mới ưu tiên category 70 và bộ 5.14.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Bạn mất áo, quần, áo khoác, mũ, giày/dép hay loại trang phục nào? | Luôn hiện. | Có. |
| Thương hiệu hoặc logo trên đồ là gì? | Luôn hiện. | Không bắt buộc nếu không rõ. |
| Đồ có màu và họa tiết như thế nào? | Luôn hiện. | Có. |
| Cỡ quần áo hoặc cỡ giày/dép là bao nhiêu? | Theo loại đã chọn. | Có; cho phép không nhớ rõ. |
| Chất liệu có đặc điểm gì? | Giày/dép hoặc người dùng muốn bổ sung. | Không bắt buộc. |
| Có tên viết trên nhãn, dấu sửa, vết rách hoặc vật trong túi áo/quần không? | Theo loại phù hợp. | Không bắt buộc; là gợi ý cho C3, được đề xuất thêm cho Sagasu. |

### 5.7. ID 7 — Phụ kiện và Trang sức

Tham khảo [Watches & Jewelry](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91273) và [Eyewear](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91265). Giữ toàn bộ câu hỏi cũ theo nhánh. Kính đăng mới ưu tiên category 69 và bộ 5.13; không bắt người nhận kính trả lời về đá trang sức.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Món đồ là kính, đồng hồ, nhẫn, vòng, dây chuyền hay phụ kiện khác? | Luôn hiện. | Có. |
| Hãng/logo hoặc kiểu dáng món đồ là gì? | Luôn hiện. | Có trả lời; cho phép không rõ hãng. |
| Đồ có màu và chất liệu gì? | Luôn hiện. | Có. |
| Đó là kính có độ, kính mát hay kính thời trang? | Nhánh kính. | Có theo nhánh; không yêu cầu chỉ số độ kính. |
| Có hộp hoặc phụ kiện đựng đi kèm không, đặc điểm thế nào? | Nhánh kính/đồng hồ có phụ kiện. | Không bắt buộc; câu bổ sung cho Sagasu. |
| Có đá/hạt trang trí nào, màu và cách bố trí thế nào? | Nhánh trang sức. | Có/không; mô tả nếu có. |
| Có chữ khắc, ký hiệu, dấu sửa hoặc dấu hiệu riêng ở mặt trong/mặt sau không? | Nhánh phù hợp. | Không bắt buộc; có thể dùng C3. |

Đồng hồ thông minh cũng không được hỏi mã mở khóa.

### 5.8. ID 8 — Văn phòng phẩm

**Không có category Pixit tương đương trực tiếp trong danh sách đã khảo sát.** Chỉ tham khảo cách hỏi hãng/màu của [Miscellaneous](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91307). Những câu riêng về sách/vở/bút dưới đây là đề xuất mới cho Sagasu.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Món đồ là sách, vở, bút, hộp bút, tài liệu hay loại khác? | Luôn hiện. | Có. |
| Tên sách/môn học hoặc thương hiệu món đồ là gì? | Theo loại đã chọn. | Có trả lời; cho phép không nhớ rõ. |
| Màu bìa/vỏ và họa tiết có đặc điểm gì? | Luôn hiện. | Có. |
| Có tên, lớp hoặc ký hiệu nào bạn đã viết lên đồ không? | Sách, vở, tài liệu hoặc đồ có ghi dấu. | Không bắt buộc. |
| Có ghi chú, trang đánh dấu hoặc dấu sử dụng nào riêng biệt không? | Sách/vở/tài liệu. | Không bắt buộc; gợi ý bổ sung cho C3. |
| Trong hộp/túi đựng có những gì? | Hộp bút/túi tài liệu. | Có theo nhánh; có thể ghi rỗng. |

### 5.9. ID 9 — Đồ dùng cá nhân

Tham khảo nhóm [Children Items](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/90853), [Miscellaneous](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91307) và câu hỏi phụ kiện của [Computers & Electronics](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91314). **Không lấy câu hỏi sự kiện của Water Bottle làm câu hỏi nhận dạng bình nước.**

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Bạn mất bình nước, hộp cơm, ô, thú bông, tai nghe hay loại đồ cá nhân nào? | Luôn hiện. | Có. |
| Hãng/logo và màu chính của món đồ là gì? | Luôn hiện. | Màu có; hãng có thể không rõ. |
| Chất liệu, kích thước hoặc dung tích ước lượng thế nào? | Bình nước, hộp cơm, ô hoặc đồ phù hợp. | Không bắt buộc; đề xuất thêm. |
| Nắp, quai, ống hút, hình dán hoặc chữ ghi có đặc điểm gì? | Nhánh bình nước/hộp đựng. | Có mô tả dấu hiệu phù hợp; đề xuất mới cho Sagasu. |
| Tai nghe có hộp, bao bảo vệ hoặc phụ kiện nào đi kèm? | Nhánh tai nghe. | Có/không và mô tả nếu có. |
| Có dấu sử dụng, đường may, hình trang trí hoặc chỗ sửa nào riêng biệt không? | Các loại còn lại. | Không bắt buộc; gợi ý cho C3. |

Giữ nguyên toàn bộ câu hỏi cũ của nhóm Đồ dùng cá nhân. Với bài mới, ưu tiên category 68 cho tai nghe, 72 cho bình nước và 67 cho đồ trẻ em. Không sửa category hoặc nội dung của bài cũ.

### 5.10. ID 10 — Đồ dùng khác

Tham khảo [Miscellaneous](https://campuslife.app.pixithq.com/public/#/squires/new-claim/item/91307) và [Default trong form chung](https://campuslife.app.pixithq.com/public/#/squires/new-claim). Tập trung mô tả linh hoạt vì nhóm này không có một cấu trúc đồ vật cố định.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Bạn đang nhận lại món đồ gì? | Luôn hiện. | Có. |
| Đồ có màu, hình dạng và kích thước gần đúng thế nào? | Luôn hiện. | Có mô tả. |
| Có hãng, logo, ký hiệu hoặc nhãn nào không? | Luôn hiện. | Không bắt buộc. |
| Có vỏ/túi hoặc đồ đi kèm không? Nếu có, hãy mô tả. | Luôn hiện. | Không bắt buộc; đề xuất thêm cho Sagasu. |

Phần dấu hiệu chỉ chủ sở hữu biết vẫn dùng C3, tránh hỏi lặp lại thành nhiều ô cùng nội dung.

### 5.11. ID 67 — Đồ dùng trẻ em

Tham khảo Children Items ở mục 2.2: nguồn hỏi hãng và màu. Những câu phân loại, phụ kiện và dấu hiệu sử dụng dưới đây là phần mở rộng cho Sagasu; không hỏi thông tin cá nhân của trẻ.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Món đồ là đồ chơi, thú bông, quần áo trẻ em hay loại đồ trẻ em nào? | Luôn hiện. | Có. |
| Món đồ có thương hiệu hoặc nhãn hiệu nào? | Luôn hiện. | Không bắt buộc nếu không rõ. |
| Màu chính, hình trang trí và kiểu dáng của món đồ như thế nào? | Luôn hiện. | Có. |
| Có chữ ghi, nhãn tên, đường may, vết sửa hoặc dấu hiệu riêng nào không? | Luôn hiện. | Không bắt buộc; gợi ý thêm cho C3. |
| Có phụ kiện hoặc bộ phận đi kèm không? Hãy mô tả. | Luôn hiện. | Có/không và mô tả nếu có. |

### 5.12. ID 68 — Máy tính & Thiết bị điện tử

Tham khảo Computers & Electronics ở mục 2.2. Dùng cho thiết bị điện tử ngoài điện thoại/laptop đã có nhóm riêng. Không áp dụng câu hỏi mã mở khóa của Pixit.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Món đồ là máy tính bảng, tai nghe, chuột, bàn phím, sạc hay thiết bị điện tử nào? | Luôn hiện. | Có. |
| Hãng, dòng máy và màu của thiết bị là gì? | Luôn hiện. | Có trả lời; cho phép không rõ dòng máy. |
| Khi mất, thiết bị có ở trong túi hoặc hộp đựng không? | Luôn hiện. | Có/không. |
| Túi/hộp có đặc điểm gì và bên trong có những đồ nào? | Có túi/hộp. | Có theo nhánh; có thể ghi không có đồ khác. |
| Thiết bị có vỏ, bao hoặc ốp bảo vệ không, loại và màu gì? | Luôn hiện. | Có/không; mô tả nếu có. |
| Có dây, đầu nối, phụ kiện, hình dán hoặc dấu sử dụng riêng nào đi kèm không? | Loại thiết bị phù hợp. | Không bắt buộc; có thể dùng C3. |
| Bạn có một phần serial hoặc chứng từ/ảnh cũ để đối chiếu không? | Luôn hiện. | Không bắt buộc. |

Không thu mật khẩu thiết bị, tài khoản, PIN hoặc OTP.

### 5.13. ID 69 — Kính mắt

Tách thành category riêng từ nhánh kính trong 5.7, bổ sung chi tiết gọng/tròng và phụ kiện. Các câu hỏi cũ ở 5.7 vẫn giữ nguyên.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Đây là kính có độ, kính mát hay kính thời trang? | Luôn hiện. | Có; không yêu cầu chỉ số độ kính. |
| Kính có thương hiệu/logo hoặc ký hiệu hãng nào? | Luôn hiện. | Không bắt buộc nếu không rõ. |
| Gọng kính và tròng kính có màu, hình dáng thế nào? | Luôn hiện. | Có. |
| Gọng làm bằng kim loại, nhựa hay chất liệu khác? | Luôn hiện. | Có trả lời; cho phép không rõ. |
| Có hộp, túi hoặc khăn lau đi kèm không? Hãy mô tả. | Luôn hiện. | Có/không và mô tả nếu có. |
| Có chữ khắc, vết xước, chỗ sửa hoặc dấu nhận dạng riêng nào? | Luôn hiện. | Không bắt buộc; gợi ý thêm cho C3. |

### 5.14. ID 70 — Giày dép

Tham khảo Footwear và nhánh giày/dép trong 5.6. Bổ sung cách phân biệt một đôi hay chiếc lẻ và các dấu sử dụng.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Đồ là giày thể thao, giày tây, sandal, dép hay loại khác? | Luôn hiện. | Có. |
| Có thương hiệu hoặc logo nào trên giày/dép? | Luôn hiện. | Không bắt buộc nếu không rõ. |
| Màu chính, phối màu và họa tiết thế nào? | Luôn hiện. | Có. |
| Cỡ giày/dép là bao nhiêu? | Luôn hiện. | Có trả lời; cho phép không nhớ rõ. |
| Chất liệu của giày/dép là gì? | Luôn hiện. | Không bắt buộc nếu không rõ. |
| Bạn mất cả đôi hay một chiếc? Có dấu mòn, rách, chữ ghi hoặc dấu riêng nào? | Luôn hiện. | Có trả lời phần đôi/chiếc; dấu riêng có thể bổ sung ở C3. |

### 5.15. ID 71 — Giấy tờ & Thẻ

Tách thành category riêng để nhận giấy tờ/thẻ rời. Bộ 5.3 vẫn giữ các câu hỏi về giấy tờ trong ví và nhánh giấy tờ của bài cũ. Không tạo category Hokie P.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Đây là giấy tờ hoặc thẻ loại gì? | Luôn hiện. | Có. |
| Họ tên ghi trên giấy tờ/thẻ là gì? | Luôn hiện. | Có. |
| Đơn vị, trường, ngân hàng hoặc cơ quan nào cấp giấy tờ/thẻ? | Luôn hiện. | Có trả lời; cho phép không rõ nếu phù hợp loại thẻ. |
| Mặt trước có màu, hình hoặc thiết kế gì để đối chiếu? | Luôn hiện. | Không bắt buộc. |
| Bạn có thể cung cấp vài ký tự cuối của mã thẻ/giấy tờ không? | Có mã và người claim biết. | Không bắt buộc; số ký tự cần chốt theo loại giấy tờ/thẻ. |
| Có bao thẻ, dây đeo hoặc phụ kiện đi kèm không, đặc điểm thế nào? | Luôn hiện. | Có/không và mô tả nếu có. |

Không yêu cầu đủ số thẻ ngân hàng, CVV, PIN hoặc OTP; ảnh bằng chứng nên che thông tin không cần cho việc đối chiếu.

### 5.16. ID 72 — Bình nước

Bộ câu hỏi nhận dạng do Sagasu đề xuất. Không sử dụng bốn câu hỏi về sự kiện/đặt chỗ đang hiển thị ở category Water Bottle của Pixit.

| Câu hỏi | Hiện khi | Mức bắt buộc đề xuất |
| --- | --- | --- |
| Bình thuộc hãng hoặc loại nào: bình giữ nhiệt, bình nhựa, bình thủy tinh hay loại khác? | Luôn hiện. | Có trả lời; cho phép không rõ hãng. |
| Màu chính, hình in hoặc họa tiết của bình là gì? | Luôn hiện. | Có. |
| Chất liệu và dung tích/kích thước ước lượng của bình thế nào? | Luôn hiện. | Không bắt buộc. |
| Nắp, quai, tay cầm hoặc ống hút có đặc điểm gì? | Theo thiết kế của bình. | Có mô tả phần phù hợp; có thể ghi không có. |
| Có tên viết, hình dán, vết móp, trầy hoặc dấu riêng nào trên bình không? | Luôn hiện. | Không bắt buộc; gợi ý thêm cho C3. |
| Bình có bao, túi, dây đeo hoặc phụ kiện nào đi kèm không? | Luôn hiện. | Có/không và mô tả nếu có. |

## 6. Cách đưa vào luồng claim hiện tại, để duyệt trước

1. Người dùng mở một bài Found P2P và bấm claim như luồng hiện có.
2. Form biết category từ bài đăng, hiển thị câu hỏi chung và nhóm câu hỏi tương ứng. Chỉ thêm bộ chọn loại đồ nhỏ hơn ở những category cần phân nhánh.
3. Không đưa C5/C6 vào form claim: không hỏi lại tên, email hoặc số điện thoại. Danh tính người gửi vẫn lấy từ tài khoản đăng nhập để phân quyền và hiển thị hồ sơ theo cơ chế hiện có.
4. Câu trả lời là bằng chứng riêng của hồ sơ claim, chỉ cho người gửi và người nhặt tham gia hồ sơ xem. Không đưa vào response bài công khai hoặc bộ tìm kiếm.
5. Không lấy `privateIdentifiers` của người nhặt để gợi ý, điền sẵn hay hiển thị đáp án cho người claim. Người nhặt tự đối chiếu khi xét duyệt.
6. Dùng câu mô tả căn cứ và ảnh bằng chứng hiện có; không tạo thêm ô mô tả trùng chức năng nếu không cần.
7. Nếu triển khai lưu câu trả lời riêng theo câu hỏi, giữ cả nội dung câu hỏi tại thời điểm gửi để hồ sơ cũ không bị đổi nghĩa khi mẫu câu hỏi được chỉnh sau này. Đây là đề xuất lưu trữ, chưa tạo bảng hoặc migration.
8. Khi claim bị từ chối hoặc hủy và người dùng gửi claim mới, lưu câu trả lời ở hồ sơ mới, giữ nguyên hồ sơ trước theo quy tắc claim hiện tại.

Không tự chấp thuận claim chỉ vì trả lời đủ câu hỏi hoặc trùng hãng/màu. Câu hỏi có thể dựa vào thông tin công khai; quyền xét duyệt vẫn thuộc người nhặt theo luồng hiện có. Phần này không bổ sung lịch hẹn giao đồ hoặc luồng Approver duyệt.

## 7. Nội dung đã chốt và nội dung cần duyệt tiếp

- [x] Bộ câu hỏi chung chỉ có **C1–C4**; bỏ C5 và C6 khỏi form claim.
- [x] Giữ nguyên toàn bộ **59 câu hỏi** ở mục 5.1–5.10 của bản trước.
- [x] Thêm 6 category còn thiếu vào DB; không thêm Hokie P và Default.
- [x] Giữ nguyên tên, ID và bài đăng của các category cũ.
- [x] Duyệt nội dung **36 câu hỏi bổ sung** cho 6 category mới ở mục 5.11–5.16.
- [x] Duyệt cách ưu tiên category cụ thể khi đăng bài mới theo mục 3.1.
- [x] Duyệt mức bắt buộc và điều kiện hiển thị của từng câu hỏi.
- [x] Duyệt cách xử lý các câu hỏi theo nhánh, tránh hỏi những thông tin không áp dụng cho món đồ.
- [x] Serial/phần mã thẻ là tùy chọn, không ép số ký tự cố định; không hỏi mật khẩu, PIN, OTP hoặc CVV.

Việc chọn tất cả câu hỏi không có nghĩa hiện mọi nhánh trên mọi hồ sơ: mỗi claim dùng C1–C4 và bộ riêng theo category, rồi chỉ hiện những nhánh phù hợp.

## 8. Kết quả cập nhật của bản duyệt 2

- Đã khảo sát 15 category nguồn; **13 nhóm được áp dụng**, loại Hokie P và Default khỏi danh mục đích.
- DB Sagasu hiện có **16 category đang hoạt động**, gồm 10 category cũ và 6 dòng mới có ID **67–72**.
- Bộ câu hỏi chung còn đúng **4 câu C1–C4**.
- Mục 5 có **16 bộ câu hỏi riêng**: giữ 59 câu cũ và bổ sung 36 câu cho category mới, tổng 95 dòng câu hỏi theo category. Người dùng chỉ trả lời bộ và nhánh áp dụng cho món đồ của mình.
- Phần category và tài liệu được commit trước bằng **38e6bad**. Sau khi duyệt, bộ câu hỏi động đã được triển khai như mục 9; luồng xét duyệt claim giữ nguyên.

### 8.1. SQL bổ sung category để tham khảo khi đồng bộ máy khác

Đoạn này ghi lại thao tác đã thực hiện trên DB Docker hiện tại. Không gán cứng ID 67–72 khi chạy trên máy khác; ID thực tế do DB cấp. Không chạy lại việc tạo schema.

```sql
BEGIN;

INSERT INTO category (name, is_active)
VALUES
    ('Đồ dùng trẻ em', true),
    ('Máy tính & Thiết bị điện tử', true),
    ('Kính mắt', true),
    ('Giày dép', true),
    ('Giấy tờ & Thẻ', true),
    ('Bình nước', true)
ON CONFLICT DO NOTHING
RETURNING category_id, name, is_active;

COMMIT;
```

SQL chỉ thêm những tên chưa có theo ràng buộc unique tên category hiện tại; không đổi tên, không chuyển bài đăng và không kích hoạt lại category đã tồn tại nhưng bị tắt.

## 9. Phần đã triển khai sau khi duyệt

### 9.1. Migration và ba bảng mới

- `BE-main/db/09_add_claim_categories.sql`: bổ sung 6 category, đã commit cùng bản tài liệu được duyệt.
- `BE-main/db/10_claim_questionnaires.sql`: tạo ba bảng bên dưới và seed **17 mẫu, 99 câu hỏi**; đã áp dụng vào Docker hiện tại.
- Repo đang dùng các script SQL theo thứ tự trong `BE-main/db`; cần chạy 09 rồi 10 trên máy khác sau các script claim 07–08. Không gán cứng category ID 67–72: migration gắn mẫu vào category theo tên lúc seed, sau đó lưu khóa ngoại theo ID.

| Bảng | Vai trò |
| --- | --- |
| `claim_question_template` | Mẫu chung và mẫu theo category, có phiên bản, trạng thái hoạt động và khóa ngoại tới category. |
| `claim_question` | Nội dung câu hỏi, kiểu nhập, lựa chọn, mức bắt buộc, thứ tự và điều kiện hiển thị. |
| `claim_answer` | Câu trả lời theo claim, kèm bản chụp nhãn câu hỏi, tên bộ và phiên bản lúc gửi. |

C3 vẫn dùng `claim.description`; C4 dùng `claim_evidence` và ghi chú bằng chứng hiện có. Không nhân đôi nội dung C3 hoặc file ảnh sang bảng câu trả lời.

### 9.2. Backend và frontend

- `GET /api/posts/{postId}/claims/questions`: lấy mẫu câu hỏi phù hợp khi người dùng được phép gửi claim cho bài đó.
- Gửi claim/gửi lại claim mang thêm phiên bản mẫu và danh sách câu trả lời; backend kiểm tra lại category, câu bắt buộc, lựa chọn, ngày mất và nhánh đang hiển thị trước khi tải ảnh, rồi kiểm tra lại khi lưu.
- C1 hỗ trợ ngày chính xác, gần đúng hoặc không nhớ rõ; giờ mất không bắt buộc và dùng giờ Việt Nam.
- Các trường nhánh chỉ hiện khi phù hợp; câu trả lời của nhánh đang ẩn không được gửi hoặc chấp nhận ở backend.
- C5/C6 không nằm trong form. Không hiển thị hay điền sẵn `privateIdentifiers` của người nhặt vào câu trả lời.
- Chi tiết claim hiển thị câu trả lời được lưu lúc gửi; chỉ người claim và người nhặt được xem, theo quyền của hồ sơ hiện có.
- Claim cũ không có câu trả lời dạng mới vẫn đọc được căn cứ, bằng chứng và lịch sử. Gửi lại sau khi hủy/từ chối tạo claim mới với mẫu hiện hành, giữ nguyên hồ sơ cũ.
- Nếu thêm category mới chưa gắn mẫu, dùng mẫu Đồ dùng khác làm dự phòng; không sửa service/controller CRUD category.
- Không bổ sung tự động duyệt, lịch hẹn giao đồ hoặc module Approver.
