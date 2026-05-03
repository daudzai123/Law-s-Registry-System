package com.mcit.entity;

import com.mcit.enums.LawType;
import com.mcit.enums.Status;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Column(name = "collection")
    private String collection;

    @Column(name = "collection2")
    private String collection2;


    private String titleEng;
    private String titlePs;
    private String titleDr;

    @Column(name = "publish_date")
    private String publishDate;

    @Enumerated(EnumType.STRING)
    private Status status;

     @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;  // Change default to false (hide by default)
    
    public boolean isPublic() {
        return isPublic;
    }
    
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    private String descriptionEng;
    private String descriptionPs;
    private String descriptionDr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User user;

    @Column(name = "create_date", updatable = false)
    private String createDate;

    @Column(name = "updated_date")
    private String updateDate;

    // One-to-Many relationship with attachments
    @OneToMany(mappedBy = "law", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<LawAttachment> attachments = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        this.createDate = currentHijriDateTime();
    }

    @PreUpdate
    public void onUpdate() {
        this.updateDate = currentHijriDateTime();
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
    
    // Helper methods for attachments
    public void addAttachment(LawAttachment attachment) {
        attachments.add(attachment);
        attachment.setLaw(this);
    }
    
    public void removeAttachment(LawAttachment attachment) {
        attachments.remove(attachment);
        attachment.setLaw(null);
    }
    
    public LawAttachment getAttachmentByLanguage(String language) {
        return attachments.stream()
                .filter(a -> a.getLanguage().equalsIgnoreCase(language))
                .findFirst()
                .orElse(null);
    }
    
    public List<LawAttachment> getAttachmentsByLanguage(String language) {
        return attachments.stream()
                .filter(a -> a.getLanguage().equalsIgnoreCase(language))
                .collect(Collectors.toList());
    }
}