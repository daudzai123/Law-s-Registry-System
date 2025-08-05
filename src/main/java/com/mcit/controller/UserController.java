package com.mcit.controller;

import com.mcit.dto.ChangePasswordRequest;
import com.mcit.dto.ResetPasswordOTPRequest;
import com.mcit.dto.ResetPasswordRequest;
import com.mcit.entity.MyUser;
import com.mcit.enums.Role;
import com.mcit.repo.ForgotPasswordRepository;
import com.mcit.repo.MyUserRepository;
import com.mcit.service.ForgotPasswordService;
import com.mcit.service.MyUserDetailService;
import jakarta.validation.Valid;
import org.springframework.data.util.ReflectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class UserController {

    private final MyUserRepository myUserRepository;
    private final MyUserDetailService myUserDetailService;
    private final ForgotPasswordService forgotPasswordService;

    public UserController(MyUserRepository myUserRepository, MyUserDetailService myUserDetailService, ForgotPasswordRepository forgotPasswordRepository, ForgotPasswordService forgotPasswordService) {
        this.myUserRepository = myUserRepository;
        this.myUserDetailService = myUserDetailService;
        this.forgotPasswordService = forgotPasswordService;
    }

    // get all users
    @GetMapping("/users")
    public List<MyUser> getAllUsers() {
        return myUserRepository.findAll();
    }

    //get specific user
    @GetMapping("/user/{id}")
    public Optional<MyUser> getUserById(@PathVariable Long id) {
        return myUserRepository.findById(id);
    }

    //partial update
    @PatchMapping("/user/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Optional<MyUser> existingUserOpt = myUserRepository.findById(id);

        if (existingUserOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found", "message", "No user found with id: " + id));
        }

        MyUser user = existingUserOpt.get();

        updates.forEach((field, value) -> {
            Field userField = org.springframework.util.ReflectionUtils.findField(MyUser.class, field);
            if (userField != null) {
                userField.setAccessible(true);

                // Prevent updating ID and password directly
                if (field.equalsIgnoreCase("id")) {
                    throw new IllegalArgumentException("Updating ID is not allowed");
                } else if (field.equalsIgnoreCase("password")) {
                    throw new IllegalArgumentException("Use the change password endpoint to update the password");
                }

                // Convert username and email to lowercase before saving
                if (field.equalsIgnoreCase("username") || field.equalsIgnoreCase("email")) {
                    value = value.toString().toLowerCase();
                }

                // Convert role and literacyLevel to their respective enums
                if (field.equalsIgnoreCase("role") && value instanceof String) {
                    value = Role.valueOf(((String) value).toUpperCase()); // Convert string to enum
                }
                ReflectionUtils.setField(userField, user, value);
            }
        });

        // Save the updated user
        myUserRepository.save(user);

        return ResponseEntity.ok(user);
    }

    // Delete user by id
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!myUserRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        myUserRepository.deleteById(id);

        return ResponseEntity
                .noContent()
                .build();
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
    @PostMapping("users/validate-otp-code")
    public ResponseEntity<String> validateOtpCode(@Valid @RequestBody ResetPasswordRequest request) {
        return forgotPasswordService.validateOtpCode(request);
    }

    // reset password end point
    @PostMapping("users/reset-password")
    public ResponseEntity<String> requestPasswordReset(
            @RequestHeader("Authorization") String resetToken, // Get token from header
            @Valid @RequestBody ResetPasswordRequest request) {

        return forgotPasswordService.requestPasswordReset(resetToken, request);
    }

    // Forgot Password Endpoint
    @PostMapping("/users/forgot-password")
    public ResponseEntity<String> createAndSendOtpCodeToEmail(
            @Valid @RequestBody ResetPasswordOTPRequest request) {
        ResponseEntity<String> result = forgotPasswordService.createOtpCode(request);

        // Simply return the result directly since result already contains status and body
        return result;
    }


    // change password endpoint
    @PutMapping("users/change-password")
    public ResponseEntity<String> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Perform password change logic
        myUserDetailService.changePassword(userDetails.getUsername(), request);

        // Return a success response
        return ResponseEntity.ok("Password changed successfully.");
    }

    @GetMapping("/users/count-user-status")
    public ResponseEntity<Map<String, Long>> getUserStats() {
        long activeCount = myUserRepository.countByIsActiveTrue();
        long inactiveCount = myUserRepository.countByIsActiveFalse();
        long totalCount = myUserRepository.count(); // built-in method

        Map<String, Long> stats = Map.of(
                "activeUsers", activeCount,
                "inactiveUsers", inactiveCount,
                "totalUsers", totalCount
        );

        return ResponseEntity.ok(stats);
    }
}