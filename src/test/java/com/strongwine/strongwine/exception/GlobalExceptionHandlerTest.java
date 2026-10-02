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
