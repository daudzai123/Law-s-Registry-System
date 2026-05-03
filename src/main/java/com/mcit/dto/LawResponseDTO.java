package com.mcit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;
import java.util.List;

@Data
public class LawResponseDTO {
    private Long id;
    private LawType type;
    private Long sequenceNumber;
    private String collection;
    private String collection2;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private String publishDate;
    private Status status;
    private String descriptionEng;
    private String descriptionPs;
    private String descriptionDr;
    private String createDate;
    private String updateDate;
    private String attachmentSize;
    private Long userId;
    @JsonProperty("public")
     private boolean isPublic; 
    // Only use attachments array - remove old fields
    private List<AttachmentDTO> attachments;
}