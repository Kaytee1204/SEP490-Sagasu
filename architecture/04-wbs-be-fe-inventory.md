# Thống Kê Module / API Backend (BE), Màn Hình Frontend (FE) & Bảng WBS Project Tracking — Sagasu

Tài liệu này tổng hợp đầy đủ số lượng **Module, Controller, API Backend (`BE-main` & `AI-service`)**, danh sách **Màn hình Frontend (`FE-main`)** và **Bảng WBS Project Tracking** chi tiết (đầy đủ 8 cột chuẩn SEP490) của dự án **Sagasu — Hệ thống Quản lý Đồ thất lạc & Nhặt được tích hợp AI Đa phương thức**.

---

## 1. Tổng Quan Số Lượng Toàn Hệ Thống

| Hạng mục | Số lượng | Chi tiết |
| :--- | :---: | :--- |
| **Module nghiệp vụ Backend (Swagger Tags)** | **14 Modules** | Đánh số chuẩn từ `01.` đến `14.` trên Swagger UI |
| **Backend REST Controllers** | **21 Controllers** | Nằm trong package `com.example.base.controller.*` |
| **Backend REST API Endpoints** | **117 APIs** | **113 API hiển thị chính thức** (`[1.1]` → `[14.6]`) + **4 API ẩn (`@Hidden`)** |
| **WebSocket Real-time Endpoint** | **1 STOMP Broker** | `/ws/chat` (Kênh nhận `/app/chat.send`, `/app/chat.read`; kênh phát `/user/queue/chat`) |
| **AI Service Endpoints (`FastAPI`)** | **3 APIs** | `POST /api/match` (YOLOv8 + ViT/CLIP + HSV + Spatio-Temporal), `GET /api/cache-stats`, `GET /health` |
| **Background Batch / Scheduler** | **4 Batches** | Quét hết hạn lịch hẹn 12h (`@Scheduled`), Khóa/Mở khóa tự động 15 phút, Import tài khoản hàng loạt từ Excel, Lưu nháp tự động IndexedDB |
| **Màn hình chức năng Frontend (`FE-main`)** | **33 Màn hình** | Tương ứng với **37 đường dẫn Route** (chưa tính 5 route redirect tương thích ngược) |
| **Cơ sở dữ liệu PostgreSQL** | **38 Bảng** | Chia thành 8 phân hệ dữ liệu (xem chi tiết tại [01-database-schema.md](./01-database-schema.md)) |

---

## 2. Thống Kê 14 Module & 21 Controller Backend (`BE-main`)

| Mã Module (Swagger Tag) | Controller phụ trách | Số API | Phạm vi đánh số |
| :--- | :--- | :---: | :---: |
| **01. Auth - Đăng nhập & Quên mật khẩu** | `AuthController` | **4** *(+4 ẩn)* | `[1.1]` → `[1.4]` |
| **02. Account Management - Quản lý tài khoản & Hồ sơ** | `AccountController` | **10** | `[2.1]` → `[2.10]` |
| **03. Category Management - Quản lý danh mục đồ vật** | `CategoryController` | **4** | `[3.1]` → `[3.4]` |
| **04. Posts (Lost & Found) - Bài đăng & AI Smart Matching** | `PostController` | **8** | `[4.1]` → `[4.8]` |
| **05. My Posts - Quản lý bài đăng cá nhân** | `PostManagementController` | **5** | `[5.1]` → `[5.5]` |
| **06. Claims (P2P) - Yêu cầu nhận đồ & Bộ câu hỏi xác minh** | `ClaimQuestionnaireController`, `ClaimController` | **13** | `[6.1]` → `[6.13]` |
| **07. Chat (P2P) - Trò chuyện & Chia sẻ vị trí** | `ChatController`, `ChatAttachmentController`, `ChatLocationAddressController` | **13** | `[7.1]` → `[7.13]` |
| **08. Handover (P2P) - Bàn giao tài sản trực tiếp** | `P2pHandoverController` | **4** | `[8.1]` → `[8.4]` |
| **09. Office Intake & Storage - Tiếp nhận tại quầy & Kho lưu trữ** | `ApproverReceiptController`, `ReceiptLocationController` | **6** | `[9.1]` → `[9.6]` |
| **10. Office Claims - Tiếp nhận & Xét duyệt Claim tại Văn phòng** | `OfficeClaimController` | **9** | `[10.1]` → `[10.9]` |
| **11. Office Workflow & Invitations - Chuyển giao, Lịch hẹn & Trao trả** | `OfficeWorkflowController`, `OfficeInvitationController`, `OfficeIntakeInvitationController` | **20** | `[11.1]` → `[11.20]` |
| **12. Office & Moderation Management - Kiểm duyệt & Quản trị kho** | `OfficeManagementController` | **7** | `[12.1]` → `[12.7]` |
| **13. Reports & Dispute Resolution - Báo cáo & Điều tra giải quyết** | `ReportController`, `ReportReviewController` | **11** | `[13.1]` → `[13.11]` |
| **14. Notifications - Thông báo đa kênh & Cài đặt tùy chọn** | `NotificationController` | **6** | `[14.1]` → `[14.6]` |
| **Tổng cộng** | **21 Controllers** | **113 hiển thị (+4 ẩn)** | `[1.1]` → `[14.6]` |

