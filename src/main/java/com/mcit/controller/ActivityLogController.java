package com.mcit.controller;

import com.mcit.dto.ActivityLogResponseDTO;
import com.mcit.entity.ActivityLog;
import com.mcit.repo.ActivityLogRepository;
import com.mcit.service.ActivityLogService;
import com.mcit.service.CurrentUserInfoService;
import com.mcit.specification.ActivityLogCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/activity-log")
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private CurrentUserInfoService currentUserInfoService;

    @GetMapping("/all")
    public Page<ActivityLog> getFilteredActivityLogs(
            Pageable pageable,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String searchItem
    ){
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("id").descending()
        );
        return activityLogService.getLogActivitiesFiltered(entityName, action, userName, searchItem, sortedPageable);
    }

    @GetMapping("/filter")
    public Page<ActivityLogResponseDTO> getFilteredActivityLogs(
            Pageable pageable,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String searchItem
    ){
        ActivityLogCriteria criteria = new ActivityLogCriteria();
        criteria.setEntityName(entityName);
        criteria.setAction(action);
        criteria.setLogsStartDate(startDate);
        criteria.setLogsEndDate(endDate);
        criteria.setSearchTerm(searchItem);

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by("id").descending()
        );

        return activityLogService.findByCriteria(criteria, sortedPageable);
    }
}
