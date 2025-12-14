package com.mcit.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
@Entity
@Table(
        name = "laws",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "sequence_number")
        }
)
public class Law {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private LawType type;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "title_eng", nullable = true)
    private String titleEng;

    @Column(name = "title_ps", nullable = false)
    private String titlePs;

    @Column(name = "title_dr", nullable = false)
    private String titleDr;

    @Column(name = "publish_date", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate publishDate;


    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "attachment")
    private String attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private MyUser user;
}
