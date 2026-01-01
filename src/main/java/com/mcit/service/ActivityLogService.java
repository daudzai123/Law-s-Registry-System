package com.mcit.service;

import com.mcit.dto.ActivityLogResponseDTO;
import com.mcit.entity.ActivityLog;
import com.mcit.repo.ActivityLogRepository;
import com.mcit.specification.ActivityLogCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private CurrentUserInfoService currentUserInfoService;

    /**
     * Logs an activity.
     * If username is null, the currently authenticated user is used.
     */
    public void logActivity(
            String entityName,
            Long recordId,
            String action,
            String content,
            String username
    ) {
        String finalUsername =
                (username != null && !username.isBlank())
                        ? username
                        : currentUserInfoService.getCurrentUserUsername();

        ActivityLog activityLog = new ActivityLog();
        activityLog.setTimestamp(LocalDateTime.now());
        activityLog.setAction(action);
        activityLog.setEntityName(entityName);
        activityLog.setRecordId(recordId);
        activityLog.setUserName(finalUsername);
        activityLog.setContent(content);

        activityLogRepository.save(activityLog);
    }

    public Page<ActivityLog> getLogActivitiesFiltered(
            String entityName,
            String action,
            String userName,
            String searchItem,
            Pageable pageable
    ) {
        Page<ActivityLog> activityLogs =
                activityLogRepository.filterActivityLogs(
                        entityName, action, userName, searchItem, pageable);

        return new PageImpl<>(
                activityLogs.getContent(),
                pageable,
                activityLogs.getTotalElements()
        );
    }

    public Page<ActivityLogResponseDTO> findByCriteria(
            ActivityLogCriteria criteria,
            Pageable pageable
    ) {
        Specification<ActivityLog> spec =
                activityLogRepository.buildSpecification(criteria);

        Page<ActivityLog> all =
                activityLogRepository.findAll(spec, pageable);

        List<ActivityLogResponseDTO> dtoList = all.getContent()
                .stream()
                .map(this::mapEntityToDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, all.getTotalElements());
    }

    private ActivityLogResponseDTO mapEntityToDTO(ActivityLog activityLog) {
        ActivityLogResponseDTO dto = new ActivityLogResponseDTO();
        dto.setId(activityLog.getId());
        dto.setEntityName(activityLog.getEntityName());
        dto.setAction(activityLog.getAction());
        dto.setContent(activityLog.getContent());
        dto.setTimestamp(activityLog.getTimestamp());
        dto.setRecordId(activityLog.getRecordId());
        dto.setUserName(activityLog.getUserName());
        return dto;
    }
}
