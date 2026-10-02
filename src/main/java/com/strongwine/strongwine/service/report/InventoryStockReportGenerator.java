package com.strongwine.strongwine.service.report;

import com.strongwine.strongwine.entity.Inventory;
import com.strongwine.strongwine.repository.InventoryRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryStockReportGenerator extends AbstractExcelReportGenerator<Void> {

    private final InventoryRepository inventoryRepository;

    public InventoryStockReportGenerator(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    protected String getSheetName() {
        return "Báo Cáo Tồn Kho";
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{"Tên Rượu", "Kho Hàng", "Tồn Thực Tế", "Đã Giữ (Reserved)", "Khả Dụng", "Ngưỡng Cảnh Báo", "Trạng Thái"};
    }

    @Override
    protected void populateDataRows(Sheet sheet, Void criteria) {
        List<Inventory> list = inventoryRepository.findAll();
        int r = 1;
        for (Inventory inv : list) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(inv.getWine() != null ? inv.getWine().getName() : "N/A");
            row.createCell(1).setCellValue(inv.getWarehouse() != null ? inv.getWarehouse().getName() : "N/A");
            row.createCell(2).setCellValue(inv.getCurrentQuantity() != null ? inv.getCurrentQuantity() : 0);
            row.createCell(3).setCellValue(inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0);
            int current = inv.getCurrentQuantity() != null ? inv.getCurrentQuantity() : 0;
            int reserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
            int available = current - reserved;
            row.createCell(4).setCellValue(available);
            int reorder = inv.getReorderLevel() != null ? inv.getReorderLevel() : 10;
            row.createCell(5).setCellValue(reorder);
            String status = available <= reorder ? "CẢNH BÁO SẮP HẾT" : "ỔN ĐỊNH";
            row.createCell(6).setCellValue(status);
        }
    }
}
