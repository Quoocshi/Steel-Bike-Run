package org.example.steelbikerunbackend.module.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.steelbikerunbackend.common.exception.AppException;
import org.example.steelbikerunbackend.common.exception.ErrorCode;
import org.example.steelbikerunbackend.module.user.dto.UserProfileResponse;
import org.example.steelbikerunbackend.module.user.entity.User;
import org.example.steelbikerunbackend.module.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        return UserProfileResponse.from(user);
    }
}
