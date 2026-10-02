package com.strongwine.strongwine.service.strategy;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
