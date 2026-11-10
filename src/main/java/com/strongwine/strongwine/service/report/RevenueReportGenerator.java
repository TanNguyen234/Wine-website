package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.dto.RevenueReportCriteria;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.OrderRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executive Excel report generator for daily revenue and order insights
 * Designed according to Maison de Vins luxury aesthetic and clean architectural patterns.
 */
@Service
public class RevenueReportGenerator extends AbstractExcelReportGenerator<RevenueReportCriteria> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final byte[] COLOR_BORDEAUX = new byte[]{(byte) 116, (byte) 44, (byte) 61}; // #742c3d
    private static final byte[] COLOR_GOLD = new byte[]{(byte) 176, (byte) 140, (byte) 89};     // #b08c59
    private static final byte[] COLOR_LIGHT_BG = new byte[]{(byte) 249, (byte) 246, (byte) 240}; // #f9f6f0

    private final OrderRepository orderRepository;

    public RevenueReportGenerator(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    protected String getSheetName() {
        return "Tổng Hợp Doanh Thu Ngày";
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{
                "STT", "Ngày", "Số Đơn Thành Công", "Số Chai Rượu Bán", "Doanh Thu (VNĐ)", "Tỷ Trọng (%)"
        };
    }

    @Override
    protected void populateDataRows(Sheet sheet, RevenueReportCriteria criteria) {
        // Handled in full multi-sheet generation below
    }

    /**
     * Generate multi-sheet luxury revenue workbook
     */
    @Override
    @Transactional(readOnly = true)
    public byte[] generateReport(RevenueReportCriteria criteria) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        LocalDate endDate;

        if (criteria != null && criteria.getFromDate() != null && criteria.getToDate() != null) {
            LocalDate f = criteria.getFromDate();
            LocalDate t = criteria.getToDate();
            if (f.isAfter(t)) {
                startDate = t;
                endDate = f;
            } else {
                startDate = f;
                endDate = t;
            }
            if (ChronoUnit.DAYS.between(startDate, endDate) > 90) {
                startDate = endDate.minusDays(89);
            }
        } else {
            int days = (criteria != null && criteria.getDays() != null && criteria.getDays() > 0 && criteria.getDays() <= 90)
                    ? criteria.getDays() : 7;
            endDate = today;
            startDate = endDate.minusDays(days - 1);
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        List<Order> paidOrders = orderRepository.findPaidOrdersWithItemsBetween(startDateTime, endDateTime);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            StylesBundle styles = new StylesBundle(workbook);

            // 1. Sheet 1: Daily Revenue Summary
            buildDailyRevenueSheet(workbook, styles, startDate, endDate, paidOrders);

            // 2. Sheet 2: Detailed Paid Orders
            buildOrderDetailsSheet(workbook, styles, startDate, endDate, paidOrders);

            // 3. Sheet 3: Top Selling Wines
            buildTopProductsSheet(workbook, styles, startDate, endDate, paidOrders);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi xuất file báo cáo doanh thu Excel", e);
        }
    }

    private void buildDailyRevenueSheet(XSSFWorkbook workbook, StylesBundle styles,
                                        LocalDate startDate, LocalDate endDate,
                                        List<Order> paidOrders) {
        Sheet sheet = workbook.createSheet("Doanh Thu Theo Ngày");
        sheet.setDisplayGridlines(true);

        // Title banner
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(34);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("BÁO CÁO DOANH THU BÁN HÀNG — STRONGWINE");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        // Subtitle banner
        Row subtitleRow = sheet.createRow(1);
        subtitleRow.setHeightInPoints(20);
        Cell subCell = subtitleRow.createCell(0);
        long daysSpan = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        subCell.setCellValue(String.format("Giai đoạn: Từ %s đến %s (%d ngày)  |  Thời điểm xuất: %s",
                startDate.format(DATE_FMT), endDate.format(DATE_FMT), daysSpan,
                LocalDateTime.now().format(DATETIME_FMT)));
        subCell.setCellStyle(styles.subtitleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 5));

        // Table headers
        Row headerRow = sheet.createRow(3);
        headerRow.setHeightInPoints(26);
        String[] headers = getHeaders();
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle);
        }

        // Aggregate orders per day
        double grandTotalRevenue = paidOrders.stream()
                .filter(o -> o.getTotalPrice() != null)
                .mapToDouble(o -> o.getTotalPrice().doubleValue())
                .sum();

        int rowIndex = 4;
        int stt = 1;
        int totalOrderCount = 0;
        int totalBottleCount = 0;

        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            final LocalDate curDate = d;
            List<Order> dayOrders = paidOrders.stream()
                    .filter(o -> o.getOrderDate() != null && o.getOrderDate().toLocalDate().equals(curDate))
                    .toList();

            int dayOrderCount = dayOrders.size();
            int dayBottles = dayOrders.stream()
                    .flatMap(o -> o.getOrderItems() != null ? o.getOrderItems().stream() : java.util.stream.Stream.empty())
                    .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                    .sum();
            double dayRevenue = dayOrders.stream()
                    .filter(o -> o.getTotalPrice() != null)
                    .mapToDouble(o -> o.getTotalPrice().doubleValue())
                    .sum();
            double ratio = grandTotalRevenue > 0 ? (dayRevenue / grandTotalRevenue) * 100.0 : 0.0;

            totalOrderCount += dayOrderCount;
            totalBottleCount += dayBottles;

            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(20);

            Cell c0 = row.createCell(0);
            c0.setCellValue(stt++);
            c0.setCellStyle(styles.centerCellStyle);

            Cell c1 = row.createCell(1);
            c1.setCellValue(curDate.format(DATE_FMT));
            c1.setCellStyle(styles.centerCellStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(dayOrderCount);
            c2.setCellStyle(styles.numberCellStyle);

            Cell c3 = row.createCell(3);
            c3.setCellValue(dayBottles);
            c3.setCellStyle(styles.numberCellStyle);

            Cell c4 = row.createCell(4);
            c4.setCellValue(dayRevenue);
            c4.setCellStyle(styles.currencyCellStyle);

            Cell c5 = row.createCell(5);
            c5.setCellValue(Math.round(ratio * 10.0) / 10.0);
            c5.setCellStyle(styles.percentCellStyle);
        }

        // Summary / Grand Total Row
        Row sumRow = sheet.createRow(rowIndex);
        sumRow.setHeightInPoints(24);

        Cell s0 = sumRow.createCell(0);
        s0.setCellValue("TỔNG CỘNG");
        s0.setCellStyle(styles.totalLabelStyle);

        Cell s1 = sumRow.createCell(1);
        s1.setCellValue("");
        s1.setCellStyle(styles.totalLabelStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, 1));

        Cell s2 = sumRow.createCell(2);
        s2.setCellValue(totalOrderCount);
        s2.setCellStyle(styles.totalNumberStyle);

        Cell s3 = sumRow.createCell(3);
        s3.setCellValue(totalBottleCount);
        s3.setCellStyle(styles.totalNumberStyle);

        Cell s4 = sumRow.createCell(4);
        s4.setCellValue(grandTotalRevenue);
        s4.setCellStyle(styles.totalCurrencyStyle);

        Cell s5 = sumRow.createCell(5);
        s5.setCellValue(grandTotalRevenue > 0 ? 100.0 : 0.0);
        s5.setCellStyle(styles.totalPercentStyle);

        // Auto size columns with padding
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1200);
        }
    }

    private void buildOrderDetailsSheet(XSSFWorkbook workbook, StylesBundle styles,
                                        LocalDate startDate, LocalDate endDate,
                                        List<Order> paidOrders) {
        Sheet sheet = workbook.createSheet("Chi Tiết Giao Dịch");
        sheet.setDisplayGridlines(true);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("DANH SÁCH ĐƠN HÀNG THANH TOÁN THÀNH CÔNG");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));

        String[] headers = {
                "STT", "Mã Đơn", "Ngày Đặt", "Khách Hàng", "Người Nhận",
                "Số Điện Thoại", "Địa Chỉ Giao Hàng", "Phương Thức", "Mã Giao Dịch", "Tổng Tiền (VNĐ)"
        };

        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle);
        }

        int r = 3;
        int idx = 1;
        double sumMoney = 0.0;

        for (Order ord : paidOrders) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(20);

            Cell c0 = row.createCell(0);
            c0.setCellValue(idx++);
            c0.setCellStyle(styles.centerCellStyle);

            Cell c1 = row.createCell(1);
            c1.setCellValue("#" + ord.getId());
            c1.setCellStyle(styles.centerCellStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(ord.getOrderDate() != null ? ord.getOrderDate().format(DATETIME_FMT) : "");
            c2.setCellStyle(styles.centerCellStyle);

            Cell c3 = row.createCell(3);
            c3.setCellValue(ord.getUser() != null ? ord.getUser().getUsername() : "Khách vãng lai");
            c3.setCellStyle(styles.leftCellStyle);

            Cell c4 = row.createCell(4);
            c4.setCellValue(ord.getShippingFullName() != null ? ord.getShippingFullName() : "");
            c4.setCellStyle(styles.leftCellStyle);

            Cell c5 = row.createCell(5);
            c5.setCellValue(ord.getShippingPhone() != null ? ord.getShippingPhone() : "");
            c5.setCellStyle(styles.centerCellStyle);

            Cell c6 = row.createCell(6);
            c6.setCellValue(ord.getShippingAddress() != null ? ord.getShippingAddress() : "");
            c6.setCellStyle(styles.leftCellStyle);

            Cell c7 = row.createCell(7);
            c7.setCellValue(ord.getPaymentMethod() != null ? ord.getPaymentMethod().name() : "N/A");
            c7.setCellStyle(styles.centerCellStyle);

            Cell c8 = row.createCell(8);
            c8.setCellValue(ord.getPaymentReference() != null ? ord.getPaymentReference() : "—");
            c8.setCellStyle(styles.centerCellStyle);

            double price = ord.getTotalPrice() != null ? ord.getTotalPrice().doubleValue() : 0.0;
            sumMoney += price;
            Cell c9 = row.createCell(9);
            c9.setCellValue(price);
            c9.setCellStyle(styles.currencyCellStyle);
        }

        // Summary row
        Row sumRow = sheet.createRow(r);
        sumRow.setHeightInPoints(24);
        Cell s0 = sumRow.createCell(0);
        s0.setCellValue("TỔNG CỘNG (" + paidOrders.size() + " đơn hàng)");
        s0.setCellStyle(styles.totalLabelStyle);
        for (int i = 1; i <= 8; i++) {
            Cell empty = sumRow.createCell(i);
            empty.setCellStyle(styles.totalLabelStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 8));

        Cell s9 = sumRow.createCell(9);
        s9.setCellValue(sumMoney);
        s9.setCellStyle(styles.totalCurrencyStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1200);
        }
    }

    private void buildTopProductsSheet(XSSFWorkbook workbook, StylesBundle styles,
                                       LocalDate startDate, LocalDate endDate,
                                       List<Order> paidOrders) {
        Sheet sheet = workbook.createSheet("Top Rượu Bán Chạy");
        sheet.setDisplayGridlines(true);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("THỐNG KÊ TOP SẢN PHẨM RƯỢU THEO DOANH SỐ");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        String[] headers = {
                "Hạng", "Mã Rượu", "Tên Sản Phẩm", "Loại Rượu", "Đơn Giá (VNĐ)", "Số Lượng Bán (Chai)", "Tổng Doanh Thu (VNĐ)"
        };

        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(styles.headerStyle);
        }

        // Aggregate by Wine
        Map<Long, ProductStat> statsMap = new LinkedHashMap<>();
        for (Order ord : paidOrders) {
            if (ord.getOrderItems() == null) continue;
            for (OrderItem item : ord.getOrderItems()) {
                Wine wine = item.getWine();
                if (wine == null) continue;
                Long wId = wine.getId();
                int q = item.getQuantity() != null ? item.getQuantity() : 0;
                double unitPrice = item.getPrice() != null ? item.getPrice().doubleValue() : (wine.getPrice() != null ? wine.getPrice().doubleValue() : 0.0);
                double total = unitPrice * q;

                statsMap.compute(wId, (k, old) -> {
                    if (old == null) {
                        return new ProductStat(wine, q, total);
                    } else {
                        old.quantity += q;
                        old.revenue += total;
                        return old;
                    }
                });
            }
        }

        List<ProductStat> sortedStats = statsMap.values().stream()
                .sorted((a, b) -> Double.compare(b.revenue, a.revenue))
                .toList();

        int r = 3;
        int rank = 1;
        int sumQuantity = 0;
        double sumRevenue = 0.0;

        for (ProductStat stat : sortedStats) {
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(20);

            Cell c0 = row.createCell(0);
            c0.setCellValue(rank++);
            c0.setCellStyle(styles.centerCellStyle);

            Cell c1 = row.createCell(1);
            c1.setCellValue(stat.wine.getId() != null ? "#" + stat.wine.getId() : "");
            c1.setCellStyle(styles.centerCellStyle);

            Cell c2 = row.createCell(2);
            c2.setCellValue(stat.wine.getName() != null ? stat.wine.getName() : "");
            c2.setCellStyle(styles.leftCellStyle);

            Cell c3 = row.createCell(3);
            c3.setCellValue(stat.wine.getType() != null ? stat.wine.getType() : "Vang cao cấp");
            c3.setCellStyle(styles.centerCellStyle);

            Cell c4 = row.createCell(4);
            c4.setCellValue(stat.wine.getPrice() != null ? stat.wine.getPrice().doubleValue() : 0.0);
            c4.setCellStyle(styles.currencyCellStyle);

            Cell c5 = row.createCell(5);
            c5.setCellValue(stat.quantity);
            c5.setCellStyle(styles.numberCellStyle);
            sumQuantity += stat.quantity;

            Cell c6 = row.createCell(6);
            c6.setCellValue(stat.revenue);
            c6.setCellStyle(styles.currencyCellStyle);
            sumRevenue += stat.revenue;
        }

        // Summary row
        Row sumRow = sheet.createRow(r);
        sumRow.setHeightInPoints(24);
        Cell s0 = sumRow.createCell(0);
        s0.setCellValue("TỔNG DOANH SỐ TOP SẢN PHẨM");
        s0.setCellStyle(styles.totalLabelStyle);
        for (int i = 1; i <= 4; i++) {
            Cell empty = sumRow.createCell(i);
            empty.setCellStyle(styles.totalLabelStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 4));

        Cell s5 = sumRow.createCell(5);
        s5.setCellValue(sumQuantity);
        s5.setCellStyle(styles.totalNumberStyle);

        Cell s6 = sumRow.createCell(6);
        s6.setCellValue(sumRevenue);
        s6.setCellStyle(styles.totalCurrencyStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1200);
        }
    }

    private static class ProductStat {
        Wine wine;
        int quantity;
        double revenue;

        ProductStat(Wine wine, int quantity, double revenue) {
            this.wine = wine;
            this.quantity = quantity;
            this.revenue = revenue;
        }
    }

    /**
     * Shared styles conforming to luxury dark boutique branding
     */
    private static class StylesBundle {
        final CellStyle titleStyle;
        final CellStyle subtitleStyle;
        final CellStyle headerStyle;
        final CellStyle centerCellStyle;
        final CellStyle leftCellStyle;
        final CellStyle numberCellStyle;
        final CellStyle currencyCellStyle;
        final CellStyle percentCellStyle;
        final CellStyle totalLabelStyle;
        final CellStyle totalNumberStyle;
        final CellStyle totalCurrencyStyle;
        final CellStyle totalPercentStyle;

        StylesBundle(XSSFWorkbook wb) {
            DataFormat df = wb.createDataFormat();

            // Fonts
            XSSFFont titleFont = wb.createFont();
            titleFont.setFontName(FONT_FAMILY);
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            titleFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFFont headerFont = wb.createFont();
            headerFont.setFontName(FONT_FAMILY);
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

            XSSFFont subFont = wb.createFont();
            subFont.setFontName(FONT_FAMILY);
            subFont.setItalic(true);
            subFont.setFontHeightInPoints((short) 9);
            subFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            XSSFFont regularFont = wb.createFont();
            regularFont.setFontName(FONT_FAMILY);
            regularFont.setFontHeightInPoints((short) 10);

            XSSFFont totalFont = wb.createFont();
            totalFont.setFontName(FONT_FAMILY);
            totalFont.setBold(true);
            totalFont.setFontHeightInPoints((short) 10);

            // Title Style
            XSSFCellStyle ts = wb.createCellStyle();
            ts.setFont(titleFont);
            ts.setFillForegroundColor(new XSSFColor(COLOR_BORDEAUX, null));
            ts.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            ts.setAlignment(HorizontalAlignment.CENTER);
            ts.setVerticalAlignment(VerticalAlignment.CENTER);
            titleStyle = ts;

            // Subtitle Style
            XSSFCellStyle sts = wb.createCellStyle();
            sts.setFont(subFont);
            sts.setAlignment(HorizontalAlignment.CENTER);
            sts.setVerticalAlignment(VerticalAlignment.CENTER);
            subtitleStyle = sts;

            // Header Style
            XSSFCellStyle hs = wb.createCellStyle();
            hs.setFont(headerFont);
            hs.setFillForegroundColor(new XSSFColor(COLOR_BORDEAUX, null));
            hs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            hs.setAlignment(HorizontalAlignment.CENTER);
            hs.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(hs);
            headerStyle = hs;

            // Center Text Style
            XSSFCellStyle cs = wb.createCellStyle();
            cs.setFont(regularFont);
            cs.setAlignment(HorizontalAlignment.CENTER);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(cs);
            centerCellStyle = cs;

            // Left Text Style
            XSSFCellStyle ls = wb.createCellStyle();
            ls.setFont(regularFont);
            ls.setAlignment(HorizontalAlignment.LEFT);
            ls.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(ls);
            leftCellStyle = ls;

            // Number Style
            XSSFCellStyle ns = wb.createCellStyle();
            ns.setFont(regularFont);
            ns.setDataFormat(df.getFormat("#,##0"));
            ns.setAlignment(HorizontalAlignment.RIGHT);
            ns.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(ns);
            numberCellStyle = ns;

            // Currency Style
            XSSFCellStyle curs = wb.createCellStyle();
            curs.setFont(regularFont);
            curs.setDataFormat(df.getFormat("#,##0 \"₫\""));
            curs.setAlignment(HorizontalAlignment.RIGHT);
            curs.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(curs);
            currencyCellStyle = curs;

            // Percent Style
            XSSFCellStyle ps = wb.createCellStyle();
            ps.setFont(regularFont);
            ps.setDataFormat(df.getFormat("0.0\"%\""));
            ps.setAlignment(HorizontalAlignment.RIGHT);
            ps.setVerticalAlignment(VerticalAlignment.CENTER);
            setThinBorders(ps);
            percentCellStyle = ps;

            // Total styles
            XSSFCellStyle tls = wb.createCellStyle();
            tls.setFont(totalFont);
            tls.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            tls.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tls.setAlignment(HorizontalAlignment.CENTER);
            tls.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(tls);
            totalLabelStyle = tls;

            XSSFCellStyle tns = wb.createCellStyle();
            tns.setFont(totalFont);
            tns.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            tns.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tns.setDataFormat(df.getFormat("#,##0"));
            tns.setAlignment(HorizontalAlignment.RIGHT);
            tns.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(tns);
            totalNumberStyle = tns;

            XSSFCellStyle tcs = wb.createCellStyle();
            tcs.setFont(totalFont);
            tcs.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            tcs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tcs.setDataFormat(df.getFormat("#,##0 \"₫\""));
            tcs.setAlignment(HorizontalAlignment.RIGHT);
            tcs.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(tcs);
            totalCurrencyStyle = tcs;

            XSSFCellStyle tps = wb.createCellStyle();
            tps.setFont(totalFont);
            tps.setFillForegroundColor(new XSSFColor(COLOR_LIGHT_BG, null));
            tps.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tps.setDataFormat(df.getFormat("0.0\"%\""));
            tps.setAlignment(HorizontalAlignment.RIGHT);
            tps.setVerticalAlignment(VerticalAlignment.CENTER);
            setDoubleBottomBorders(tps);
            totalPercentStyle = tps;
        }

        private static void setThinBorders(CellStyle cs) {
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
            cs.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }

        private static void setDoubleBottomBorders(CellStyle cs) {
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderBottom(BorderStyle.DOUBLE);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
            cs.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            cs.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        }
    }
}
