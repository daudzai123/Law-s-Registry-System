package com.mcit.repo;

import com.mcit.entity.LawAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface LawAttachmentRepository extends JpaRepository<LawAttachment, Long> {
    
    List<LawAttachment> findByLawId(Long lawId);
    
    List<LawAttachment> findByLawIdAndLanguage(Long lawId, String language);
    
    @Modifying
    @Transactional
    @Query("DELETE FROM LawAttachment a WHERE a.law.id = :lawId")
    void deleteByLawId(@Param("lawId") Long lawId);
    
    Optional<LawAttachment> findByLawIdAndIsPrimaryTrue(Long lawId);
    
    long countByLawId(Long lawId);
    long countByFilePath(String filePath);
    
    
}