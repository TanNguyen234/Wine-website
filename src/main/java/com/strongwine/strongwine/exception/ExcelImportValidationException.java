package com.strongwine.strongwine.exception;

import com.strongwine.strongwine.service.strategy.ImportErrorItem;
import java.util.List;

public class ExcelImportValidationException extends StrongWineException {
    private final List<ImportErrorItem> errors;

    public ExcelImportValidationException(String message, List<ImportErrorItem> errors) {
        super(message);
        this.errors = errors;
    }

    public List<ImportErrorItem> getErrors() {
        return errors;
    }
}
