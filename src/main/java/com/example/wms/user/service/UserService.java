package com.example.wms.user.service;

import com.example.wms.common.exception.BusinessException;
import com.example.wms.user.dto.AssignRoleRequest;
import com.example.wms.user.dto.CreateUserRequest;
import com.example.wms.user.dto.UpdateUserRequest;
import com.example.wms.user.dto.UserDto;
import com.example.wms.user.mapper.UserMapper;
import com.example.wms.user.model.User;
import com.example.wms.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper       userMapper;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("USERNAME_TAKEN",
                    "Username '" + request.username() + "' is already taken.",
                    HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException("EMAIL_TAKEN",
                    "Email '" + request.email() + "' is already registered.",
                    HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .active(true)
                .build();

        return userMapper.toDto(userRepository.save(user));
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserDto findById(UUID id) {
        return userMapper.toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<UserDto> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toDto);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest request) {
        User user = getOrThrow(id);

        if (request.email() != null) {
            if (!request.email().equals(user.getEmail())
                    && userRepository.existsByEmail(request.email())) {
                throw new BusinessException("EMAIL_TAKEN",
                        "Email '" + request.email() + "' is already registered.",
                        HttpStatus.CONFLICT);
            }
            user.setEmail(request.email());
        }

        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (request.active() != null) {
            user.setActive(request.active());
        }

        return userMapper.toDto(userRepository.save(user));
    }

    // -------------------------------------------------------------------------
    // Delete (soft)
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(UUID id) {
        User user = getOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
    }

    // -------------------------------------------------------------------------
    // Assign role
    // -------------------------------------------------------------------------

    @Transactional
    public UserDto assignRole(UUID id, AssignRoleRequest request) {
        User user = getOrThrow(id);
        user.setRole(request.role());
        return userMapper.toDto(userRepository.save(user));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User getOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }
}
