package com.mcit.controller;

import com.mcit.dto.ActivityLogResponseDTO;
import com.mcit.dto.PaginatedResponseDTO;
import com.mcit.entity.ActivityLog;
import com.mcit.service.ActivityLogService;
import com.mcit.service.LogCleanupService;
import com.mcit.specification.ActivityLogCriteria;
import com.mcit.repo.ActivityLogRepository;  // ADD THIS IMPORT
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activity-log")
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;
    
    @Autowired
    private LogCleanupService logCleanupService;  // ADD THIS
    
    @Autowired
    private ActivityLogRepository activityLogRepository;  // ADD THIS - FIXES THE ERROR

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
    
    @GetMapping("/failed-attempts")
    public ResponseEntity<Long> getFailedAttempts(
            @RequestParam String username
    ) {
        long count = activityLogService.getFailedAttempts(username);
        return ResponseEntity.ok(count);
    }

   /**
     * Manual cleanup with flexible time units
     * Examples:
     * - DELETE /api/activity-log/cleanup?retentionValue=1&unit=YEARS
     * - DELETE /api/activity-log/cleanup?retentionValue=6&unit=MONTHS
     * - DELETE /api/activity-log/cleanup?retentionValue=30&unit=DAYS
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> manualCleanup(
            @RequestParam(defaultValue = "1") int retentionValue,
            @RequestParam(defaultValue = "YEARS") String unit
    ) {
        int deleted = logCleanupService.manualCleanup(retentionValue, unit);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", String.format("Cleanup completed: %d logs older than %d %s were deleted", 
                     deleted, retentionValue, unit));
        response.put("deletedCount", deleted);
        response.put("retentionValue", retentionValue);
        response.put("unit", unit);
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get retention policy information
     */
    @GetMapping("/retention-info")
    public ResponseEntity<Map<String, Object>> getRetentionInfo() {
        Map<String, Object> info = logCleanupService.getRetentionStatistics();
        
        // Add policy information
        info.put("retentionPolicy", "Keep last 1 year of logs");
        info.put("cleanupSchedule", "Monthly on the 1st at 2 AM");
        info.put("nextCleanupEstimate", getNextCleanupDate());
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * Get logs grouped by year/month (for monitoring)
     */
    @GetMapping("/statistics/by-year")
    public ResponseEntity<Map<String, Object>> getLogsByYear() {
        Map<String, Object> stats = new HashMap<>();
        
        // Get current year
        int currentYear = LocalDateTime.now().getYear();
        
        // Check logs for current year and previous year
        for (int i = 0; i <= 2; i++) {
            int year = currentYear - i;
            LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0, 0);
            LocalDateTime endOfYear = LocalDateTime.of(year, 12, 31, 23, 59, 59);
            
            long count = activityLogRepository.countLogsBetween(startOfYear, endOfYear);
            stats.put("year_" + year, count);
        }
        
        // Add oldest and newest log info
        LocalDateTime oldest = activityLogRepository.getOldestLogTimestamp();
        LocalDateTime newest = activityLogRepository.getNewestLogTimestamp();
        
        stats.put("oldestLogYear", oldest != null ? oldest.getYear() : "N/A");
        stats.put("newestLogYear", newest != null ? newest.getYear() : "N/A");
        stats.put("totalLogs", activityLogRepository.count());
        
        return ResponseEntity.ok(stats);
    }
    
    private String getNextCleanupDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextCleanup;
        
        if (now.getDayOfMonth() == 1 && now.getHour() < 2) {
            nextCleanup = now.withHour(2).withMinute(0).withSecond(0);
        } else {
            nextCleanup = now.plusMonths(1)
                              .withDayOfMonth(1)
                              .withHour(2)
                              .withMinute(0)
                              .withSecond(0);
        }
        
        return nextCleanup.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}