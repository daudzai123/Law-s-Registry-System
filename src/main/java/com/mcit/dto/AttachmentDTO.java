package com.mcit.dto;

import lombok.Data;

@Data
public class AttachmentDTO {
    private Long id;
    private String language;
    private String filePath;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private Boolean isPrimary;
    private Integer version;
    private String uploadDate;
    private String description;
}