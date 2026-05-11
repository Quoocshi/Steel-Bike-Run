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

    // Driver
    DRIVER_NOT_FOUND(404, "Driver not found", HttpStatus.NOT_FOUND),

    // Trip
    TRIP_NOT_FOUND(404, "Trip not found", HttpStatus.NOT_FOUND),
    INVALID_TRIP_STATUS(400, "Invalid trip status transition", HttpStatus.BAD_REQUEST),
    TRIP_ALREADY_ACCEPTED(409, "Trip has already been accepted by another driver", HttpStatus.CONFLICT),
    NO_DRIVERS_AVAILABLE(404, "No drivers available in the area", HttpStatus.NOT_FOUND),
    DRIVER_NOT_AUTHORIZED(403, "Driver is not authorized for this trip", HttpStatus.FORBIDDEN),
    INVALID_COORDINATES(400, "Invalid coordinates: latitude must be in [-90, 90] and longitude in [-180, 180]",
            HttpStatus.BAD_REQUEST),

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
