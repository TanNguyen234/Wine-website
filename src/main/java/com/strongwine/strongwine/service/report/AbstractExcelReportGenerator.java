package com.strongwine.strongwine.service.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public abstract class AbstractExcelReportGenerator<C> {

    protected static final byte[] COLOR_BORDEAUX = new byte[]{(byte) 116, (byte) 44, (byte) 61}; // #742c3d
    protected static final byte[] COLOR_LIGHT_BG = new byte[]{(byte) 249, (byte) 246, (byte) 240}; // #f9f6f0
    protected static final String FONT_FAMILY = "Segoe UI";

    public byte[] generateReport(C criteria) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(getSheetName());
            sheet.setDisplayGridlines(true);

            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = getHeaders();
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            populateDataRows(sheet, criteria);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1400, 3600));
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi sinh báo cáo Excel", e);
        }
    }

    protected abstract String getSheetName();
    protected abstract String[] getHeaders();
    protected abstract void populateDataRows(Sheet sheet, C criteria);

    protected CellStyle createHeaderStyle(Workbook workbook) {
        if (workbook instanceof XSSFWorkbook xwb) {
            XSSFCellStyle style = xwb.createCellStyle();
            XSSFFont font = xwb.createFont();
            font.setFontName(FONT_FAMILY);
            font.setBold(true);
            font.setFontHeightInPoints((short) 10);
            font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(COLOR_BORDEAUX, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(style);
            return style;
        } else {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setFontName(FONT_FAMILY);
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(style);
            return style;
        }
    }

    public static void setThinBorders(CellStyle cs) {
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
        cs.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cs.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cs.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cs.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    public static void setDoubleBottomBorders(CellStyle cs) {
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderBottom(BorderStyle.DOUBLE);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
        cs.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        cs.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
    }
}
