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
import org.springframework.context.annotation.Lazy;
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
        @Lazy
        private final DriverLocationService driverLocationService;

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
                                // Xóa stale Redis entry để heartbeat mới ghi lại vị trí chính xác.
                                driverLocationService.removeDriverLocation(driver.getId().toString());
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

                // Xóa stale Redis entry (nếu có) để đảm bảo driver được tìm thấy
                // ngay khi heartbeat đầu tiên đến — ngăn stale location từ session trước.
                driverLocationService.removeDriverLocation(newDriver.getId().toString());

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
                // Xóa location khỏi Redis — tránh stale entry xuất hiện trong findNearbyDrivers
                driverLocationService.removeDriverLocation(driver.getId().toString());

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
                        if (desired) {
                                // Driver is reconnecting (app restart) while DB already shows online.
                                // Purge any stale Redis location entry so the next GPS heartbeat
                                // (within ~3 s) writes a fresh one, making MatchingEngine find them.
                                driverLocationService.removeDriverLocation(driver.getId().toString());
                                log.info("Driver [{}] reconnect detected – cleared stale Redis entry, awaiting fresh heartbeat",
                                                user.getEmail());
                        } else {
                                log.info("Driver [{}] status already Offline, no-op", user.getEmail());
                        }
                        return DriverProfileResponse.from(driver, false);
                }

                driver.setOnline(desired);
                driver = driverRepository.save(driver);
                cacheRepository.evict(userEmail);

                // Khi driver offline: xóa location khỏi Redis ngay lập tức.
                // Nếu không xóa, Redis HASH vẫn còn TTL 60s với isOnline=true
                // -> MatchingEngine vẫn tìm thấy driver này dù đã offline.
                if (!desired) {
                        driverLocationService.removeDriverLocation(driver.getId().toString());
                        log.info("Driver [{}] location removed from Redis (went offline)", user.getEmail());
                } else {
                        // Khi driver online: xóa stale Redis entry (nếu có) để đảm bảo
                        // heartbeat đầu tiên từ mobile sẽ ghi lại vị trí mới nhất.
                        // Ngăn stale location (từ session trước) bị dùng sai
                        // trong trường hợp heartbeat đầu tiên bị trì hoãn (GPS cold-start).
                        driverLocationService.removeDriverLocation(driver.getId().toString());
                        log.info("Driver [{}] marked online, stale Redis entry cleared (awaiting fresh heartbeat)", user.getEmail());
                }

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

        /**
         * Xóa toàn bộ trạng thái online của driver:
         * - Set {@code isOnline = false} trong PostgreSQL
         * - Evict driver profile cache
         * - Xóa location khỏi Redis
         *
         * <p>Dùng khi user logout mà không qua "Chuyển về Khách hàng".
         * Đảm bảo driver không bị stale trong matching system.
         *
         * @param userId UUID của user đang logout
         */
        @Transactional
        public void clearDriverOnlineState(java.util.UUID userId) {
                Driver driver = driverRepository.findByUserIdWithUser(userId)
                                .orElse(null);
                if (driver == null) {
                        log.debug("No driver profile found for userId={}, skipping clear", userId);
                        return;
                }

                if (driver.isOnline()) {
                        driver.setOnline(false);
                        driverRepository.save(driver);
                        log.info("Driver [{}] set offline on logout", driver.getUser().getEmail());
                }

                cacheRepository.evict(driver.getUser().getEmail());
                driverLocationService.removeDriverLocation(driver.getId().toString());
        }
}
