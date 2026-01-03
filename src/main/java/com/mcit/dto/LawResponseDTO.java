package com.mcit.dto;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import lombok.Data;

@Data
public class LawResponseDTO {

    private Long id;
    private LawType type;
    private Long sequenceNumber;
    private String titleEng;
    private String titlePs;
    private String titleDr;
    private String publishDate;
    private Status status;
    private String description;
    private String createDate;
    private String updateDate;
    private String attachment;
    private String attachmentSize;
    private Long userId;
}
