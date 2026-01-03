package com.mcit.entity;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;

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

    @Column(name = "create_date", updatable = false, nullable = false)
    private String createDate;

    // ✅ MUST be updatable
    @Column(name = "updated_date")
    private String updateDate;

    // 🔹 Set create date
    @PrePersist
    public void onCreate() {
        this.createDate = currentHijriDateTime();
    }

    // 🔹 Set update date
    @PreUpdate
    public void onUpdate() {
        this.updateDate = currentHijriDateTime();
    }

    private String currentHijriDateTime() {
        HijrahDate hijri = HijrahDate.now();
        LocalTime time = LocalTime.now();

        return String.format(
                "%04d-%02d-%02d %02d:%02d",
                hijri.get(ChronoField.YEAR),
                hijri.get(ChronoField.MONTH_OF_YEAR),
                hijri.get(ChronoField.DAY_OF_MONTH),
                time.getHour(),
                time.getMinute()
        );
    }
}
