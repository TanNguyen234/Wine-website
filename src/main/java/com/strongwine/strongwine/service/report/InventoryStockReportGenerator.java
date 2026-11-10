package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.repository.InventoryRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InventoryStockReportGenerator extends AbstractExcelReportGenerator<Void> {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventoryRepository inventoryRepository;

    public InventoryStockReportGenerator(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    protected String getSheetName() {
        return "Báo Cáo Tồn Kho";
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{
                "STT",
                "Mã Rượu",
                "Tên Rượu Vang",
                "Kho Lưu Trữ",
                "Tồn Thực Tế (Chai)",
                "Đang Giữ Chỗ (Chai)",
                "Khả Dụng (Chai)",
                "Ngưỡng Cảnh Báo",
                "Trạng Thái",
                "Cập Nhật Lúc"
        };
    }

    @Override
    public byte[] generateReport(Void criteria) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(getSheetName());
            sheet.setDisplayGridlines(true);

            DataFormat df = workbook.createDataFormat();

            // Fonts
            XSSFFont titleFont = workbook.createFont();
            titleFont.setFontName(FONT_FAMILY);
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFFont subFont = workbook.createFont();
            subFont.setFontName(FONT_FAMILY);
            subFont.setItalic(true);
            subFont.setFontHeightInPoints((short) 9);
            subFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            XSSFFont headerFont = workbook.createFont();
            headerFont.setFontName(FONT_FAMILY);
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFFont regularFont = workbook.createFont();
            regularFont.setFontName(FONT_FAMILY);
            regularFont.setFontHeightInPoints((short) 10);

            XSSFFont warnFont = workbook.createFont();
            warnFont.setFontName(FONT_FAMILY);
            warnFont.setBold(true);
            warnFont.setFontHeightInPoints((short) 10);
            warnFont.setColor(IndexedColors.RED.getIndex());

            XSSFFont okFont = workbook.createFont();
            okFont.setFontName(FONT_FAMILY);
            okFont.setBold(true);
            okFont.setFontHeightInPoints((short) 10);
            okFont.setColor(IndexedColors.DARK_GREEN.getIndex());

            XSSFFont totalFont = workbook.createFont();
            totalFont.setFontName(FONT_FAMILY);
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 10);

            // Styles
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(new XSSFColor(COLOR_BORDEAUX, null));
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle subtitleStyle = workbook.createCellStyle();
            subtitleStyle.setFont(subFont);
            subtitleStyle.setAlignment(HorizontalAlignment.CENTER);
            subtitleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(COLOR_BORDEAUX, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(headerStyle);

            XSSFCellStyle leftStyle = workbook.createCellStyle();
            leftStyle.setFont(regularFont);
            leftStyle.setAlignment(HorizontalAlignment.LEFT);
            leftStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(leftStyle);

            XSSFCellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setFont(regularFont);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(centerStyle);

            XSSFCellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setFont(regularFont);
            numberStyle.setDataFormat(df.getFormat("#,##0"));
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);
            numberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(numberStyle);

            XSSFCellStyle warnStyle = workbook.createCellStyle();
            warnStyle.setFont(warnFont);
            warnStyle.setAlignment(HorizontalAlignment.CENTER);
            warnStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(warnStyle);

            XSSFCellStyle okStyle = workbook.createCellStyle();
            okStyle.setFont(okFont);
            okStyle.setAlignment(HorizontalAlignment.CENTER);
            okStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(okStyle);

            XSSFCellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(totalFont);
            totalLabelStyle.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(totalLabelStyle);

            XSSFCellStyle totalNumberStyle = workbook.createCellStyle();
            totalNumberStyle.setFont(totalFont);
            totalNumberStyle.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            totalNumberStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalNumberStyle.setDataFormat(df.getFormat("#,##0"));
            totalNumberStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalNumberStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(totalNumberStyle);

            String[] headers = getHeaders();

            // 1. Title Row
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(34);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO TỒN KHO HẦM RƯỢU & KHO VẬN — STRONGWINE");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

            // 2. Subtitle with Date and Time of Export
            Row subtitleRow = sheet.createRow(1);
            subtitleRow.setHeightInPoints(20);
            Cell subCell = subtitleRow.createCell(0);
            subCell.setCellValue(String.format("Thời điểm xuất báo cáo: %s  |  Hệ thống quản lý kho vận StrongWine  |  Đơn vị tính: Chai",
                    LocalDateTime.now().format(DATETIME_FMT)));
            subCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, headers.length - 1));

            // 3. Table Header Row
            Row headerRow = sheet.createRow(3);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // 4. Data Rows
            List<Inventory> list = inventoryRepository.findAll();
            int r = 4;
            int idx = 1;
            int sumCurrent = 0;
            int sumReserved = 0;
            int sumAvailable = 0;

            for (Inventory inv : list) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(20);

                int current = inv.getCurrentQuantity() != null ? inv.getCurrentQuantity() : 0;
                int reserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
                int available = current - reserved;
                int reorder = inv.getReorderLevel() != null ? inv.getReorderLevel() : 10;

                sumCurrent += current;
                sumReserved += reserved;
                sumAvailable += available;

                Cell c0 = row.createCell(0);
                c0.setCellValue(idx++);
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(inv.getWine() != null ? "#" + inv.getWine().getId() : "-");
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(inv.getWine() != null ? inv.getWine().getName() : "N/A");
                c2.setCellStyle(leftStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(inv.getWarehouse() != null ? inv.getWarehouse().getName() : "N/A");
                c3.setCellStyle(leftStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(current);
                c4.setCellStyle(numberStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(reserved);
                c5.setCellStyle(numberStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(available);
                c6.setCellStyle(numberStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue(reorder);
                c7.setCellStyle(numberStyle);

                boolean isLow = available <= reorder;
                Cell c8 = row.createCell(8);
                c8.setCellValue(isLow ? "CẢNH BÁO SẮP HẾT" : "ỔN ĐỊNH");
                c8.setCellStyle(isLow ? warnStyle : okStyle);

                Cell c9 = row.createCell(9);
                c9.setCellValue(inv.getUpdatedAt() != null ? inv.getUpdatedAt().format(DATETIME_FMT) : "—");
                c9.setCellStyle(centerStyle);
            }

            // 5. Total Row
            Row sumRow = sheet.createRow(r);
            sumRow.setHeightInPoints(24);

            Cell s0 = sumRow.createCell(0);
            s0.setCellValue("TỔNG TỒN KHO TOÀN HỆ THỐNG (" + list.size() + " dòng sản phẩm)");
            s0.setCellStyle(totalLabelStyle);

            for (int i = 1; i <= 3; i++) {
                Cell empty = sumRow.createCell(i);
                empty.setCellStyle(totalLabelStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 3));

            Cell s4 = sumRow.createCell(4);
            s4.setCellValue(sumCurrent);
            s4.setCellStyle(totalNumberStyle);

            Cell s5 = sumRow.createCell(5);
            s5.setCellValue(sumReserved);
            s5.setCellStyle(totalNumberStyle);

            Cell s6 = sumRow.createCell(6);
            s6.setCellValue(sumAvailable);
            s6.setCellStyle(totalNumberStyle);

            for (int i = 7; i < headers.length; i++) {
                Cell empty = sumRow.createCell(i);
                empty.setCellStyle(totalLabelStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(r, r, 7, headers.length - 1));

            // Auto-size columns with padding
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1400, 3600));
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi sinh báo cáo tồn kho Excel", e);
        }
    }

    @Override
    protected void populateDataRows(Sheet sheet, Void criteria) {
        // Handled in generateReport
    }
}
