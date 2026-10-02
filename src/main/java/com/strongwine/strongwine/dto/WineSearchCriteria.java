package com.strongwine.strongwine.dto;

import java.math.BigDecimal;
import java.util.List;

public class WineSearchCriteria {
    private String keyword;
    private List<String> types;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private List<String> countries;
    private List<Integer> vintageYears;
    private Long categoryId;
    private Boolean inStockOnly;

    public WineSearchCriteria() {
    }

    public WineSearchCriteria(String keyword, List<String> types, BigDecimal minPrice, BigDecimal maxPrice,
                              List<String> countries, List<Integer> vintageYears, Long categoryId, Boolean inStockOnly) {
        this.keyword = keyword;
        this.types = types;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.countries = countries;
        this.vintageYears = vintageYears;
        this.categoryId = categoryId;
        this.inStockOnly = inStockOnly;
    }

    public static WineSearchCriteriaBuilder builder() {
        return new WineSearchCriteriaBuilder();
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public List<String> getCountries() {
        return countries;
    }

    public void setCountries(List<String> countries) {
        this.countries = countries;
    }

    public List<Integer> getVintageYears() {
        return vintageYears;
    }

    public void setVintageYears(List<Integer> vintageYears) {
        this.vintageYears = vintageYears;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Boolean getInStockOnly() {
        return inStockOnly;
    }

    public void setInStockOnly(Boolean inStockOnly) {
        this.inStockOnly = inStockOnly;
    }

    public static class WineSearchCriteriaBuilder {
        private String keyword;
        private List<String> types;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private List<String> countries;
        private List<Integer> vintageYears;
        private Long categoryId;
        private Boolean inStockOnly;

        public WineSearchCriteriaBuilder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public WineSearchCriteriaBuilder types(List<String> types) {
            this.types = types;
            return this;
        }

        public WineSearchCriteriaBuilder minPrice(BigDecimal minPrice) {
            this.minPrice = minPrice;
            return this;
        }

        public WineSearchCriteriaBuilder maxPrice(BigDecimal maxPrice) {
            this.maxPrice = maxPrice;
            return this;
        }

        public WineSearchCriteriaBuilder countries(List<String> countries) {
            this.countries = countries;
            return this;
        }

        public WineSearchCriteriaBuilder vintageYears(List<Integer> vintageYears) {
            this.vintageYears = vintageYears;
            return this;
        }

        public WineSearchCriteriaBuilder categoryId(Long categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public WineSearchCriteriaBuilder inStockOnly(Boolean inStockOnly) {
            this.inStockOnly = inStockOnly;
            return this;
        }

        public WineSearchCriteria build() {
            return new WineSearchCriteria(keyword, types, minPrice, maxPrice, countries, vintageYears, categoryId, inStockOnly);
        }
    }
}
