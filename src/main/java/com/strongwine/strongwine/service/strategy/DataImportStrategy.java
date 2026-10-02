package com.strongwine.strongwine.service.strategy;

import java.io.InputStream;

public interface DataImportStrategy<T> {
    ImportResult<T> importData(InputStream inputStream, ImportOptions options);
    boolean supportsFormat(String fileExtension);
}
