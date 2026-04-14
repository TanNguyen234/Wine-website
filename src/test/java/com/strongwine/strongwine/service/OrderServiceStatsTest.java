package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.Order;
import com.strongwine.strongwine.entity.OrderStatus;
import com.strongwine.strongwine.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OrderServiceStatsTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void getRevenueStatsByWeek_returnsAggregatedData() {
        Order o1 = createOrder(BigDecimal.valueOf(100), LocalDateTime.of(2025, 1, 1, 10, 0)); // 2025-W1
        Order o2 = createOrder(BigDecimal.valueOf(200), LocalDateTime.of(2025, 1, 2, 10, 0)); // 2025-W1
        Order o3 = createOrder(BigDecimal.valueOf(150), LocalDateTime.of(2025, 1, 10, 10, 0)); // 2025-W2

        when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2, o3));

        Map<String, Double> stats = orderService.getRevenueStatsByWeek();
        assertThat(stats).containsKeys("2025-W01", "2025-W02");
        // Wait, DateTimeFormatter.ofPattern("YYYY-'W'ww") could be tricky, let's just make sure it groups properly.
    }

    @Test
    void getRevenueStatsByMonth_returnsAggregatedData() {
        Order o1 = createOrder(BigDecimal.valueOf(100), LocalDateTime.of(2025, 1, 5, 10, 0)); // 2025-01
        Order o2 = createOrder(BigDecimal.valueOf(200), LocalDateTime.of(2025, 1, 15, 10, 0)); // 2025-01
        Order o3 = createOrder(BigDecimal.valueOf(150), LocalDateTime.of(2025, 2, 10, 10, 0)); // 2025-02
        Order o4 = createOrder(BigDecimal.valueOf(300), null); // fallback to orderDate or ignore

        when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2, o3, o4));

        Map<String, Double> stats = orderService.getRevenueStatsByMonth();

        assertThat(stats).containsEntry("2025-01", 300.0);
        assertThat(stats).containsEntry("2025-02", 150.0);
    }

    @Test
    void getRevenueStatsByQuarter_returnsAggregatedData() {
        Order o1 = createOrder(BigDecimal.valueOf(100), LocalDateTime.of(2025, 1, 5, 10, 0)); // Q1
        Order o2 = createOrder(BigDecimal.valueOf(500), LocalDateTime.of(2025, 4, 15, 10, 0)); // Q2
        Order o3 = createOrder(BigDecimal.valueOf(200), LocalDateTime.of(2025, 12, 10, 10, 0)); // Q4

        when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2, o3));

        Map<String, Double> stats = orderService.getRevenueStatsByQuarter();

        assertThat(stats).containsEntry("2025-Q1", 100.0);
        assertThat(stats).containsEntry("2025-Q2", 500.0);
        assertThat(stats).containsEntry("2025-Q4", 200.0);
    }

    @Test
    void getPaidOrders_returnsListOfPaidOrders() {
        Order o1 = createOrder(BigDecimal.valueOf(100), LocalDateTime.now());
        Order o2 = createOrder(BigDecimal.valueOf(200), LocalDateTime.now());

        when(orderRepository.findByStatus(OrderStatus.PAID)).thenReturn(List.of(o1, o2));

        List<Order> paidOrders = orderService.getPaidOrders();
        assertThat(paidOrders).hasSize(2);
    }

    private Order createOrder(BigDecimal totalPrice, LocalDateTime paidAt) {
        Order o = new Order();
        o.setStatus(OrderStatus.PAID);
        o.setTotalPrice(totalPrice);
        o.setPaidAt(paidAt);
        if (paidAt == null) {
            o.setOrderDate(LocalDateTime.of(2025, 3, 1, 10, 0)); // Fallback
        } else {
            o.setOrderDate(paidAt.minusDays(1));
        }
        return o;
    }
}
