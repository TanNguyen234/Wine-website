package com.strongwine.strongwine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Service for handling file uploads and storage
 */
@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");

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
        if (!isValidFileSize(file)) {
            throw new IllegalArgumentException("Kích thước file vượt quá giới hạn 5MB");
        }
        String extension = extractAllowedExtension(file.getOriginalFilename());
        if (extension == null || !hasMatchingMagicBytes(file, extension)) {
            throw new IllegalArgumentException("Định dạng file ảnh không hợp lệ (chỉ hỗ trợ JPG, JPEG, PNG)");
        }

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = uploadPath.resolve(filename).normalize();
        if (!filePath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Đường dẫn lưu trữ không hợp lệ");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

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
            String filename = filePath.substring("/uploads/".length());
            if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return false;
            }
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path fileToDelete = uploadRoot.resolve(filename).normalize();
            if (!fileToDelete.startsWith(uploadRoot)) {
                return false;
            }

            if (Files.exists(fileToDelete) && Files.isRegularFile(fileToDelete)) {
                Files.delete(fileToDelete);
                return true;
            }
        } catch (IOException e) {
            return false;
        }

        return false;
    }

    /**
     * Validate file type (extension + MIME + magic bytes)
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
        boolean validMime = contentType.equalsIgnoreCase("image/jpeg")
                || contentType.equalsIgnoreCase("image/jpg")
                || contentType.equalsIgnoreCase("image/png");
        if (!validMime) {
            return false;
        }

        String extension = extractAllowedExtension(file.getOriginalFilename());
        if (extension == null) {
            return false;
        }
        try {
            return hasMatchingMagicBytes(file, extension);
        } catch (IOException e) {
            return false;
        }
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
        return file.getSize() > 0 && file.getSize() <= MAX_FILE_SIZE_BYTES;
    }

    private String extractAllowedExtension(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
        String sanitized = Paths.get(originalFilename).getFileName() != null
                ? Paths.get(originalFilename).getFileName().toString()
                : originalFilename;
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex < 0) {
            return null;
        }
        String ext = sanitized.substring(dotIndex).toLowerCase(Locale.ROOT);
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : null;
    }

    private boolean hasMatchingMagicBytes(MultipartFile file, String extension) throws IOException {
        byte[] header = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(header);
        }
        if (".jpg".equals(extension) || ".jpeg".equals(extension)) {
            return read >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }
        if (".png".equals(extension)) {
            return read >= 8
                    && (header[0] & 0xFF) == 0x89
                    && (header[1] & 0xFF) == 0x50
                    && (header[2] & 0xFF) == 0x4E
                    && (header[3] & 0xFF) == 0x47
                    && (header[4] & 0xFF) == 0x0D
                    && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A
                    && (header[7] & 0xFF) == 0x0A;
        }
        return false;
    }
}






