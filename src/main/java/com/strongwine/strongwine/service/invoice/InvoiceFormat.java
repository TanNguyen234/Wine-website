package com.strongwine.strongwine.service.invoice;

public enum InvoiceFormat {
    PDF("application/pdf", ".pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");

    private final String contentType;
    private final String extension;

    InvoiceFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public static InvoiceFormat fromParam(String rawFormat) {
        if (rawFormat == null || rawFormat.isBlank()) {
            return PDF;
        }
        String normalized = rawFormat.trim().toUpperCase();
        return switch (normalized) {
            case "PDF" -> PDF;
            case "XLSX", "EXCEL" -> XLSX;
            default -> throw new IllegalArgumentException("Unsupported invoice format: " + rawFormat);
        };
    }
}
