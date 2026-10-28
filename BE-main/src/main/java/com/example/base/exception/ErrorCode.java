package com.example.base.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    POST_SEARCH_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "POST_400_SEARCH_TIME_RANGE", "Thời gian phải là 24H, 7D hoặc CUSTOM; không kết hợp mốc tương đối với khoảng ngày tùy chỉnh"),
    POST_SEARCH_FILTER_INVALID(HttpStatus.BAD_REQUEST, "POST_400_SEARCH_FILTER", "Bộ lọc không hợp lệ: loại bài LOST/FOUND, mã danh mục dương, từ khóa tối đa 200 ký tự, địa điểm tối đa 1000 ký tự"),
    POST_SEARCH_DATE_INVALID(HttpStatus.BAD_REQUEST, "POST_400_SEARCH_DATE", "Ngày phải có dạng yyyy-MM-dd; ngày bắt đầu không được sau ngày kết thúc"),
    POST_HANDOVER_TIME_REQUIRED(HttpStatus.BAD_REQUEST, "POST_400_HANDOVER_REQUIRED", "Vui lòng đề xuất ngày/giờ giao đồ cho Approver"),
    POST_HANDOVER_TIME_INVALID(HttpStatus.BAD_REQUEST, "POST_400_HANDOVER_TIME", "Lịch giao đồ phải ở tương lai và chỉ áp dụng khi giao Approver"),
    POST_NOT_EDITABLE(HttpStatus.CONFLICT, "POST_409_STATE", "Bài đã xóa, bị ẩn hoặc không còn mở để chỉnh sửa"),
    POST_STALE(HttpStatus.CONFLICT, "POST_409_STALE", "Bài đăng đã thay đổi. Vui lòng tải lại trước khi lưu"),
    POST_IMAGES_INVALID(HttpStatus.BAD_REQUEST, "POST_400_IMAGES", "Danh sách ảnh không hợp lệ hoặc ảnh không thuộc bài đăng"),
    POST_FILTER_INVALID(HttpStatus.BAD_REQUEST, "POST_400_FILTER", "Bộ lọc bài đăng không hợp lệ"),
    POST_CUSTODY_INVALID(HttpStatus.BAD_REQUEST, "POST_400_CUSTODY", "Chọn tự giữ hoặc giao văn phòng"),
    POST_OWNER_REQUIRED(HttpStatus.NOT_FOUND, "POST_404_OWNER", "Không tìm thấy bài đăng thuộc tài khoản của bạn"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT_404", "Không tìm thấy danh mục"),
    CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT, "CAT_409", "Tên danh mục đã tồn tại"),
    POST_CATEGORY_UNAVAILABLE(HttpStatus.BAD_REQUEST, "POST_400_CATEGORY", "Danh mục không tồn tại hoặc đã ngừng sử dụng"),
    POST_PAGINATION_INVALID(HttpStatus.BAD_REQUEST, "POST_400_PAGE", "Trang phải từ 1, số bài mỗi trang từ 1 đến 100"),
    POST_PAGINATION_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "POST_400_PAGE_FORMAT", "Trang và số bài mỗi trang phải là số nguyên hợp lệ"),
    POST_ID_INVALID(HttpStatus.BAD_REQUEST, "POST_400_ID", "Mã bài đăng phải là số nguyên dương"),
    POST_ID_FORMAT_INVALID(HttpStatus.BAD_REQUEST, "POST_400_ID_FORMAT", "Mã bài đăng phải là số nguyên dương hợp lệ"),
    POST_NOT_VISIBLE(HttpStatus.NOT_FOUND, "POST_404", "Bài đăng không tồn tại hoặc không còn hiển thị"),
    IMAGE_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_503_CONFIG", "Chưa cấu hình R2. Vui lòng cấu hình lưu trữ ảnh hoặc đăng bài không kèm ảnh."),
    IMAGE_UPLOAD_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "IMAGE_503_UPLOAD", "Không tải được ảnh lên R2. Vui lòng thử lại."),
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
    EXCEL_PARSE_ERROR(HttpStatus.BAD_REQUEST, "EXCEL_400", "Không thể đọc file Excel, vui lòng kiểm tra định dạng .xlsx/.xls");

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
