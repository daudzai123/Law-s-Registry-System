package com.mcit.controller;

import com.mcit.dto.ActivityLogResponseDTO;
import com.mcit.dto.PaginatedResponseDTO;
import com.mcit.entity.ActivityLog;
import com.mcit.service.ActivityLogService;
import com.mcit.specification.ActivityLogCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/activity-log")
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;

    @GetMapping("/all")
    public ResponseEntity<PaginatedResponseDTO<?>> getActivityLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String searchItem,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {

        // Build Sort
        Sort sortObj = Sort.by(Sort.Direction.fromString(sort[1]), sort[0]);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        // If dates are provided, use advanced filter with criteria
        if (startDate != null || endDate != null || searchItem != null) {
            ActivityLogCriteria criteria = new ActivityLogCriteria();
            criteria.setEntityName(entityName);
            criteria.setAction(action);
            criteria.setUserName(userName);
            criteria.setLogsStartDate(startDate);
            criteria.setLogsEndDate(endDate);
            criteria.setSearchTerm(searchItem);

            Page<ActivityLogResponseDTO> result = activityLogService.findByCriteria(criteria, pageable);
            return ResponseEntity.ok(
                    new PaginatedResponseDTO<>(
                            result.getContent(),
                            result.getNumber(),
                            result.getSize(),
                            result.getTotalElements(),
                            result.getTotalPages(),
                            result.hasNext(),
                            result.hasPrevious()
                    )
            );
        }

        // Instead of Page<ActivityLog>
        Page<ActivityLog> result = activityLogService.getLogActivitiesFiltered(
                entityName, action, userName, searchItem, pageable
        );

        // Map to DTO
        List<ActivityLogResponseDTO> dtoList = result.getContent()
                .stream()
                .map(activityLogService::mapEntityToDTO) // use your mapper
                .toList();

        // Wrap in a PageImpl
        Page<ActivityLogResponseDTO> dtoPage = new PageImpl<>(
                dtoList,
                pageable,
                result.getTotalElements()
        );

        return ResponseEntity.ok(
                new PaginatedResponseDTO<>(
                        dtoPage.getContent(),
                        dtoPage.getNumber(),
                        dtoPage.getSize(),
                        dtoPage.getTotalElements(),
                        dtoPage.getTotalPages(),
                        dtoPage.hasNext(),
                        dtoPage.hasPrevious()
                )
        );

    }
}
