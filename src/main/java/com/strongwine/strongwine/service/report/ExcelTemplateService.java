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

            // Headers
            String[] headers = {
                "Tên Sản Phẩm (*)", "Loại Rượu (*)", "Năm Sản Xuất (*)", "Giá Bán (VNĐ) (*)",
                "Xuất Xứ", "Mô Tả", "Tên Danh Mục", "Số Lượng Kho Khởi Tạo", "URL Hình Ảnh"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            Object[][] sampleRows = {
                {"Château Margaux Premier Grand Cru", "Red", 2018, 12500000, "France",
                 "Hương quả mọng đen, hoa tím, da thuộc và gỗ tuyết tùng quý tộc.", "Vang đỏ", 18,
                 "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80"},
                {"Dom Pérignon Vintage Champagne", "Sparkling", 2013, 6800000, "France",
                 "Bọt mịn tinh tế, hương hoa trắng, cam bergamot và bánh brioche nướng.", "Vang sủi", 24,
                 "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=900&q=80"},
                {"Cloudy Bay Sauvignon Blanc Reserve", "White", 2022, 1180000, "New Zealand",
                 "Hương chanh dây, bưởi hồng và khoáng chất sảng khoái cho tiệc hải sản.", "Vang trắng", 36,
                 "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=900&q=80"},
                {"Whispering Angel Côtes de Provence Rosé", "Rose", 2022, 950000, "France",
                 "Sắc hồng phấn sang trọng, hương dâu tây trắng và đào tiên thanh lịch.", "Vang hồng", 30,
                 "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=900&q=80"}
            };

            for (int r = 0; r < sampleRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] data = sampleRows[r];
                row.createCell(0).setCellValue((String) data[0]);
                row.createCell(1).setCellValue((String) data[1]);
                row.createCell(2).setCellValue((Integer) data[2]);
                row.createCell(3).setCellValue((Integer) data[3]);
                row.createCell(4).setCellValue((String) data[4]);
                row.createCell(5).setCellValue((String) data[5]);
                row.createCell(6).setCellValue((String) data[6]);
                row.createCell(7).setCellValue((Integer) data[7]);
                row.createCell(8).setCellValue((String) data[8]);
            }

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
