package com.mcit.dto;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;

import java.util.Map;

@Data
public class LawSummaryReportDTO {

    private int year;
    private Integer month;

    private Map<LawType, Long> byType;
    private Map<Status, Long> byStatus;
    private Map<LawType, Map<Status, Long>> byTypeAndStatus;
}
