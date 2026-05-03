package com.mcit.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

@Entity
@Table(name = "law_attachments")
@Data
public class LawAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "law_id", nullable = false)
    private Law law;

    @Column(nullable = false, length = 10)
    private String language; // 'eng', 'ps', 'ar', etc.

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "is_primary")
    private Boolean isPrimary = false;

    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "upload_date")
    private String uploadDate;



    @PrePersist
    public void onCreate() {
        this.uploadDate = currentHijriDateTime();
    }

    private String currentHijriDateTime() {
        HijrahDate hijri = HijrahDate.now();
        LocalTime time = LocalTime.now();
        return String.format("%04d-%02d-%02d %02d:%02d",
                hijri.get(ChronoField.YEAR),
                hijri.get(ChronoField.MONTH_OF_YEAR),
                hijri.get(ChronoField.DAY_OF_MONTH),
                time.getHour(),
                time.getMinute());
    }
}