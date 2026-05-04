package org.example.steelbikerunbackend.module.driver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.enums.UserRole;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.common.security.JwtUtil;
import org.example.steelbikerunbackend.module.driver.cache.DriverProfileCacheRepository;
import org.example.steelbikerunbackend.module.driver.dto.DriverProfileResponse;
import org.example.steelbikerunbackend.module.driver.dto.DriverStatusRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchDriverRequest;
import org.example.steelbikerunbackend.module.driver.dto.SwitchRoleResponse;
import org.example.steelbikerunbackend.module.user.cache.UserProfileCacheRepository;
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
        private final DriverProfileCacheRepository cacheRepository;
        private final UserProfileCacheRepository userProfileCacheRepository;
        private final JwtUtil jwtUtil;

        /**
         * Chuyển user sang chế độ Driver.
         *
         * <ul>
         * <li><b>Lần đầu tiên</b>: bắt buộc cung cấp thông tin xe để tạo profile
         * -> tự động set {@code isOnline = true}.</li>
         * <li><b>Các lần sau</b>: profile đã tồn tại, chỉ đảm bảo
         * {@code isOnline = true} (luôn online khi switch sang Driver).</li>
         * </ul>
         *
         * @param userEmail email lấy từ JWT (principal)
         * @param request   thông tin xe — bắt buộc khi tạo profile lần đầu
         */
        @Transactional
        public SwitchRoleResponse switchToDriver(String userEmail, SwitchDriverRequest request) {

                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                // Chỉ CUSTOMER mới có thể switch sang Driver
                if (user.getRole() != UserRole.CUSTOMER) {
                        throw new AppException(ErrorCode.ACCESS_DENIED,
                                        "Chỉ tài khoản CUSTOMER mới có thể chuyển sang chế độ Driver");
                }

                Optional<Driver> existing = driverRepository.findByUserIdWithUser(user.getId());

                // Profile đã tồn tại -> đảm bảo isOnline = true và role = DRIVER
                if (existing.isPresent()) {
                        Driver driver = existing.get();

                        // Cập nhật trạng thái online nếu chưa online
                        if (!driver.isOnline()) {
                                driver.setOnline(true);
                                driver = driverRepository.save(driver);
                                cacheRepository.evict(userEmail);
                        }

                        // Cập nhật role sang DRIVER
                        user.setRole(UserRole.DRIVER);
                        userRepository.save(user);
                        userProfileCacheRepository.evict(userEmail);

                        String token = jwtUtil.generateToken(user.getEmail(), UserRole.DRIVER.name());
                        log.info("Driver [{}] switched to Driver mode -> role=DRIVER, Online", user.getEmail());
                        return SwitchRoleResponse.of(token, DriverProfileResponse.from(driver, false));
                }

                // Profile chưa tồn tại -> validate vehicle info rồi tạo mới
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
                                .isOnline(true) // Luôn online ngay sau khi tạo profile
                                .rating(5.0f)
                                .totalTrips(0)
                                .faceScanPassed(false)
                                .build();

                newDriver = driverRepository.save(newDriver);

                // Cập nhật role sang DRIVER
                user.setRole(UserRole.DRIVER);
                userRepository.save(user);
                userProfileCacheRepository.evict(userEmail);

                DriverProfileResponse newResponse = DriverProfileResponse.from(newDriver, true);
                cacheRepository.put(userEmail, newResponse);

                String token = jwtUtil.generateToken(user.getEmail(), UserRole.DRIVER.name());
                log.info("Driver profile CREATED for user [{}], vehiclePlate={}, role=DRIVER",
                                user.getEmail(), newDriver.getVehiclePlate());
                return SwitchRoleResponse.of(token, newResponse);
        }

        /**
         * Chuyển tài xế về chế độ Customer.
         *
         * <p>
         * Set {@code isOnline = false} và evict cache. Chỉ DRIVER mới
         * được gọi endpoint này.
         * </p>
         *
         * @param userEmail email lấy từ JWT (principal)
         */
        @Transactional
        public SwitchRoleResponse switchToCustomer(String userEmail) {

                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                // Chỉ DRIVER mới có thể switch về Customer
                if (user.getRole() != UserRole.DRIVER) {
                        throw new AppException(ErrorCode.ACCESS_DENIED,
                                        "Chỉ tài khoản DRIVER mới có thể chuyển về chế độ Customer");
                }

                Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                                                "Profile Driver chưa tồn tại"));

                // Set offline trước khi về Customer mode
                if (driver.isOnline()) {
                        driver.setOnline(false);
                        driver = driverRepository.save(driver);
                        cacheRepository.evict(userEmail);
                }

                // Cập nhật role về CUSTOMER
                user.setRole(UserRole.CUSTOMER);
                userRepository.save(user);
                userProfileCacheRepository.evict(userEmail);

                String token = jwtUtil.generateToken(user.getEmail(), UserRole.CUSTOMER.name());
                log.info("Driver [{}] switched back to Customer mode -> role=CUSTOMER, Offline", user.getEmail());
                return SwitchRoleResponse.of(token, DriverProfileResponse.from(driver, false));
        }

        /**
         * Cập nhật trạng thái online/offline của tài xế.
         *
         * <p>
         * Chỉ DRIVER được gọi. Không liên quan đến việc switch role.
         * </p>
         *
         * @param userEmail email lấy từ JWT
         * @param request   {@link DriverStatusRequest} chứa trạng thái mong muốn
         */
        @Transactional
        public DriverProfileResponse setOnlineStatus(String userEmail, DriverStatusRequest request) {

                User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                                                "Profile Driver chưa tồn tại. Hãy gọi /driver/switch trước."));

                boolean desired = request.isOnline();
                if (driver.isOnline() == desired) {
                        log.info("Driver [{}] status already {}, no-op", user.getEmail(),
                                        desired ? "Online" : "Offline");
                        return DriverProfileResponse.from(driver, false);
                }

                driver.setOnline(desired);
                driver = driverRepository.save(driver);
                cacheRepository.evict(userEmail);

                log.info("Driver [{}] status updated -> {}", user.getEmail(),
                                desired ? "Online" : "Offline");
                return DriverProfileResponse.from(driver, false);
        }

        /**
         * Lấy profile Driver theo email của user đang đăng nhập.
         *
         * <p>
         * Cache-Aside pattern:
         * <ol>
         * <li>Kiểm tra Redis trước (HIT -> trả về ngay).</li>
         * <li>MISS -> query DB -> lưu vào Redis -> trả về.</li>
         * </ol>
         */
        @Transactional(readOnly = true)
        public DriverProfileResponse getMyProfile(String userEmail) {

                return cacheRepository.get(userEmail).orElseGet(() -> {

                        User user = userRepository.findByEmail(userEmail)
                                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

                        Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                                        .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                                                        "Profile Driver chưa tồn tại. Hãy gọi /driver/switch trước."));

                        DriverProfileResponse response = DriverProfileResponse.from(driver, false);

                        cacheRepository.put(userEmail, response);
                        log.info("[Cache] Driver profile loaded from DB and cached: {}", userEmail);

                        return response;
                });
        }
}
