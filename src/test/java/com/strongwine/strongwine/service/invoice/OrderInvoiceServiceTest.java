package com.strongwine.strongwine.service.invoice;

import com.strongwine.strongwine.controller.InvoiceController;
import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderItem;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.entity.PaymentMethod;
import com.strongwine.strongwine.entity.PaymentStatus;
import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.OrderRepository;
import com.strongwine.strongwine.repository.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderInvoiceServiceTest {

    private Order sampleOrder() {
        User owner = new User();
        owner.setId(9L);
        owner.setUsername("minhanh");
        owner.setFullName("Nguyen Minh Anh");
        owner.setEmail("minhanh@strongwine.vn");
        owner.setRole("USER");

        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        warehouse.setName("Kho Tong Sai Gon");
        warehouse.setLocation("Quan 7, TP.HCM");
        warehouse.setActive(true);

        Wine margaux = new Wine();
        margaux.setId(101L);
        margaux.setName("Chateau Margaux 2018");
        margaux.setType("Red");
        margaux.setPrice(new BigDecimal("3500000"));

        Wine apricot = new Wine();
        apricot.setId(102L);
        apricot.setName("Ruou Mo Yen Tu");
        apricot.setType("Traditional");
        apricot.setPrice(new BigDecimal("500000"));

        Order order = new Order();
        order.setId(4201L);
        order.setUser(owner);
        order.setOrderDate(LocalDateTime.of(2026, 11, 14, 10, 30));
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.SUCCESS);
        order.setPaymentMethod(PaymentMethod.VNPAY);
        order.setShippingFullName("Nguyen Minh Anh");
        order.setShippingPhone("0909123456");
        order.setShippingAddress("123 Dong Khoi, District 1, Ho Chi Minh City");
        order.setSubtotalAmount(new BigDecimal("4500000"));
        order.setDiscountAmount(new BigDecimal("500000"));
        order.setVoucherCode("VIPWINE500");
        order.setShippingFee(new BigDecimal("30000"));
        order.setTotalPrice(new BigDecimal("4030000"));
        order.setAllocatedWarehouse(warehouse);

        OrderItem item1 = new OrderItem();
        item1.setOrder(order);
        item1.setWine(margaux);
        item1.setQuantity(1);
        item1.setPrice(new BigDecimal("3500000"));

        OrderItem item2 = new OrderItem();
        item2.setOrder(order);
        item2.setWine(apricot);
        item2.setQuantity(2);
        item2.setPrice(new BigDecimal("500000"));

        order.setOrderItems(new ArrayList<>(List.of(item1, item2)));
        return order;
    }

    @Test
    void generatePdfInvoiceContainsOrderFinancialsAndValidPdfSignature() throws Exception {
        OrderInvoiceService service = new OrderInvoiceService();
        Order order = sampleOrder();

        byte[] pdfBytes = service.generateInvoice(order, InvoiceFormat.PDF);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 200);
        assertEquals("%PDF", new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII));

        StringBuilder extractedText = new StringBuilder();
        try (PdfReader reader = new PdfReader(pdfBytes)) {
            assertTrue(reader.getNumberOfPages() >= 1);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                extractedText.append(extractor.getTextFromPage(i)).append('\n');
            }
        }

        String text = extractedText.toString();
        assertTrue(text.contains("4201"), "PDF must include order ID");
        assertTrue(text.contains("Nguyen Minh Anh"), "PDF must include recipient name");
        assertTrue(text.contains("Chateau Margaux 2018"), "PDF must include line item wine name");
        assertTrue(text.contains("Ruou Mo Yen Tu"), "PDF must include traditional wine line item");
        assertTrue(text.contains("VIPWINE500"), "PDF must include applied voucher code");
        assertTrue(text.contains("4,500,000") || text.contains("4.500.000"), "PDF must include subtotal");
        assertTrue(text.contains("500,000") || text.contains("500.000"), "PDF must include discount amount");
        assertTrue(text.contains("30,000") || text.contains("30.000"), "PDF must include shipping fee");
        assertTrue(text.contains("4,030,000") || text.contains("4.030.000"), "PDF must include grand total");
    }

    @Test
    void generateXlsxInvoiceOpensCleanlyAndSanitizesFormulas() throws Exception {
        OrderInvoiceService service = new OrderInvoiceService();
        Order order = sampleOrder();
        order.setShippingFullName("=CMD|'/C calc'!A0");
        order.getOrderItems().get(0).getWine().setName("+HYPERLINK(\"http://evil.invalid\",\"Click\")");

        byte[] xlsxBytes = service.generateInvoice(order, InvoiceFormat.XLSX);

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 200);

        List<String> allStrings = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(xlsxBytes))) {
            assertTrue(wb.getNumberOfSheets() >= 1);
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        String val = cell.getStringCellValue();
                        allStrings.add(val);
                        assertFalse(val.startsWith("=") || val.startsWith("+") || val.startsWith("-"),
                                "Formula injection risk detected in XLSX cell: " + val);
                    }
                }
            }
        }

        String joined = String.join("\n", allStrings);
        assertTrue(joined.contains("4201"));
        assertTrue(joined.contains("'=CMD|'/C calc'!A0"));
        assertTrue(joined.contains("'+HYPERLINK"));
        assertTrue(joined.contains("VIPWINE500"));
    }

    @Test
    void invoiceControllerEnforcesOwnerOrAdminAuthorizationAndSetsDownloadHeaders() {
        OrderRepository orderRepository = mock(OrderRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        OrderInvoiceService invoiceService = new OrderInvoiceService();
        InvoiceController controller = new InvoiceController(orderRepository, userRepository, invoiceService);

        Order order = sampleOrder();
        when(orderRepository.findById(4201L)).thenReturn(Optional.of(order));

        User owner = order.getUser();
        User stranger = new User();
        stranger.setId(77L);
        stranger.setUsername("stranger");
        stranger.setRole("USER");

        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setRole("ADMIN");

        when(userRepository.findByUsername("minhanh")).thenReturn(Optional.of(owner));
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(stranger));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        Authentication strangerAuth = new UsernamePasswordAuthenticationToken(
                "stranger", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication ownerAuth = new UsernamePasswordAuthenticationToken(
                "minhanh", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        ResponseEntity<byte[]> forbiddenResp = controller.downloadUserInvoice(4201L, "pdf", strangerAuth);
        assertEquals(HttpStatus.FORBIDDEN, forbiddenResp.getStatusCode());

        ResponseEntity<byte[]> adminRouteForbidden = controller.downloadAdminInvoice(4201L, "pdf", ownerAuth);
        assertEquals(HttpStatus.FORBIDDEN, adminRouteForbidden.getStatusCode());

        ResponseEntity<byte[]> ownerPdfResp = controller.downloadUserInvoice(4201L, "pdf", ownerAuth);
        assertEquals(HttpStatus.OK, ownerPdfResp.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, ownerPdfResp.getHeaders().getContentType());
        assertTrue(String.valueOf(ownerPdfResp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment"));
        assertTrue(String.valueOf(ownerPdfResp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("4201.pdf"));
        assertNotNull(ownerPdfResp.getBody());
        assertEquals("%PDF", new String(ownerPdfResp.getBody(), 0, 4, StandardCharsets.US_ASCII));

        ResponseEntity<byte[]> adminXlsxResp = controller.downloadAdminInvoice(4201L, "xlsx", adminAuth);
        assertEquals(HttpStatus.OK, adminXlsxResp.getStatusCode());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                String.valueOf(adminXlsxResp.getHeaders().getContentType()));
        assertTrue(String.valueOf(adminXlsxResp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("4201.xlsx"));
        assertNotNull(adminXlsxResp.getBody());
        assertTrue(adminXlsxResp.getBody().length > 200);
    }
}
