package org.example.steelbikerunbackend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Auth
    USER_NOT_FOUND(404, "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(409, "Email already exists", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS(409, "Phone number already exists", HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(401, "Invalid email/phone or password", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(401, "Unauthorized", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(403, "Access denied", HttpStatus.FORBIDDEN),

    // JWT
    TOKEN_INVALID(401, "Token is invalid or expired", HttpStatus.UNAUTHORIZED),

    // General
    BAD_REQUEST(400, "Bad request", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(500, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
