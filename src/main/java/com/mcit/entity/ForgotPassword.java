package com.mcit.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class ForgotPassword {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String otpCode;
        private LocalDateTime otpExpirationDate;
        private Boolean isUsed;
        private LocalDateTime createdDate;

        // Many-to-One relationship with User
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private MyUser user;
}