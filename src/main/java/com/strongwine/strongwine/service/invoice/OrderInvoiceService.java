package com.strongwine.strongwine.service.invoice;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.service.report.AbstractExcelReportGenerator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class OrderInvoiceService {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color BORDEAUX = new Color(116, 44, 61);
    private static final Color CHAMPAGNE_BG = new Color(249, 246, 240);
    private static final Color MUTED_GRAY = new Color(90, 90, 90);
    private static final byte[] EXCEL_BORDEAUX = new byte[]{(byte) 116, (byte) 44, (byte) 61};
    private static final byte[] EXCEL_LIGHT_BG = new byte[]{(byte) 249, (byte) 246, (byte) 240};

    public byte[] generateInvoice(Order order, InvoiceFormat format) {
        if (order == null) {
            throw new IllegalArgumentException("Order must not be null");
        }
        InvoiceFormat targetFormat = format != null ? format : InvoiceFormat.PDF;
        return switch (targetFormat) {
            case PDF -> generatePdfInvoice(order);
            case XLSX -> generateXlsxInvoice(order);
        };
    }

    private byte[] generatePdfInvoice(Order order) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, bos);
            document.open();

            BaseFont unicodeBase = resolveUnicodeBaseFont();
            Font brandFont = createFont(unicodeBase, 18f, Font.BOLD, BORDEAUX);
            Font subtitleFont = createFont(unicodeBase, 10f, Font.ITALIC, MUTED_GRAY);
            Font sectionFont = createFont(unicodeBase, 11f, Font.BOLD, BORDEAUX);
            Font headerFont = createFont(unicodeBase, 10f, Font.BOLD, Color.WHITE);
            Font bodyFont = createFont(unicodeBase, 10f, Font.NORMAL, Color.BLACK);
            Font boldFont = createFont(unicodeBase, 10f, Font.BOLD, Color.BLACK);

            Paragraph title = new Paragraph("STRONGWINE — COMMERCIAL INVOICE", brandFont);
            title.setAlignment(Element.ALIGN_LEFT);
            document.add(title);

            Paragraph sub = new Paragraph("Maison de Vins | Fine Wines & Traditional Spirits | Tax & Logistics Invoice", subtitleFont);
            sub.setSpacingAfter(12f);
            document.add(sub);

            PdfPTable metaTable = new PdfPTable(2);
            metaTable.setWidthPercentage(100);
            metaTable.setWidths(new float[]{1.15f, 1f});
            metaTable.setSpacingAfter(14f);

            String recipient = resolveRecipientName(order);
            String phone = safeStr(order.getShippingPhone(), "-");
            String address = safeStr(order.getShippingAddress(), "-");
            String warehouseName = order.getAllocatedWarehouse() != null
                    ? safeStr(order.getAllocatedWarehouse().getName(), "Main Warehouse")
                    : "Main Warehouse";

            String leftInfo = "Customer / Recipient: " + recipient + "\n"
                    + "Phone: " + phone + "\n"
                    + "Delivery Address: " + address + "\n"
                    + "Allocated Warehouse: " + warehouseName;

            String orderDateText = order.getOrderDate() != null ? order.getOrderDate().format(DATE_TIME_FMT) : "-";
            String statusText = order.getStatus() != null ? order.getStatus().name() : "-";
            String payStatusText = order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "-";
            String payMethodText = order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "-";

            String rightInfo = "Invoice / Order ID: #" + order.getId() + "\n"
                    + "Order Date: " + orderDateText + "\n"
                    + "Order Status: " + statusText + "\n"
                    + "Payment: " + payMethodText + " (" + payStatusText + ")";

            PdfPCell leftCell = new PdfPCell(new Phrase(leftInfo, bodyFont));
            leftCell.setPadding(8f);
            leftCell.setBackgroundColor(CHAMPAGNE_BG);
            leftCell.setBorderColor(Color.LIGHT_GRAY);
            metaTable.addCell(leftCell);

            PdfPCell rightCell = new PdfPCell(new Phrase(rightInfo, bodyFont));
            rightCell.setPadding(8f);
            rightCell.setBackgroundColor(CHAMPAGNE_BG);
            rightCell.setBorderColor(Color.LIGHT_GRAY);
            metaTable.addCell(rightCell);

            document.add(metaTable);

            Paragraph itemsHeading = new Paragraph("ORDER LINE ITEMS", sectionFont);
            itemsHeading.setSpacingAfter(6f);
            document.add(itemsHeading);

            PdfPTable itemsTable = new PdfPTable(5);
            itemsTable.setWidthPercentage(100);
            itemsTable.setWidths(new float[]{0.5f, 2.5f, 0.7f, 1.1f, 1.2f});
            itemsTable.setSpacingAfter(14f);

            String[] colHeaders = {"#", "Product", "Qty", "Unit Price (VND)", "Line Total (VND)"};
            for (String h : colHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(BORDEAUX);
                cell.setPadding(6f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                itemsTable.addCell(cell);
            }

            List<OrderItem> items = order.getOrderItems() != null ? order.getOrderItems() : List.of();
            int idx = 1;
            for (OrderItem item : items) {
                String wineName = item.getWine() != null ? safeStr(item.getWine().getName(), "Wine") : "Wine";
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                addItemCell(itemsTable, String.valueOf(idx++), bodyFont, Element.ALIGN_CENTER);
                addItemCell(itemsTable, wineName, bodyFont, Element.ALIGN_LEFT);
                addItemCell(itemsTable, String.valueOf(qty), bodyFont, Element.ALIGN_CENTER);
                addItemCell(itemsTable, formatVnd(unitPrice), bodyFont, Element.ALIGN_RIGHT);
                addItemCell(itemsTable, formatVnd(lineTotal), bodyFont, Element.ALIGN_RIGHT);
            }

            document.add(itemsTable);

            FinancialBreakdown totals = resolveFinancials(order);
            PdfPTable totalsTable = new PdfPTable(2);
            totalsTable.setWidthPercentage(55);
            totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalsTable.setWidths(new float[]{1.2f, 1f});

            addTotalRow(totalsTable, "Subtotal", formatVnd(totals.subtotal()) + " VND", bodyFont, false);
            String discountLabel = totals.voucherCode() != null && !totals.voucherCode().isBlank()
                    ? "Discount (" + totals.voucherCode() + ")"
                    : "Discount";
            addTotalRow(totalsTable, discountLabel, "-" + formatVnd(totals.discount()) + " VND", bodyFont, false);
            addTotalRow(totalsTable, "Shipping Fee", formatVnd(totals.shippingFee()) + " VND", bodyFont, false);
            addTotalRow(totalsTable, "GRAND TOTAL", formatVnd(totals.total()) + " VND", boldFont, true);

            document.add(totalsTable);
            document.close();
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate PDF invoice for order #" + order.getId(), ex);
        }
    }

    private byte[] generateXlsxInvoice(Order order) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try {
            SXSSFSheet sheet = workbook.createSheet("Invoice #" + order.getId());
            sheet.trackAllColumnsForAutoSizing();

            XSSFCellStyle titleStyle = createExcelHeaderStyle(workbook, (short) 13);
            XSSFCellStyle headerStyle = createExcelHeaderStyle(workbook, (short) 10);
            XSSFCellStyle labelStyle = createExcelLabelStyle(workbook, true);
            XSSFCellStyle valueStyle = createExcelLabelStyle(workbook, false);
            XSSFCellStyle currencyStyle = createExcelCurrencyStyle(workbook, false);
            XSSFCellStyle totalLabelStyle = createExcelTotalStyle(workbook, false);
            XSSFCellStyle totalCurrencyStyle = createExcelTotalStyle(workbook, true);

            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(28);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("STRONGWINE — COMMERCIAL INVOICE #" + order.getId());
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            int r = 2;
            r = addMetaExcelRow(sheet, r, "Order ID", "#" + order.getId(), labelStyle, valueStyle);
            r = addMetaExcelRow(sheet, r, "Order Date",
                    order.getOrderDate() != null ? order.getOrderDate().format(DATE_TIME_FMT) : "-", labelStyle, valueStyle);
            r = addMetaExcelRow(sheet, r, "Customer / Recipient",
                    AbstractExcelReportGenerator.sanitizeExcelText(resolveRecipientName(order)), labelStyle, valueStyle);
            r = addMetaExcelRow(sheet, r, "Phone",
                    AbstractExcelReportGenerator.sanitizeExcelText(safeStr(order.getShippingPhone(), "-")), labelStyle, valueStyle);
            r = addMetaExcelRow(sheet, r, "Delivery Address",
                    AbstractExcelReportGenerator.sanitizeExcelText(safeStr(order.getShippingAddress(), "-")), labelStyle, valueStyle);
            r = addMetaExcelRow(sheet, r, "Allocated Warehouse",
                    AbstractExcelReportGenerator.sanitizeExcelText(
                            order.getAllocatedWarehouse() != null ? safeStr(order.getAllocatedWarehouse().getName(), "Main Warehouse") : "Main Warehouse"),
                    labelStyle, valueStyle);
            r = addMetaExcelRow(sheet, r, "Payment",
                    (order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "-")
                            + " / " + (order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "-"),
                    labelStyle, valueStyle);

            r++;
            Row headerRow = sheet.createRow(r++);
            headerRow.setHeightInPoints(24);
            String[] headers = {"#", "Product", "Qty", "Unit Price (VND)", "Line Total (VND)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            List<OrderItem> items = order.getOrderItems() != null ? order.getOrderItems() : List.of();
            int idx = 1;
            for (OrderItem item : items) {
                Row row = sheet.createRow(r++);
                String wineName = item.getWine() != null ? safeStr(item.getWine().getName(), "Wine") : "Wine";
                int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                BigDecimal unitPrice = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                Cell c0 = row.createCell(0);
                c0.setCellValue(idx++);
                c0.setCellStyle(valueStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(AbstractExcelReportGenerator.sanitizeExcelText(wineName));
                c1.setCellStyle(valueStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(qty);
                c2.setCellStyle(valueStyle);

                Cell c3 = row.createCell(3);
                c3.setCellValue(unitPrice.doubleValue());
                c3.setCellStyle(currencyStyle);

                Cell c4 = row.createCell(4);
                c4.setCellValue(lineTotal.doubleValue());
                c4.setCellStyle(currencyStyle);
            }

            r++;
            FinancialBreakdown totals = resolveFinancials(order);
            r = addTotalExcelRow(sheet, r, "Subtotal", totals.subtotal(), labelStyle, currencyStyle);
            String discountLabel = totals.voucherCode() != null && !totals.voucherCode().isBlank()
                    ? "Discount (" + AbstractExcelReportGenerator.sanitizeExcelText(totals.voucherCode()) + ")"
                    : "Discount";
            r = addTotalExcelRow(sheet, r, discountLabel, totals.discount(), labelStyle, currencyStyle);
            r = addTotalExcelRow(sheet, r, "Shipping Fee", totals.shippingFee(), labelStyle, currencyStyle);
            addTotalExcelRow(sheet, r, "GRAND TOTAL", totals.total(), totalLabelStyle, totalCurrencyStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 3800));
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to generate XLSX invoice for order #" + order.getId(), ex);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
            }
        }
    }

    private BaseFont resolveUnicodeBaseFont() {
        List<String> candidates = List.of(
                "C:\\Windows\\Fonts\\segoeui.ttf",
                "C:\\Windows\\Fonts\\arial.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
        );
        for (String path : candidates) {
            try {
                if (Files.isReadable(Path.of(path))) {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Font createFont(BaseFont unicodeBase, float size, int style, Color color) {
        if (unicodeBase != null) {
            return new Font(unicodeBase, size, style, color);
        }
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private void addItemCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6f);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String val, Font font, boolean highlight) {
        PdfPCell cLabel = new PdfPCell(new Phrase(label, font));
        cLabel.setPadding(6f);
        cLabel.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        cLabel.setBorderColor(Color.LIGHT_GRAY);
        if (highlight) {
            cLabel.setBackgroundColor(CHAMPAGNE_BG);
        }
        table.addCell(cLabel);

        PdfPCell cVal = new PdfPCell(new Phrase(val, font));
        cVal.setPadding(6f);
        cVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cVal.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        cVal.setBorderColor(Color.LIGHT_GRAY);
        if (highlight) {
            cVal.setBackgroundColor(CHAMPAGNE_BG);
        }
        table.addCell(cVal);
    }

    private int addMetaExcelRow(Sheet sheet, int rowIdx, String label, String val, CellStyle labelStyle, CellStyle valStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell c0 = row.createCell(0);
        c0.setCellValue(label);
        c0.setCellStyle(labelStyle);
        Cell c1 = row.createCell(1);
        c1.setCellValue(val);
        c1.setCellStyle(valStyle);
        return rowIdx + 1;
    }

    private int addTotalExcelRow(Sheet sheet, int rowIdx, String label, BigDecimal amount, CellStyle labelStyle, CellStyle amountStyle) {
        Row row = sheet.createRow(rowIdx);
        Cell cLabel = row.createCell(3);
        cLabel.setCellValue(label);
        cLabel.setCellStyle(labelStyle);
        Cell cVal = row.createCell(4);
        cVal.setCellValue(amount != null ? amount.doubleValue() : 0.0);
        cVal.setCellStyle(amountStyle);
        return rowIdx + 1;
    }

    private XSSFCellStyle createExcelHeaderStyle(SXSSFWorkbook wb, short size) {
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setFontName("Segoe UI");
        font.setBold(true);
        font.setFontHeightInPoints(size);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));

        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(EXCEL_BORDEAUX, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        AbstractExcelReportGenerator.setThinBorders(style);
        return style;
    }

    private XSSFCellStyle createExcelLabelStyle(SXSSFWorkbook wb, boolean bold) {
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setFontName("Segoe UI");
        font.setBold(bold);
        font.setFontHeightInPoints((short) 10);

        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFont(font);
        AbstractExcelReportGenerator.setThinBorders(style);
        return style;
    }

    private XSSFCellStyle createExcelCurrencyStyle(SXSSFWorkbook wb, boolean bold) {
        XSSFCellStyle style = createExcelLabelStyle(wb, bold);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        return style;
    }

    private XSSFCellStyle createExcelTotalStyle(SXSSFWorkbook wb, boolean currency) {
        XSSFFont font = (XSSFFont) wb.createFont();
        font.setFontName("Segoe UI");
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);

        XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(EXCEL_LIGHT_BG, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        if (currency) {
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
        }
        AbstractExcelReportGenerator.setDoubleBottomBorders(style);
        return style;
    }

    private String resolveRecipientName(Order order) {
        if (order.getShippingFullName() != null && !order.getShippingFullName().isBlank()) {
            return order.getShippingFullName();
        }
        if (order.getUser() != null) {
            if (order.getUser().getFullName() != null && !order.getUser().getFullName().isBlank()) {
                return order.getUser().getFullName();
            }
            if (order.getUser().getUsername() != null) {
                return order.getUser().getUsername();
            }
        }
        return "Guest Customer";
    }

    private FinancialBreakdown resolveFinancials(Order order) {
        BigDecimal itemsSum = BigDecimal.ZERO;
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                if (item.getPrice() != null && item.getQuantity() != null) {
                    itemsSum = itemsSum.add(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                }
            }
        }
        BigDecimal subtotal = order.getSubtotalAmount() != null
                ? order.getSubtotalAmount()
                : (itemsSum.compareTo(BigDecimal.ZERO) > 0 ? itemsSum : safeAmount(order.getTotalPrice()));
        BigDecimal discount = safeAmount(order.getDiscountAmount());
        BigDecimal shippingFee = safeAmount(order.getShippingFee());
        BigDecimal total = order.getTotalPrice() != null
                ? order.getTotalPrice()
                : subtotal.subtract(discount).add(shippingFee).max(BigDecimal.ZERO);
        return new FinancialBreakdown(subtotal, discount, order.getVoucherCode(), shippingFee, total);
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String safeStr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String formatVnd(BigDecimal amount) {
        NumberFormat fmt = NumberFormat.getIntegerInstance(Locale.US);
        return fmt.format(amount != null ? amount.longValue() : 0L);
    }

    private record FinancialBreakdown(
            BigDecimal subtotal,
            BigDecimal discount,
            String voucherCode,
            BigDecimal shippingFee,
            BigDecimal total
    ) {
    }
}
