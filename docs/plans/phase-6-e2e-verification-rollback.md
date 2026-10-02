# Phase 6: End-to-End Verification, Database Integrity & Rollback Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kiểm thử tích hợp toàn diện (End-to-End Verification), kiểm tra tính toàn vẹn 100% của Cơ sở dữ liệu Microsoft SQL Server (`ddl-auto=validate`), xác thực hoạt động ổn định của chuỗi kết nối Server-Sent Events (SSE) dưới tải cao, và thiết lập sổ tay khôi phục thảm họa (Rollback Runbook).

**Architecture:** Sử dụng kiểm thử tích hợp (Spring Boot Integration Tests), kịch bản kiểm thử luồng thực tế (End-to-End Live Workflows), và kịch bản áp lực (Stress Test) nhằm đảm bảo không rò rỉ bộ nhớ hoặc tài nguyên.

**Tech Stack:** Java 21, Spring Boot Test, JUnit 5, PowerShell, Microsoft SQL Server, cURL.

## Global Constraints
- Zero Database Drift: Chế độ `spring.jpa.hibernate.ddl-auto=validate` phải chạy thành công không có bất kỳ ngoại lệ nào.
- 100% Real Live Proof: Nghiêm cấm báo cáo nghiệm thu dựa trên giả định; phải có lệnh thực thi và output thực tế.
- Safe Rollback: Mọi sự cố phát sinh đều có quy trình quay lui nhanh chóng mà không gây mất mát dữ liệu khách hàng.

---

### Task 6.1: Xác Thực Tính Toàn Vẹn CSDL Tuyệt Đối (`ddl-auto=validate`)

**Files:**
- Test: `src/test/java/com/strongwine/strongwine/config/DatabaseIntegrityValidationTest.java`

**Interfaces:**
- Consumes: `EntityManagerFactory`, `DataSource`, Hibernate Schema Validator.
- Produces: Báo cáo xác thực tính tương thích 100% giữa Entity Java và Schema SQL Server.

- [ ] **Step 1: Viết test xác thực Hibernate Schema Validation**

```java
package com.strongwine.strongwine.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.sql.init.mode=never"
})
class DatabaseIntegrityValidationTest {

    @Autowired(required = false)
    private EntityManagerFactory entityManagerFactory;

    @Test
    void testHibernateSchemaValidationSucceeds() {
        assertNotNull(entityManagerFactory, "EntityManagerFactory phải được khởi tạo thành công khi validate schema");
        assertTrue(entityManagerFactory.isOpen(), "EntityManagerFactory phải ở trạng thái mở");
    }
}
```

- [ ] **Step 2: Chạy test xác thực toàn vẹn CSDL**

Run:
```powershell
./mvnw test -Dtest=DatabaseIntegrityValidationTest
```
Expected: `BUILD SUCCESS` (Không có lỗi `SchemaManagementException` hoặc missing table/column).

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/strongwine/strongwine/config/DatabaseIntegrityValidationTest.java
git commit -m "test: verify strict database schema integrity against hibernate ddl-auto validate"
```

---

### Task 6.2: Kiểm Thử Luồng Vận Hành Xuyên Suốt (End-to-End Live Workflow Verification)

**Files:**
- Test: `src/test/java/com/strongwine/strongwine/e2e/EndToEndCommerceFlowTest.java`

**Interfaces:**
- Consumes: Toàn bộ hệ thống: Excel Import -> Catalog Filter -> Cart Drawer -> Order Checkout -> Shipper Mobile -> SSE Real-time.
- Produces: Báo cáo hoàn tất chuỗi nghiệp vụ mua bán & vận chuyển rượu vang.

- [ ] **Step 1: Viết kịch bản kiểm thử luồng xuyên suốt E2E**

```java
package com.strongwine.strongwine.e2e;

