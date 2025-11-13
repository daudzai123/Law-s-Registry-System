package com.mcit.specification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogCriteria {
    private String entityName;
    private String action;
    private LocalDate logsStartDate;
    private LocalDate logsEndDate;
    private String searchTerm;
}
