package com.strongwine.strongwine.service.strategy;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult<T> {
    private List<T> successItems = new ArrayList<>();
    private List<ImportErrorItem> errors = new ArrayList<>();
    private int updatedCount = 0;
    private int insertedCount = 0;

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
}
