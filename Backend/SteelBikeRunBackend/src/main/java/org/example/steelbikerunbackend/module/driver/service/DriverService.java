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

    // khi user bấm nút "Chuyển sang lái xe", hàm này sẽ chạy
    // lần đầu thì bắt buộc phải nhập thông tin xe, lần sau thì thôi
    @Transactional
    public SwitchRoleResponse switchToDriver(String userEmail, SwitchDriverRequest request) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // chỉ customer mới được switch, driver đang chạy thì switch làm gì :))
        if (user.getRole() != UserRole.CUSTOMER) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Chỉ tài khoản CUSTOMER mới có thể chuyển sang chế độ Driver");
        }

        // kiểm tra xem user này đã từng đăng ký tài xế chưa
        Optional<Driver> existing = driverRepository.findByUserIdWithUser(user.getId());

        // nếu đã có profile rồi thì chỉ cần bật online + đổi role là xong
        if (existing.isPresent()) {
            Driver driver = existing.get();

            // bật online nếu đang offline (có thể họ vừa switch về customer xong switch lại)
            if (!driver.isOnline()) {
                driver.setOnline(true);
                driver = driverRepository.save(driver);
                cacheRepository.evict(userEmail); // cache cũ sai rồi, xóa đi
            }

            // cập nhật role trong DB và xóa cache user để lần sau đọc lại đúng
            user.setRole(UserRole.DRIVER);
            userRepository.save(user);
            userProfileCacheRepository.evict(userEmail);

            // tạo token mới với role DRIVER luôn, mobile khỏi cần login lại
            String token = jwtUtil.generateToken(user.getEmail(), UserRole.DRIVER.name());
            log.info("Driver [{}] switched to Driver mode → role=DRIVER, Online", user.getEmail());
            return SwitchRoleResponse.of(token, DriverProfileResponse.from(driver, false));
        }

        // chưa có profile → lần đầu đăng ký làm tài xế, cần thông tin xe
        if (request == null) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Cần cung cấp thông tin xe để kích hoạt lần đầu");
        }

        // kiểm tra biển số và bằng lái xem có bị trùng không
        if (driverRepository.existsByVehiclePlate(request.vehiclePlate())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Biển số xe '" + request.vehiclePlate() + "' đã được đăng ký");
        }
        if (driverRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Số bằng lái '" + request.licenseNumber() + "' đã được đăng ký");
        }

        // tạo mới driver, mặc định online luôn và rating khởi điểm 5 sao
        Driver newDriver = Driver.builder()
                .user(user)
                .vehiclePlate(request.vehiclePlate().toUpperCase().trim())
                .vehicleModel(request.vehicleModel().trim())
                .vehicleColor(request.vehicleColor().trim())
                .licenseNumber(request.licenseNumber().trim())
                .isOnline(true) // vừa đăng ký xong thì online luôn cho nóng
                .rating(5.0f)
                .totalTrips(0)
                .faceScanPassed(false)
                .build();

        newDriver = driverRepository.save(newDriver);

        // đổi role user → DRIVER và dọn cache cũ
        user.setRole(UserRole.DRIVER);
        userRepository.save(user);
        userProfileCacheRepository.evict(userEmail);

        // cache profile mới luôn để lần sau khỏi query DB
        DriverProfileResponse newResponse = DriverProfileResponse.from(newDriver, true);
        cacheRepository.put(userEmail, newResponse);

        // trả token mới có role DRIVER kèm profile
        String token = jwtUtil.generateToken(user.getEmail(), UserRole.DRIVER.name());
        log.info("Driver profile CREATED for user [{}], vehiclePlate={}, role=DRIVER",
                user.getEmail(), newDriver.getVehiclePlate());
        return SwitchRoleResponse.of(token, newResponse);
    }

    // khi tài xế muốn quay về làm khách hàng bình thường
    @Transactional
    public SwitchRoleResponse switchToCustomer(String userEmail) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // ngược lại với trên, chỉ DRIVER mới được gọi cái này
        if (user.getRole() != UserRole.DRIVER) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Chỉ tài khoản DRIVER mới có thể chuyển về chế độ Customer");
        }

        Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                        "Profile Driver chưa tồn tại"));

        // tắt online trước khi về, không để driver "ma" trôi nổi trong hệ thống
        if (driver.isOnline()) {
            driver.setOnline(false);
            driver = driverRepository.save(driver);
            cacheRepository.evict(userEmail);
        }

        // đổi role về CUSTOMER và xóa cache
        user.setRole(UserRole.CUSTOMER);
        userRepository.save(user);
        userProfileCacheRepository.evict(userEmail);

        // trả token mới có role CUSTOMER, mobile tự lưu đè token cũ
        String token = jwtUtil.generateToken(user.getEmail(), UserRole.CUSTOMER.name());
        log.info("Driver [{}] switched back to Customer mode → role=CUSTOMER, Offline", user.getEmail());
        return SwitchRoleResponse.of(token, DriverProfileResponse.from(driver, false));
    }

    // tài xế đang ở driver mode muốn bật/tắt online (không liên quan đến switch role)
    // ví dụ: nghỉ ăn cơm 30 phút thì offline, ăn xong bật lại
    @Transactional
    public DriverProfileResponse setOnlineStatus(String userEmail, DriverStatusRequest request) {

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Driver driver = driverRepository.findByUserIdWithUser(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST,
                        "Profile Driver chưa tồn tại. Hãy gọi /driver/switch trước."));

        boolean desired = request.isOnline();

        // nếu trạng thái đã đúng rồi thì thôi, không cần làm gì cả
        if (driver.isOnline() == desired) {
            log.info("Driver [{}] status already {}, no-op", user.getEmail(),
                    desired ? "Online" : "Offline");
            return DriverProfileResponse.from(driver, false);
        }

        driver.setOnline(desired);
        driver = driverRepository.save(driver);
        cacheRepository.evict(userEmail); // trạng thái đổi rồi thì cache cũ vứt đi

        log.info("Driver [{}] status updated → {}", user.getEmail(),
                desired ? "Online" : "Offline");
        return DriverProfileResponse.from(driver, false);
    }

    // lấy thông tin profile driver, ưu tiên đọc từ Redis trước cho nhanh
    @Transactional(readOnly = true)
    public DriverProfileResponse getMyProfile(String userEmail) {

        // cache hit → trả về ngay, không cần đụng DB
        return cacheRepository.get(userEmail).orElseGet(() -> {

            // cache miss → query DB rồi lưu lại để lần sau dùng
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
