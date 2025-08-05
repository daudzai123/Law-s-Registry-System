package com.mcit.service;

import com.mcit.exception.FileStorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    @Autowired
    public FileStorageService() {
        // Specify the directory where you want to store the uploaded files
        this.fileStorageLocation = Paths.get("E:\\Law's Registry System\\attachment").toAbsolutePath().normalize();
        try {
            // Create the directory if it doesn't exist
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the directory to store files", ex);
        }
    }

    public String saveProfileImage(MultipartFile file, String username) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String fileName = "profile_" + username + "." + extension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation.toString(); // or relative path like: "uploads/profile_username.jpg"
        } catch (IOException ex) {
            throw new FileStorageException("Could not store profile image for " + username, ex);
        }
    }



    // public String saveFile(MultipartFile file) {
    //     // Clean the file name to remove any unwanted characters
    //     String fileName = StringUtils.cleanPath(file.getOriginalFilename());
    //     try {
    //         // Define the file path where the file will be stored
    //         Path targetLocation = this.fileStorageLocation.resolve(fileName);
    //         // Copy the file to the target location, replacing any existing file
    //         Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
    //         // Return the file path as a string
    //         return targetLocation.toString();
    //     } catch (IOException ex) {
    //         throw new FileStorageException("Could not store file " + fileName, ex);
    //     }
    // }

    public String saveFile(MultipartFile file) {
        // Clean the original file name
        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFileName);
        String baseName = originalFileName.replace("." + extension, "");

        // Add timestamp to avoid overwriting
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = baseName + "_" + timestamp + "." + extension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return targetLocation.toString();
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName, ex);
        }
    }
    

    public void deleteFile(String filePath) {
        try {
            Path pathToDelete = Paths.get(filePath).toAbsolutePath().normalize();
            Files.deleteIfExists(pathToDelete);  // Delete the file if it exists
        } catch (IOException ex) {
            throw new FileStorageException("Could not delete file at " + filePath, ex);
        }
    }

}
