package com.mcit.entity;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "laws")
@Data
public class Law {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private LawType type;

    @Column(name = "sequence_number")
    private Long sequenceNumber;

    private String titleEng;

    @Column(nullable = false)
    private String titlePs;

    @Column(nullable = false)
    private String titleDr;

    @Column(name = "publish_date", nullable = false)
    private String publishDate; // Hijri-Qamari only

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

}
