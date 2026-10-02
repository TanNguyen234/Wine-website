package com.strongwine.strongwine.service.strategy;

public class ImportErrorItem {
    private int rowNumber;
    private String fieldName;
    private String invalidValue;
    private String errorMessage;

    public ImportErrorItem() {
    }

    public ImportErrorItem(int rowNumber, String fieldName, String invalidValue, String errorMessage) {
        this.rowNumber = rowNumber;
        this.fieldName = fieldName;
        this.invalidValue = invalidValue;
        this.errorMessage = errorMessage;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getInvalidValue() {
        return invalidValue;
    }

    public void setInvalidValue(String invalidValue) {
        this.invalidValue = invalidValue;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
