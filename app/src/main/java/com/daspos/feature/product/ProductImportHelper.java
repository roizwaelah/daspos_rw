package com.daspos.feature.product;

import android.content.Context;
import android.net.Uri;

import com.daspos.model.Product;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProductImportHelper {
    public static class InvalidRow {
        private final int rowNumber;
        private final String reason;
        private final String[] rawRow;

        public InvalidRow(int rowNumber, String reason, String[] rawRow) {
            this.rowNumber = rowNumber;
            this.reason = reason;
            this.rawRow = rawRow;
        }

        public int getRowNumber() { return rowNumber; }
        public String getReason() { return reason; }
        public String[] getRawRow() { return rawRow; }
    }

    public static class Analysis {
        private final List<Product> validProducts;
        private final List<InvalidRow> invalidRows;

        public Analysis(List<Product> validProducts, List<InvalidRow> invalidRows) {
            this.validProducts = validProducts;
            this.invalidRows = invalidRows;
        }

        public List<Product> getValidProducts() { return validProducts; }
        public List<InvalidRow> getInvalidRows() { return invalidRows; }
    }

    public static class ParsedImport {
        private final List<String[]> rawRows;
        private final boolean headerDetected;
        private final String separator;
        private final String[] columns;
        private final int[] suggestedMapping;
        private final String signature;

        public ParsedImport(List<String[]> rawRows, boolean headerDetected, String separator, String[] columns, int[] suggestedMapping, String signature) {
            this.rawRows = rawRows;
            this.headerDetected = headerDetected;
            this.separator = separator;
            this.columns = columns;
            this.suggestedMapping = suggestedMapping;
            this.signature = signature;
        }

        public boolean isHeaderDetected() { return headerDetected; }
        public String getSeparator() { return separator; }
        public String[] getColumns() { return columns; }
        public int[] getSuggestedMapping() { return suggestedMapping; }
        public String getSignature() { return signature; }
        public int getRawRowCount() { return rawRows.size(); }

        public Analysis analyze(int[] mapping) {
            List<Product> valid = new ArrayList<>();
            List<InvalidRow> invalid = new ArrayList<>();
            int rowNo = 1;
            for (String[] row : rawRows) {
                rowNo++;
                ProductResult result = toProduct(row, mapping);
                if (result.product != null) valid.add(result.product);
                else invalid.add(new InvalidRow(rowNo, result.reason, row));
            }
            return new Analysis(valid, invalid);
        }

        public List<Product> buildProducts(int[] mapping) { return analyze(mapping).getValidProducts(); }
        public int countValid(int[] mapping) { return analyze(mapping).getValidProducts().size(); }
        public int countInvalid(int[] mapping) { return analyze(mapping).getInvalidRows().size(); }
    }

    private static class ProductResult {
        final Product product;
        final String reason;
        ProductResult(Product product, String reason) {
            this.product = product;
            this.reason = reason;
        }
    }

    public static ParsedImport parseCsv(Context context, Uri uri) throws Exception {
        List<String[]> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(context.getContentResolver().openInputStream(uri)));
        String line;
        int[] mapping = new int[]{0, 1, 2};
        boolean firstRow = true;
        boolean detected = false;
        String separator = ",";
        String[] columns = new String[]{"Kolom 1", "Kolom 2", "Kolom 3"};

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            if (firstRow) separator = detectSeparator(line);
            String[] parts = split(line, separator);

            if (firstRow) {
                firstRow = false;
                columns = buildColumnNames(parts);
                int[] detectedMap = detectHeader(parts);
                if (detectedMap != null) {
                    mapping = detectedMap;
                    detected = true;
                    continue;
                }
            }
            rows.add(parts);
        }
        reader.close();
        return new ParsedImport(rows, detected, separator, columns, mapping, buildSignature(columns));
    }

    public static ParsedImport parseSpreadsheet(Context context, Uri uri) throws Exception {
        InputStream in = context.getContentResolver().openInputStream(uri);
        Workbook workbook = WorkbookFactory.create(in);
        Sheet sheet = workbook.getSheetAt(0);
        List<String[]> rows = new ArrayList<>();
        boolean first = true;
        boolean detected = false;
        int[] mapping = new int[]{0, 1, 2};
        String[] columns = new String[]{"Kolom 1", "Kolom 2", "Kolom 3"};
        DataFormatter formatter = new DataFormatter();

        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            int maxCols = Math.max(3, row.getLastCellNum());
            for (int i = 0; i < maxCols; i++) cells.add(getString(formatter, row, i));
            String[] arr = cells.toArray(new String[0]);

            if (first) {
                first = false;
                columns = buildColumnNames(arr);
                int[] detectedMap = detectHeader(arr);
                if (detectedMap != null) {
                    mapping = detectedMap;
                    detected = true;
                    continue;
                }
            }
            rows.add(arr);
        }

        workbook.close();
        if (in != null) in.close();
        return new ParsedImport(rows, detected, "xlsx", columns, mapping, buildSignature(columns));
    }

    public static boolean writeTemplateCsv(Context context, Uri uri) {
        try {
            String csv = "nama,harga,stok\nKopi Susu,18000,20\nMie Instan,4500,50\n";
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            if (out == null) return false;
            out.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            out.close();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean writeTemplateXlsx(Context context, Uri uri) {
        try {
            org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.xssf.usermodel.XSSFSheet sh = wb.createSheet("Template");
            Row h = sh.createRow(0);
            h.createCell(0).setCellValue("nama");
            h.createCell(1).setCellValue("harga");
            h.createCell(2).setCellValue("stok");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue("Kopi Susu");
            r1.createCell(1).setCellValue(18000);
            r1.createCell(2).setCellValue(20);
            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue("Mie Instan");
            r2.createCell(1).setCellValue(4500);
            r2.createCell(2).setCellValue(50);
            OutputStream out = context.getContentResolver().openOutputStream(uri);
            if (out == null) {
                wb.close();
                return false;
            }
            wb.write(out);
            out.flush();
            out.close();
            wb.close();
            return true;
        } catch (Exception | org.apache.poi.javax.xml.stream.FactoryConfigurationError e) { return false; }
    }

    private static ProductResult toProduct(String[] cells, int[] mapping) {
        if (mapping == null || mapping.length < 3) return new ProductResult(null, "Mapping tidak valid");
        String name = getByIndex(cells, mapping[0]).trim();
        String priceText = getByIndex(cells, mapping[1]).trim();
        String stockText = getByIndex(cells, mapping[2]).trim();

        if (name.isEmpty()) return new ProductResult(null, "Nama produk kosong");
        Double price = parseDoubleSafe(priceText);
        if (price == null) return new ProductResult(null, "Harga tidak valid");
        Integer stock = parseIntSafe(stockText);
        if (stock == null) return new ProductResult(null, "Stok tidak valid");

        return new ProductResult(new Product(UUID.randomUUID().toString(), name, price, stock), null);
    }

    private static int[] detectHeader(String[] cells) {
        int nameIdx = -1, priceIdx = -1, stockIdx = -1;
        for (int i = 0; i < cells.length; i++) {
            String normalized = normalize(cells[i]);
            if (normalized.contains("nama") || normalized.contains("name") || normalized.equals("produk")) nameIdx = i;
            if (normalized.contains("harga") || normalized.contains("price")) priceIdx = i;
            if (normalized.contains("stok") || normalized.contains("stock")) stockIdx = i;
        }
        if (nameIdx >= 0 && priceIdx >= 0 && stockIdx >= 0) return new int[]{nameIdx, priceIdx, stockIdx};
        return null;
    }

    private static String[] buildColumnNames(String[] header) {
        String[] columns = new String[Math.max(3, header.length)];
        for (int i = 0; i < columns.length; i++) {
            String value = i < header.length ? header[i] : "";
            value = value == null ? "" : value.trim();
            columns[i] = value.isEmpty() ? ("Kolom " + (i + 1)) : value;
        }
        return columns;
    }

    private static String detectSeparator(String line) {
        if (line.contains(";")) return ";";
        if (line.contains("\t")) return "\t";
        return ",";
    }

    private static String[] split(String line, String sep) {
        if ("\t".equals(sep)) return line.split("\\t");
        return line.split(java.util.regex.Pattern.quote(sep));
    }

    private static String getByIndex(String[] arr, int idx) {
        if (idx < 0 || idx >= arr.length) return "";
        return arr[idx] == null ? "" : arr[idx];
    }

    private static String buildSignature(String[] columns) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append("|");
            sb.append(normalize(columns[i]));
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    private static String normalize(String v) {
        return (v == null ? "" : v).trim().toLowerCase(Locale.ROOT);
    }

    private static String getString(DataFormatter formatter, Row row, int i) {
        try { return formatter.formatCellValue(row.getCell(i)).trim(); } catch (Exception e) { return ""; }
    }

    private static Double parseDoubleSafe(String v) {
        try { return Double.parseDouble(v.replace(",", "").replace(" ", "")); } catch (Exception e) { return null; }
    }

    private static Integer parseIntSafe(String v) {
        try { return Integer.parseInt(v.replace(",", "").replace(" ", "")); } catch (Exception e) { return null; }
    }
}
