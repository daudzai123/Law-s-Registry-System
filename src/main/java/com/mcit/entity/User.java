package com.mcit.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mcit.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstname;

    private String lastname;

    private String fathername;

    @Column(unique = true)
    private String nid;

    @Column(unique = true)
    private String phone;

    @NotBlank
    @Email
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String username;

    @NotBlank
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(columnDefinition = "boolean default true")
    public Boolean isActive = true;

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

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "position")
    private String position;


}
