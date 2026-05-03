package com.mcit.dto;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;
import java.util.List;

@Data
public class LawSearchCriteriaDTO {
    private LawType type;  // Keep for backward compatibility
    private List<LawType> types;  // Add for multiple types support
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private String publishDate;
    private Status status;
    private Long userId;
    
    // Helper method to get effective types (prioritizes 'types' over 'type')
    public List<LawType> getEffectiveTypes() {
        if (types != null && !types.isEmpty()) {
            return types;
        }
        if (type != null) {
            return List.of(type);
        }
        return null; // null means return ALL types
    }
}