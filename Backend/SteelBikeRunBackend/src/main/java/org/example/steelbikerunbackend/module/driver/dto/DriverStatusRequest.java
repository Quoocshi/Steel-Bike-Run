package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Yêu cầu cập nhật trạng thái online/offline của tài xế")
public record DriverStatusRequest(

        @Schema(description = "Trạng thái mong muốn: true = Online, false = Offline", example = "true")
        @NotNull(message = "Trạng thái isOnline không được để trống")
        Boolean isOnline
) {}
