package com.strongwine.strongwine.service.strategy;

import java.util.List;

public interface DataExportStrategy<T> {
    byte[] exportData(List<T> data);
    String getContentType();
    String getFileExtension();
}
