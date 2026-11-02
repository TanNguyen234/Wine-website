package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.repository.OrderRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
    protected void populateDataRows(Sheet sheet, Void criteria) {
        List<Order> list = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "orderDate"));
        int r = 1;
        for (Order order : list) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(order.getId() != null ? "#" + order.getId() : "");
            row.createCell(1).setCellValue(order.getOrderDate() != null ? order.getOrderDate().format(DATE_FMT) : "");
            row.createCell(2).setCellValue(order.getUser() != null ? order.getUser().getUsername() : "Khách vãng lai");
            row.createCell(3).setCellValue(order.getShippingFullName() != null ? order.getShippingFullName() : "");
            row.createCell(4).setCellValue(order.getShippingPhone() != null ? order.getShippingPhone() : "");
            row.createCell(5).setCellValue(order.getShippingAddress() != null ? order.getShippingAddress() : "");
            row.createCell(6).setCellValue(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "");
            row.createCell(7).setCellValue(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "");
            row.createCell(8).setCellValue(order.getStatus() != null ? order.getStatus().name() : "");
            double price = order.getTotalPrice() != null ? order.getTotalPrice().doubleValue() : 0.0;
            row.createCell(9).setCellValue(price);
        }
    }
}