---

## 3. Thống Kê 33 Màn Hình Chức Năng Frontend (`FE-main`)

| STT | Phân hệ | Tên màn hình (Component) | Đường dẫn (Route) | Đối tượng sử dụng |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Auth & Public** | `LandingLoginPage` | `/` (khi chưa đăng nhập) | Khách (Guest) |
| **2** | **Auth & Public** | `AuthPage` (`mode=login`) | `/dang-nhap` | Tất cả Role |
| **3** | **Auth & Public** | `LoginForm` (`forgot_email`) | `/quen-mat-khau` (Bước 1: Nhập Email) | Tất cả Role |
| **4** | **Auth & Public** | `LoginForm` (`forgot_otp`) | `/quen-mat-khau` (Bước 2: Xác thực OTP) | Tất cả Role |
| **5** | **Auth & Public** | `LoginForm` (`forgot_new_password`) | `/quen-mat-khau` (Bước 3: Đặt mật khẩu mới & Mở khóa) | Tất cả Role |
| **6** | **Auth & Public** | `InvitationPage` (Nhận đồ) | `/van-phong/lich-hen` | Public qua Email Token 12h |
| **7** | **Auth & Public** | `InvitationPage` (Giao đồ) | `/van-phong/hen-giao-do` | Public qua Email Token 12h |
| **8** | **Người dùng (Client)** | `HomePage` | `/` (sau khi đăng nhập) | User, Approver, Manager, Admin |
| **9** | **Người dùng (Client)** | `SearchPage` | `/tim-kiem` | User, Approver, Manager, Admin |
| **10** | **Người dùng (Client)** | `ItemDetailPage` | `/chi-tiet/:id` | User, Approver, Manager, Admin |
| **11** | **Người dùng (Client)** | `PostItemPage` | `/dang-tin`, `/dang-tin-nhat-duoc` | User, Approver, Manager, Admin |
| **12** | **Người dùng (Client)** | `EditPostPage` | `/sua-tin/:id` | Chủ bài đăng |
| **13** | **Người dùng (Client)** | `MyPostsPage` | `/tin-dang-cua-toi`, `/bai-dang-cua-toi` | Chủ bài đăng |
| **14** | **Người dùng (Client)** | `SmartMatchPage` | `/smart-match` | User, Approver, Manager, Admin |
| **15** | **Người dùng (Client)** | `ClaimItemPage` (`SubmitClaimPage`) | `/xac-minh/:id` | Người nhận đồ (Claimant) |
| **16** | **Người dùng (Client)** | `MyClaimsPage` | `/claim-cua-toi` | Người gửi & Chủ bài đăng |
| **17** | **Người dùng (Client)** | `PostClaimsPage` | `/bai-dang/:id/claims` | Chủ bài đăng / Approver |
| **18** | **Người dùng (Client)** | `ClaimDetailPage` (+ `HandoverPanel`) | `/bai-dang/:postId/claims/:claimId` | Chủ bài đăng & Người nhận |
| **19** | **Người dùng (Client)** | `ChatPage` | `/tin-nhan`, `/tin-nhan/:conversationId` | Hai bên P2P |
| **20** | **Người dùng (Client)** | `ProfilePage` | `/ho-so` | Tất cả Role |
| **21** | **Báo cáo & Văn phòng (User)** | `MyReportsPage` | `/bao-cao-cua-toi` | Người gửi báo cáo |
| **22** | **Báo cáo & Văn phòng (User)** | `ReportDetailPage` | `/bao-cao/:id` | Người gửi báo cáo |
| **23** | **Báo cáo & Văn phòng (User)** | `ReportExchangesPage` | `/bao-cao-trao-doi` | Người liên quan giải trình |
| **24** | **Báo cáo & Văn phòng (User)** | `OfficeClaimForm` | `/van-phong/xac-minh/:id`, `/approver/office-claims/new` | User (Online) & Approver (Tại quầy) |
| **25** | **Cán bộ Tiếp nhận (`APPROVER`)** | `ReceiptQueuePage` | `/approver/receipts` | Approver |
| **26** | **Cán bộ Tiếp nhận (`APPROVER`)** | `IntakeFormPage` | `/approver/receipts/new`, `/approver/receipts/:id/edit` | Approver |
| **27** | **Cán bộ Tiếp nhận (`APPROVER`)** | `IntakeDetailPage` | `/approver/receipts/:id` | Approver |
| **28** | **Cán bộ Tiếp nhận & User** | `OfficeClaimList` | `/approver/office-claims`, `/van-phong/claims` | Approver & User (`mine`) |
| **29** | **Cán bộ Tiếp nhận & User** | `OfficeClaimDetail` | `/approver/office-claims/:id`, `/van-phong/claims/:id` | Approver & User (`mine`) |
| **30** | **Quản trị & Văn phòng chung** | `OfficeTransfers` | `/admin/transfers`, `/approver/transfers`, `/van-phong/chuyen-giao` | Manager, Approver, User |
| **31** | **Quản trị & Văn phòng chung** | `StoragePage` | `/admin/storage`, `/approver/storage` | Manager & Approver |
| **32** | **Quản trị (`ADMIN` / `MANAGER`)** | `AccountDashboardPage` & `AdminUserDetailsPage` | `/admin`, `/admin/users`, `/admin/users/:id` | Admin & Manager |
| **33** | **Quản trị (`MANAGER`)** | `CategoryManagementPage`, `PostAdministration`, `StaffPostDetail`, `AdminReportsPage` (`ReportReviewPage`) | `/admin/categories`, `/admin/items`, `/admin/items/:id`, `/admin/reports` | Manager |

