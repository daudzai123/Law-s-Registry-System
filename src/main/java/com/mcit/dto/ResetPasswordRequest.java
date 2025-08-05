package com.mcit.dto;

public record ResetPasswordRequest(
        String newPassword,
        String confirmNewPassword,
        String otpCode
) {
}
