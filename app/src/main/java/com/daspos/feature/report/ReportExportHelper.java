package com.daspos.feature.report;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.daspos.model.ReportItem;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.OutputStreamCompat;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportExportHelper {
    public static String buildSuggestedFileName(String prefix, String ext) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return prefix + "_" + stamp + "." + ext;
    }

    public static boolean exportPdf(Context context, Uri uri, String title, List<ReportItem> items, int count, double income) {
        PdfDocument document = new PdfDocument();
        try {
            List<ReportItem> safeItems = items == null ? java.util.Collections.emptyList() : items;
            boolean monthlyMode = isMonthlyRows(safeItems);

            Paint titlePaint = paint(16f, true, Color.BLACK);
            Paint subtitlePaint = paint(10f, false, Color.DKGRAY);
            Paint headerTextPaint = paint(10f, true, Color.WHITE);
            Paint bodyTextPaint = paint(10f, false, Color.BLACK);
            Paint bodyTextBoldPaint = paint(10f, true, Color.BLACK);
            Paint borderPaint = paint(1f, false, Color.parseColor("#D1D5DB"));
            borderPaint.setStyle(Paint.Style.STROKE);
            Paint headerFillPaint = paint(1f, false, Color.parseColor("#2E7D32"));
            headerFillPaint.setStyle(Paint.Style.FILL);
            Paint zebraFillPaint = paint(1f, false, Color.parseColor("#F7F8FA"));
            zebraFillPaint.setStyle(Paint.Style.FILL);

            final int pageWidth = 595;  // A4 portrait
            final int pageHeight = 842; // A4 portrait
            final int marginLeft = 28;
            final int marginRight = 28;
            final int contentLeft = marginLeft;
            final int contentRight = pageWidth - marginRight;
            final int pageBottom = pageHeight - 36;
            final int rowHeight = 22;
            final int cellPadding = 6;
            final int[] colWidths = monthlyMode
                    ? new int[]{36, 180, 170, 153} // No, Tanggal, Transaksi, Total
                    : new int[]{36, 160, 140, 95, 108}; // No, ID, Tanggal, Waktu, Total
            final String[] headers = monthlyMode
                    ? new String[]{"No", "Tanggal", "Transaksi", "Total"}
                    : new String[]{"No", "ID", "Tanggal", "Waktu", "Total"};

            int pageNumber = 1;
            int itemIndex = 0;
            boolean wroteAnyPage = false;
            while (!wroteAnyPage || itemIndex < safeItems.size()) {
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                int y = 34;
                canvas.drawText("Laporan Penjualan DasPos", contentLeft, y, titlePaint);
                y += 18;
                canvas.drawText("Periode: " + safe(title), contentLeft, y, subtitlePaint);
                y += 14;
                canvas.drawText("Dibuat: " + formatGeneratedAt(), contentLeft, y, subtitlePaint);
                y += 14;
                canvas.drawText("Total transaksi: " + count, contentLeft, y, subtitlePaint);
                y += 14;
                canvas.drawText("Total pendapatan: " + CurrencyUtils.formatRupiah(income), contentLeft, y, subtitlePaint);
                y += 12;
                canvas.drawText("Halaman " + pageNumber, contentRight - 54, y, subtitlePaint);

                int tableTop = y + 14;
                int tableHeaderBottom = tableTop + rowHeight;
                canvas.drawRect(contentLeft, tableTop, contentRight, tableHeaderBottom, headerFillPaint);

                int x = contentLeft;
                for (int col = 0; col < headers.length; col++) {
                    int colLeft = x;
                    int colRight = x + colWidths[col];
                    drawCellBorder(canvas, colLeft, tableTop, colRight, tableHeaderBottom, borderPaint);
                    String headerText = headers[col];
                    drawLeftText(canvas, ellipsize(headerTextPaint, headerText, colWidths[col] - (cellPadding * 2)),
                            colLeft + cellPadding, tableTop + 15, headerTextPaint);
                    x = colRight;
                }

                y = tableHeaderBottom;
                int rowNumber = 1 + itemIndex;
                while (itemIndex < safeItems.size() && y + rowHeight <= pageBottom) {
                    ReportItem item = safeItems.get(itemIndex);
                    int rowBottom = y + rowHeight;
                    if ((itemIndex % 2) == 1) {
                        canvas.drawRect(contentLeft, y, contentRight, rowBottom, zebraFillPaint);
                    }

                    String[] rowValues = rowValuesFor(item, rowNumber, monthlyMode);
                    x = contentLeft;
                    for (int col = 0; col < headers.length; col++) {
                        int colLeft = x;
                        int colRight = x + colWidths[col];
                        drawCellBorder(canvas, colLeft, y, colRight, rowBottom, borderPaint);

                        if (isCurrencyColumn(col, monthlyMode)) {
                            drawRightText(canvas,
                                    ellipsize(bodyTextBoldPaint, rowValues[col], colWidths[col] - (cellPadding * 2)),
                                    colRight - cellPadding,
                                    y + 15,
                                    bodyTextBoldPaint);
                        } else {
                            drawLeftText(canvas,
                                    ellipsize(bodyTextPaint, rowValues[col], colWidths[col] - (cellPadding * 2)),
                                    colLeft + cellPadding,
                                    y + 15,
                                    bodyTextPaint);
                        }
                        x = colRight;
                    }

                    y = rowBottom;
                    rowNumber++;
                    itemIndex++;
                }

                if (safeItems.isEmpty()) {
                    int rowBottom = y + rowHeight;
                    drawCellBorder(canvas, contentLeft, y, contentRight, rowBottom, borderPaint);
                    drawLeftText(canvas, "Tidak ada data untuk periode ini", contentLeft + cellPadding, y + 15, bodyTextPaint);
                }

                document.finishPage(page);
                wroteAnyPage = true;
                pageNumber++;
            }
            OutputStream out = OutputStreamCompat.openForWrite(context, uri);
            if (out == null) return false;
            document.writeTo(out);
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            document.close();
        }
    }

    public static boolean exportCsv(Context context, Uri uri, List<ReportItem> items) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("ID,Waktu,Total\n");
            for (ReportItem item : items) {
                sb.append(item.getId()).append(",");
                sb.append(item.getTime()).append(",");
                sb.append(item.getTotal()).append("\n");
            }
            OutputStream out = OutputStreamCompat.openForWrite(context, uri);
            if (out == null) return false;
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean exportXlsx(Context context, Uri uri, List<ReportItem> items, int count, double income) {
        XSSFWorkbook workbook = null;
        try {
            workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("Laporan");
            List<ReportItem> safeItems = items == null ? java.util.Collections.emptyList() : items;
            boolean monthlyMode = isMonthlyRows(safeItems);

            DataFormat dataFormat = workbook.createDataFormat();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            Font bodyBoldFont = workbook.createFont();
            bodyBoldFont.setBold(true);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            CellStyle metaLabelStyle = baseStyle(workbook, HorizontalAlignment.LEFT);
            metaLabelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            metaLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            metaLabelStyle.setFont(bodyBoldFont);

            CellStyle metaValueStyle = baseStyle(workbook, HorizontalAlignment.LEFT);
            CellStyle metaCurrencyStyle = baseStyle(workbook, HorizontalAlignment.RIGHT);
            metaCurrencyStyle.setDataFormat(dataFormat.getFormat("\"Rp\" #,##0"));

            CellStyle headerStyle = baseStyle(workbook, HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);

            CellStyle textLeftStyle = baseStyle(workbook, HorizontalAlignment.LEFT);
            CellStyle textCenterStyle = baseStyle(workbook, HorizontalAlignment.CENTER);
            CellStyle currencyStyle = baseStyle(workbook, HorizontalAlignment.RIGHT);
            currencyStyle.setDataFormat(dataFormat.getFormat("\"Rp\" #,##0"));

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Laporan Penjualan DasPos");
            titleCell.setCellStyle(titleStyle);

            Row generatedRow = sheet.createRow(rowIndex++);
            generatedRow.createCell(0).setCellValue("Dibuat");
            generatedRow.createCell(1).setCellValue(formatGeneratedAt());
            generatedRow.getCell(0).setCellStyle(metaLabelStyle);
            generatedRow.getCell(1).setCellStyle(metaValueStyle);

            Row headerInfo = sheet.createRow(rowIndex++);
            headerInfo.createCell(0).setCellValue("Total transaksi");
            headerInfo.createCell(1).setCellValue(count);
            headerInfo.getCell(0).setCellStyle(metaLabelStyle);
            headerInfo.getCell(1).setCellStyle(metaValueStyle);

            Row incomeRow = sheet.createRow(rowIndex++);
            incomeRow.createCell(0).setCellValue("Total pendapatan");
            incomeRow.createCell(1).setCellValue(income);
            incomeRow.getCell(0).setCellStyle(metaLabelStyle);
            incomeRow.getCell(1).setCellStyle(metaCurrencyStyle);

            rowIndex++;

            String[] headers = monthlyMode
                    ? new String[]{"No", "Tanggal", "Transaksi", "Total"}
                    : new String[]{"No", "ID", "Tanggal", "Waktu", "Total"};

            Row header = sheet.createRow(rowIndex++);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = header.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
            }

            int rowNumber = 1;
            for (ReportItem item : safeItems) {
                Row row = sheet.createRow(rowIndex++);
                String[] values = rowValuesFor(item, rowNumber, monthlyMode);
                for (int col = 0; col < values.length; col++) {
                    Cell cell = row.createCell(col);
                    if (isCurrencyColumn(col, monthlyMode)) {
                        cell.setCellValue(item.getTotal());
                        cell.setCellStyle(currencyStyle);
                    } else if (col == 0) {
                        cell.setCellValue(rowNumber);
                        cell.setCellStyle(textCenterStyle);
                    } else {
                        cell.setCellValue(values[col]);
                        cell.setCellStyle(textLeftStyle);
                    }
                }
                rowNumber++;
            }

            if (monthlyMode) {
                sheet.setColumnWidth(0, 2200);
                sheet.setColumnWidth(1, 6200);
                sheet.setColumnWidth(2, 5000);
                sheet.setColumnWidth(3, 4200);
            } else {
                sheet.setColumnWidth(0, 2200);
                sheet.setColumnWidth(1, 5600);
                sheet.setColumnWidth(2, 5400);
                sheet.setColumnWidth(3, 3200);
                sheet.setColumnWidth(4, 4200);
            }

            sheet.createFreezePane(0, 6);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(5, Math.max(5, rowIndex - 1), 0, headers.length - 1));

            OutputStream out = OutputStreamCompat.openForWrite(context, uri);
            if (out == null) return false;
            workbook.write(out);
            out.flush();
            out.close();
            return true;
        } catch (Throwable e) {
            return false;
        } finally {
            try { if (workbook != null) workbook.close(); } catch (Exception ignored) { }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String formatGeneratedAt() {
        return new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("id", "ID")).format(new Date());
    }

    private static Paint paint(float textSize, boolean bold, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setFakeBoldText(bold);
        paint.setColor(color);
        return paint;
    }

    private static void drawCellBorder(Canvas canvas, int left, int top, int right, int bottom, Paint paint) {
        canvas.drawRect(left, top, right, bottom, paint);
    }

    private static void drawLeftText(Canvas canvas, String text, int x, int baselineY, Paint paint) {
        canvas.drawText(text == null ? "" : text, x, baselineY, paint);
    }

    private static void drawRightText(Canvas canvas, String text, int x, int baselineY, Paint paint) {
        String value = text == null ? "" : text;
        float width = paint.measureText(value);
        canvas.drawText(value, x - width, baselineY, paint);
    }

    private static String ellipsize(Paint paint, String text, int maxWidth) {
        String value = safe(text);
        if (paint.measureText(value) <= maxWidth) return value;
        String ellipsis = "...";
        float ellipsisWidth = paint.measureText(ellipsis);
        int end = value.length();
        while (end > 0 && paint.measureText(value, 0, end) + ellipsisWidth > maxWidth) {
            end--;
        }
        return (end <= 0) ? ellipsis : value.substring(0, end) + ellipsis;
    }

    private static boolean isMonthlyRows(List<ReportItem> items) {
        if (items == null || items.isEmpty()) return false;
        int monthlyLike = 0;
        for (ReportItem item : items) {
            String dateInfo = safe(item.getDate()).toLowerCase(Locale.ROOT);
            String time = safe(item.getTime()).trim();
            if (time.isEmpty() && dateInfo.contains("transaksi")) monthlyLike++;
        }
        return monthlyLike >= Math.max(1, items.size() / 2);
    }

    private static String[] rowValuesFor(ReportItem item, int rowNumber, boolean monthlyMode) {
        if (monthlyMode) {
            return new String[]{
                    String.valueOf(rowNumber),
                    safe(item.getId()),
                    safe(item.getDate()),
                    CurrencyUtils.formatRupiah(item.getTotal())
            };
        }

        return new String[]{
                String.valueOf(rowNumber),
                safe(item.getId()),
                safe(item.getDate()),
                safe(item.getTime()),
                CurrencyUtils.formatRupiah(item.getTotal())
        };
    }

    private static boolean isCurrencyColumn(int column, boolean monthlyMode) {
        return monthlyMode ? column == 3 : column == 4;
    }

    private static CellStyle baseStyle(XSSFWorkbook workbook, HorizontalAlignment horizontalAlignment) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(horizontalAlignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        return style;
    }
}
