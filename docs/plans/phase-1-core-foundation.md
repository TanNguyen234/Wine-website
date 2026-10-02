# Phase 1: Core Foundation & Tooling Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thiết lập nền tảng kỹ thuật vững chắc: Tích hợp thư viện Apache POI cho Excel, xây dựng hợp đồng trừu tượng Strategy Pattern cho dữ liệu, chuẩn hóa phân cấp Domain Exceptions kèm `@RestControllerAdvice`, và dựng khung kết nối Server-Sent Events (SSE) có cơ chế Heartbeat.

**Architecture:** Sử dụng kiến trúc phân lớp sạch (Clean Layered Architecture) kết hợp Strategy Pattern cho luồng I/O dữ liệu, tách biệt Exception Handling theo chuẩn RFC 7807, và quản lý các kết nối thời gian thực qua `SseEmitter` thread-safe.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Web, Apache POI 5.2.5 (`poi`, `poi-ooxml`), JUnit 5, Mockito.

## Global Constraints
- Database Preservation: Tuyệt đối không thay đổi, không xóa bất kỳ bảng hoặc cột nào trên SQL Server.
- Schema Validation: `spring.jpa.hibernate.ddl-auto=validate` phải luôn chạy thành công.
- No Mocks in Production Runtime: Chỉ sử dụng mocks trong môi trường kiểm thử `src/test/java`.
- Per-Task Real Test: Mỗi task bắt buộc phải thực thi kiểm thử tự động (JUnit) và kiểm chứng runtime thực tế trước khi chuyển sang task kế tiếp.

---

### Task 1.1: Tích Hợp Dependencies Apache POI Vào `pom.xml`

**Files:**
- Modify: `pom.xml:135-138`
- Test: `src/test/java/com/strongwine/strongwine/config/PoiDependencyTest.java`

**Interfaces:**
- Consumes: Maven Build Tool, JDK 21.
- Produces: `org.apache.poi.xssf.usermodel.XSSFWorkbook`, `org.apache.poi.ss.usermodel.Workbook`.

- [ ] **Step 1: Viết test kiểm tra sự hiện diện của Apache POI trong classpath**

```java
package com.strongwine.strongwine.config;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoiDependencyTest {
    @Test
    void testPoiClassesAvailableInClasspath() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
            assertNotNull(clazz);
        });
    }

    @Test
    void testCreateEmptyWorkbookInMemory() {
        assertDoesNotThrow(() -> {
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                assertNotNull(workbook.createSheet("TestSheet"));
            }
        });
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=PoiDependencyTest
```
Expected: `BUILD FAILURE` do chưa import class `org.apache.poi.xssf.usermodel.XSSFWorkbook` trong `pom.xml`.

- [ ] **Step 3: Thêm Apache POI vào `pom.xml`**

