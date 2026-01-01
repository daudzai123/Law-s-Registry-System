package com.mcit.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@ToString
@EnableAspectJAutoProxy
public class BackupDB {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String backupPath;
    private LocalDateTime created_at;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties(
            value = { "lastname","position","isActive",
                    "profileImage","email","password" },
            allowSetters = true
    )

    private User creator;

}
