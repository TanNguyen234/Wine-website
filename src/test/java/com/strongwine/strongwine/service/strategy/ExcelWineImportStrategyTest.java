package com.strongwine.strongwine.service.strategy;

import com.strongwine.strongwine.entity.Category;
import com.strongwine.strongwine.entity.Warehouse;
import com.strongwine.strongwine.entity.Wine;
import com.strongwine.strongwine.repository.CategoryRepository;
import com.strongwine.strongwine.repository.InventoryRepository;
import com.strongwine.strongwine.repository.WarehouseRepository;
import com.strongwine.strongwine.repository.WineRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelWineImportStrategyTest {

    @Mock private WineRepository wineRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private WarehouseRepository warehouseRepository;

    private ExcelWineImportStrategy strategy;

    private static final Object[][] REAL_BULK_WINES = new Object[][]{
        {"Château Cheval Blanc Saint-Émilion Premier Grand Cru Classé A", "Red", 2018, 13800000.0, "France",
            "Tuyệt tác Bờ Phải Bordeaux kết hợp Merlot và Cabernet Franc; hương quả mâm xôi đen, hoa violet, nấm truffle, da thuộc và hậu vị lụa là.",
            "Vang đỏ", 18, "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=920&q=80"},
        {"Château Haut-Brion Pessac-Léognan Premier Grand Cru Classé", "Red", 2017, 12600000.0, "France",
            "Lâu đài cổ nhất trong bảng phân hạng 1855; nổi bật hương khói khoáng chất đặc trưng vùng Graves, cassis, hộp xì gà và gỗ tuyết tùng.",
            "Vang đỏ", 15, "https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=920&q=80"},
        {"Tignanello Marchesi Antinori Toscana IGT", "Red", 2019, 3850000.0, "Italy",
            "Biểu tượng tiên phong dòng Super Tuscan từ Sangiovese, Cabernet Sauvignon và Cabernet Franc; hương anh đào chín, thảo mộc Địa Trung Hải và vani.",
            "Vang đỏ", 36, "https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=920&q=80"},
        {"Ornellaia Tenuta dell'Ornellaia Bolgheri Superiore", "Red", 2018, 6450000.0, "Italy",
            "Cấu trúc quyền lực nhưng vô cùng mềm mại từ vùng duyên hải Bolgheri; hương quả lý chua đen, hương thảo, sô-cô-la đắng và gia vị ấm.",
            "Vang đỏ", 22, "https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=920&q=80"},
        {"Solaia Marchesi Antinori Toscana IGT", "Red", 2018, 8750000.0, "Italy",
            "Vang đỏ đỉnh cao từ sườn đồi đầy nắng nhất điền trang Tignanello; đậm đặc hương quả mọng đen, bạc hà, cà phê rang và tannin mịn màng.",
            "Vang đỏ", 14, "https://images.unsplash.com/photo-1584916201218-f4242ceb4809?auto=format&fit=crop&w=920&q=80"},
        {"Vega Sicilia Único Ribera del Duero Gran Reserva", "Red", 2013, 11900000.0, "Spain",
            "Huyền thoại vang Tây Ban Nha ủ 10 năm trước khi xuất xưởng; tầng hương sâu thẳm của mận khô, thuốc lá, gỗ đàn hương và thảo mộc rừng.",
            "Vang đỏ", 12, "https://images.unsplash.com/photo-1568213816046-0ee1c42bd559?auto=format&fit=crop&w=920&q=80"},
        {"Penfolds Bin 389 Cabernet Shiraz South Australia", "Red", 2020, 2150000.0, "Australia",
            "Được mệnh danh là 'Baby Grange' ủ trong chính thùng sồi của Penfolds Grange niên vụ trước; cân bằng hoàn hảo giữa cấu trúc Cabernet và độ đậm Shiraz.",
            "Vang đỏ", 48, "https://images.unsplash.com/photo-1547595628-c61a29f496f0?auto=format&fit=crop&w=920&q=80"},
        {"Stag's Leap Wine Cellars CASK 23 Napa Valley", "Red", 2019, 6890000.0, "USA",
            "Một trong những Cabernet Sauvignon danh giá nhất thế giới từ Napa Valley; hương việt quất, cassis, hoa violet và kết cấu mượt như nhung.",
            "Vang đỏ", 20, "https://images.unsplash.com/photo-1578911373434-0cb395d2cbfb?auto=format&fit=crop&w=920&q=80"},
        {"Caymus Vineyards Special Selection Cabernet Sauvignon", "Red", 2018, 5250000.0, "USA",
            "Phong cách Napa Valley đậm đà quyến rũ; hương mứt mâm xôi đen, ca cao, vani gỗ sồi mới và tannin tròn trịa dễ thưởng thức.",
            "Vang đỏ", 28, "https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=921&q=80"},
        {"Seña Aconcagua Valley Chadwick & Mondavi", "Red", 2019, 4290000.0, "Chile",
            "Liên doanh lịch sử giữa Eduardo Chadwick và Robert Mondavi theo chuẩn canh tác sinh học Biodynamic; hương trái cây đỏ, tiêu đen và khoáng chất núi cao.",
            "Vang đỏ", 30, "https://images.unsplash.com/photo-1506377247377-2a5b3b417ebb?auto=format&fit=crop&w=921&q=80"},
        {"Concha y Toro Don Melchor Cabernet Sauvignon Puente Alto", "Red", 2020, 3590000.0, "Chile",
            "Biểu tượng Cabernet Sauvignon từ thung lũng Maipo ở độ cao 650m dưới chân núi Andes; hương lý chua đen tinh khiết, than chì và hậu vị bền bỉ.",
            "Vang đỏ", 35, "https://images.unsplash.com/photo-1553361371-9b22f78e8b1d?auto=format&fit=crop&w=921&q=80"},
        {"Bodega Catena Zapata Nicolás Catena Zapata Mendoza", "Red", 2019, 2650000.0, "Argentina",
            "Sự pha trộn Cabernet Sauvignon và Malbec từ các vườn nho độ cao kỷ lục tại Mendoza; hương hoa tím, quả đen chín, hạt tiêu và vị chát sắc sảo.",
            "Vang đỏ", 40, "https://images.unsplash.com/photo-1516594915697-87eb3b1c14ea?auto=format&fit=crop&w=921&q=80"},
        {"Domaine Leflaive Puligny-Montrachet Blanc", "White", 2020, 4850000.0, "France",
            "Đỉnh cao Chardonnay vùng Burgundy canh tác Biodynamic; hương hoa trắng, chanh vàng Meyer, hạt phỉ nướng và vị khoáng đá vôi tinh khiết.",
            "Vang trắng", 18, "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=920&q=80"},
        {"William Fèvre Chablis Grand Cru Les Clos", "White", 2020, 3350000.0, "France",
            "Grand Cru danh tiếng nhất vùng Chablis; độ chua sắc nét, hương vỏ bưởi, táo xanh, hàu biển và khoáng chất đá phấn Kimmeridgian kéo dài.",
            "Vang trắng", 24, "https://images.unsplash.com/photo-1566754436893-98224ee05be4?auto=format&fit=crop&w=920&q=80"},
        {"Cloudy Bay Te Koko Barrel-Fermented Sauvignon Blanc", "White", 2020, 1690000.0, "New Zealand",
            "Sauvignon Blanc lên men bằng men tự nhiên trong thùng sồi Pháp; hương chanh ngâm, sả, gừng tươi, bánh brioche và cấu trúc kem mịn độc đáo.",
            "Vang trắng", 36, "https://images.unsplash.com/photo-1585553616435-2dc0a54e271d?auto=format&fit=crop&w=920&q=80"},
        {"Penfolds Yattarna Bin 144 Chardonnay", "White", 2019, 3950000.0, "Australia",
            "Được ví như 'Grange trắng' của nước Úc từ vùng khí hậu mát Tasmania; hương đào trắng, hoa nhài, đá lửa và gỗ sồi Pháp cực kỳ tinh tế.",
            "Vang trắng", 16, "https://images.unsplash.com/photo-1470337458703-46ad1756a187?auto=format&fit=crop&w=921&q=80"},
        {"Domaines Ott Château de Selle Côtes de Provence Rosé", "Rose", 2022, 1390000.0, "France",
            "Vang hồng Grand Cru vùng Provence trong dáng chai amphora biểu tượng; hương cam đỏ, đào trắng, thảo mộc khô và hậu vị khoáng thanh tao.",
            "Vang hồng", 42, "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=920&q=80"},
        {"Minuty Rose et Or Côtes de Provence", "Rose", 2022, 1150000.0, "France",
            "Biểu tượng mùa hè vùng Saint-Tropez từ những gốc nho Grenache cổ thụ; hương bưởi hồng, bạc hà, dâu rừng trắng và vị chua sảng khoái.",
            "Vang hồng", 50, "https://images.unsplash.com/photo-1558001373-7b93ee48ffa0?auto=format&fit=crop&w=921&q=80"},
        {"Krug Grande Cuvée 171ème Édition Brut Champagne", "Sparkling", 2015, 6950000.0, "France",
            "Phối trộn từ hơn 130 loại vang dự trữ thuộc 12 niên vụ khác nhau; hương bánh hạnh nhân, mật ong, kẹo vỏ cam và bọt sủi mịn hoàn hảo.",
            "Vang sủi", 16, "https://images.unsplash.com/photo-1594498653385-d5172c532c00?auto=format&fit=crop&w=920&q=80"},
        {"Louis Roederer Cristal Millésimé Brut Champagne", "Sparkling", 2014, 8450000.0, "France",
            "Champagne Prestige Cuvée từng phục vụ Sa hoàng Nga từ năm 1876; hương quả có múi chín, đào trắng, hạnh nhân nướng và độ khoáng phấn trắng.",
            "Vang sủi", 14, "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=920&q=80"}
    };

    @BeforeEach
    void setUp() {
        strategy = new ExcelWineImportStrategy(wineRepository, categoryRepository, inventoryRepository, warehouseRepository);
    }

    private byte[] createSampleExcel(boolean includeInvalidRow) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Wines");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Tên Sản Phẩm");
            header.createCell(1).setCellValue("Loại Rượu");
            header.createCell(2).setCellValue("Năm Sản Xuất");
            header.createCell(3).setCellValue("Giá Bán");
            header.createCell(4).setCellValue("Xuất Xứ");
            header.createCell(5).setCellValue("Mô Tả");
            header.createCell(6).setCellValue("Danh Mục");
            header.createCell(7).setCellValue("Số Lượng Kho");
            header.createCell(8).setCellValue("URL Hình Ảnh");

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Chateau Margaux 2018");
            row1.createCell(1).setCellValue("Red");
            row1.createCell(2).setCellValue(2018);
            row1.createCell(3).setCellValue(2500000.0);
            row1.createCell(4).setCellValue("Pháp");
            row1.createCell(5).setCellValue("Hương gỗ sồi đậm đà");
            row1.createCell(6).setCellValue("Vang Pháp");
            row1.createCell(7).setCellValue(24);
            row1.createCell(8).setCellValue("https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?auto=format&fit=crop&w=900&q=80");

            if (includeInvalidRow) {
                Row row2 = sheet.createRow(2);
                row2.createCell(0).setCellValue("Invalid Wine");
                row2.createCell(1).setCellValue("Unknown");
                row2.createCell(2).setCellValue(1800);
                row2.createCell(3).setCellValue(-500);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] buildRealBulkDatasetWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Danh Sách Rượu Vang");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] headers = {
                "Tên Sản Phẩm (*)", "Loại Rượu (*)", "Năm Sản Xuất (*)", "Giá Bán (VNĐ) (*)",
                "Xuất Xứ", "Mô Tả", "Tên Danh Mục", "Số Lượng Kho Khởi Tạo", "URL Hình Ảnh"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 0 || i == 5 || i == 8 ? 11000 : 5200);
            }

            for (int r = 0; r < REAL_BULK_WINES.length; r++) {
                Object[] w = REAL_BULK_WINES[r];
                Row row = sheet.createRow(r + 1);
                row.createCell(0).setCellValue((String) w[0]);
                row.createCell(1).setCellValue((String) w[1]);
                row.createCell(2).setCellValue((Integer) w[2]);
                row.createCell(3).setCellValue((Double) w[3]);
                row.createCell(4).setCellValue((String) w[4]);
                row.createCell(5).setCellValue((String) w[5]);
                row.createCell(6).setCellValue((String) w[6]);
                row.createCell(7).setCellValue((Integer) w[7]);
                row.createCell(8).setCellValue((String) w[8]);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void testImportValidExcel_DryRun() throws IOException {
        byte[] excelBytes = createSampleExcel(false);
        when(wineRepository.findByNameIgnoreCaseAndYearAndDeletedFalse("Chateau Margaux 2018", 2018))
                .thenReturn(Optional.empty());

        ImportOptions options = ImportOptions.builder().dryRun(true).build();
        ImportResult<Wine> result = strategy.importData(new ByteArrayInputStream(excelBytes), options);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getInsertedCount());
        verify(wineRepository, never()).save(any(Wine.class));
    }

    @Test
    void testImportInvalidExcel_CollectsErrors() throws IOException {
        byte[] excelBytes = createSampleExcel(true);
        ImportOptions options = ImportOptions.builder().dryRun(true).build();
        ImportResult<Wine> result = strategy.importData(new ByteArrayInputStream(excelBytes), options);

        assertEquals(1, result.getSuccessCount());
        assertTrue(result.getErrorCount() >= 1);
        assertEquals(3, result.getErrors().get(0).getRowNumber());
    }

    @Test
    void testGenerateAndVerify20RealWinesBulkImportFile() throws IOException {
        byte[] workbookBytes = buildRealBulkDatasetWorkbook();

        Path sampleDir = Paths.get("sample-data");
        Files.createDirectories(sampleDir);
        Path sampleXlsxPath = sampleDir.resolve("strongwine-admin-bulk-import-20-wines.xlsx");
        Files.write(sampleXlsxPath, workbookBytes);

        Path staticSamplesDir = Paths.get("src", "main", "resources", "static", "samples");
        Files.createDirectories(staticSamplesDir);
        Path staticXlsxPath = staticSamplesDir.resolve("strongwine-admin-bulk-import-20-wines.xlsx");
        Files.write(staticXlsxPath, workbookBytes);

        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("Ten_San_Pham,Loai_Ruou,Nam_San_Xuat,Gia_Ban_VND,Xuat_Xu,Mo_Ta,Ten_Danh_Muc,So_Luong_Kho_Khoi_Tao,URL_Hinh_Anh\n");
        for (Object[] w : REAL_BULK_WINES) {
            csvBuilder.append('"').append(((String) w[0]).replace("\"", "\"\"")).append("\",")
                    .append(w[1]).append(',')
                    .append(w[2]).append(',')
                    .append(String.format("%.0f", (Double) w[3])).append(',')
                    .append('"').append(w[4]).append("\",")
                    .append('"').append(((String) w[5]).replace("\"", "\"\"")).append("\",")
                    .append('"').append(w[6]).append("\",")
                    .append(w[7]).append(',')
                    .append('"').append(w[8]).append("\"\n");
        }
        Files.writeString(sampleDir.resolve("strongwine-admin-bulk-import-20-wines.csv"),
                csvBuilder.toString(), StandardCharsets.UTF_8);

        when(wineRepository.findByNameIgnoreCaseAndYearAndDeletedFalse(anyString(), anyInt()))
                .thenReturn(Optional.empty());
        when(categoryRepository.findByName(anyString()))
                .thenAnswer(inv -> {
                    Category c = new Category();
                    c.setName(inv.getArgument(0));
                    return Optional.of(c);
                });
        Warehouse wh = new Warehouse();
        wh.setId(1L);
        wh.setName("Main Warehouse");
        wh.setActive(true);
        when(warehouseRepository.findById(1L))
                .thenReturn(Optional.of(wh));
        when(wineRepository.save(any(Wine.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        try (InputStream is = Files.newInputStream(sampleXlsxPath)) {
            ImportOptions options = ImportOptions.builder()
                    .dryRun(false)
                    .defaultWarehouseId(1L)
                    .build();
            ImportResult<Wine> result = strategy.importData(is, options);

            assertEquals(20, result.getSuccessCount(), "All 20 real wines must succeed");
            assertEquals(0, result.getErrorCount(), "Must have 0 validation errors");
            assertEquals(20, result.getInsertedCount(), "All 20 wines must be inserted");
        }

        ArgumentCaptor<Wine> wineCaptor = ArgumentCaptor.forClass(Wine.class);
        verify(wineRepository, times(20)).save(wineCaptor.capture());
        List<Wine> savedWines = wineCaptor.getAllValues();
        Set<String> distinctImages = new HashSet<>();
        for (Wine w : savedWines) {
            assertNotNull(w.getImageUrl(), "Every imported wine must have an image URL");
            assertTrue(w.getImageUrl().startsWith("https://"), "Image URL must be HTTPS");
            distinctImages.add(w.getImageUrl());
        }
        assertEquals(20, distinctImages.size(), "All 20 bulk-imported wines must have unique image URLs");
    }
}
