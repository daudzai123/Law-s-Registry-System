package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogResponseDTO {
    private Long id;
    private String entityName;
    private String content;
    private String action;
    private String hijriTimestamp;  // keep only Hijri
    private Long recordId;
    private String userName;
}
