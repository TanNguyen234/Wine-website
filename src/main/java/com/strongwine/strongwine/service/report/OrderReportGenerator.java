package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.repository.OrderRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderReportGenerator extends AbstractExcelReportGenerator<Void> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OrderRepository orderRepository;

    public OrderReportGenerator(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    protected String getSheetName() {
        return "Báo Cáo Đơn Hàng";
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{
                "STT",
                "Mã Đơn",
                "Ngày Đặt",
                "Khách Hàng",
                "Người Nhận",
                "Số Điện Thoại",
                "Địa Chỉ Giao Hàng",
                "Phương Thức",
                "Trạng Thái Thanh Toán",
                "Trạng Thái Đơn",
                "Tổng Tiền (VNĐ)"
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

            XSSFCellStyle centerStyle = workbook.createCellStyle();
            centerStyle.setFont(regularFont);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);
            centerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(centerStyle);

            XSSFCellStyle leftStyle = workbook.createCellStyle();
            leftStyle.setFont(regularFont);
            leftStyle.setAlignment(HorizontalAlignment.LEFT);
            leftStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(leftStyle);

            XSSFCellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setFont(regularFont);
            currencyStyle.setDataFormat(df.getFormat("#,##0 \"₫\""));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            currencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(currencyStyle);

            XSSFCellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(totalFont);
            totalLabelStyle.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabelStyle.setAlignment(HorizontalAlignment.CENTER);
            totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(totalLabelStyle);

            XSSFCellStyle totalCurrencyStyle = workbook.createCellStyle();
            totalCurrencyStyle.setFont(totalFont);
            totalCurrencyStyle.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            totalCurrencyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalCurrencyStyle.setDataFormat(df.getFormat("#,##0 \"₫\""));
            totalCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);
            totalCurrencyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(totalCurrencyStyle);

            String[] headers = getHeaders();

            // 1. Title Row
            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(34);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("BÁO CÁO TỔNG HỢP TOÀN BỘ ĐƠN HÀNG — STRONGWINE");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

            // 2. Subtitle Row with Date and Time of Export
            Row subtitleRow = sheet.createRow(1);
            subtitleRow.setHeightInPoints(20);
            Cell subCell = subtitleRow.createCell(0);
            subCell.setCellValue(String.format("Thời điểm xuất báo cáo: %s  |  Hệ thống quản lý bán hàng StrongWine",
                    LocalDateTime.now().format(DATE_FMT)));
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
            List<Order> list = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate"));
            int r = 4;
            int idx = 1;
            double sumTotal = 0.0;

            for (Order order : list) {
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(20);

                Cell c0 = row.createCell(0);
                c0.setCellValue(idx++);
                c0.setCellStyle(centerStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(order.getId() != null ? "#" + order.getId() : "");
                c1.setCellStyle(centerStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(order.getOrderDate() != null ? order.getOrderDate().format(DATE_FMT) : "");
                c2.setCellStyle(centerStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(order.getUser() != null ? order.getUser().getUsername() : "Khách vãng lai");
                c3.setCellStyle(leftStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(order.getShippingFullName() != null ? order.getShippingFullName() : "");
                c4.setCellStyle(leftStyle);

                Cell c5 = row.createCell(5);
                c5.setCellValue(order.getShippingPhone() != null ? order.getShippingPhone() : "");
                c5.setCellStyle(centerStyle);

                Cell c6 = row.createCell(6);
                c6.setCellValue(order.getShippingAddress() != null ? order.getShippingAddress() : "");
                c6.setCellStyle(leftStyle);

                Cell c7 = row.createCell(7);
                c7.setCellValue(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "");
                c7.setCellStyle(centerStyle);

                Cell c8 = row.createCell(8);
                c8.setCellValue(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "");
                c8.setCellStyle(centerStyle);

                Cell c9 = row.createCell(9);
                c9.setCellValue(order.getStatus() != null ? order.getStatus().name() : "");
                c9.setCellStyle(centerStyle);

                double price = order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0;
                sumTotal += price;

                Cell c10 = row.createCell(10);
                c10.setCellValue(price);
                c10.setCellStyle(currencyStyle);
            }

            // 5. Total Row
            Row sumRow = sheet.createRow(r);
            sumRow.setHeightInPoints(24);

            Cell s0 = sumRow.createCell(0);
            s0.setCellValue("TỔNG CỘNG (" + list.size() + " đơn hàng)");
            s0.setCellStyle(totalLabelStyle);

            for (int i = 1; i <= 9; i++) {
                Cell empty = sumRow.createCell(i);
                empty.setCellStyle(totalLabelStyle);
            }
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 9));

            Cell s10 = sumRow.createCell(10);
            s10.setCellValue(sumTotal);
            s10.setCellStyle(totalCurrencyStyle);

            // Auto-size columns with padding
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1400, 3600));
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi sinh báo cáo đơn hàng Excel", e);
        }
    }

    @Override
    protected void populateDataRows(Sheet sheet, Void criteria) {
        // Handled in generateReport
    }
}
