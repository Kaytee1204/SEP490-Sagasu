package com.example.base.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    HANDOVER_NOT_FOUND(HttpStatus.NOT_FOUND,"HANDOVER_404","Không tìm thấy bàn giao P2P của bạn"),
    HANDOVER_STATE_CHANGED(HttpStatus.CONFLICT,"HANDOVER_409_STATE","Trạng thái bàn giao đã thay đổi hoặc thao tác không còn được phép. Vui lòng tải lại hồ sơ"),
    WORKFLOW_INPUT_INVALID(HttpStatus.BAD_REQUEST,"WORKFLOW_400","Thông tin không hợp lệ. Vui lòng kiểm tra và gửi lại"),
    WORKFLOW_DUPLICATE_PAYLOAD(HttpStatus.CONFLICT,"WORKFLOW_409_RETRY","Yêu cầu đã gửi có nội dung khác. Vui lòng tải lại"),
    REPORT_DUPLICATE(HttpStatus.CONFLICT,"REPORT_409_OPEN","Bạn đã có báo cáo đang chờ xử lý cho đối tượng này"),
    REPORT_REASON_INVALID(HttpStatus.BAD_REQUEST,"REPORT_400_REASON","Vui lòng chọn lý do phù hợp với loại báo cáo"),
    REPORT_DESCRIPTION_REQUIRED(HttpStatus.BAD_REQUEST,"REPORT_400_DESCRIPTION","Vui lòng mô tả chi tiết lý do báo cáo"),
    REPORT_SELF_POST_NOT_ALLOWED(HttpStatus.BAD_REQUEST,"REPORT_400_SELF","Bạn không thể tự báo cáo bài đăng của mình"),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND,"REPORT_404","Không tìm thấy báo cáo của bạn"),
    REPORT_TARGET_UNAVAILABLE(HttpStatus.NOT_FOUND,"REPORT_404_TARGET","Không tìm thấy đối tượng hoặc bạn không có quyền báo cáo đối tượng này"),
    CHAT_ADDRESS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_503_ADDRESS", "Chưa lấy được tên địa chỉ. Vị trí đã chọn vẫn được giữ nguyên"),
    CHAT_ATTACHMENT_INVALID(HttpStatus.BAD_REQUEST, "CHAT_400_FILE", "Tệp phải là JPG, PNG hoặc PDF hợp lệ và nằm trong giới hạn chat"),
    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_404", "Không tìm thấy cuộc trò chuyện được phép truy cập"),
    CHAT_UNAVAILABLE(HttpStatus.CONFLICT, "CHAT_409_STATE", "Chỉ được nhắn tin sau khi claim P2P được chấp thuận và bài đang chờ trao trả"),
    CHAT_INPUT_INVALID(HttpStatus.BAD_REQUEST, "CHAT_400", "Nội dung hoặc con trỏ tin nhắn không hợp lệ"),
    CHAT_DUPLICATE_PAYLOAD(HttpStatus.CONFLICT, "CHAT_409_RETRY", "Mã gửi tin đã được dùng cho nội dung khác"),
    CLAIM_QUESTIONNAIRE_INVALID(HttpStatus.BAD_REQUEST, "CLAIM_400_ANSWERS", "Câu trả lời claim chưa hợp lệ"),
    CLAIM_QUESTIONNAIRE_STALE(HttpStatus.CONFLICT, "CLAIM_409_QUESTIONS", "Bộ câu hỏi đã thay đổi. Vui lòng mở lại form claim"),
    CLAIM_QUESTIONNAIRE_UNAVAILABLE(HttpStatus.CONFLICT, "CLAIM_409_NO_QUESTIONS", "Chưa có bộ câu hỏi khả dụng cho claim"),
    CLAIM_NOT_FOUND(HttpStatus.NOT_FOUND, "CLAIM_404", "Không tìm thấy hồ sơ claim được phép truy cập"),
    CLAIM_POST_UNAVAILABLE(HttpStatus.CONFLICT, "CLAIM_409_POST", "Chỉ có thể claim bài Found đang hiển thị, còn mở và thuộc P2P hoặc đã được phòng ban xác nhận tiếp nhận"),
    CLAIM_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CLAIM_400_SELF", "Không thể claim bài đăng của chính mình"),
    CLAIM_DUPLICATE(HttpStatus.CONFLICT, "CLAIM_409_DUPLICATE", "Bạn đã có claim đang xử lý cho bài này. Chỉ được gửi hồ sơ mới sau khi bị từ chối hoặc đã hủy"),
    CLAIM_STATE_INVALID(HttpStatus.CONFLICT, "CLAIM_409_STATE", "Claim hoặc bài đăng đã đổi trạng thái. Vui lòng tải lại"),
    CLAIM_STALE(HttpStatus.CONFLICT, "CLAIM_409_STALE", "Hồ sơ đã được xử lý ở phiên khác. Vui lòng tải lại"),
    CLAIM_INPUT_INVALID(HttpStatus.BAD_REQUEST, "CLAIM_400_INPUT", "Nội dung, lý do, trạng thái lọc hoặc phiên bản claim không hợp lệ"),
    CLAIM_POST_EDIT_BLOCKED(HttpStatus.CONFLICT, "CLAIM_409_EDIT", "Bài đã có claim nên không thể chỉnh sửa nội dung"),
    CLAIM_POST_DELETE_BLOCKED(HttpStatus.CONFLICT, "CLAIM_409_DELETE", "Không thể xóa bài khi còn claim đang chờ hoặc đã được chấp thuận"),
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "RECEIPT_404", "Không tìm thấy phiếu của bạn"),
    RECEIPT_NOT_EDITABLE(HttpStatus.CONFLICT, "RECEIPT_409_STATE", "Chỉ phiếu nháp mới được chỉnh sửa hoặc gửi duyệt. Vui lòng tải lại phiếu."),
    RECEIPT_STALE(HttpStatus.CONFLICT, "RECEIPT_409_STALE", "Phiếu đã thay đổi ở phiên khác. Vui lòng tải lại trước khi tiếp tục."),
    POST_SEARCH_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "POST_400_SEARCH_TIME_RANGE",
            "Thời gian phải là 24H, 7D hoặc CUSTOM; không kết hợp mốc tương đối với khoảng ngày tùy chỉnh"),
    POST_SEARCH_FILTER_INVALID(HttpStatus.BAD_REQUEST, "POST_400_SEARCH_FILTER",
            "Bộ lọc không hợp lệ: loại bài LOST/FOUND, mã danh mục dương, từ khóa tối đa 200 ký tự, địa điểm tối đa 1000 ký tự"),
    POST_SEARCH_DATE_INVALID(HttpStatus.BAD_REQUEST, "POST_400_SEARCH_DATE",
            "Ngày phải có dạng yyyy-MM-dd; ngày bắt đầu không được sau ngày kết thúc"),
    POST_HANDOVER_TIME_DISABLED(HttpStatus.BAD_REQUEST, "POST_400_HANDOVER_TIME_DISABLED", "Hiện không hỗ trợ đặt lịch giao đồ"),
    POST_CUSTODY_CHANGE_REQUIRES_HANDOVER(HttpStatus.BAD_REQUEST, "POST_400_CUSTODY_CHANGE",
            "Không đổi luồng trong form sửa bài. Dùng nút Giao cho bên tiếp nhận tại chi tiết bài đăng"),
    POST_OFFICE_HANDOVER_UNAVAILABLE(HttpStatus.CONFLICT, "POST_409_OFFICE_HANDOVER",
            "Chỉ gửi yêu cầu tiếp nhận cho bài nhặt được tự giữ, đang hiển thị và còn mở"),
    POST_OFFICE_HANDOVER_CLAIM_BLOCKED(HttpStatus.CONFLICT, "POST_409_OFFICE_HANDOVER_CLAIM",
            "Chưa thể giao bên tiếp nhận khi bài còn claim đang chờ hoặc đã được chấp thuận"),
    POST_NOT_EDITABLE(HttpStatus.CONFLICT, "POST_409_STATE", "Bài đã xóa, bị ẩn hoặc không còn mở để chỉnh sửa"),
    POST_STALE(HttpStatus.CONFLICT, "POST_409_STALE", "Bài đăng đã thay đổi. Vui lòng tải lại trước khi lưu"),
    POST_IMAGES_INVALID(HttpStatus.BAD_REQUEST, "POST_400_IMAGES",
            "Danh sách ảnh không hợp lệ hoặc ảnh không thuộc bài đăng"),
    POST_FILTER_INVALID(HttpStatus.BAD_REQUEST, "POST_400_FILTER", "Bộ lọc bài đăng không hợp lệ"),
    POST_CUSTODY_INVALID(HttpStatus.BAD_REQUEST, "POST_400_CUSTODY", "Bài mới mặc định là P2P; giao bên tiếp nhận sau khi đăng thành công"),
    POST_OWNER_REQUIRED(HttpStatus.NOT_FOUND, "POST_404_OWNER", "Không tìm thấy bài đăng thuộc tài khoản của bạn"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT_404", "Không tìm thấy danh mục"),
    CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT, "CAT_409", "Tên danh mục đã tồn tại"),
    POST_CATEGORY_UNAVAILABLE(HttpStatus.BAD_REQUEST, "POST_400_CATEGORY",
            "Danh mục không tồn tại hoặc đã ngừng sử dụng"),
    POST_PAGINATION_INVALID(HttpStatus.BAD_REQUEST, "POST_400_PAGE", "Trang phải từ 1, số bài mỗi trang từ 1 đến 100"),
    POST_PAGINATION_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "POST_400_PAGE_FORMAT",
            "Trang và số bài mỗi trang phải là số nguyên hợp lệ"),
    POST_ID_INVALID(HttpStatus.BAD_REQUEST, "POST_400_ID", "Mã bài đăng phải là số nguyên dương"),
    POST_ID_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "POST_400_ID_FORMAT", "Mã bài đăng phải là số nguyên dương hợp lệ"),
    POST_NOT_VISIBLE(HttpStatus.NOT_FOUND, "POST_404", "Bài đăng không tồn tại hoặc không còn hiển thị"),
    IMAGE_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_503_CONFIG",
            "Chưa cấu hình R2. Vui lòng cấu hình lưu trữ ảnh hoặc đăng bài không kèm ảnh."),
    IMAGE_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_503_UPLOAD",
            "Không tải được ảnh lên R2. Vui lòng thử lại."),
    IMAGE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "IMAGE_400_COUNT", "Chỉ được tải tối đa 5 ảnh"),
    IMAGE_SIZE_INVALID(HttpStatus.BAD_REQUEST, "IMAGE_400_SIZE", "Mỗi ảnh phải từ 1 byte đến 5 MB"),
    IMAGE_CONTENT_INVALID(HttpStatus.BAD_REQUEST, "IMAGE_400_CONTENT", "Tệp phải là ảnh JPG hoặc PNG hợp lệ"),
    IMAGE_FORMAT_UNSUPPORTED(HttpStatus.BAD_REQUEST, "IMAGE_400_FORMAT", "Chỉ hỗ trợ ảnh JPG và PNG"),
    IMAGE_DIMENSIONS_INVALID(HttpStatus.BAD_REQUEST, "IMAGE_400_DIMENSIONS", "Ảnh không được vượt quá 20 megapixel"),
    IMAGE_READ_FAILED(HttpStatus.BAD_REQUEST, "IMAGE_400_READ", "Không đọc được nội dung ảnh"),
    IMAGE_CORRUPTED(HttpStatus.BAD_REQUEST, "IMAGE_400_CORRUPTED", "Ảnh bị hỏng hoặc không đọc được"),
    STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_503", "Dịch vụ lưu trữ ảnh chưa sẵn sàng"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "ERR_500", "Lỗi hệ thống nội bộ"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "ERR_400", "Yêu cầu không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ERR_401", "Chưa xác thực hoặc phiên đăng nhập không hợp lệ"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "ERR_403", "Không có quyền truy cập"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "ERR_404", "Không tìm thấy tài nguyên"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ACC_404", "Không tìm thấy tài khoản"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "ACC_409", "Email/Tài khoản đã tồn tại trong hệ thống"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "ACC_403_LOCKED", "Tài khoản đang bị khóa hoặc ngừng hoạt động"),
    ACCOUNT_DELETED(HttpStatus.FORBIDDEN, "ACC_403_DELETED", "Tài khoản đã bị xóa"),
    ACCOUNT_EXPIRED(HttpStatus.FORBIDDEN, "ACC_403_EXPIRED", "Tài khoản đã hết thời hạn hiệu lực"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_401", "Tài khoản hoặc mật khẩu không chính xác"),
    OTP_NOT_FOUND(HttpStatus.BAD_REQUEST, "OTP_404", "Không tìm thấy mã OTP đang hoạt động, vui lòng yêu cầu mã mới"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "OTP_400_EXPIRED", "Mã OTP đã hết hạn"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "OTP_400_INVALID", "Mã OTP không chính xác"),
    OTP_MAX_ATTEMPTS(HttpStatus.BAD_REQUEST, "OTP_400_MAX", "Đã vượt quá số lần nhập sai mã OTP cho phép"),
    EXCEL_PARSE_ERROR(HttpStatus.BAD_REQUEST, "EXCEL_400",
            "Không thể đọc file Excel, vui lòng kiểm tra định dạng .xlsx/.xls");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
