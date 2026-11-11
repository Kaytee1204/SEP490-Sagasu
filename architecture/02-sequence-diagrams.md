# 02. Sơ Đồ Tuần Tự (Sequence Diagrams) Các Luồng Nghiệp Vụ Chính — Sagasu

⬅️ [Xem trước: 01. Thiết kế Cơ sở dữ liệu & ERD](./01-database-schema.md) | 🏠 [Trang chủ Kiến trúc](./README.md) | ➡️ [Xem tiếp: 03. Tài liệu Tổng hợp](./03-full-overview.md)

---

## Mục Lục Nhanh Các Luồng Nghiệp Vụ

1. [Luồng 1: Đăng nhập (Chuẩn hóa Email, Khóa sau 5 lần sai) & Quên mật khẩu qua OTP](#luồng-1-đăng-nhập-tự-động-chuẩn-hóa-email-khóa-sau-5-lần-sai--quên-mật-khẩu-qua-otp)
2. [Luồng 2: Đăng tin Đồ thất lạc / Nhặt được, Kiểm duyệt & Gợi ý Thông minh (Smart Match AI)](#luồng-2-đăng-tin-đồ-thất-lạc--nhặt-được-kiểm-duyệt--gợi-ý-thông-minh-smart-match-ai)
3. [Luồng 3: Xác minh quyền sở hữu P2P (Submit Claim + Questionnaire) & Mở phòng Chat](#luồng-3-xác-minh-quyền-sở-hữu-p2p-submit-claim--questionnaire--mở-phòng-chat)
4. [Luồng 4: Trò chuyện P2P thời gian thực (Text, Tệp đính kèm, Vị trí bản đồ & Đã xem)](#luồng-4-trò-chuyện-p2p-thời-gian-thực-text-tệp-đính-kèm-vị-trí-bản-đồ--đã-xem)
5. [Luồng 5: Bàn giao đồ P2P (Lập biên bản, Xác nhận hoàn tất hoặc Báo cáo sự cố)](#luồng-5-bàn-giao-đồ-p2p-lập-biên-bản-xác-nhận-hoàn-tất-hoặc-báo-cáo-sự-cố)
6. [Luồng 6: Nghiệp vụ Văn phòng (Chuyển giao / Lưu kho -> Đặt lịch hẹn Email Token 12h -> Xác minh & Trao trả tại quầy)](#luồng-6-nghiệp-vụ-văn-phòng-chuyển-giao--lưu-kho---đặt-lịch-hẹn-email-token-12h---xác-minh--trao-trả-tại-quầy)
7. [Luồng 7: Báo cáo vi phạm, Điều tra của Manager (Chụp Snapshot, Phong tỏa bài đăng, Hỏi đáp Inquiry) & Quyết định xử lý](#luồng-7-báo-cáo-vi-phạm-điều-tra-của-manager-chụp-snapshot-phong-tỏa-bài-đăng-hỏi-đáp-inquiry--quyết-định-xử-lý)

---

## Luồng 1: Đăng nhập (Tự động chuẩn hóa Email, Khóa sau 5 lần sai) & Quên mật khẩu qua OTP

- **Actors**: Người dùng (`User`), Hệ thống Email (`EmailService`)
- **Bảng CSDL tác động**: `accounts`, `refresh_tokens`, `otp_challenges`, `login_audit_logs`
- **Quy tắc nghiệp vụ chính**:
  - Email đăng nhập không phân biệt hoa thường (`normalizeEmail`).
  - Sai mật khẩu hiển thị cảnh báo chữ đỏ ngay tại trang Login kèm số lần thử còn lại (`5 - failed_login_count`).
  - Nhập sai 5 lần liên tiếp sẽ khóa tài khoản tạm thời 15 phút (`locked_until`).
  - Người dùng lấy lại quyền truy cập qua luồng **Quên mật khẩu** với mã OTP 6 số gửi qua Email.

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng
    participant FE as Frontend (LoginPage / ForgotPassword)
    participant AuthCtrl as AuthController
    participant AuthSvc as AuthService
    participant DB as PostgreSQL (accounts / otp_challenges)
    participant Mail as EmailService

    rect rgb(240, 248, 255)
    note over User, DB: 1A. Đăng nhập & Kiểm tra khóa tài khoản (5 lần sai)
    User->>FE: Nhập Email (hoa/thường) & Mật khẩu
    FE->>AuthCtrl: POST /api/auth/login {identifier, password}
    AuthCtrl->>AuthSvc: login(request, ip, userAgent)
    AuthSvc->>AuthSvc: normalizeEmail(identifier.trim().toLowerCase())
    AuthSvc->>DB: findByEmailIgnoreCase(email)
    alt Tài khoản đang bị khóa (locked_until > now)
        AuthSvc-->>FE: 423 LOCKED ("Tài khoản đang bị tạm khóa...")
        FE-->>User: Hiển thị khung chữ đỏ báo thời gian mở khóa
    else Mật khẩu không khớp
        AuthSvc->>DB: Tăng failed_login_count + 1
        alt failed_login_count >= 5
            AuthSvc->>DB: Set locked_until = now + 15 phút
            AuthSvc-->>FE: 423 LOCKED ("Nhập sai 5 lần, khóa 15 phút")
        else failed_login_count < 5
            AuthSvc-->>FE: 401 UNAUTHORIZED ("Còn X lần thử trước khi khóa")
        end
        FE-->>User: Hiển thị thông báo lỗi chữ đỏ ngay tại form Login
    else Đăng nhập thành công
        AuthSvc->>DB: Reset failed_login_count = 0, locked_until = null
        AuthSvc->>DB: Lưu refresh_tokens & login_audit_logs
        AuthSvc-->>FE: 200 OK {accessToken, refreshToken, user}
        FE-->>User: Chuyển hướng vào trang chủ
    end
    end

    rect rgb(255, 250, 240)
    note over User, Mail: 1B. Quên mật khẩu & Đặt lại mật khẩu qua OTP
    User->>FE: Chọn "Quên mật khẩu?" & nhập Email
    FE->>AuthCtrl: POST /api/auth/forgot-password {email}
    AuthCtrl->>AuthSvc: forgotPassword(email)
    AuthSvc->>DB: Tạo bản ghi otp_challenges (purpose = FORGOT_PASSWORD)
    AuthSvc->>Mail: Gửi mã OTP 6 số qua Email
    AuthSvc-->>FE: 200 OK
    User->>FE: Nhập mã OTP + Mật khẩu mới
    FE->>AuthCtrl: POST /api/auth/reset-password {email, otp, newPassword}
    AuthCtrl->>AuthSvc: resetPassword(...)
    AuthSvc->>DB: Xác thực OTP, cập nhật password_hash, xóa khóa locked_until
    AuthSvc-->>FE: 200 OK ("Đặt lại mật khẩu thành công")
    end
```

---

## Luồng 2: Đăng tin Đồ thất lạc / Nhặt được, Kiểm duyệt & Gợi ý Thông minh (Smart Match AI)

- **Actors**: Người đăng tin (`Author`), Kiểm duyệt viên (`Approver`)
- **Bảng CSDL tác động**: `category`, `post`, `post_image`, `post_creation_request`, `notification`
- **Quy tắc nghiệp vụ chính**:
  - Hỗ trợ lưu nháp trên trình duyệt (`localStorage` theo tài khoản) và chống gửi trùng lặp bằng khóa `X-Request-Id` (`post_creation_request`).
  - Tải ảnh lên **Cloudflare R2** (`post_image`).
  - Khi bài đăng được duyệt (`VISIBLE`), hệ thống hỗ trợ tìm kiếm các bài đăng đối ứng (`LOST` <-> `FOUND`) bằng PostgreSQL Full-Text Search kết hợp chấm điểm tương đồng (`SmartMatchService`) và gửi thông báo cho chủ bài.

```mermaid
sequenceDiagram
    autonumber
    actor Author as Người đăng tin
    actor Approver as Kiểm duyệt viên (Approver)
    participant FE as Frontend (CreatePostPage)
    participant PostCtrl as PostController / ModerationController
    participant PostSvc as PostCreationService / ModerationService
    participant R2 as Cloudflare R2 Storage
    participant DB as PostgreSQL (post / post_image)
    participant MatchSvc as SmartMatchService (AI / FTS)
    participant NotifSvc as NotificationService

    Author->>FE: Nhập thông tin bài đăng + Chọn ảnh (Tự động lưu nháp)
    FE->>PostCtrl: POST /api/posts (Header: X-Request-Id, Multipart/JSON)
    PostCtrl->>PostSvc: createPost(accountId, requestId, payload, files)
    PostSvc->>DB: Kiểm tra Idempotency trong post_creation_request
    PostSvc->>R2: Upload ảnh bài đăng -> nhận storage_key
    PostSvc->>DB: INSERT post (status=OPEN, moderation_status=PENDING) & post_image
    PostSvc->>DB: INSERT post_creation_request (account_id, request_id, post_id)
    PostSvc-->>FE: 201 CREATED (PostDetailResponse)
    FE->>FE: Xóa bản nháp trên trình duyệt

    rect rgb(240, 255, 240)
    note over Approver, NotifSvc: Kiểm duyệt bài đăng & Gợi ý bài đăng trùng khớp
    Approver->>PostCtrl: PATCH /api/moderation/posts/{postId}/approve
    PostCtrl->>PostSvc: approvePost(postId, approverId)
    PostSvc->>DB: UPDATE post SET moderation_status = 'VISIBLE'
    PostSvc->>NotifSvc: notify(Author, POST_APPROVED, IN_APP)
    
    Author->>FE: Xem chi tiết bài đăng & mục "Bài đăng tương tự"
    FE->>PostCtrl: GET /api/posts/{postId}/matches
    PostCtrl->>MatchSvc: findSmartMatches(postId)
    MatchSvc->>DB: Truy vấn ứng viên ngược loại (LOST <-> FOUND) cùng danh mục / từ khóa
    MatchSvc->>MatchSvc: Chấm điểm tương đồng (Tiêu đề, Mô tả, Vị trí, Thời gian / AI Rerank)
    MatchSvc-->>FE: Danh sách bài đăng gợi ý phù hợp nhất
    end
```

---

## Luồng 3: Xác minh quyền sở hữu P2P (Submit Claim + Questionnaire) & Mở phòng Chat

- **Actors**: Người nhận đồ (`Claimant`), Người nhặt đồ (`Finder`)
- **Bảng CSDL tác động**: `claim_question_template`, `claim_question`, `claim`, `claim_answer`, `claim_evidence`, `claim_activity`, `chat_conversation`, `chat_participant`, `notification`
- **Quy tắc nghiệp vụ chính**:
  - Người nhận đồ (Claimant) phải trả lời **Bộ câu hỏi xác minh theo danh mục** (`claim_question_template`) và đính kèm ảnh bằng chứng (`claim_evidence`).
  - Người nhặt được đồ (Finder) xem danh sách Claim được gom nhóm theo bài đăng, đối chiếu câu trả lời với `private_identifiers`: có thể **Yêu cầu bổ sung thông tin (`NEEDS_INFO`)**, **Từ chối (`REJECTED`)**, hoặc **Chấp nhận (`APPROVED`)**.
  - Khi một Claim được `APPROVED`, bài đăng chuyển sang `RESERVED` và hệ thống tự động khởi tạo/kích hoạt phòng **Chat P2P** (`chat_conversation`).

```mermaid
sequenceDiagram
    autonumber
    actor Claimant as Người nhận đồ (Claimant)
    actor Finder as Người nhặt đồ (Finder)
    participant FE as Frontend
    participant ClaimCtrl as ClaimController
    participant ClaimSvc as ClaimService
    participant ChatSvc as ChatConversationService
    participant DB as PostgreSQL (claim / chat_conversation)
    participant NotifSvc as NotificationService

    Claimant->>FE: Mở bài đăng FOUND -> Bấm "Yêu cầu nhận lại đồ"
    FE->>ClaimCtrl: GET /api/posts/{postId}/claim-questionnaire
    ClaimCtrl->>DB: Lấy bộ câu hỏi theo Category của bài đăng
    ClaimCtrl-->>FE: Danh sách câu hỏi xác minh
    
    Claimant->>FE: Điền câu trả lời + Tải ảnh bằng chứng
    FE->>ClaimCtrl: POST /api/posts/{postId}/claims
    ClaimCtrl->>ClaimSvc: submitClaim(postId, claimantId, answers, evidences)
    ClaimSvc->>DB: INSERT claim (status=PENDING), claim_answer, claim_evidence, claim_activity
    ClaimSvc->>NotifSvc: notify(Finder, CLAIM_NEW, IN_APP)
    ClaimSvc-->>FE: 201 CREATED

    Finder->>FE: Xem danh sách Claim được gom nhóm theo Bài đăng
    alt Thông tin chưa rõ ràng
        Finder->>ClaimCtrl: POST /api/claims/{claimId}/request-info {reason}
        ClaimCtrl->>ClaimSvc: requestMoreInfo(claimId, finderId, reason)
        ClaimSvc->>DB: UPDATE claim SET status = 'NEEDS_INFO'
        ClaimSvc->>NotifSvc: notify(Claimant, CLAIM_NEEDS_INFO, BOTH: App + Email)
        Claimant->>ClaimCtrl: PUT /api/claims/{claimId}/resubmit
        ClaimCtrl->>ClaimSvc: UPDATE claim SET status = 'PENDING'
    else Xác minh đúng chủ sở hữu
        Finder->>ClaimCtrl: POST /api/claims/{claimId}/approve
        ClaimCtrl->>ClaimSvc: approveClaim(claimId, finderId)
        ClaimSvc->>DB: UPDATE claim SET status = 'APPROVED'
        ClaimSvc->>DB: UPDATE post SET status = 'RESERVED'
        ClaimSvc->>ChatSvc: ensureConversationExists(postId, claimantId, claimId)
        ChatSvc->>DB: INSERT/UPDATE chat_conversation & chat_participant
        ClaimSvc->>NotifSvc: notify(Claimant, CLAIM_APPROVED, BOTH: App + Email)
        ClaimSvc-->>FE: 200 OK (Mở khóa tính năng Chat & Bàn giao)
    end
```

---

## Luồng 4: Trò chuyện P2P thời gian thực (Text, Tệp đính kèm, Vị trí bản đồ & Đã xem)

- **Actors**: Người gửi (`Sender`), Người nhận (`Receiver`)
- **Bảng CSDL tác động**: `chat_conversation`, `chat_participant`, `chat_message`, `chat_attachment`, `notification`
- **Quy tắc nghiệp vụ chính**:
  - Hỗ trợ gửi tin nhắn văn bản (`TEXT`), đính kèm ảnh/PDF (`ATTACHMENT` / `MIXED`), và chia sẻ tọa độ điểm hẹn trên bản đồ (`LOCATION` với `latitude`, `longitude`, `location_label`).
  - Đồng bộ thời gian thực qua **WebSocket STOMP** (`/ws/chat`) kèm thông báo đẩy trên trình duyệt (Browser Notification) khi tab đang ẩn.

```mermaid
sequenceDiagram
    autonumber
    actor Sender as Người gửi (Finder / Claimant)
    actor Receiver as Người nhận
    participant FE as Frontend (ChatPage + STOMP Client)
    participant ChatCtrl as ChatController
    participant ChatMsgSvc as ChatMessageService
    participant R2 as Cloudflare R2
    participant DB as PostgreSQL (chat_message)
    participant WS as STOMP Broker (/user/queue/chat)
    participant NotifSvc as NotificationService

    opt Gửi kèm hình ảnh / tài liệu
        Sender->>FE: Chọn tệp đính kèm
        FE->>ChatCtrl: POST /api/chat/conversations/{id}/attachments
        ChatCtrl->>R2: Upload tệp -> lưu tạm metadata
        ChatCtrl-->>FE: Trả về danh sách uploadedAttachments
    end

    Sender->>FE: Nhập tin nhắn hoặc Chọn vị trí trên Bản đồ (Leaflet)
    FE->>ChatCtrl: POST /api/chat/conversations/{id}/messages {type, body, lat, lng, clientMessageId}
    ChatCtrl->>ChatMsgSvc: sendMessage(conversationId, senderId, request)
    ChatMsgSvc->>DB: Kiểm tra client_message_id (Idempotency)
    ChatMsgSvc->>DB: Tăng last_message_sequence, INSERT chat_message & chat_attachment
    ChatMsgSvc->>WS: Phát sự kiện MESSAGE_CREATED tới /user/{receiver}/queue/chat
    ChatMsgSvc->>NotifSvc: notify(Receiver, CHAT_NEW_MESSAGE, IN_APP + Browser Push)
    ChatMsgSvc-->>FE: 201 CREATED (MessageDto)

    WS-->>Receiver: Hiển thị tin nhắn / Bản đồ vị trí ngay lập tức
    Receiver->>FE: Mở khung chat đọc tin nhắn
    FE->>ChatCtrl: POST /api/chat/conversations/{id}/read {lastReadSequence}
    ChatCtrl->>DB: UPDATE chat_participant SET last_read_sequence, UPDATE chat_message SET read_at
    ChatCtrl->>WS: Phát sự kiện READ_RECEIPT tới Sender ("Đã xem")
```

---

## Luồng 5: Bàn giao đồ P2P (Lập biên bản, Xác nhận hoàn tất hoặc Báo cáo sự cố)

- **Actors**: Người nhặt đồ (`Finder`), Người nhận đồ (`Claimant`)
- **Bảng CSDL tác động**: `p2p_handover`, `p2p_handover_evidence`, `p2p_handover_issue`, `claim`, `post`, `report`, `report_hold`
- **Quy tắc nghiệp vụ chính**:
  - Trước khi thao tác, `ReportHoldGuard` kiểm tra xem bài đăng có đang bị phong tỏa do tranh chấp hay không.
  - Người nhặt đồ lập biên bản bàn giao kèm ảnh minh chứng (`WAITING_CONFIRMATION`).
  - Người nhận đồ xác nhận đã nhận (`COMPLETED` -> bài đăng chuyển sang `RETURNED`, tự động đóng các claim khác), hoặc báo cáo sự cố (`DISPUTED` -> tạo phiếu `report` loại `HANDOVER_ISSUE` để Manager xử lý).

```mermaid
sequenceDiagram
    autonumber
    actor Finder as Người nhặt đồ (Finder)
    actor Claimant as Người nhận đồ (Claimant)
    participant HndCtrl as P2pHandoverController
    participant HndSvc as P2pHandoverService
    participant Guard as ReportHoldGuard
    participant R2 as Cloudflare R2
    participant DB as PostgreSQL (p2p_handover / post / claim)
    participant RptSvc as ReportService
    participant NotifSvc as NotificationService

    Finder->>HndSvc: POST /api/claims/{claimId}/handover (note + ảnh bằng chứng)
    HndSvc->>Guard: assertPostNotOnHold(postId)
    HndSvc->>R2: Lưu ảnh bằng chứng bàn giao
    HndSvc->>DB: INSERT p2p_handover (status = WAITING_CONFIRMATION) & p2p_handover_evidence
    HndSvc->>NotifSvc: notify(Claimant, HANDOVER_RECORDED, BOTH: App + Email)
    HndSvc-->>Finder: 201 CREATED (Chờ người nhận xác nhận)

    alt Người nhận xác nhận đã nhận đúng đồ
        Claimant->>HndSvc: POST /api/claims/{claimId}/handover/confirm
        HndSvc->>Guard: assertPostNotOnHold(postId)
        HndSvc->>DB: UPDATE p2p_handover SET status = 'COMPLETED', confirmed_at = now()
        HndSvc->>DB: UPDATE claim SET status = 'COMPLETED'
        HndSvc->>DB: UPDATE post SET status = 'RETURNED'
        HndSvc->>DB: Tự động chuyển các Claim khác của bài đăng sang CLOSED (AUTO_CLOSE)
        HndSvc->>NotifSvc: notify(Finder, HANDOVER_COMPLETED, IN_APP)
        HndSvc-->>Claimant: 200 OK (Hoàn tất trao trả)
    else Phát sinh tranh chấp / sai lệch khi bàn giao
        Claimant->>HndSvc: POST /api/claims/{claimId}/handover/report-issue {description, files}
        HndSvc->>DB: UPDATE p2p_handover SET status = 'DISPUTED'
        HndSvc->>RptSvc: Tạo Report (kind = HANDOVER_ISSUE) + liên kết p2p_handover_issue
        HndSvc->>NotifSvc: notify(Finder & Manager, HANDOVER_DISPUTED, BOTH: App + Email)
        HndSvc-->>Claimant: 201 CREATED (Chuyển sang quy trình Giải quyết tranh chấp)
    end
```

---

## Luồng 6: Nghiệp vụ Văn phòng (Chuyển giao / Lưu kho -> Đặt lịch hẹn Email Token 12h -> Xác minh & Trao trả tại quầy)

- **Actors**: Người nhặt đồ (`Finder`), Nhân viên Văn phòng (`Officer / Approver`), Người nhận đồ (`Claimant`)
- **Bảng CSDL tác động**: `storage_location`, `item_receipt`, `item_receipt_image`, `office_transfer`, `office_intake_appointment`, `appointment`, `office_claim_verification`, `office_handover`, `office_handover_evidence`
- **Quy tắc nghiệp vụ chính**:
  - Hỗ trợ 2 nguồn đồ vào kho Văn phòng: **Người dùng đăng bài P2P rồi xin chuyển cho Văn phòng (`office_transfer`)** hoặc **Mang đồ đến nộp trực tiếp tại quầy (`WALK_IN`)**.
  - Mọi lịch hẹn (Nộp đồ vào văn phòng hoặc Nhận lại đồ từ văn phòng) đều gửi **Email kèm liên kết Token có thời hạn phản hồi 12 giờ** (`CONFIRMED` / `DECLINED` / tự động `EXPIRED` qua Cron Job).
  - Khi trao trả tại quầy, nhân viên thực hiện bước **Xác minh trực tiếp (`office_claim_verification`)** trước khi lập biên bản xuất kho (`office_handover`).

```mermaid
sequenceDiagram
    autonumber
    actor Finder as Người nhặt đồ
    actor Officer as Nhân viên Văn phòng (Approver)
    actor Claimant as Người nhận đồ
    participant OffCtrl as OfficeWorkflowController / OfficeClaimController
    participant OffSvc as OfficeTransferService / OfficeReturnService
    participant Mail as AppointmentEmailService
    participant DB as PostgreSQL (item_receipt / appointment / office_handover)
    participant NotifSvc as NotificationService

    rect rgb(240, 248, 255)
    note over Finder, DB: 6A. Chuyển giao đồ từ bài đăng P2P vào Kho Văn phòng
    Finder->>OffCtrl: POST /api/posts/{postId}/office-transfers {reason}
    OffCtrl->>DB: INSERT office_transfer (status = PENDING)
    Officer->>OffCtrl: POST /api/office/transfers/{transferId}/appointments {scheduledAt, location}
    OffCtrl->>DB: INSERT office_intake_appointment (response_status = PENDING, deadline = +12h)
    OffCtrl->>Mail: Gửi Email mời mang đồ đến Văn phòng (kèm link Xác nhận / Từ chối)
    Finder->>OffCtrl: GET /api/public/office-intake-appointments/respond?token=...&action=CONFIRM
    OffCtrl->>DB: UPDATE office_intake_appointment SET response_status = 'CONFIRMED'
    Officer->>OffCtrl: POST /api/office/transfers/{transferId}/receive {storageLocationId, condition}
    OffCtrl->>DB: INSERT item_receipt (status = IN_STORAGE), UPDATE post SET custody_mode = 'OFFICE'
    end

    rect rgb(255, 250, 240)
    note over Officer, NotifSvc: 6B. Duyệt Claim Văn phòng, Hẹn lịch đến nhận & Xác minh trao trả tại quầy
    Claimant->>OffCtrl: Nộp Claim cho bài đăng thuộc quản lý Văn phòng (OFFICE)
    Officer->>OffCtrl: Duyệt sơ bộ Claim & Tạo lịch hẹn nhận đồ (POST /api/office/claims/{id}/appointments)
    OffCtrl->>DB: INSERT appointment (status = SCHEDULED, response_status = PENDING, deadline = +12h)
    OffCtrl->>Mail: Gửi Email mời Claimant đến quầy (kèm link xác nhận trong 12h)
    OffCtrl->>NotifSvc: notify(Claimant, APPOINTMENT_INVITATION, BOTH: App + Email)
    Claimant->>OffCtrl: Xác nhận tham gia lịch hẹn qua link Email (CONFIRMED)

    note over Officer, Claimant: Tại quầy giao dịch Văn phòng
    Officer->>OffCtrl: POST /api/office/claims/{id}/verifications {result: PASSED, note}
    OffCtrl->>DB: INSERT office_claim_verification (result = PASSED)
    Officer->>OffCtrl: POST /api/office/claims/{id}/handover (Kèm ảnh chụp biên bản bàn giao tại quầy)
    OffCtrl->>DB: INSERT office_handover (status = COMPLETED/WAITING_CONFIRMATION)
    OffCtrl->>DB: UPDATE item_receipt SET status = 'RETURNED', post SET status = 'RETURNED'
    OffCtrl->>NotifSvc: notify(Claimant & Finder, OFFICE_RETURN_COMPLETED, IN_APP)
    end
```

---

## Luồng 7: Báo cáo vi phạm, Điều tra của Manager (Chụp Snapshot, Phong tỏa bài đăng, Hỏi đáp Inquiry) & Quyết định xử lý

- **Actors**: Người báo cáo (`Reporter`), Quản lý (`Manager`), Bên liên quan (`Party`: Finder/Claimant)
- **Bảng CSDL tác động**: `report`, `report_evidence`, `report_activity`, `report_snapshot`, `report_asset`, `report_inquiry`, `report_reply_evidence`, `report_decision`, `report_hold`, `report_recipient`
- **Quy tắc nghiệp vụ chính**:
  - Ngay khi báo cáo được gửi hoặc khi Manager bắt đầu xem xét (`IN_REVIEW`), hệ thống tự động chụp **Snapshot toàn bộ ngữ cảnh** (`report_snapshot` & `report_asset`) để tránh việc các bên sửa/xóa dữ liệu phi tang.
  - Manager có thể bật **Phong tỏa bài đăng (`report_hold`)**, gửi **Câu hỏi điều tra (`report_inquiry`)** yêu cầu các bên giải trình kèm bằng chứng (`report_reply_evidence`), và ban hành **Quyết định xử lý (`report_decision`)** tự động cập nhật trạng thái Bài đăng / Claim / Handover / Tài khoản.

```mermaid
sequenceDiagram
    autonumber
    actor Reporter as Người báo cáo
    actor Manager as Quản lý (Manager)
    actor Party as Bên liên quan (Finder / Claimant)
    participant RptCtrl as ReportController / ReportReviewController
    participant RptSvc as ReportService / ReportContextService
    participant RevSvc as ReportReviewService / ReportInquiryService
    participant DB as PostgreSQL (report / report_snapshot / report_hold / report_decision)
    participant NotifSvc as NotificationService

    Reporter->>RptCtrl: POST /api/reports {kind, postId/claimId/messageId, description, files}
    RptCtrl->>RptSvc: createReport(reporterId, request)
    RptSvc->>DB: INSERT report (status = OPEN) & report_evidence
    RptSvc->>DB: Chụp ngữ cảnh ban đầu vào report_snapshot (JSONB) & đóng băng report_asset
    RptSvc-->>Reporter: 201 CREATED

    Manager->>RptCtrl: POST /api/manager/reports/{reportId}/start-review
    RptCtrl->>RevSvc: startReview(reportId, managerId)
    RevSvc->>DB: UPDATE report SET status = 'IN_REVIEW', assigned_to = managerId

    opt Phong tỏa tạm thời bài đăng để điều tra
        Manager->>RptCtrl: POST /api/manager/reports/{reportId}/hold {reason}
        RptCtrl->>RevSvc: placeHold(reportId, managerId, reason)
        RevSvc->>DB: INSERT report_hold (Chặn mọi thao tác sửa/xóa/bàn giao trên bài đăng)
    end

    opt Gửi yêu cầu giải trình (Inquiry) cho các bên
        Manager->>RptCtrl: POST /api/manager/reports/{reportId}/inquiries {recipientId, question}
        RptCtrl->>RevSvc: createInquiry(...)
        RevSvc->>DB: INSERT report_inquiry
        RevSvc->>NotifSvc: notify(Party, REPORT_INQUIRY, BOTH: App + Email)
        
        Party->>RptCtrl: POST /api/reports/{reportId}/inquiries/{inquiryId}/reply {response, files}
        RptCtrl->>RevSvc: replyInquiry(...)
        RevSvc->>DB: UPDATE report_inquiry SET response, INSERT report_reply_evidence
        RevSvc->>NotifSvc: notify(Manager, REPORT_INQUIRY_REPLIED, IN_APP)
    end

    Manager->>RptCtrl: POST /api/manager/reports/{reportId}/resolve {action, conclusion, basis}
    RptCtrl->>RevSvc: resolveReport(reportId, managerId, decisionRequest)
    RevSvc->>DB: Ghi nhận before_state -> Thực thi hành động (Ẩn bài / Hủy Claim / Hoàn tất Handover / Khóa TK) -> Ghi nhận after_state
    RevSvc->>DB: INSERT report_decision, UPDATE report SET status = 'RESOLVED'
    RevSvc->>DB: Giải phóng report_hold (nếu có)
    RevSvc->>NotifSvc: notify(Reporter & Party, REPORT_RESOLVED, BOTH: App + Email)
    RevSvc-->>Manager: 200 OK (Hoàn tất giải quyết báo cáo)
```

---

⬅️ [Xem trước: 01. Thiết kế Cơ sở dữ liệu & ERD](./01-database-schema.md) | 🏠 [Trang chủ Kiến trúc](./README.md) | ➡️ [Xem tiếp: 03. Tài liệu Tổng hợp](./03-full-overview.md)
