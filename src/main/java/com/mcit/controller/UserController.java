package com.mcit.controller;

import com.mcit.dto.*;
import com.mcit.entity.User;
import com.mcit.enums.Role;
import com.mcit.exception.ResourceNotFoundException;
import com.mcit.repo.ForgotPasswordRepository;
import com.mcit.repo.UserRepository;
import com.mcit.service.ActivityLogService;
import com.mcit.service.FileStorageService;
import com.mcit.service.ForgotPasswordService;
import com.mcit.service.MyUserDetailService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final MyUserDetailService myUserDetailService;
    private final ForgotPasswordService forgotPasswordService;
    private final FileStorageService fileStorageService;
    private final ActivityLogService activityLogService;

    public UserController(UserRepository userRepository, MyUserDetailService myUserDetailService, ForgotPasswordRepository forgotPasswordRepository, ForgotPasswordService forgotPasswordService, FileStorageService fileStorageService, ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.myUserDetailService = myUserDetailService;
        this.forgotPasswordService = forgotPasswordService;
        this.fileStorageService = fileStorageService;
        this.activityLogService = activityLogService;
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));

        updates.forEach((field, value) -> {
            Field userField = org.springframework.util.ReflectionUtils.findField(User.class, field);
            if (userField != null) {
                userField.setAccessible(true);

                if (field.equalsIgnoreCase("id")) {
                    throw new IllegalArgumentException("Updating ID is not allowed");
                }
                if (field.equalsIgnoreCase("password")) {
                    throw new IllegalArgumentException("Use change password endpoint");
                }

                if (field.equalsIgnoreCase("username") || field.equalsIgnoreCase("email")) {
                    value = value.toString().toLowerCase();
                }

                if (field.equalsIgnoreCase("role") && value instanceof String) {
                    value = Role.valueOf(value.toString().toUpperCase());
                }

                ReflectionUtils.setField(userField, user, value);
            }
        });

        User updatedUser = userRepository.save(user);

        // ✅ ACTIVITY LOG
        activityLogService.logActivity(
                "User",
                updatedUser.getId(),
                "UPDATE",
                "User updated: " + updatedUser.getUsername(),
                userDetails.getUsername()
        );

        return ResponseEntity.ok(updatedUser);
    }


    //get specific user
    @GetMapping("/{id}")
    public Optional<User> getUserById(@PathVariable Long id) {
        return userRepository.findById(id);
    }

    // Pagination and Search endpoints
    @GetMapping
    public ResponseEntity<PaginatedResponseDTO<UserResponseDTO>> getUsersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort
    ) {
        Page<User> result = myUserDetailService.getUsersPaginated(page, size, sort);

        List<UserResponseDTO> users = result.getContent()
                .stream()
                .map(this::mapToUserDTO)
                .toList();

        return ResponseEntity.ok(
                new PaginatedResponseDTO<>(
                        users,
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages(),
                        result.hasNext(),
                        result.hasPrevious()
                )
        );
    }

    private UserResponseDTO mapToUserDTO(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getFirstname() + " " + user.getLastname(), // ✅ fullName
                user.getFathername(),
                user.getNid(),
                user.getPhone(),
                user.getEmail(),
                user.getUsername(),
                user.getPosition(),
                user.getRole().name(),     // ✅ enum → String
                user.getIsActive(),
                user.getProfileImage(),
                user.getCreateDate(),
                user.getUpdateDate()
        );
    }

    @GetMapping("/sorted")
    public Page<User> getUsersSorted(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        return myUserDetailService.getUsersSorted(page, size, sortBy);
    }

    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam String username) {
        return Collections.singletonList(myUserDetailService.findByUsername(username));
    }

    @GetMapping("/search-paginated")
    public Page<User> searchUsersPaginated(
            @RequestParam String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        return myUserDetailService.searchUsersPaginated(username, page, size, sortBy);
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));

        if (user.getProfileImage() != null && !user.getProfileImage().isBlank()) {
            String fileName = user.getProfileImage().replace("profileImages/", "");
            fileStorageService.deleteProfileImage(fileName);
        }

        userRepository.delete(user);

        // ✅ ACTIVITY LOG
        activityLogService.logActivity(
                "User",
                user.getId(),
                "DELETE",
                "User deleted: " + user.getUsername(),
                userDetails.getUsername()
        );

        return ResponseEntity.noContent().build();
    }

    // endpoint for getting roles enum
    @GetMapping("/enums/roles")
    public ResponseEntity<List<Map<String, String>>> getRoles() {
        List<Map<String, String>> roles = Arrays.stream(Role.values())
                .map(role -> Map.of("name", role.getDisplayName(), "value", role.name()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(roles);
    }

    // validate otp endpoint
    @PostMapping("/validate-otp-code")
    public ResponseEntity<String> validateOtpCode(@Valid @RequestBody ResetPasswordRequest request) {
        return forgotPasswordService.validateOtpCode(request);
    }

    // reset password end point
    @PostMapping("/reset-password")
    public ResponseEntity<String> requestPasswordReset(
            @RequestHeader("Authorization") String resetToken, // Get token from header
            @Valid @RequestBody ResetPasswordRequest request) {

        return forgotPasswordService.requestPasswordReset(resetToken, request);
    }

    // Forgot Password Endpoint
    @PostMapping("/forgot-password")
    public ResponseEntity<String> createAndSendOtpCodeToEmail(
            @Valid @RequestBody ResetPasswordOTPRequest request) {
        ResponseEntity<String> result = forgotPasswordService.createOtpCode(request);

        // Simply return the result directly since result already contains status and body
        return result;
    }

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        myUserDetailService.changePassword(userDetails.getUsername(), request);

        activityLogService.logActivity(
                "User",
                null,
                "CHANGE_PASSWORD",
                "Password changed",
                userDetails.getUsername()
        );

        return ResponseEntity.ok("Password changed successfully.");
    }

    @GetMapping("/count-user-status")
    public ResponseEntity<Map<String, Long>> getUserStats() {
        long activeCount = userRepository.countByIsActiveTrue();
        long inactiveCount = userRepository.countByIsActiveFalse();
        long totalCount = userRepository.count(); // built-in method

        Map<String, Long> stats = Map.of(
                "activeUsers", activeCount,
                "inactiveUsers", inactiveCount,
                "totalUsers", totalCount
        );

        return ResponseEntity.ok(stats);
    }
}