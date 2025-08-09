package com.mcit.dto;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;

@Data
public class LawSearchCriteriaDTO {
    private LawType type;
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private String publishDate; // in yyyy-MM-dd format
    private Status status;
    private Long userId;
}
