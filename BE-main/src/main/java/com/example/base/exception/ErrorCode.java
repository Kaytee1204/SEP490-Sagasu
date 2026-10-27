package com.example.base.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "CAT_404", "Không tìm thấy danh mục"),
    CATEGORY_NAME_EXISTS(HttpStatus.CONFLICT, "CAT_409", "Tên danh mục đã tồn tại"),
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
