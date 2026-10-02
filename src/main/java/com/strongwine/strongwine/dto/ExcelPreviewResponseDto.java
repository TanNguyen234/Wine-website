package com.strongwine.strongwine.dto;

import com.strongwine.strongwine.service.strategy.ImportErrorItem;
import java.util.List;

public class ExcelPreviewResponseDto {
    private int totalRows;
    private int validRowsCount;
    private int errorRowsCount;
    private int insertCount;
    private int updateCount;
    private List<ImportErrorItem> errors;

    public ExcelPreviewResponseDto() {
    }

    public ExcelPreviewResponseDto(int totalRows, int validRowsCount, int errorRowsCount, int insertCount, int updateCount, List<ImportErrorItem> errors) {
        this.totalRows = totalRows;
        this.validRowsCount = validRowsCount;
        this.errorRowsCount = errorRowsCount;
        this.insertCount = insertCount;
        this.updateCount = updateCount;
        this.errors = errors;
    }

    public static ExcelPreviewResponseDtoBuilder builder() {
        return new ExcelPreviewResponseDtoBuilder();
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getValidRowsCount() {
        return validRowsCount;
    }

    public void setValidRowsCount(int validRowsCount) {
        this.validRowsCount = validRowsCount;
    }

    public int getErrorRowsCount() {
        return errorRowsCount;
    }

    public void setErrorRowsCount(int errorRowsCount) {
        this.errorRowsCount = errorRowsCount;
    }

    public int getInsertCount() {
        return insertCount;
    }

    public void setInsertCount(int insertCount) {
        this.insertCount = insertCount;
    }

    public int getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(int updateCount) {
        this.updateCount = updateCount;
    }

    public List<ImportErrorItem> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportErrorItem> errors) {
        this.errors = errors;
    }

    public static class ExcelPreviewResponseDtoBuilder {
        private int totalRows;
        private int validRowsCount;
        private int errorRowsCount;
        private int insertCount;
        private int updateCount;
        private List<ImportErrorItem> errors;

        public ExcelPreviewResponseDtoBuilder totalRows(int totalRows) {
            this.totalRows = totalRows;
            return this;
        }

        public ExcelPreviewResponseDtoBuilder validRowsCount(int validRowsCount) {
            this.validRowsCount = validRowsCount;
            return this;
        }

        public ExcelPreviewResponseDtoBuilder errorRowsCount(int errorRowsCount) {
            this.errorRowsCount = errorRowsCount;
            return this;
        }

        public ExcelPreviewResponseDtoBuilder insertCount(int insertCount) {
            this.insertCount = insertCount;
            return this;
        }

        public ExcelPreviewResponseDtoBuilder updateCount(int updateCount) {
            this.updateCount = updateCount;
            return this;
        }

        public ExcelPreviewResponseDtoBuilder errors(List<ImportErrorItem> errors) {
            this.errors = errors;
            return this;
        }

        public ExcelPreviewResponseDto build() {
            return new ExcelPreviewResponseDto(totalRows, validRowsCount, errorRowsCount, insertCount, updateCount, errors);
        }
    }
}
