package com.mcit.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BackupDTO {
    Long id;
    String backupPath;
    LocalDateTime created_at;
    Long creator;
}
