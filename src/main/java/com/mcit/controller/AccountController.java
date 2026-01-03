package com.mcit.controller;

import com.mcit.dto.UserProfileDTO;
import com.mcit.dto.UserResponseDTO;
import com.mcit.entity.User;
import com.mcit.exception.FileStorageException;
import com.mcit.jwt.JwtUtilityClass;
import com.mcit.jwt.LoginForm;
import com.mcit.repo.UserRepository;
import com.mcit.service.ActivityLogService;
import com.mcit.service.FileStorageService;
import com.mcit.service.MyUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AccountController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtilityClass jwtUtilityClass;
    @Autowired
    private MyUserDetailService myUserDetailService;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private ActivityLogService activityLogService;

    private Optional<User> findByUsernameOrEmail(String identifier) {
        Optional<User> userOpt = userRepository.findByUsername(identifier);
        return userOpt.isPresent() ? userOpt : userRepository.findByEmail(identifier);
    }

    private String extractUsernameFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtilityClass.extractUsername(jwt);
    }

    @PostMapping("/register")
    public ResponseEntity<?> createUser(
            @RequestHeader("Authorization") String token,
            @RequestPart("user") User user,
                @RequestPart(value = "image", required = false) MultipartFile imageFile) {

        String creatorUsername = extractUsernameFromToken(token);

        // Check username/email uniqueness
        if (userRepository.existsByUsername(user.getUsername().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username already taken", "message", "Please choose another username."));
        }
        if (userRepository.existsByEmail(user.getEmail().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email already taken", "message", "Please choose another email."));
        }

        // Handle profile image
        String profileImagePath;
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                profileImagePath = fileStorageService.saveProfileImage(imageFile); // use null if userId not yet available
            } catch (FileStorageException ex) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", ex.getMessage()));
            }
        } else {
            profileImagePath = "profileImages/default.png"; // store relative path
        }

        // Set user fields
        user.setUsername(user.getUsername().toLowerCase());
        user.setEmail(user.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setProfileImage(profileImagePath);

        User savedUser = userRepository.save(user);

        activityLogService.logActivity(
                "User",
                savedUser.getId(),
                "CREATE",
                "User registered with username: " + savedUser.getUsername(),
                creatorUsername
        );

        savedUser.setPassword(null);

        return ResponseEntity.ok(savedUser);
    }

    @PutMapping(value = "/profile", consumes = {"multipart/form-data"})
    public ResponseEntity<UserProfileDTO> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestPart(value = "image") MultipartFile imageFile) {

        String identifier = extractUsernameFromToken(token);
        Optional<User> userOpt = findByUsernameOrEmail(identifier);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        User user = userOpt.get();

        if (imageFile != null && !imageFile.isEmpty()) {
            // Delete old profile image if exists
            if (user.getProfileImage() != null && !user.getProfileImage().endsWith("default.jpg")) {
                fileStorageService.deleteProfileImage(user.getProfileImage().replace("profileImages/", ""));
            }

            try {
                String profileImagePath = fileStorageService.saveProfileImage(imageFile);
                user.setProfileImage(profileImagePath);
            } catch (FileStorageException ex) {
                return ResponseEntity.badRequest()
                        .body((UserProfileDTO) Map.of("error", ex.getMessage()));
            }

        }

        User updatedUser = userRepository.save(user);

        activityLogService.logActivity(
                "User",
                updatedUser.getId(),
                "UPDATE",
                "Profile image updated",
                user.getUsername()
        );

        return ResponseEntity.ok(new UserProfileDTO(updatedUser));
    }

    @GetMapping("/profile/image/{username}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        String imagePath = userOpt.get().getProfileImage();
        if (imagePath == null || imagePath.isBlank()) return ResponseEntity.notFound().build();

        Path fullPath = Paths.get("E:\\Law's Registry System\\attachment\\profileImages")
                .resolve(Paths.get(imagePath).getFileName())
                .normalize();

        if (!Files.exists(fullPath)) return ResponseEntity.notFound().build();

        try {
            Resource resource = new UrlResource(fullPath.toUri());

            // Detect media type from extension
            String ext = fullPath.toString().toLowerCase();
            MediaType mediaType = ext.endsWith(".png, .jpg, jpeg") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticateAndGetToken(@RequestBody LoginForm loginForm) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginForm.identifier(), loginForm.password()
                    )
            );

            Optional<User> optionalUser = findByUsernameOrEmail(loginForm.identifier());

            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
            }

            User user = optionalUser.get();

            if (!user.getIsActive()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Inactive user. Please contact admin.");
            }

            if (authentication.isAuthenticated()) {
                activityLogService.logActivity(
                        "User",
                        user.getId(),
                        "LOGIN",
                        "User logged in",
                        user.getUsername()
                );
                UserDetails userDetails = myUserDetailService.loadUserByUsername(loginForm.identifier());
                String token = jwtUtilityClass.generateToken(userDetails);
                return ResponseEntity.ok(token);
            }

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid username/email or password");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Authentication error");
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader("Authorization") String token
    ) {
        String identifier = extractUsernameFromToken(token);
        Optional<User> userOpt = findByUsernameOrEmail(identifier);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            activityLogService.logActivity(
                    "User",
                    user.getId(),
                    "LOGOUT",
                    "User logged out",
                    user.getUsername()
            );
        }

        // For JWT, logout is handled on client side by deleting the token
        return ResponseEntity.ok(Map.of(
                "message", "Logout successful"
        ));
    }


    @GetMapping("/account")
    public UserResponseDTO getAccount(@RequestHeader("Authorization") String token) {
        String identifier = extractUsernameFromToken(token);
        User user = findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new UserResponseDTO(
                user.getId(),
                user.getFirstname() + " " + user.getLastname(),
                user.getFathername(),
                user.getNid(),
                user.getPhone(),
                user.getEmail(),
                user.getUsername(),
                user.getPosition(),              
                user.getRole().name(),
                user.getIsActive(),
                user.getProfileImage(),
                user.getCreateDate(),
                user.getUpdateDate()
        );
    }


    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<User> toggleUserActive(@PathVariable Long id) {
        Optional<User> existingUser = userRepository.findById(id);
        if (existingUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = existingUser.get();
        user.setIsActive(!user.getIsActive());
        User updatedUser = userRepository.save(user);

        activityLogService.logActivity(
                "User",
                updatedUser.getId(),
                "STATUS_CHANGE",
                updatedUser.getIsActive() ? "User activated" : "User deactivated",
                user.getUsername()
        );

        return ResponseEntity.ok(updatedUser);
    }
}