---

## 4. Bảng WBS Project Tracking Toàn Dự Án (Chuẩn 8 Cột SEP490 — 92 Hạng Mục)

| Function/Screen | Feature | Sub Feature | Type | Function/Screen Description | Level | Est. Effort | Planned |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Main Layout, Navbar & Notification Bell | Common | Navigation & Layout | Screen | Khung giao diện người dùng tích hợp thanh điều hướng, menu tài khoản, chuông thông báo thời gian thực và kiểm soát phân quyền route. | Medium | 8 | Iteration 1 |
| Admin & Staff Workspace Layout | Common | Navigation & Layout | Screen | Khung giao diện quản trị có Sidebar thu gọn/mở rộng tự động theo vai trò (ADMIN, MANAGER, APPROVER) và ngăn kéo điều hướng trên thiết bị di động. | Medium | 8 | Iteration 1 |
| Landing Login Page | Public | Onboarding | Screen | Màn hình chào mừng kết hợp giới thiệu tính năng tìm đồ thất lạc bằng AI và biểu mẫu đăng nhập ngay tại trang chủ khi chưa xác thực. | Simple | 4 | Iteration 1 |
| User Login Screen | Authentication | Sign In & Lockout | Screen | Màn hình đăng nhập bằng Email/Mật khẩu, hiển thị cảnh báo chữ đỏ trực tiếp số lần thử còn lại và khóa tạm thời 15 phút khi nhập sai quá 5 lần. | Medium | 8 | Iteration 1 |
| Forgot Password - Request OTP Screen | Authentication | Password Recovery | Screen | Màn hình bước 1 khôi phục mật khẩu cho phép người dùng nhập Email để yêu cầu gửi mã xác thực OTP 6 chữ số có hiệu lực 5 phút. | Simple | 4 | Iteration 1 |
| Forgot Password - Verify OTP Screen | Authentication | Password Recovery | Screen | Màn hình bước 2 nhập và kiểm tra tính hợp lệ của mã OTP 6 chữ số (dùng 1 lần) kèm chức năng gửi lại mã OTP mới. | Simple | 4 | Iteration 1 |
| Forgot Password - Reset & Unlock Screen | Authentication | Password Recovery | Screen | Màn hình bước 3 đặt lại mật khẩu mới (8-16 ký tự, có ký tự đặc biệt), tự động mở khóa tài khoản đang bị tạm khóa và đưa bộ đếm lỗi về 0. | Medium | 6 | Iteration 1 |
| Auth Login & Lockout API | Authentication | Sign In & Lockout | API | API xác thực thông tin đăng nhập (`POST /api/auth/login`), chuẩn hóa email hoa/thường, ghi nhật ký đăng nhập, đếm lỗi và cấp JWT Token. | Medium | 8 | Iteration 1 |
| Request Forgot Password OTP API | Authentication | Password Recovery | API | API (`POST /api/auth/forgot-password`) kiểm tra tài khoản, sinh mã OTP 6 số hiệu lực 5 phút và gửi email khôi phục qua SMTP. | Simple | 4 | Iteration 1 |
| Verify Reset Password OTP API | Authentication | Password Recovery | API | API (`POST /api/auth/verify-reset-otp`) kiểm tra tính hợp lệ và thời hạn của mã OTP trước khi cho phép người dùng sang bước đặt mật khẩu mới. | Simple | 4 | Iteration 1 |
| Reset Password & Unlock Account API | Authentication | Password Recovery | API | API (`POST /api/auth/reset-password`) xác thực OTP lần cuối, mã hóa mật khẩu mới bằng BCrypt, xóa `locked_until` và reset `failed_attempt_count = 0`. | Medium | 6 | Iteration 1 |
| Account Lockout 15m & OTP Expiry Batch | Authentication | Security Policy | Batch | Cơ chế kiểm tra thời hạn khóa tài khoản 15 phút (`locked_until`) và tự động vô hiệu hóa các mã OTP quá hạn 5 phút trong `otp_challenges`. | Simple | 4 | Iteration 1 |
| User Profile & Password Change Screen | Account Management | Personal Profile | Screen | Màn hình xem và cập nhật số điện thoại, đổi mật khẩu trực tiếp, cấu hình nhận thông báo đa kênh và tra cứu lịch sử đăng nhập cá nhân. | Medium | 10 | Iteration 1 |
| Account Management Dashboard Screen | Account Management | Admin User CRUD | Screen | Màn hình quản trị danh sách tài khoản có thẻ thống kê trạng thái, bộ lọc theo từ khóa/Role/Status, tạo/sửa tài khoản, khóa/mở khóa và xóa mềm. | Complex | 14 | Iteration 1 |
| Account Create & Edit Modal | Account Management | Admin User CRUD | Screen | Hộp thoại thêm mới hoặc chỉnh sửa thông tin tài khoản (Họ tên, Email, Mật khẩu, Role, Trạng thái) có phân quyền riêng cho Admin và Manager. | Medium | 6 | Iteration 1 |
| Account Excel Import Modal | Account Management | Excel Import/Export | Screen | Hộp thoại tải file Excel mẫu, chọn file `.xlsx`/`.xls` để import tài khoản hàng loạt và hiển thị báo cáo chi tiết số dòng thành công/lỗi. | Medium | 6 | Iteration 1 |
| Admin User Details Screen | Account Management | Admin User CRUD | Screen | Màn hình xem chi tiết hồ sơ thành viên, lịch sử hoạt động, gửi cảnh báo vi phạm và thao tác khóa/mở khóa tài khoản dành cho Admin/Manager. | Medium | 8 | Iteration 1 |
| Get & Update Personal Profile API | Account Management | Personal Profile | API | Cụm 2 API (`GET /api/accounts/me`, `PUT /api/accounts/me`) truy xuất thông tin tài khoản đang đăng nhập, cập nhật SĐT và đổi mật khẩu cá nhân. | Medium | 6 | Iteration 1 |
| Personal Login Audit History Report | Account Management | Security Audit | Report | API & Báo cáo (`GET /api/accounts/me/audit-logs`) trích xuất 10 bản ghi lịch sử đăng nhập gần nhất kèm địa chỉ IP, thời gian và kết quả đăng nhập. | Simple | 4 | Iteration 1 |
| Admin List & Filter Accounts API | Account Management | Admin User CRUD | API | API (`GET /api/accounts`, `GET /api/accounts/{id}`) tìm kiếm, lọc tài khoản theo từ khóa, Role, Status kết hợp phân trang từ PostgreSQL. | Medium | 6 | Iteration 1 |
| Admin Create & Update Account API | Account Management | Admin User CRUD | API | Cụm 2 API (`POST /api/accounts`, `PUT /api/accounts/{id}`) tạo mới và cập nhật tài khoản, kiểm tra trùng Email và ràng buộc quyền gán Role. | Medium | 8 | Iteration 1 |
| Admin Toggle Lock & Soft-Delete Account API | Account Management | Admin User CRUD | API | Cụm 2 API (`PATCH /api/accounts/{id}/status`, `DELETE /api/accounts/{id}`) khóa/mở khóa tài khoản, xóa mềm và thu hồi toàn bộ Refresh Token. | Medium | 6 | Iteration 1 |
| Account Bulk Excel Import Batch | Account Management | Excel Import/Export | Batch | Tiến trình đọc file Excel qua Apache POI (`POST /api/accounts/import`), kiểm tra định dạng từng dòng, mã hóa BCrypt và lưu hàng loạt vào DB. | Complex | 12 | Iteration 1 |
| Account Status Summary & Excel Template Report | Account Management | Excel Import/Export | Report | Chức năng tổng hợp số liệu tài khoản theo trạng thái (`ACTIVE`, `SUSPENDED`, `DELETED`) và xuất file Excel mẫu (`GET /api/accounts/import-template`). | Simple | 4 | Iteration 1 |
| Category Management Screen | Category Management | Category CRUD | Screen | Màn hình quản lý danh mục đồ vật dành cho Manager hỗ trợ tìm kiếm, lọc theo trạng thái hoạt động, thêm mới, đổi tên và bật/tắt danh mục. | Medium | 8 | Iteration 1 |
| Category List & Filter API | Category Management | Category CRUD | API | API (`GET /api/categories`) tra cứu và phân trang danh sách danh mục đồ thất lạc theo từ khóa và trạng thái `active`. | Simple | 4 | Iteration 1 |
| Category Create, Rename & Toggle Status API | Category Management | Category CRUD | API | Cụm 3 API (`POST /api/categories`, `PUT /api/categories/{id}`, `DELETE /api/categories/{id}`) thêm mới, đổi tên chống trùng lặp và bật/tắt danh mục. | Medium | 6 | Iteration 1 |
| Home Page | Item Management | Public Discovery | Screen | Trang chủ sau đăng nhập hiển thị Banner tìm kiếm động, bản đồ khu vực thất lạc, quy trình 4 bước xử lý và lối tắt đăng tin nhanh. | Medium | 8 | Iteration 1 |
| Advanced Search & Filter Screen | Item Management | Public Discovery | Screen | Màn hình tìm kiếm bài đăng công khai kết hợp từ khóa Full-Text Search tiếng Việt, lọc theo loại tin (Lost/Found), danh mục và khoảng thời gian. | Complex | 14 | Iteration 1 |
| Item Detail Screen | Item Management | Post Details | Screen | Màn hình chi tiết bài đăng hiển thị thư viện ảnh, thông tin vị trí/thời gian, trạng thái lưu kho văn phòng, nút gửi Claim, mở Chat và ghi chú nhận dạng riêng tư. | Complex | 14 | Iteration 1 |
| Create Lost / Found Post Screen | Item Management | Post Composer | Screen | Màn hình đăng tin đồ thất lạc hoặc nhặt được có tải lên tối đa 5 ảnh JPG/PNG, xem trước trực tiếp (Live Preview) và khôi phục bản nháp tự động. | Complex | 16 | Iteration 1 |
| Edit Post Screen & Dialog | Item Management | My Posts | Screen | Màn hình và hộp thoại chỉnh sửa bài đăng cá nhân cho phép cập nhật tiêu đề, mô tả, danh mục, thời gian, địa điểm và thêm/xóa hình ảnh minh họa. | Complex | 12 | Iteration 1 |
| My Posts Management Screen | Item Management | My Posts | Screen | Màn hình quản lý danh sách bài đăng của tôi, lọc theo trạng thái kiểm duyệt/xử lý, sửa bài, xóa mềm hoặc gửi yêu cầu bàn giao đồ cho văn phòng. | Medium | 10 | Iteration 1 |
| Offline Post Draft Auto-Save Batch | Item Management | Post Composer | Batch | Cơ chế tự động lưu và khôi phục bản nháp bài đăng cùng tệp ảnh trên IndexedDB của trình duyệt, kết hợp `X-Request-Id` chống gửi trùng khi rớt mạng. | Medium | 8 | Iteration 1 |
| Public Posts Full-Text Search API | Item Management | Public Discovery | API | Cụm 2 API (`GET /api/posts`, `GET /api/posts/categories`) tìm kiếm bài đăng `VISIBLE` bằng PostgreSQL Full-Text Search GIN Index và bộ lọc đa chiều. | Complex | 10 | Iteration 1 |
| Post Detail & Private Identifiers API | Item Management | Post Details | API | Cụm 2 API (`GET /api/posts/{id}`, `PUT /api/posts/{id}/private-identifiers`) trả về chi tiết bài đăng và quản lý dấu hiệu nhận dạng bí mật của chủ bài. | Medium | 8 | Iteration 1 |
| Create Lost / Found Post & R2 Upload API | Item Management | Post Composer | API | Cụm 3 API (`POST /api/posts`, `/lost`, `/found`) kiểm tra định dạng/dung lượng ảnh, tải ảnh lên Cloudflare R2 và lưu bài đăng chống gửi trùng. | Complex | 14 | Iteration 1 |
| My Posts List & Edit Snapshot API | Item Management | My Posts | API | Cụm 2 API (`GET /api/me/posts`, `GET /api/posts/{id}/edit`) lấy danh sách bài đăng cá nhân và tải dữ liệu bài đăng kèm khóa lạc quan `expectedUpdatedAt`. | Medium | 6 | Iteration 1 |
| Update & Soft-Delete Own Post API | Item Management | My Posts | API | Cụm 2 API (`PUT /api/posts/{id}`, `DELETE /api/posts/{id}`) cập nhật nội dung/hình ảnh bài đăng, dọn dẹp ảnh cũ trên R2 và xóa mềm bài đăng. | Complex | 10 | Iteration 1 |
| Request Office Custody Handover API | Item Management | My Posts | API | API (`POST /api/posts/{id}/office-handover`) chuyển chế độ lưu giữ bài đăng Found từ tự giữ (`P2P`) sang gửi yêu cầu bàn giao cho văn phòng (`OFFICE`). | Medium | 6 | Iteration 1 |
| AI Smart Match Page | Smart Matching (AI) | Multimodal Matching | Screen | Màn hình đối sánh thông minh hiển thị danh sách gợi ý bài đăng ngược chiều có độ tương đồng trên 60% kèm phân rã điểm số chi tiết. | Medium | 8 | Iteration 2 |
| AI Smart Match Modal (In-Post) | Smart Matching (AI) | Multimodal Matching | Screen | Hộp thoại tìm đồ vật trùng khớp ngay trên trang chi tiết bài đăng, hiển thị phần trăm tương đồng hình ảnh, văn bản, địa điểm và thời gian. | Medium | 8 | Iteration 2 |
| Backend Smart Match Orchestrator API | Smart Matching (AI) | Multimodal Matching | API | API (`GET /api/posts/{id}/smart-matches`) truy vấn ứng viên ngược chiều (`LOST` <-> `FOUND`), gọi AI Service chấm điểm và fallback Java khi AI bận. | Complex | 12 | Iteration 2 |
| AI Multimodal Matching Engine API | Smart Matching (AI) | Multimodal Matching | API | API FastAPI (`POST /api/match`) kết hợp YOLOv8 cắt vật thể, ViT/CLIP trích xuất vector ảnh & tiếng Việt, HSV màu sắc và chấm điểm không gian/thời gian. | Complex | 20 | Iteration 2 |
| AI Vector Store & Health Check API | Smart Matching (AI) | Multimodal Matching | API | Cụm 2 API FastAPI (`GET /api/cache-stats`, `GET /health`) quản lý bộ nhớ đệm Persistent Vector Store và giám sát trạng thái mô hình YOLOv8/CLIP. | Simple | 4 | Iteration 2 |
| Submit P2P Claim & Questionnaire Screen | Claims Management (P2P) | Claim Submission | Screen | Màn hình gửi yêu cầu nhận đồ P2P, tự động tải bộ câu hỏi xác minh theo danh mục đồ vật, nhập câu trả lời và đính kèm ảnh bằng chứng sở hữu. | Complex | 14 | Iteration 2 |
| My Claims Screen (Sent & Received) | Claims Management (P2P) | Claim Review | Screen | Màn hình quản lý danh sách các yêu cầu nhận đồ tôi đã gửi đi và tổng hợp các bài đăng của tôi đang có người gửi yêu cầu nhận đồ. | Complex | 10 | Iteration 2 |
| Post Claims Grouped List Screen | Claims Management (P2P) | Claim Review | Screen | Màn hình danh sách toàn bộ yêu cầu nhận đồ của một bài đăng Found cụ thể, hỗ trợ lọc theo trạng thái xét duyệt và so sánh các ứng viên. | Medium | 8 | Iteration 2 |
| P2P Claim Detail & Review Screen | Claims Management (P2P) | Claim Review | Screen | Màn hình chi tiết yêu cầu nhận đồ P2P cho phép chủ bài đăng đối chiếu câu trả lời xác minh, yêu cầu bổ sung thông tin, từ chối hoặc duyệt Claim. | Complex | 14 | Iteration 2 |
| Claim Questionnaire Template API | Claims Management (P2P) | Claim Submission | API | API (`GET /api/posts/{postId}/claim-questionnaire`) truy xuất bộ câu hỏi xác minh đang hoạt động tương ứng với danh mục của bài đăng. | Simple | 4 | Iteration 2 |
| Submit & Resubmit P2P Claim API | Claims Management (P2P) | Claim Submission | API | Cụm 3 API (`POST /api/posts/{postId}/claims`, `POST /api/claims/{id}/evidences`, `PUT /api/claims/{id}/resubmit`) nộp Claim, tải bằng chứng R2 và gửi lại. | Complex | 12 | Iteration 2 |
| Cancel Own P2P Claim API | Claims Management (P2P) | Claim Submission | API | API (`POST /api/claims/{id}/cancel`) cho phép người nhận hủy yêu cầu nhận đồ và tự động mở lại trạng thái bài đăng nếu đang ở trạng thái giữ chỗ. | Medium | 6 | Iteration 2 |
| My Claims & Post Claims Query API | Claims Management (P2P) | Claim Review | API | Cụm 7 API (`GET /api/me/claims`, `/claim-posts`, `/posts/{postId}/claims`, `/claims/{id}`, `/activities`) truy vấn danh sách, chi tiết và lịch sử Claim. | Complex | 12 | Iteration 2 |
| Review P2P Claim Decision API | Claims Management (P2P) | Claim Review | API | API (`PATCH /api/claims/{id}/review`) thực thi quyết định Duyệt (`APPROVED`), Từ chối (`REJECTED`) hoặc Yêu cầu bổ sung (`NEEDS_INFO`) và mở phòng Chat. | Complex | 10 | Iteration 2 |
| P2P Real-time Chat & Map Location Screen | P2P Chat | Real-time Messaging | Screen | Màn hình trò chuyện trực tuyến giữa người nhặt và người nhận đồ hỗ trợ nhắn tin thời gian thực, gửi ảnh/PDF, chia sẻ tọa độ bản đồ MapLibre và báo đã xem. | Complex | 18 | Iteration 2 |
| Open & List Chat Conversations API | P2P Chat | Real-time Messaging | API | Cụm 4 API (`POST /api/chat/conversations`, `GET /conversations`, `GET /conversations/{id}`, `GET /unread-count`) mở phòng chat P2P và đếm tin chưa đọc. | Medium | 8 | Iteration 2 |
| Chat Messages History & Send Text API | P2P Chat | Real-time Messaging | API | Cụm 3 API (`GET /messages`, `POST /messages`, `POST /read`) phân trang lịch sử tin nhắn theo con trỏ, gửi tin nhắn văn bản và đánh dấu đã đọc. | Complex | 10 | Iteration 2 |
| Chat Share Location & Reverse Geocode API | P2P Chat | Attachments & Map | API | Cụm 3 API (`POST /location`, `POST /messages/{id}/location/stop`, `GET /reverse-geocode`) chia sẻ vị trí hẹn gặp trên bản đồ và phân giải địa chỉ tiếng Việt. | Medium | 8 | Iteration 2 |
| Chat Attachment Presigned R2 Upload API | P2P Chat | Attachments & Map | API | Cụm 3 API (`POST /attachments/presign`, `POST /attachments/messages`, `GET /attachments/{id}/file`) tạo Presigned URL tải ảnh/PDF lên R2 và gửi vào phòng chat. | Medium | 8 | Iteration 2 |
| WebSocket STOMP Real-time Chat Broker | P2P Chat | Real-time Messaging | API | Kênh giao tiếp WebSocket (`/ws/chat`) xác thực JWT, đẩy tin nhắn mới và sự kiện đã đọc (`READ_RECEIPT`) tức thời tới các thành viên trong phòng chat. | Complex | 12 | Iteration 2 |
| P2P Handover & Issue Reporting Panel | P2P Handover | Direct Handover | Screen | Giao diện lập biên bản bàn giao đồ trực tiếp P2P kèm ảnh minh chứng, người nhận xác nhận hoàn tất trao trả hoặc báo cáo sự cố tranh chấp. | Complex | 12 | Iteration 2 |
| Create & Query P2P Handover Record API | P2P Handover | Direct Handover | API | Cụm 2 API (`GET /api/claims/{claimId}/handover`, `POST /handover`) lập biên bản bàn giao P2P kèm tải ảnh minh chứng lên Cloudflare R2. | Medium | 8 | Iteration 2 |
| Confirm P2P Handover & Report Issue API | P2P Handover | Direct Handover | API | Cụm 2 API (`POST /handover/confirm`, `POST /handover/issues`) xác nhận hoàn tất nhận đồ (`RETURNED`) hoặc ghi nhận sự cố bàn giao chuyển lên Manager. | Medium | 8 | Iteration 2 |
| Approver Receipt Queue Screen | Office Operations | Walk-in Intake | Screen | Màn hình hàng đợi phiếu tiếp nhận tại quầy dành cho Approver hỗ trợ tìm kiếm theo mã phiếu/tên đồ vật và lọc theo trạng thái lưu kho. | Medium | 10 | Iteration 2 |
| Approver Intake Form Screen | Office Operations | Walk-in Intake | Screen | Màn hình lập mới hoặc chỉnh sửa phiếu nháp tiếp nhận tài sản tại quầy gồm 3 phần: thông tin đồ vật, nơi/thời gian nhặt, đặc điểm bí mật và tối đa 5 ảnh. | Complex | 16 | Iteration 2 |
| Approver Intake Detail & Publish Screen | Office Operations | Walk-in Intake | Screen | Màn hình xem chi tiết biên bản tiếp nhận, kiểm tra vị trí kệ lưu trữ và xác nhận hoàn tất tiếp nhận để tự động xuất bản bài đăng Found công khai. | Complex | 12 | Iteration 2 |
| Approver Walk-in Receipt Draft CRUD API | Office Operations | Walk-in Intake | API | Cụm 5 API (`GET/POST/PUT /api/approver/receipts/*`, `GET /api/receipt-locations`) tạo/sửa phiếu nháp tiếp nhận tại quầy và kiểm tra khóa lạc quan. | Complex | 12 | Iteration 2 |
| Approver Confirm Intake & Publish Post API | Office Operations | Walk-in Intake | API | API (`POST /api/approver/receipts/{id}/submit`) xác nhận nhập kho (`IN_STORAGE`), sinh mã biên bản và tự động đăng bài Found công khai đã duyệt. | Medium | 8 | Iteration 2 |
| Office Claim Submission Form Screen | Office Operations | Office Claims | Screen | Màn hình gửi yêu cầu nhận lại tài sản đang lưu kho tại văn phòng (hỗ trợ cả người dùng gửi trực tuyến và Approver lập hộ tại quầy). | Complex | 12 | Iteration 2 |
| Office Claim List Screen | Office Operations | Office Claims | Screen | Màn hình danh sách yêu cầu nhận đồ tại văn phòng có bộ lọc trạng thái xử lý, tìm kiếm và theo dõi tiến độ lịch hẹn nhận đồ. | Medium | 10 | Iteration 2 |
| Office Claim Detail, Appointment & Return Screen | Office Operations | Office Claims | Screen | Màn hình xử lý toàn trình Claim văn phòng: xét duyệt sơ bộ, đặt lịch hẹn nhận đồ, ghi nhận kết quả xác minh tại quầy và lập biên bản xuất kho trao trả. | Complex | 18 | Iteration 2 |
| Submit & Resubmit Office Claim API | Office Operations | Office Claims | API | Cụm 4 API (`POST /api/office/claims`, `POST /posts/{postId}/office-claims`, `PUT /resubmit`, `POST /cancel`) nộp, gửi lại và hủy Claim văn phòng. | Complex | 12 | Iteration 2 |
| Review Office Claim & Schedule Appointment API | Office Operations | Office Claims | API | Cụm 5 API (`GET /api/office/claims/*`, `PATCH /review`, `POST /resend-invitation`) duyệt sơ bộ Claim văn phòng, tạo lịch hẹn nhận đồ và gửi Email mời. | Complex | 12 | Iteration 2 |
| Office Transfers Custody Screen | Office Operations | Custody Transfers | Screen | Màn hình quản lý các yêu cầu chuyển giao đồ nhặt được từ người dùng vào kho văn phòng, đặt lịch hẹn giao đồ và xác nhận tiếp nhận vào kệ lưu trữ. | Complex | 14 | Iteration 2 |
| Public Email Invitation Confirmation Screen (Claim) | Office Operations | 12h Invitation Token | Screen | Màn hình công khai (`/van-phong/lich-hen`) cho phép người nhận đồ bấm link trong Email (hiệu lực 12 giờ) để xác nhận hoặc từ chối lịch hẹn nhận đồ. | Medium | 6 | Iteration 2 |
| Public Email Invitation Confirmation Screen (Intake) | Office Operations | 12h Invitation Token | Screen | Màn hình công khai (`/van-phong/hen-giao-do`) cho phép người nhặt bấm link trong Email (hiệu lực 12 giờ) để xác nhận hoặc từ chối lịch hẹn giao đồ. | Medium | 6 | Iteration 2 |
| Office Custody Transfer & Intake Schedule API | Office Operations | Custody Transfers | API | Cụm 8 API (`/api/office/transfers/*`, `/intake-invitations/*`) đặt lịch hẹn nộp đồ, xác nhận qua token 12h và hoàn tất nhập kho từ bài đăng P2P. | Complex | 14 | Iteration 2 |
| Office Counter Verification & Handover Return API | Office Operations | Custody & Return | API | Cụm 12 API (`/api/office/claims/{id}/appointments`, `/verifications`, `/handover/*`, `/invitations/*`) xác minh tại quầy và lập biên bản xuất kho trao trả. | Complex | 16 | Iteration 2 |
| Office Appointment 12h Expiry Scheduler Batch | Office Operations | 12h Invitation Token | Batch | Tiến trình chạy ngầm định kỳ mỗi 60 giây (`OfficeInvitationScheduler`) tự động đánh dấu hết hạn (`EXPIRED`) các lời mời lịch hẹn quá 12 giờ chưa phản hồi. | Medium | 6 | Iteration 2 |
| Post Administration & Moderation Screen | Office & Moderation | Post Moderation | Screen | Màn hình quản trị toàn bộ bài đăng dành cho Manager hỗ trợ lọc theo trạng thái kiểm duyệt (`PENDING`, `VISIBLE`, `REJECTED`, `HIDDEN`) và loại bài. | Medium | 10 | Iteration 3 |
| Staff Post Moderation Detail Screen | Office & Moderation | Post Moderation | Screen | Màn hình chi tiết kiểm duyệt bài đăng cho phép Manager xem đầy đủ thông tin, dấu hiệu riêng tư và thực hiện Duyệt, Từ chối hoặc Ẩn bài đăng kèm lý do. | Medium | 8 | Iteration 3 |
| Storage & Shelf Location Management Screen | Office & Moderation | Storage Management | Screen | Màn hình quản lý kho lưu trữ văn phòng cho phép tra cứu tài sản đang lưu kho theo vị trí và thêm/sửa/bật/tắt các kệ/tủ lưu trữ (`storage_location`). | Complex | 12 | Iteration 3 |
| Manager Post Moderation API | Office & Moderation | Post Moderation | API | Cụm 3 API (`GET /api/office/posts`, `GET /api/office/posts/{id}`, `PATCH /moderation`) tra cứu và cập nhật quyết định kiểm duyệt bài đăng toàn hệ thống. | Medium | 8 | Iteration 3 |
| Storage Locations CRUD & Inventory Report | Office & Moderation | Storage Management | Report | Cụm 4 API & Báo cáo (`GET /api/office/storage-items`, `GET/POST/PUT /storage-locations`) thống kê tài sản tồn kho theo kệ/tủ và quản lý danh mục vị trí kho. | Medium | 8 | Iteration 3 |
| My Violation Reports List Screen | Reports & Disputes | User Reporting | Screen | Màn hình danh sách các báo cáo vi phạm (bài đăng hoặc người dùng) mà tôi đã gửi kèm trạng thái tiếp nhận và tiến độ điều tra của Manager. | Medium | 6 | Iteration 3 |
| My Violation Report Detail Screen | Reports & Disputes | User Reporting | Screen | Màn hình xem chi tiết nội dung báo cáo vi phạm đã gửi, danh sách tệp bằng chứng đính kèm và dòng thời gian xử lý (`report_activity`). | Medium | 6 | Iteration 3 |
| User Report Inquiry Exchanges Screen | Reports & Disputes | Investigation | Screen | Màn hình giải trình báo cáo cho phép người bị báo cáo hoặc người báo cáo phản hồi câu hỏi điều tra từ Manager kèm tệp bằng chứng và xem kết luận xử lý. | Complex | 12 | Iteration 3 |
| Manager Report Review & Investigation Screen | Reports & Disputes | Investigation | Screen | Màn hình điều tra chuyên sâu dành cho Manager: xem ảnh chụp nguyên trạng (`Snapshot`), phong tỏa giao nhận (`Hold`), gửi câu hỏi điều tra và ra phán quyết (`Conclusion`). | Complex | 18 | Iteration 3 |
| User Violation Report Submission API | Reports & Disputes | User Reporting | API | Cụm 3 API (`POST /api/reports`, `GET /api/me/reports`, `GET /api/me/reports/{id}`) gửi báo cáo vi phạm kèm bằng chứng và tự động chụp Snapshot ban đầu. | Medium | 10 | Iteration 3 |
| Manager Investigation, Hold & Conclusion API | Reports & Disputes | Investigation | API | Cụm 8 API (`/api/report-reviews/*`, `/api/me/report-exchanges/*`) nhận hồ sơ, phong tỏa/mở phong tỏa giao dịch, hỏi đáp 2 chiều và thực thi quyết định kỷ luật. | Complex | 18 | Iteration 3 |
| Violation Snapshot & Investigation Dossier Report | Reports & Disputes | Audit & Snapshot | Report | Chức năng trích xuất hồ sơ điều tra toàn diện gồm bản chụp cứng bài đăng/Claim/biên bản tại thời điểm báo cáo (`report_snapshot`) và nhật ký phán quyết. | Medium | 8 | Iteration 3 |
| In-App Notification Center & Preferences UI | Notifications | Multi-channel Alerts | Screen | Giao diện chuông thông báo thời gian thực, danh sách thông báo có phân trang, đánh dấu đã đọc, Web Notification API và bảng bật/tắt tùy chọn nhận tin/Email. | Complex | 12 | Iteration 3 |
| Multi-channel Notification & Preference API | Notifications | Multi-channel Alerts | API | Cụm 6 API (`/api/notifications/*`) quản lý thông báo trong ứng dụng, đếm tin chưa đọc, lưu tùy chọn kênh nhận và điều phối gửi Email/In-App tự động theo sự kiện. | Complex | 12 | Iteration 3 |

