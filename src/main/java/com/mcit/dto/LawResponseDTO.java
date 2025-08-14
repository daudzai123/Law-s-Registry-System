package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class LawResponseDTO {
    private Long id;
    private LawType type;
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishDate;
    private Status status;
    private String description;
    private String attachment;
    private String attachmentSize;
    private Long userId;
}
