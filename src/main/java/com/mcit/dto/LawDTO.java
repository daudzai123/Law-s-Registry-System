package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;
import java.util.List;

@Data
public class LawDTO {
    private Long id;
    private Long sequenceNumber;
    private String collection;
    private String collection2;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private LawType type;
    private Status status;
    private String descriptionEng;
    private String descriptionPs;
    private String descriptionDr;
    private Long userId;
    private String publishDate;
    private String createDate;
    private String updateDate;
    private List<AttachmentDTO> attachments;
    @JsonProperty("public")
     private boolean isPublic;
}