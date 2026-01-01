package com.mcit.service;

import com.mcit.dto.ResetPasswordOTPRequest;
import com.mcit.dto.ResetPasswordRequest;
import com.mcit.entity.ForgotPassword;
import com.mcit.entity.User;
import com.mcit.jwt.JwtUtilityClass;
import com.mcit.repo.ForgotPasswordRepository;
import com.mcit.repo.UserRepository;
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
    private UserRepository userRepository;

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
        User user = userRepository.findByEmail(email).orElse(null);

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

        User user = userRepository.findByEmail(username).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

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

            helper.setFrom("idreesdaudzai2000@gmail.com");
            helper.setTo(recipientEmail);
            helper.setSubject("Password Reset OTP | Law MIS");

            String body =  "<div style='font-family: Arial, Helvetica, sans-serif; background-color: #f4f6f8; padding: 20px;'>" +
                    "  <div style='max-width: 600px; margin: auto; background-color: #ffffff; padding: 30px; border-radius: 6px;'>" +

                    "    <h2 style='color: #2c3e50; margin-bottom: 20px;'>Password Reset Request</h2>" +

                    "    <p style='font-size: 14px; color: #333;'>Dear <strong>" + firstName + " " + lastName + "</strong>,</p>" +

                    "    <p style='font-size: 14px; color: #333;'>We received a request to reset your password for your account.</p>" +

                    "    <p style='font-size: 14px; color: #333;'>Please use the following One-Time Password (OTP) to proceed:</p>" +

                    "    <div style='text-align: center; margin: 30px 0;'>" +
                    "      <span style='display: inline-block; font-size: 28px; letter-spacing: 6px; font-weight: bold; color: #000; padding: 12px 24px; border: 1px solid #ccc; border-radius: 4px;'>" +
                    otpCode +
                    "      </span>" +
                    "    </div>" +

                    "    <p style='font-size: 14px; color: #333;'>This OTP is valid for <strong>5 minutes</strong>. For your security, please do not share this code with anyone.</p>" +

                    "    <p style='font-size: 14px; color: #333;'>If you did not request a password reset, please ignore this email.</p>" +

                    "    <hr style='border: none; border-top: 1px solid #e0e0e0; margin: 30px 0;' />" +

                    "    <p style='font-size: 13px; color: #555;'>Regards,<br>" +
                    "    <strong>Law Management System</strong><br>" +
                    "    Ministry of Law and Justice</p>" +

                    "  </div>" +
                    "</div>";

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
