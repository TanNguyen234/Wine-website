package com.strongwine.strongwine.service.strategy;

import java.util.ArrayList;
import java.util.List;

public class ImportResult<T> {
    private List<T> successItems = new ArrayList<>();
    private List<ImportErrorItem> errors = new ArrayList<>();
    private int updatedCount = 0;
    private int insertedCount = 0;

    public ImportResult() {
    }

    public void addSuccess(T item) {
        successItems.add(item);
    }

    public void addError(ImportErrorItem error) {
        errors.add(error);
    }

    public int getSuccessCount() {
        return successItems.size();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getTotalCount() {
        return getSuccessCount() + getErrorCount();
    }

    public boolean hasErrorsOnly() {
        return getSuccessCount() == 0 && getErrorCount() > 0;
    }

    public List<T> getSuccessItems() {
        return successItems;
    }

    public void setSuccessItems(List<T> successItems) {
        this.successItems = successItems;
    }

    public List<ImportErrorItem> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportErrorItem> errors) {
        this.errors = errors;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
        this.insertedCount = insertedCount;
    }
}
