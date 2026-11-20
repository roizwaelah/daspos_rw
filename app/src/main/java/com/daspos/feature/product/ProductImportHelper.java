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

import com.daspos.shared.util.OutputStreamCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ProductImportHelper {
    // Mapping slots: 0=name, 1=price, 2=stock (required); 3..11 optional.
    // Optional slots use -1 when the column is not mapped.
    public static final int FIELD_COUNT = 12;
    public static final int IDX_NAME = 0;
    public static final int IDX_PRICE = 1;
    public static final int IDX_STOCK = 2;
    public static final int IDX_BARCODE = 3;
    public static final int IDX_UNIT_NAME = 4;
    public static final int IDX_COST_PRICE = 5;
    public static final int IDX_PRICE_RENTENG = 6;
    public static final int IDX_PRICE_PAK = 7;
    public static final int IDX_PRICE_KARTON = 8;
    public static final int IDX_FACTOR_RENTENG = 9;
    public static final int IDX_FACTOR_PAK = 10;
    public static final int IDX_FACTOR_KARTON = 11;

    private static int[] emptyMapping() {
        int[] mapping = new int[FIELD_COUNT];
        java.util.Arrays.fill(mapping, -1);
        mapping[IDX_NAME] = 0;
        mapping[IDX_PRICE] = 1;
        mapping[IDX_STOCK] = 2;
        return mapping;
    }

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
            int[] normalized = normalizeMapping(mapping);
            List<Product> valid = new ArrayList<>();
            List<InvalidRow> invalid = new ArrayList<>();
            int rowNo = 1;
            for (String[] row : rawRows) {
                rowNo++;
                ProductResult result = toProduct(row, normalized);
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
        int[] mapping = emptyMapping();
        boolean firstRow = true;
        boolean detected = false;
        String separator = ",";
        String[] columns = defaultColumnNames();

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
        int[] mapping = emptyMapping();
        String[] columns = defaultColumnNames();
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
            String csv = "nama,harga,stok,barcode,satuan,harga_modal,"
                    + "harga_renteng,harga_pak,harga_karton,"
                    + "faktor_renteng,faktor_pak,faktor_karton\n"
                    + "Kopi Susu,18000,20,8990001,pcs,12000,180000,360000,900000,10,20,50\n"
                    + "Mie Instan,4500,50,,,pcs,,,,,,\n";
            OutputStream out = OutputStreamCompat.openForWrite(context, uri);
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
            String[] headers = {
                    "nama", "harga", "stok", "barcode", "satuan", "harga_modal",
                    "harga_renteng", "harga_pak", "harga_karton",
                    "faktor_renteng", "faktor_pak", "faktor_karton"
            };
            Row h = sh.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            Row r1 = sh.createRow(1);
            Object[] sample1 = {"Kopi Susu", 18000, 20, "8990001", "pcs", 12000,
                    180000, 360000, 900000, 10, 20, 50};
            for (int i = 0; i < sample1.length; i++) setCellValue(r1, i, sample1[i]);
            Row r2 = sh.createRow(2);
            Object[] sample2 = {"Mie Instan", 4500, 50, "", "pcs", "", "", "", "", "", "", ""};
            for (int i = 0; i < sample2.length; i++) setCellValue(r2, i, sample2[i]);
            OutputStream out = OutputStreamCompat.openForWrite(context, uri);
            if (out == null) {
                wb.close();
                return false;
            }
            wb.write(out);
            out.flush();
            out.close();
            wb.close();
            return true;
        } catch (Throwable e) { return false; }
    }

    public static int[] normalizeMapping(int[] mapping) {
        int[] out = emptyMapping();
        if (mapping == null) return out;
        for (int i = 0; i < FIELD_COUNT && i < mapping.length; i++) {
            out[i] = mapping[i];
        }
        return out;
    }

    private static String[] defaultColumnNames() {
        return new String[]{"Kolom 1", "Kolom 2", "Kolom 3"};
    }

    private static ProductResult toProduct(String[] cells, int[] mapping) {
        if (mapping == null || mapping.length < 3) return new ProductResult(null, "Mapping tidak valid");
        String name = getByIndex(cells, mapping[IDX_NAME]).trim();
        String priceText = getByIndex(cells, mapping[IDX_PRICE]).trim();
        String stockText = getByIndex(cells, mapping[IDX_STOCK]).trim();

        if (name.isEmpty()) return new ProductResult(null, "Nama produk kosong");
        Double price = parseDoubleSafe(priceText);
        if (price == null) return new ProductResult(null, "Harga tidak valid");
        Integer stock = parseIntSafe(stockText);
        if (stock == null) return new ProductResult(null, "Stok tidak valid");

        String barcode = optionalText(cells, mapping, IDX_BARCODE);
        String unitName = optionalText(cells, mapping, IDX_UNIT_NAME);
        String optionalError = validateOptionalNumbers(cells, mapping);
        if (optionalError != null) return new ProductResult(null, optionalError);
        Double costPrice = optionalDouble(cells, mapping, IDX_COST_PRICE);
        Double priceRenteng = optionalDouble(cells, mapping, IDX_PRICE_RENTENG);
        Double pricePak = optionalDouble(cells, mapping, IDX_PRICE_PAK);
        Double priceKarton = optionalDouble(cells, mapping, IDX_PRICE_KARTON);
        Integer factorRenteng = optionalInt(cells, mapping, IDX_FACTOR_RENTENG);
        Integer factorPak = optionalInt(cells, mapping, IDX_FACTOR_PAK);
        Integer factorKarton = optionalInt(cells, mapping, IDX_FACTOR_KARTON);

        Product product = new Product(
                UUID.randomUUID().toString(),
                name,
                barcode,
                price,
                costPrice == null ? 0 : costPrice,
                stock,
                unitName,
                true,
                price,
                priceRenteng == null ? 0 : priceRenteng,
                pricePak == null ? 0 : pricePak,
                priceKarton == null ? 0 : priceKarton,
                factorRenteng == null ? 0 : factorRenteng,
                factorPak == null ? 0 : factorPak,
                factorKarton == null ? 0 : factorKarton
        );
        return new ProductResult(product, null);
    }

    private static String optionalText(String[] cells, int[] mapping, int fieldIndex) {
        int col = mapping[fieldIndex];
        if (col < 0) return "";
        return getByIndex(cells, col).trim();
    }

    private static Double optionalDouble(String[] cells, int[] mapping, int fieldIndex) {
        String text = optionalText(cells, mapping, fieldIndex);
        if (text.isEmpty()) return null;
        return parseDoubleSafe(text);
    }

    private static Integer optionalInt(String[] cells, int[] mapping, int fieldIndex) {
        String text = optionalText(cells, mapping, fieldIndex);
        if (text.isEmpty()) return null;
        return parseIntSafe(text);
    }

    private static String validateOptionalNumbers(String[] cells, int[] mapping) {
        int[][] numericFields = {
                {IDX_COST_PRICE, IDX_PRICE_RENTENG, IDX_PRICE_PAK, IDX_PRICE_KARTON},
                {IDX_FACTOR_RENTENG, IDX_FACTOR_PAK, IDX_FACTOR_KARTON}
        };
        String[] labels = {
                "Harga modal", "Harga renteng", "Harga pak", "Harga karton",
                "Faktor renteng", "Faktor pak", "Faktor karton"
        };
        int labelIdx = 0;
        for (int[] group : numericFields) {
            for (int field : group) {
                String label = labels[labelIdx++];
                String text = optionalText(cells, mapping, field);
                if (text.isEmpty()) continue;
                boolean ok = field <= IDX_PRICE_KARTON
                        ? parseDoubleSafe(text) != null
                        : parseIntSafe(text) != null;
                if (!ok) return label + " tidak valid";
            }
        }
        return null;
    }

    private static int[] detectHeader(String[] cells) {
        int[] mapping = emptyMapping();
        boolean[] used = new boolean[FIELD_COUNT];
        for (int i = 0; i < cells.length; i++) {
            String normalized = normalize(cells[i]);
            int field = detectField(normalized);
            if (field < 0 || used[field]) continue;
            used[field] = true;
            mapping[field] = i;
        }
        // Header detection only counts when the three required columns matched.
        if (mapping[IDX_NAME] < 0 || mapping[IDX_PRICE] < 0 || mapping[IDX_STOCK] < 0) return null;
        return mapping;
    }

    private static int detectField(String normalized) {
        if (normalized.isEmpty()) return -1;
        // "isi"/"content" indicate pack-size factors ("isi renteng",
        // "contents per carton"), same role as "factor"/"faktor".
        boolean isFactor = normalized.contains("factor") || normalized.contains("faktor")
                || containsWord(normalized, "isi") || normalized.contains("content");
        if (normalized.contains("renteng")) return isFactor ? IDX_FACTOR_RENTENG : IDX_PRICE_RENTENG;
        if (normalized.contains("karton")) return isFactor ? IDX_FACTOR_KARTON : IDX_PRICE_KARTON;
        if (containsWord(normalized, "pak")) return isFactor ? IDX_FACTOR_PAK : IDX_PRICE_PAK;
        // Compound unit-name headers must win over the generic "name" keyword.
        if (normalized.contains("unit name") || normalized.contains("nama satuan")) return IDX_UNIT_NAME;
        if (normalized.contains("barcode") || normalized.contains("ean") || normalized.contains("upc")) return IDX_BARCODE;
        if (normalized.contains("modal") || normalized.contains("hpp")
                || normalized.contains("cost") || normalized.contains("hargamodal")) return IDX_COST_PRICE;
        if (normalized.contains("nama") || normalized.contains("name") || normalized.equals("produk")) return IDX_NAME;
        // "harga"/"price" is checked before unit keywords so "harga satuan" and
        // "unit price" resolve to the sell price, not the unit name.
        if (normalized.contains("harga") || normalized.contains("price")) return IDX_PRICE;
        if (normalized.contains("stok") || normalized.contains("stock")) return IDX_STOCK;
        if (normalized.contains("satuan") || normalized.contains("unitname")
                || normalized.equals("unit") || normalized.startsWith("unit ")) return IDX_UNIT_NAME;
        return -1;
    }

    private static boolean containsWord(String value, String word) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(^|[^a-z])" + java.util.regex.Pattern.quote(word) + "([^a-z]|$)")
                .matcher(value);
        return m.find();
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

    private static void setCellValue(Row row, int index, Object value) {
        if (value instanceof Number) {
            row.createCell(index).setCellValue(((Number) value).doubleValue());
        } else {
            row.createCell(index).setCellValue(String.valueOf(value));
        }
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
