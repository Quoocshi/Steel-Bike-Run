package org.example.steelbikerunbackend.module.driver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.entity.Driver;
import org.example.steelbikerunbackend.module.driver.repository.DriverRepository;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final UserRepository userRepository;
    private final DriverRepository driverRepository;

    /**
     * Switch sang chế độ Driver.
     *
     * <p>Logic:
     * <ol>
     *   <li>Xác thực user có role DRIVER.</li>
     *   <li>Tìm profile Driver theo userId.</li>
     *   <li>Nếu chưa có → tạo mới bằng thông tin xe trong request.</li>
     *   <li>Nếu đã có → toggle trạng thái is_online (online ↔ offline).</li>
     *   <li>Trả về DriverProfileResponse kèm cờ isNewProfile.</li>
     * </ol>
     *
     * @param userEmail email lấy từ JWT (principal)
     * @param request   thông tin xe — bắt buộc khi tạo profile lần đầu
     */
    @Transactional
    public DriverProfileResponse switchDriver(String userEmail, SwitchDriverRequest request) {

        // 1. Tìm user
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 2. Kiểm tra role
        if (user.getRole() != UserRole.DRIVER) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Chỉ tài khoản có role DRIVER mới có thể kích hoạt chế độ tài xế");
        }

        // 3. Tìm driver profile
        Optional<Driver> existing = driverRepository.findByUserIdWithUser(user.getId());

        // 4a. Profile đã tồn tại → toggle is_online
        if (existing.isPresent()) {
            Driver driver = existing.get();
            boolean newStatus = !driver.isOnline();
            driver.setOnline(newStatus);
            driver = driverRepository.save(driver);

            log.info("Driver [{}] toggled online status → {}", user.getEmail(), newStatus);
            return DriverProfileResponse.from(driver, false);
        }

        // 4b. Profile chưa tồn tại → validate vehicle info rồi tạo mới
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cần cung cấp thông tin xe để kích hoạt lần đầu");
        }
        if (driverRepository.existsByVehiclePlate(request.vehiclePlate())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Biển số xe '" + request.vehiclePlate() + "' đã được đăng ký");
        }
        if (driverRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Số bằng lái '" + request.licenseNumber() + "' đã được đăng ký");
        }

        Driver newDriver = Driver.builder()
                .user(user)
                .vehiclePlate(request.vehiclePlate().toUpperCase().trim())
                .vehicleModel(request.vehicleModel().trim())
                .vehicleColor(request.vehicleColor().trim())
                .licenseNumber(request.licenseNumber().trim())
                .isOnline(true)        // Online ngay sau khi tạo
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();

        newDriver = driverRepository.save(newDriver);

        log.info("Driver profile CREATED for user [{}], vehiclePlate={}", user.getEmail(),
                newDriver.getVehiclePlate());
        return DriverProfileResponse.from(newDriver, true);
    }

    /**
     * Lấy profile Driver theo email của user đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public DriverProfileResponse getMyProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                        "Profile Driver chưa tồn tại. Hãy gọi /switch trước."));

        return DriverProfileResponse.from(driver, false);
    }
}
