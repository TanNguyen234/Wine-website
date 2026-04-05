package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.WineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Year;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service class for Wine business logic
 */
@Service
@Transactional
public class WineService {

    private static final Set<String> ALLOWED_WINE_TYPES = new HashSet<>(Arrays.asList("Red", "White", "Rose", "Sparkling"));
    private static final String DEFAULT_IMAGE_URL = "https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=900&q=80";
    
    @Autowired
    private WineRepository wineRepository;
    
    /**
     * Get all wines
     */
    public List<Wine> getAllWines() {
        return wineRepository.findByDeletedFalse();
    }
    
    public Page<Wine> getAllWinesPage(Pageable pageable) {
        return wineRepository.findByDeletedFalse(pageable);
    }
    
    public long countWines() {
        return wineRepository.count();
    }

    public List<Object[]> getTopSellingWines(int limit) {
        return wineRepository.getTopSellingWines(org.springframework.data.domain.PageRequest.of(0, limit));
    }
    
    /**
     * Get wine by ID
     */
    public Optional<Wine> getWineById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return wineRepository.findById(id).filter(w -> !w.isDeleted());
    }
    
    /**
     * Create a new wine
     */
    public Wine createWine(Wine wine) {
        normalizeAndValidateWine(wine, null);
        return wineRepository.save(wine);
    }
    
    /**
     * Update an existing wine
     */
    public Wine updateWine(Long id, Wine wineDetails) {
        Wine wine = wineRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wine not found with id: " + id));
        if (wine.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wine is deleted");
        }

        normalizeAndValidateWine(wineDetails, id);
        
        wine.setName(wineDetails.getName());
        wine.setType(wineDetails.getType());
        wine.setYear(wineDetails.getYear());
        wine.setPrice(wineDetails.getPrice());
        wine.setDescription(wineDetails.getDescription());
        wine.setCountry(wineDetails.getCountry());
        wine.setImageUrl(wineDetails.getImageUrl());
        wine.setCategory(wineDetails.getCategory());
        
        return wineRepository.save(wine);
    }
    
    /**
     * Delete a wine
     * Note: Soft delete implemented
     */
    public void deleteWine(Long id) {
        Wine wine = wineRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wine not found with id: " + id));
        if (wine.isDeleted()) {
            return;
        }
        wine.setDeleted(true);
        wineRepository.save(wine);
    }
    
    /**
     * Get wine by ID (throws exception if not found)
     */
    public Wine getWineByIdOrThrow(Long id) {
        Wine wine = wineRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wine not found with id: " + id));
        if (wine.isDeleted()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Wine is deleted");
        return wine;
    }

    public Page<Wine> searchWinesPage(String keyword, String type, String country, Integer year,
                                      BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        String normalizedKeyword = normalizeOptionalText(keyword, 255);
        String normalizedCountry = normalizeOptionalText(country, 100);
        String normalizedType = normalizeType(type, false);

        if (year != null && (year < 1900 || year > Year.now().getValue() + 1)) {
            throw new IllegalArgumentException("Năm sản xuất không hợp lệ");
        }
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá tối thiểu không hợp lệ");
        }
        if (maxPrice != null && maxPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Giá tối đa không hợp lệ");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("Khoảng giá không hợp lệ");
        }

        return wineRepository.searchWines(normalizedKeyword, normalizedType, normalizedCountry, year, minPrice, maxPrice, pageable);
    }
    
    /**
     * Search wines by name, type, year, and price range
     * Name search is case-insensitive and supports partial matching
     */
    public List<Wine> searchWines(String keyword, String type, String country, Integer year,
                                 BigDecimal minPrice, BigDecimal maxPrice) {
        return searchWinesPage(keyword, type, country, year, minPrice, maxPrice, Pageable.unpaged()).getContent();
    }
    
    /**
     * Get wines by type
     */
    public List<Wine> getWinesByType(String type) {
        return wineRepository.findByTypeAndDeletedFalse(type);
    }
    
    /**
     * Get wines by year
     */
    public List<Wine> getWinesByYear(Integer year) {
        return wineRepository.findByYearAndDeletedFalse(year);
    }
    
    /**
     * Get wines by price range
     */
    public List<Wine> getWinesByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return wineRepository.findByPriceBetweenAndDeletedFalse(minPrice, maxPrice);
    }

    private void normalizeAndValidateWine(Wine wine, Long currentWineId) {
        if (wine == null) {
            throw new IllegalArgumentException("Dữ liệu sản phẩm không hợp lệ");
        }

        String normalizedName = normalizeOptionalText(wine.getName(), 255);
        if (normalizedName == null) {
            throw new IllegalArgumentException("Tên sản phẩm là bắt buộc");
        }

        if (currentWineId == null) {
            if (wineRepository.existsByNameIgnoreCaseAndDeletedFalse(normalizedName)) {
                throw new IllegalArgumentException("Tên sản phẩm đã tồn tại");
            }
        } else if (wineRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(normalizedName, currentWineId)) {
            throw new IllegalArgumentException("Tên sản phẩm đã tồn tại");
        }

        String normalizedType = normalizeType(wine.getType(), true);
        Integer normalizedYear = wine.getYear();
        if (normalizedYear == null || normalizedYear < 1900 || normalizedYear > Year.now().getValue() + 1) {
            throw new IllegalArgumentException("Năm sản xuất không hợp lệ");
        }

        BigDecimal normalizedPrice = wine.getPrice();
        if (normalizedPrice == null || normalizedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Giá sản phẩm phải lớn hơn 0");
        }

        String normalizedDescription = normalizeOptionalText(wine.getDescription(), 1000);
        String normalizedCountry = normalizeOptionalText(wine.getCountry(), 100);
        String normalizedImageUrl = normalizeImageUrl(wine.getImageUrl());

        wine.setName(normalizedName);
        wine.setType(normalizedType);
        wine.setYear(normalizedYear);
        wine.setPrice(normalizedPrice.stripTrailingZeros());
        wine.setDescription(normalizedDescription);
        wine.setCountry(normalizedCountry);
        wine.setImageUrl(normalizedImageUrl);
    }

    private String normalizeType(String type, boolean required) {
        String normalized = normalizeOptionalText(type, 50);
        if (normalized == null) {
            if (required) {
                throw new IllegalArgumentException("Loại rượu là bắt buộc");
            }
            return null;
        }

        if (!ALLOWED_WINE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Loại rượu không hợp lệ");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    private String normalizeImageUrl(String imageUrl) {
        String normalized = normalizeOptionalText(imageUrl, 500);
        if (normalized == null) {
            return DEFAULT_IMAGE_URL;
        }

        boolean valid = normalized.startsWith("https://") || normalized.startsWith("http://") || normalized.startsWith("/uploads/");
        if (!valid) {
            throw new IllegalArgumentException("Đường dẫn ảnh không hợp lệ");
        }
        return normalized;
    }
}

