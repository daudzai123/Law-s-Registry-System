package com.mcit.controller;

import com.mcit.entity.Law;
import com.mcit.entity.MyUser;
import com.mcit.exception.DuplicateLawException;
import com.mcit.repo.MyUserRepository;
import com.mcit.service.FileStorageService;
import com.mcit.service.LawService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/laws")
@RequiredArgsConstructor
public class LawController {

    private final LawService lawService;
    private final MyUserRepository userRepository;
    private final FileStorageService fileStorageService;

    private static final long MAX_FILE_SIZE = 3 * 1024 * 1024; // 3 MB

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> addLaw(
            @RequestPart("law") Law law,
            @RequestPart(value = "attachment", required = false) MultipartFile attachmentFile,
            HttpServletRequest request
    ) throws IOException {

        // Validate logged-in user
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<MyUser> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid user.");
        }
        law.setUser(userOpt.get());

        // Set tawsheehDate if null
        if (law.getTawsheehDate() == null) {
            law.setTawsheehDate(LocalDate.now());
        }

        // Validate attachment presence (required)
        if (attachmentFile == null || attachmentFile.isEmpty()) {
            return ResponseEntity.badRequest().body("Attachment file is required.");
        }

        // Validate attachment size
        if (attachmentFile.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body("Attachment size exceeds 3MB limit.");
        }

        // Validate attachment file type by content type AND extension (pdf or docx)
        if (!isAllowedFile(attachmentFile)) {
            return ResponseEntity.badRequest()
                    .body("Only PDF and DOCX files are allowed.");
        }

        // Save attachment file and set filename in Law entity
        String savedFilename = fileStorageService.saveFile(attachmentFile);
        law.setAttachment(savedFilename);

        try {
            Law savedLaw = lawService.saveLaw(law);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedLaw);
        } catch (DuplicateLawException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save law.");
        }
    }

    @GetMapping
    public ResponseEntity<List<Law>> getAllLaws() {
        return ResponseEntity.ok(lawService.findAll());
    }

    private boolean isAllowedFile(MultipartFile file) {
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean validContentType = "application/pdf".equalsIgnoreCase(contentType) ||
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType);

        boolean validExtension = filename != null && filename.toLowerCase().matches(".*\\.(pdf|docx)$");

        return validContentType && validExtension;
    }
}
