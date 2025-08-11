package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class LawSearchCriteriaDTO {
    private LawType type;
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishDate; // in yyyy-MM-dd format
    private Status status;
    private Long userId;
}