Thêm vào thẻ `<dependencies>` trong [pom.xml](file:///D:/Projects/strongwine_2/Wine-website/pom.xml):
```xml
		<!-- Apache POI for Excel Processing -->
		<dependency>
			<groupId>org.apache.poi</groupId>
			<artifactId>poi</artifactId>
			<version>5.2.5</version>
		</dependency>
		<dependency>
			<groupId>org.apache.poi</groupId>
			<artifactId>poi-ooxml</artifactId>
			<version>5.2.5</version>
		</dependency>
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=PoiDependencyTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra lệnh biên dịch toàn bộ dự án:
```powershell
./mvnw compile -DskipTests
```
Expected output: `BUILD SUCCESS` (không có xung đột thư viện).

- [ ] **Step 6: Commit**

```powershell
git add pom.xml src/test/java/com/strongwine/strongwine/config/PoiDependencyTest.java
git commit -m "chore: add apache poi 5.2.5 dependencies for excel processing"
```

---

### Task 1.2: Xây Dựng Hợp Đồng Strategy Pattern Cho Nhập/Xuất Dữ Liệu

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/strategy/DataImportStrategy.java`
- Create: `src/main/java/com/strongwine/strongwine/service/strategy/DataExportStrategy.java`
- Create: `src/main/java/com/strongwine/strongwine/service/strategy/ImportResult.java`
- Create: `src/main/java/com/strongwine/strongwine/service/strategy/ImportErrorItem.java`
- Create: `src/main/java/com/strongwine/strongwine/service/strategy/ImportOptions.java`
- Test: `src/test/java/com/strongwine/strongwine/service/strategy/StrategyInterfaceTest.java`

**Interfaces:**
- Consumes: Java Standard Library (`InputStream`, `List`).
- Produces: `DataImportStrategy<T>`, `DataExportStrategy<T>`, `ImportResult<T>`.

- [ ] **Step 1: Viết test cho Strategy Interfaces và Result Models**

```java
package com.strongwine.strongwine.service.strategy;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StrategyInterfaceTest {

    @Test
    void testImportResultAggregation() {
        ImportResult<String> result = new ImportResult<>();
        result.addSuccess("Item 1");
        result.addSuccess("Item 2");
        result.addError(new ImportErrorItem(3, "price", "-100", "Giá không được âm"));

        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getErrorCount());
        assertEquals(3, result.getTotalCount());
        assertFalse(result.hasErrorsOnly());
    }

    @Test
    void testDummyStrategyImplementation() {
        DataImportStrategy<String> dummyStrategy = new DataImportStrategy<>() {
            @Override
            public ImportResult<String> importData(InputStream inputStream, ImportOptions options) {
                ImportResult<String> res = new ImportResult<>();
                res.addSuccess("Mocked Item");
                return res;
            }

            @Override
            public boolean supportsFormat(String fileExtension) {
                return "txt".equalsIgnoreCase(fileExtension);
            }
        };

        assertTrue(dummyStrategy.supportsFormat("txt"));
        assertFalse(dummyStrategy.supportsFormat("xlsx"));
        ImportResult<String> res = dummyStrategy.importData(new ByteArrayInputStream("data".getBytes()), new ImportOptions());
        assertEquals(1, res.getSuccessCount());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=StrategyInterfaceTest
```
Expected: `BUILD FAILURE` (Compilation failure: cannot find symbol `DataImportStrategy`, `ImportResult`).

- [ ] **Step 3: Triển khai các interface và model Strategy**

Tạo `ImportOptions.java`:
```java
package com.strongwine.strongwine.service.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportOptions {
    @Builder.Default
    private boolean dryRun = false; // Chế độ xem trước (không ghi DB)
    
    @Builder.Default
    private InventoryMode inventoryMode = InventoryMode.ADD; // ADD (cộng dồn) hoặc REPLACE (ghi đè)

    @Builder.Default
    private Long defaultWarehouseId = 1L; // Kho mặc định nếu file không ghi

    public enum InventoryMode {
        ADD,
        REPLACE
    }
}
```

Tạo `ImportErrorItem.java`:
```java
package com.strongwine.strongwine.service.strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorItem {
    private int rowNumber;
    private String fieldName;
    private String invalidValue;
    private String errorMessage;
}
```

Tạo `ImportResult.java`:
```java
package com.strongwine.strongwine.service.strategy;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ImportResult<T> {
    private List<T> successItems = new ArrayList<>();
    private List<ImportErrorItem> errors = new ArrayList<>();
    private int updatedCount = 0;
    private int insertedCount = 0;

    public void addSuccess(T item) {
        successItems.add(item);
    }

    public void addError(ImportErrorItem error) {
        errors.add(error);
    }

    public int getSuccessCount() {
        return successItems.size();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getTotalCount() {
        return getSuccessCount() + getErrorCount();
    }

    public boolean hasErrorsOnly() {
        return getSuccessCount() == 0 && getErrorCount() > 0;
    }
}
```

Tạo `DataImportStrategy.java`:
```java
package com.strongwine.strongwine.service.strategy;

import java.io.InputStream;

public interface DataImportStrategy<T> {
    ImportResult<T> importData(InputStream inputStream, ImportOptions options);
    boolean supportsFormat(String fileExtension);
}
```

Tạo `DataExportStrategy.java`:
```java
package com.strongwine.strongwine.service.strategy;

import java.util.List;

public interface DataExportStrategy<T> {
    byte[] exportData(List<T> data);
    String getContentType();
    String getFileExtension();
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=StrategyInterfaceTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra tính nhất quán bằng compile:
```powershell
./mvnw compile -DskipTests
```
Expected output: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/service/strategy/ src/test/java/com/strongwine/strongwine/service/strategy/
git commit -m "feat: implement strategy pattern interfaces and models for data import/export"
```

---

### Task 1.3: Chuẩn Hóa Exception Hierarchy & Centralized Global Exception Handler

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/exception/StrongWineException.java`
- Create: `src/main/java/com/strongwine/strongwine/exception/ExcelImportValidationException.java`
- Create: `src/main/java/com/strongwine/strongwine/exception/ResourceNotFoundException.java`
- Create: `src/main/java/com/strongwine/strongwine/exception/InsufficientStockException.java`
- Create: `src/main/java/com/strongwine/strongwine/exception/ApiErrorResponse.java`
- Create: `src/main/java/com/strongwine/strongwine/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/strongwine/strongwine/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: Spring Web MVC (`@RestControllerAdvice`, `@ExceptionHandler`).
- Produces: Chuẩn hóa phản hồi JSON RFC 7807 cho API và Flash Attributes cho Thymeleaf.

- [ ] **Step 1: Viết test cho Exception Handling**

```java
package com.strongwine.strongwine.exception;

import com.strongwine.strongwine.service.strategy.ImportErrorItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Sản phẩm không tồn tại");
        ResponseEntity<ApiErrorResponse> response = handler.handleResourceNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Sản phẩm không tồn tại", response.getBody().getMessage());
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void testHandleExcelValidationException() {
        List<ImportErrorItem> errors = List.of(new ImportErrorItem(2, "price", "-10", "Giá không hợp lệ"));
        ExcelImportValidationException ex = new ExcelImportValidationException("Lỗi nhập file", errors);

        ResponseEntity<ApiErrorResponse> response = handler.handleExcelValidation(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getDetails().size());
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=GlobalExceptionHandlerTest
```
Expected: `BUILD FAILURE`.

- [ ] **Step 3: Tạo các Exception Class và GlobalExceptionHandler**

Tạo `StrongWineException.java`:
```java
package com.strongwine.strongwine.exception;

public class StrongWineException extends RuntimeException {
    public StrongWineException(String message) {
        super(message);
    }
    public StrongWineException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Tạo `ResourceNotFoundException.java`:
```java
package com.strongwine.strongwine.exception;

public class ResourceNotFoundException extends StrongWineException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

Tạo `InsufficientStockException.java`:
```java
package com.strongwine.strongwine.exception;

public class InsufficientStockException extends StrongWineException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
```

Tạo `ExcelImportValidationException.java`:
```java
package com.strongwine.strongwine.exception;

import com.strongwine.strongwine.service.strategy.ImportErrorItem;
import lombok.Getter;
import java.util.List;

@Getter
public class ExcelImportValidationException extends StrongWineException {
    private final List<ImportErrorItem> errors;

    public ExcelImportValidationException(String message, List<ImportErrorItem> errors) {
        super(message);
        this.errors = errors;
    }
}
```

Tạo `ApiErrorResponse.java`:
```java
package com.strongwine.strongwine.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private int status;
    private String error;
    private String message;
    private List<?> details;
}
```

Tạo `GlobalExceptionHandler.java`:
```java
package com.strongwine.strongwine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ExcelImportValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleExcelValidation(ExcelImportValidationException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message(ex.getMessage())
                .details(ex.getErrors())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientStock(InsufficientStockException ex) {
        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("Insufficient Stock")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=GlobalExceptionHandlerTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra compile:
```powershell
./mvnw compile -DskipTests
```
Expected output: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/exception/ src/test/java/com/strongwine/strongwine/exception/
git commit -m "feat: establish domain exception hierarchy and centralized global exception handler"
```

---

### Task 1.4: Xây Dựng Hạ Tầng Server-Sent Events (SSE) Đơn Hàng & Thông Báo

**Files:**
- Create: `src/main/java/com/strongwine/strongwine/service/realtime/SseNotificationService.java`
- Create: `src/main/java/com/strongwine/strongwine/controller/api/SseNotificationApiController.java`
- Test: `src/test/java/com/strongwine/strongwine/service/realtime/SseNotificationServiceTest.java`

**Interfaces:**
- Consumes: Spring Web MVC (`SseEmitter`).
- Produces: `/api/live/ping`, `/api/live/orders/{orderId}/stream`, broadcast event API.

- [ ] **Step 1: Viết test cho SSE Notification Service**

```java
package com.strongwine.strongwine.service.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;

class SseNotificationServiceTest {

    private SseNotificationService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseNotificationService();
    }

    @Test
    void testSubscribeAndEmitEvent() {
        Long orderId = 101L;
        SseEmitter emitter = sseService.subscribeOrder(orderId);
        assertNotNull(emitter);
        assertEquals(1, sseService.getOrderSubscriberCount(orderId));

        assertDoesNotThrow(() -> {
            sseService.broadcastToOrder(orderId, "order_status_updated", "{\"status\":\"DELIVERING\"}");
        });
    }

    @Test
    void testUnsubscribeOnComplete() {
        Long orderId = 102L;
        SseEmitter emitter = sseService.subscribeOrder(orderId);
        emitter.complete();
        // Giả lập callback completed
        sseService.removeOrderEmitter(orderId, emitter);
        assertEquals(0, sseService.getOrderSubscriberCount(orderId));
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test thất bại (RED)**

Run:
```powershell
./mvnw test -Dtest=SseNotificationServiceTest
```
Expected: `BUILD FAILURE` (SseNotificationService not found).

- [ ] **Step 3: Triển khai `SseNotificationService` và `SseNotificationApiController`**

Tạo `SseNotificationService.java`:
```java
package com.strongwine.strongwine.service.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
    private static final Long DEFAULT_TIMEOUT = 30 * 60 * 1000L; // 30 phút

    private final Map<Long, List<SseEmitter>> orderEmitters = new ConcurrentHashMap<>();
    private final List<SseEmitter> adminEmitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribeOrder(Long orderId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        orderEmitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeOrderEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeOrderEmitter(orderId, emitter));
        emitter.onError(e -> removeOrderEmitter(orderId, emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("connected"));
        } catch (IOException e) {
            removeOrderEmitter(orderId, emitter);
        }
        return emitter;
    }

    public SseEmitter subscribeAdmin() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        adminEmitters.add(emitter);

        emitter.onCompletion(() -> adminEmitters.remove(emitter));
        emitter.onTimeout(() -> adminEmitters.remove(emitter));
        emitter.onError(e -> adminEmitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("init").data("admin_connected"));
        } catch (IOException e) {
            adminEmitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcastToOrder(Long orderId, String eventName, Object data) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters == null || emitters.isEmpty()) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                removeOrderEmitter(orderId, emitter);
            }
        }
    }

    public void broadcastToAdmin(String eventName, Object data) {
        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                adminEmitters.remove(emitter);
            }
        }
    }

    public void removeOrderEmitter(Long orderId, SseEmitter emitter) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                orderEmitters.remove(orderId);
            }
        }
    }

    public int getOrderSubscriberCount(Long orderId) {
        List<SseEmitter> emitters = orderEmitters.get(orderId);
        return emitters != null ? emitters.size() : 0;
    }

    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        orderEmitters.forEach((orderId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                } catch (IOException e) {
                    removeOrderEmitter(orderId, emitter);
                }
            }
        });

        for (SseEmitter emitter : adminEmitters) {
            try {
                emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
            } catch (IOException e) {
                adminEmitters.remove(emitter);
            }
        }
    }
}
```

Tạo `SseNotificationApiController.java`:
```java
package com.strongwine.strongwine.controller.api;

