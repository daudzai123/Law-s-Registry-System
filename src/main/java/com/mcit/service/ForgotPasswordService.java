package com.mcit.service;

import com.mcit.dto.ResetPasswordOTPRequest;
import com.mcit.dto.ResetPasswordRequest;
import com.mcit.entity.ForgotPassword;
import com.mcit.entity.MyUser;
import com.mcit.jwt.JwtUtilityClass;
import com.mcit.repo.ForgotPasswordRepository;
import com.mcit.repo.MyUserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ForgotPasswordService {

    @Autowired
    private MyUserRepository myUserRepository;

    @Autowired
    private ForgotPasswordRepository forgotPasswordRepository;


    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private JwtUtilityClass jwtUtilityClass;

    // Generate and send OTP
    public ResponseEntity<String> createOtpCode(ResetPasswordOTPRequest request) {
        String email = request.email();
        MyUser user = myUserRepository.findByEmail(email).orElse(null);


        if (user == null) {
            return new ResponseEntity<>("User not found with email: " + email, HttpStatus.NOT_FOUND);
        }

        String otpCode = generateOtp();

        ForgotPassword forgotPassword = new ForgotPassword();
        forgotPassword.setOtpCode(otpCode);
        forgotPassword.setOtpExpirationDate(LocalDateTime.now().plusMinutes(5));
        forgotPassword.setIsUsed(false);
        forgotPassword.setCreatedDate(LocalDateTime.now());
        forgotPassword.setUser(user);

        forgotPasswordRepository.save(forgotPassword);

        sendOtpEmail(user.getEmail(), otpCode, user.getFirstname(), user.getLastname());

        return new ResponseEntity<>("OTP has been sent to your email.", HttpStatus.OK);
    }

    // Validate OTP and reset password
    public ResponseEntity<String> requestPasswordReset(String resetToken, ResetPasswordRequest request) {
        String username = validateResetToken(resetToken);
        if (username == null) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }

        if (!Objects.equals(request.newPassword(), request.confirmNewPassword())) {
            return ResponseEntity.badRequest().body("The New Password and Confirm New Password don't match");
        }

        MyUser user = myUserRepository.findByEmail(username).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        myUserRepository.save(user);

        return new ResponseEntity<>("Password has been reset successfully.", HttpStatus.OK);
    }

    // Validate OTP code
    public ResponseEntity<String> validateOtpCode(ResetPasswordRequest request) {
        ForgotPassword forgotPassword = forgotPasswordRepository.findByOtpCode(request.otpCode());

        if (forgotPassword == null) {
            return ResponseEntity.badRequest().body("Invalid OTP code");
        }

        if (Boolean.TRUE.equals(forgotPassword.getIsUsed())) {
            return ResponseEntity.badRequest().body("This code has already been used");
        }

        if (!isOTPValid(forgotPassword)) {
            return ResponseEntity.badRequest().body("OTP code expired");
        }

        forgotPassword.setIsUsed(true);
        forgotPasswordRepository.save(forgotPassword);

        String resetToken = generateResetToken(forgotPassword.getUser().getEmail());

        // return ResponseEntity.ok().body("OTP code is valid, reset token: " + resetToken);
        return ResponseEntity.ok().body(resetToken);
    }

    // === Utility Methods ===

    private String generateOtp() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    private void sendOtpEmail(String recipientEmail, String otpCode, String firstName, String lastName) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("safi.address@gmail.com");
            helper.setTo(recipientEmail);
            helper.setSubject("Password Reset OTP");

            String body = "<p>Hi dear <strong>" + firstName + " " + lastName + "</strong>,</p>" +
                    "<p>Please use this <span style='font-weight: bold; font-size: 24px; color: black;'>" + otpCode +
                    "</span> as your 6-digit code to reset your password.</p>" +
                    "<p>This OTP is valid for <strong>5 minutes</strong>. Do not share it with anyone.</p>" +
                    "<p>Thanks,<br>Innovation Management Team</p>";

            helper.setText(body, true);
            javaMailSender.send(message);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private boolean isOTPValid(ForgotPassword forgotPassword) {
        return forgotPassword.getOtpCode() != null &&
                forgotPassword.getOtpExpirationDate() != null &&
                forgotPassword.getOtpExpirationDate().isAfter(LocalDateTime.now());
    }

    private String generateResetToken(String email) {
        return jwtUtilityClass.generateResetToken(email);
    }

    private String validateResetToken(String token) {
        return jwtUtilityClass.validateResetToken(token);
    }





}