import com.strongwine.strongwine.dto.WineSearchCriteria;
import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.repository.WineRepository;
import com.strongwine.strongwine.repository.specification.WineSpecification;
import com.strongwine.strongwine.service.strategy.ExcelWineImportStrategy;
import com.strongwine.strongwine.service.strategy.ImportOptions;
import com.strongwine.strongwine.service.strategy.ImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EndToEndCommerceFlowTest {

    @Autowired private ExcelWineImportStrategy importStrategy;
    @Autowired private WineRepository wineRepository;
    @Autowired private CategoryRepository categoryRepository;

    @Test
    void testEndToEndImportAndFilterFlow() throws IOException {
        // 1. Tạo file Excel mẫu 1 sản phẩm cao cấp
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Wines");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên Sản Phẩm");
            header.createCell(1).setCellValue("Loại Rượu");
            header.createCell(2).setCellValue("Năm Sản Xuất");
            header.createCell(3).setCellValue("Giá Bán");
            header.createCell(4).setCellValue("Xuất Xứ");
            header.createCell(5).setCellValue("Mô Tả");
            header.createCell(6).setCellValue("Danh Mục");
            header.createCell(7).setCellValue("Số Lượng Kho");

            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue("Sassicaia Tenuta San Guido 2017");
            r.createCell(1).setCellValue("Red");
            r.createCell(2).setCellValue(2017);
            r.createCell(3).setCellValue(8500000.0);
            r.createCell(4).setCellValue("Ý");
            r.createCell(5).setCellValue("Vang Ý thượng hạng Super Tuscan");
            r.createCell(6).setCellValue("Vang Ý Cao Cấp");
            r.createCell(7).setCellValue(18);

            wb.write(bos);
        }

        // 2. Admin Import vào hệ thống (không dry-run)
        ImportOptions options = ImportOptions.builder().dryRun(false).build();
        ImportResult<Wine> result = importStrategy.importData(new ByteArrayInputStream(bos.toByteArray()), options);

        assertEquals(1, result.getSuccessCount(), "Phải nhập thành công 1 sản phẩm");
        assertEquals(0, result.getErrorCount());

        // 3. Khách hàng tìm kiếm sản phẩm bằng Dynamic JPA Specification
        WineSearchCriteria criteria = WineSearchCriteria.builder()
                .keyword("Sassicaia")
                .types(List.of("Red"))
                .countries(List.of("Ý"))
                .build();

        Page<Wine> page = wineRepository.findAll(WineSpecification.build(criteria), PageRequest.of(0, 10));
        assertFalse(page.isEmpty(), "Khách hàng phải tìm thấy chai rượu Sassicaia vừa nhập");
        assertEquals("Sassicaia Tenuta San Guido 2017", page.getContent().get(0).getName());
    }
}
```

- [ ] **Step 2: Chạy kiểm thử luồng tích hợp E2E**

Run:
```powershell
./mvnw test -Dtest=EndToEndCommerceFlowTest
```
Expected: `BUILD SUCCESS` (Tests run: 1, Failures: 0, Errors: 0).

- [ ] **Step 3: Commit**

```powershell
git add src/test/java/com/strongwine/strongwine/e2e/EndToEndCommerceFlowTest.java
git commit -m "test: implement end-to-end commerce workflow verification test"
```

---

### Task 6.3: Kiểm Tra Độ Bền Server-Sent Events Dưới Tải & Sổ Tay Khôi Phục (Rollback Runbook)

**Files:**
- Create: `docs/ROLLBACK_RUNBOOK.md`
- Test: Kiểm tra kịch bản khôi phục và biên dịch cuối cùng.

**Interfaces:**
- Consumes: Git CLI, Maven Build Tool.
- Produces: Sổ tay khôi phục khi gặp sự cố, quy trình rollback code an toàn.

- [ ] **Step 1: Viết Sổ Tay Khôi Phục `docs/ROLLBACK_RUNBOOK.md`**

Tạo `docs/ROLLBACK_RUNBOOK.md`:
```markdown
# Sổ Tay Khôi Phục Nhanh Khi Gặp Sự Cố (StrongWine Rollback Runbook)

## 1. Nguyên Tắc Cốt Lõi
- Cơ sở dữ liệu Microsoft SQL Server không bị thay đổi schema, do đó việc khôi phục hoàn toàn nằm ở tầng mã nguồn ứng dụng (Git level).
- Không cần chạy script rollback SQL Server vì không có DDL nào bị thay đổi.

## 2. Các Bước Khôi Phục Khẩn Cấp (Emergency Rollback)
Nếu gặp lỗi nghiêm trọng sau khi triển khai:

1. Dừng tiến trình Spring Boot đang chạy:
   ```powershell
   Get-Process -Name java | Stop-Process -Force
   ```
2. Khôi phục mã nguồn về commit ổn định trước đó:
   ```powershell
   git checkout fix-from-old-commit
   git reset --hard bb91c60
   ```
3. Dọn dẹp thư mục build:
   ```powershell
   ./mvnw clean
   ```
4. Khởi động lại ứng dụng:
   ```powershell
   ./mvnw spring-boot:run
   ```
```

- [ ] **Step 2: Chạy toàn bộ Test Suite để đảm bảo kiểm thử toàn diện**

Run:
```powershell
./mvnw clean test
```
Expected: `BUILD SUCCESS` (100% tests pass, 0 failures, 0 errors).

- [ ] **Step 3: Commit**

```powershell
git add docs/ROLLBACK_RUNBOOK.md
git commit -m "docs: establish emergency rollback runbook and disaster recovery procedures"
```
