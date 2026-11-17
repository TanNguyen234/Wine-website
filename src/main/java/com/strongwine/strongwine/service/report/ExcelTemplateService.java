package com.strongwine.strongwine.service.report;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class ExcelTemplateService {

    private static final byte[] COLOR_BORDEAUX = new byte[]{(byte) 116, (byte) 44, (byte) 61}; // #742c3d
    private static final String FONT_FAMILY = "Segoe UI";

    public byte[] generateProductImportTemplate() {
        SXSSFWorkbook workbook = AbstractExcelReportGenerator.createStreamingWorkbook();
        try {
            Sheet sheet = AbstractExcelReportGenerator.createTrackedSheet(workbook, "Danh Sách Sản Phẩm");
            sheet.setDisplayGridlines(true);

            DataFormat df = workbook.createDataFormat();

            // Header Font & Style
            XSSFFont headerFont = (XSSFFont) workbook.createFont();
            headerFont.setFontName(FONT_FAMILY);
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(COLOR_BORDEAUX, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            AbstractExcelReportGenerator.setThinBorders(headerStyle);

            // Regular Data Fonts & Styles
            XSSFFont regularFont = (XSSFFont) workbook.createFont();
            regularFont.setFontName(FONT_FAMILY);
            regularFont.setFontHeightInPoints((short) 10);

            XSSFCellStyle leftStyle = (XSSFCellStyle) workbook.createCellStyle();
            leftStyle.setFont(regularFont);
            leftStyle.setAlignment(HorizontalAlignment.LEFT);
            leftStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            AbstractExcelReportGenerator.setThinBorders(leftStyle);

            XSSFCellStyle centerStyle = (XSSFCellStyle) workbook.createCellStyle();
            centerStyle.setFont(regularFont);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            AbstractExcelReportGenerator.setThinBorders(centerStyle);

            // Explicit number format to prevent scientific notation (e.g. 1.25E7)
            XSSFCellStyle priceStyle = (XSSFCellStyle) workbook.createCellStyle();
            priceStyle.setFont(regularFont);
            priceStyle.setDataFormat(df.getFormat("#,##0"));
            priceStyle.setAlignment(HorizontalAlignment.RIGHT);
            priceStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            AbstractExcelReportGenerator.setThinBorders(priceStyle);

            XSSFCellStyle yearStyle = (XSSFCellStyle) workbook.createCellStyle();
            yearStyle.setFont(regularFont);
            yearStyle.setDataFormat(df.getFormat("0"));
            yearStyle.setAlignment(HorizontalAlignment.CENTER);
            yearStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            AbstractExcelReportGenerator.setThinBorders(yearStyle);

            XSSFCellStyle qtyStyle = (XSSFCellStyle) workbook.createCellStyle();
            qtyStyle.setFont(regularFont);
            qtyStyle.setDataFormat(df.getFormat("#,##0"));
            qtyStyle.setAlignment(HorizontalAlignment.RIGHT);
            qtyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            AbstractExcelReportGenerator.setThinBorders(qtyStyle);

            // Headers
            String[] headers = {
                "Tên Sản Phẩm (*)", "Loại Rượu (*)", "Năm Sản Xuất (*)", "Giá Bán (VNĐ) (*)",
                "Xuất Xứ", "Mô Tả", "Tên Danh Mục", "Số Lượng Kho Khởi Tạo", "URL Hình Ảnh"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            Object[][] sampleRows = {
                {"Château Margaux Premier Grand Cru", "Red", 2018, 12500000, "Pháp",
                 "Hương quả mọng đen, hoa tím, da thuộc và gỗ tuyết tùng quý tộc.", "Vang đỏ", 18,
                 "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3"},
                {"Dom Pérignon Vintage Champagne", "Sparkling", 2013, 6800000, "Pháp",
                 "Bọt mịn tinh tế, hương hoa trắng, cam bergamot và bánh brioche nướng.", "Vang sủi", 24,
                 "https://images.unsplash.com/photo-1594498653385-d5172c532c00"},
                {"Cloudy Bay Sauvignon Blanc Reserve", "White", 2022, 1180000, "New Zealand",
                 "Hương chanh dây, bưởi hồng và khoáng chất sảng khoái cho tiệc hải sản.", "Vang trắng", 36,
                 "https://images.unsplash.com/photo-1470337458703-46ad1756a187"},
                {"Whispering Angel Côtes de Provence Rosé", "Rose", 2022, 950000, "Pháp",
                 "Sắc hồng phấn sang trọng, hương dâu tây trắng và đào tiên thanh lịch.", "Vang hồng", 30,
                 "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0"}
            };

            for (int r = 0; r < sampleRows.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.setHeightInPoints(20);
                Object[] data = sampleRows[r];

                Cell c0 = row.createCell(0);
                c0.setCellValue((String) data[0]);
                c0.setCellStyle(leftStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue((String) data[1]);
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue((Integer) data[2]);
                c2.setCellStyle(yearStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue((Integer) data[3]);
                c3.setCellStyle(priceStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue((String) data[4]);
                c4.setCellStyle(leftStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue((String) data[5]);
                c5.setCellStyle(leftStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue((String) data[6]);
                c6.setCellStyle(centerStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue((Integer) data[7]);
                c7.setCellStyle(qtyStyle);

                Cell c8 = row.createCell(8);
                c8.setCellValue((String) data[8]);
                c8.setCellStyle(leftStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1400, 3600));
            }

            Sheet guideSheet = AbstractExcelReportGenerator.createTrackedSheet(workbook, "Hướng Dẫn Nhập Liệu");
            String[][] guides = {
                {"Trường dữ liệu", "Quy tắc hợp lệ"},
                {"Loại Rượu (*)", "Chấp nhận: Red, White, Rose, Sparkling, Traditional"},
                {"Năm Sản Xuất (*)", "Số nguyên từ 1900 đến năm hiện tại + 1"},
                {"Giá Bán (VNĐ) (*)", "Số dương lớn hơn 0 (không chứa ký tự chữ)"},
                {"URL Hình Ảnh", "Chấp nhận https://..., http://..., /uploads/... hoặc /images/..."}
            };
            for (int gr = 0; gr < guides.length; gr++) {
                Row gRow = guideSheet.createRow(gr);
                gRow.setHeightInPoints(22);
                Cell c0 = gRow.createCell(0);
                c0.setCellValue(guides[gr][0]);
                c0.setCellStyle(gr == 0 ? headerStyle : leftStyle);
                Cell c1 = gRow.createCell(1);
                c1.setCellValue(guides[gr][1]);
                c1.setCellStyle(gr == 0 ? headerStyle : leftStyle);
            }
            if (guideSheet instanceof SXSSFSheet sxGuide) {
                sxGuide.trackAllColumnsForAutoSizing();
            }
            guideSheet.autoSizeColumn(0);
            guideSheet.autoSizeColumn(1);
            guideSheet.setColumnWidth(0, Math.max(guideSheet.getColumnWidth(0) + 1200, 5000));
            guideSheet.setColumnWidth(1, Math.max(guideSheet.getColumnWidth(1) + 1200, 12000));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo file template Excel.", e);
        } finally {
            AbstractExcelReportGenerator.safeDispose(workbook);
        }
    }
}
