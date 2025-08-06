package com.mcit.entity;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "laws", uniqueConstraints = {
        @UniqueConstraint(columnNames = "sequence_number"),
        @UniqueConstraint(columnNames = "title_eng"),
        @UniqueConstraint(columnNames = "title_ps"),
        @UniqueConstraint(columnNames = "title_dr")
})
public class Law {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "title_eng", nullable = false)
    private String titleEng;

    @Column(name = "title_ps", nullable = false)
    private String titlePs;

    @Column(name = "title_dr", nullable = false)
    private String titleDr;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private LawType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    // store Hijri date string like "1446-02-15" or localized format
    @Column(name = "publish_date")
    private String publishDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "attachment")
    private String attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private MyUser user;
}
