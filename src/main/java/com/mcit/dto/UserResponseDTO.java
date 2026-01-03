package com.mcit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String fullName;
    private String fathername;
    private String nid;
    private String phone;
    private String email;
    private String username;
    private String position;
    private String role;
    private boolean isActive;
    private String profileImage;
    private String createDate;
    private String updateDate;
}

