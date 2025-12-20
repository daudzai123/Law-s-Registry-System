package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserResponseDTO {
    private Long id;
    private String firstname;
    private String lastname;
    private String email;
    private String username;
    private String role;
    private boolean isActive;
    private String position;
}
