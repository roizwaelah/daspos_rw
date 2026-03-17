package com.daspos.feature.report;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import com.daspos.model.ReportItem;
import com.daspos.shared.util.CurrencyUtils;
import com.daspos.shared.util.OutputStreamCompat;

import org.apache.poi.ss.usermodel.Row;
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
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            android.graphics.Canvas canvas = page.getCanvas();
            Paint titlePaint = new Paint();
            titlePaint.setTextSize(18f);
            titlePaint.setFakeBoldText(true);
            Paint textPaint = new Paint();
            textPaint.setTextSize(11f);

            int y = 40;
            canvas.drawText(title, 32, y, titlePaint);
            y += 24;
            canvas.drawText("Total transaksi: " + count, 32, y, textPaint);
            y += 18;
            canvas.drawText("Total pendapatan: " + CurrencyUtils.formatRupiah(income), 32, y, textPaint);
            y += 28;
            canvas.drawText("ID", 32, y, textPaint);
            canvas.drawText("Waktu", 220, y, textPaint);
            canvas.drawText("Total", 430, y, textPaint);
            y += 8;
            canvas.drawLine(32, y, 560, y, textPaint);
            y += 18;

            for (ReportItem item : items) {
                if (y > 800) break;
                canvas.drawText(item.getId(), 32, y, textPaint);
                canvas.drawText(item.getTime(), 220, y, textPaint);
                canvas.drawText(CurrencyUtils.formatRupiah(item.getTotal()), 430, y, textPaint);
                y += 18;
            }

            document.finishPage(page);
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
            int rowIndex = 0;
            Row headerInfo = sheet.createRow(rowIndex++);
            headerInfo.createCell(0).setCellValue("Total transaksi");
            headerInfo.createCell(1).setCellValue(count);
            Row incomeRow = sheet.createRow(rowIndex++);
            incomeRow.createCell(0).setCellValue("Total pendapatan");
            incomeRow.createCell(1).setCellValue(income);
            rowIndex++;

            Row header = sheet.createRow(rowIndex++);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Waktu");
            header.createCell(2).setCellValue("Total");

            for (ReportItem item : items) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(item.getId());
                row.createCell(1).setCellValue(item.getTime());
                row.createCell(2).setCellValue(item.getTotal());
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

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
}
