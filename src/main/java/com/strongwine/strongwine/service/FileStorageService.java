package com.strongwine.strongwine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service for handling file uploads and storage
 */
@Service
public class FileStorageService {
    
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;
    
    /**
     * Save uploaded file and return the file path
     * @param file The multipart file to save
     * @return The relative path to the saved file
     * @throws IOException if file cannot be saved
     */
    public String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + extension;
        
        // Save file
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        // Return relative path for web access
        return "/uploads/" + filename;
    }
    
    /**
     * Delete a file by its path
     * @param filePath The path to the file to delete
     * @return true if file was deleted, false otherwise
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty() || !filePath.startsWith("/uploads/")) {
            return false;
        }
        
        try {
            // Remove /uploads/ prefix to get filename
            String filename = filePath.substring("/uploads/".length());
            Path fileToDelete = Paths.get(uploadDir, filename);
            
            if (Files.exists(fileToDelete)) {
                Files.delete(fileToDelete);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error deleting file: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Validate file type
     * @param file The file to validate
     * @return true if file type is valid (jpg, jpeg, png)
     */
    public boolean isValidImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        String contentType = file.getContentType();
        if (contentType == null) {
            return false;
        }
        
        return contentType.equals("image/jpeg") || 
               contentType.equals("image/jpg") || 
               contentType.equals("image/png");
    }
    
    /**
     * Validate file size (max 5MB)
     * @param file The file to validate
     * @return true if file size is within limit
     */
    public boolean isValidFileSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        
        long maxSize = 5 * 1024 * 1024; // 5MB
        return file.getSize() <= maxSize;
    }
}






