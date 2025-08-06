package com.mcit.dto;

import lombok.Data;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import java.time.LocalDate;

@Data
public class LawDTO {
    private Long id;
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private LawType type;
    private Status status;
    private LocalDate tawsheehDate;
    private String tawsheehDepartment;
    private String description;
    private String attachment;
    private Long userId; // instead of MyUser entity
}

