package com.strongwine.strongwine.service.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ExcelTemplateService {

    public byte[] generateProductImportTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Danh Sách Sản Phẩm");

            // Cell Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle sampleStyle = workbook.createCellStyle();
            Font sampleFont = workbook.createFont();
            sampleFont.setItalic(true);
            sampleStyle.setFont(sampleFont);

            // Headers
            String[] headers = {
                "Tên Sản Phẩm (*)", "Loại Rượu (*)", "Năm Sản Xuất (*)", "Giá Bán (VNĐ) (*)",
                "Xuất Xứ", "Mô Tả", "Tên Danh Mục", "Số Lượng Kho Khởi Tạo"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dòng mẫu 1
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Château Margaux Premier Grand Cru");
            r1.createCell(1).setCellValue("Red");
            r1.createCell(2).setCellValue(2018);
            r1.createCell(3).setCellValue(12500000);
            r1.createCell(4).setCellValue("Pháp");
            r1.createCell(5).setCellValue("Hương quả mọng đen, da thuộc, gỗ sồi quý tộc.");
            r1.createCell(6).setCellValue("Vang Đỏ Cao Cấp");
            r1.createCell(7).setCellValue(12);

            // Dòng mẫu 2
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Dom Pérignon Vintage Champagne");
            r2.createCell(1).setCellValue("Sparkling");
            r2.createCell(2).setCellValue(2013);
            r2.createCell(3).setCellValue(6800000);
            r2.createCell(4).setCellValue("Pháp");
            r2.createCell(5).setCellValue("Bọt mịn tinh tế, hương hoa trắng và bơ nướng.");
            r2.createCell(6).setCellValue("Vang Sủi");
            r2.createCell(7).setCellValue(24);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo file template Excel", e);
        }
    }
}
