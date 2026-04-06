package org.example.steelbikerunbackend.module.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = """
        Thông tin xe cần cung cấp khi tài xế kích hoạt chế độ Driver lần đầu.
        Nếu profile Driver đã tồn tại, các trường này được bỏ qua.
        """)
public record SwitchDriverRequest(

        @Schema(description = "Biển số xe", example = "51G-123.45")
        @NotBlank(message = "Biển số xe không được để trống")
        @Size(max = 20, message = "Biển số xe tối đa 20 ký tự")
        String vehiclePlate,

        @Schema(description = "Hãng và dòng xe", example = "Honda Air Blade 150")
        @NotBlank(message = "Hãng xe không được để trống")
        String vehicleModel,

        @Schema(description = "Màu xe", example = "Đen")
        @NotBlank(message = "Màu xe không được để trống")
        String vehicleColor,

        @Schema(description = "Số bằng lái xe", example = "012345678901")
        @NotBlank(message = "Số bằng lái không được để trống")
        @Pattern(regexp = "^[0-9]{12}$", message = "Số bằng lái phải đúng 12 chữ số")
        String licenseNumber
) {}