import com.strongwine.strongwine.service.realtime.SseNotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/live")
public class SseNotificationApiController {

    private final SseNotificationService sseService;

    public SseNotificationApiController(SseNotificationService sseService) {
        this.sseService = sseService;
    }

    @GetMapping(value = "/orders/{orderId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderEvents(@PathVariable Long orderId) {
        return sseService.subscribeOrder(orderId);
    }

    @GetMapping(value = "/admin/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAdminNotifications() {
        return sseService.subscribeAdmin();
    }
}
```

- [ ] **Step 4: Chạy test để xác nhận test thành công (GREEN)**

Run:
```powershell
./mvnw test -Dtest=SseNotificationServiceTest
```
Expected: `BUILD SUCCESS` (Tests run: 2, Failures: 0, Errors: 0).

- [ ] **Step 5: Live Runtime Test**

Kiểm tra toàn bộ test suite của Phase 1:
```powershell
./mvnw test -Dtest=PoiDependencyTest,StrategyInterfaceTest,GlobalExceptionHandlerTest,SseNotificationServiceTest
```
Expected: `BUILD SUCCESS` (Tổng cộng 7 tests, 0 failures, 0 errors).

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/strongwine/strongwine/service/realtime/ src/main/java/com/strongwine/strongwine/controller/api/SseNotificationApiController.java src/test/java/com/strongwine/strongwine/service/realtime/
git commit -m "feat: implement real-time server-sent events (sse) broker and endpoints"
```
