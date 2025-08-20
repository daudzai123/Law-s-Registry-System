package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import java.time.LocalDate;
import java.util.Date;

@Data
public class LawDTO {
    private Long id;
    private LawType type;
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private Status status;

    // Always Miladi in DB
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishDate;

    // Comes from frontend (Hijri string)
    private String publishDateHijri;

    private String description;
    private String attachment;
    private Long userId;
}
