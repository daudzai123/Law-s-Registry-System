package com.mcit.controller;

import com.mcit.dto.AdminUserDTO;
import com.mcit.dto.UserProfileDTO;
import com.mcit.entity.MyUser;
import com.mcit.jwt.JwtUtilityClass;
import com.mcit.jwt.LoginForm;
import com.mcit.repo.MyUserRepository;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AccountController {

    @Autowired
    private MyUserRepository myUserRepository;
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

    public AccountController(MyUserRepository myUserRepository) {
        this.myUserRepository = myUserRepository;
    }

    private Optional<MyUser> findByUsernameOrEmail(String identifier) {
        Optional<MyUser> userOpt = myUserRepository.findByUsername(identifier);
        return userOpt.isPresent() ? userOpt : myUserRepository.findByEmail(identifier);
    }

    private String extractUsernameFromToken(String token) {
        String jwt = token.replace("Bearer ", "");
        return jwtUtilityClass.extractUsername(jwt);
    }

    @PostMapping("/register")
    public ResponseEntity<?> createUser(@RequestPart("user") MyUser user,
                                        @RequestPart(value = "image", required = false) MultipartFile imageFile) {

        if (myUserRepository.existsByUsername(user.getUsername().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Username already taken", "message", "Please choose another username."));
        }

        if (myUserRepository.existsByEmail(user.getEmail().toLowerCase())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Email already taken", "message", "Please choose another email."));
        }

        String profileImagePath;
        if (imageFile != null && !imageFile.isEmpty()) {
            profileImagePath = fileStorageService.saveProfileImage(imageFile, user.getUsername());
        } else {
            profileImagePath = "D:\\Law's Registry System\\attachment\\default_avatar.jpg";
        }

        user.setUsername(user.getUsername().toLowerCase());
        user.setEmail(user.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setProfileImage(profileImagePath);
        user.setCreateDate(LocalDate.now());
        MyUser savedUser = myUserRepository.save(user);
        savedUser.setPassword(null);

        return ResponseEntity.ok(savedUser);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile(@RequestHeader("Authorization") String token) {
        String identifier = extractUsernameFromToken(token);
        Optional<MyUser> userOpt = findByUsernameOrEmail(identifier);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        return ResponseEntity.ok(new UserProfileDTO(userOpt.get()));
    }

    @PutMapping(value = "/profile", consumes = {"multipart/form-data"})
    public ResponseEntity<UserProfileDTO> updateProfile(
            @RequestHeader("Authorization") String token,
            @RequestPart(value = "image", required = false) MultipartFile imageFile) {

        String identifier = extractUsernameFromToken(token);
        Optional<MyUser> userOpt = findByUsernameOrEmail(identifier);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        MyUser user = userOpt.get();

        if (imageFile != null && !imageFile.isEmpty()) {
            if (user.getProfileImage() != null) {
                fileStorageService.deleteFile(user.getProfileImage());
            }

            String profileImagePath = fileStorageService.saveProfileImage(imageFile, user.getUsername());
            user.setProfileImage(profileImagePath);
        }

        MyUser updatedUser = myUserRepository.save(user);
        return ResponseEntity.ok(new UserProfileDTO(updatedUser));
    }

    @GetMapping("/profile/image/{username}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable String username) {
        Optional<MyUser> userOpt = myUserRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        String imagePath = userOpt.get().getProfileImage();
        Path path = Paths.get(imagePath);

        if (!Files.exists(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        try {
            Resource resource = new UrlResource(path.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .body(resource);
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
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

            Optional<MyUser> optionalUser = findByUsernameOrEmail(loginForm.identifier());

            if (optionalUser.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
            }

            MyUser user = optionalUser.get();

            if (!user.getIsActive()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Inactive user. Please contact admin.");
            }

            if (authentication.isAuthenticated()) {
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

    @GetMapping("/account")
    public AdminUserDTO getAccount(@RequestHeader("Authorization") String token) {
        String identifier = extractUsernameFromToken(token);
        Optional<MyUser> userOpt = findByUsernameOrEmail(identifier);

        if (userOpt.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        MyUser user = userOpt.get();
        return new AdminUserDTO(
                user.getId(),
                user.getFirstname(),
                user.getLastname(),
                user.getFathername(),
                user.getNid(),
                user.getPhone(),
                user.getEmail(),
                user.getUsername(),
                user.getRole()
        );
    }

    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<MyUser> toggleUserActive(@PathVariable Long id) {
        Optional<MyUser> existingUser = myUserRepository.findById(id);
        if (existingUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MyUser user = existingUser.get();
        user.setIsActive(!user.getIsActive());
        MyUser updatedUser = myUserRepository.save(user);

        return ResponseEntity.ok(updatedUser);
    }
}