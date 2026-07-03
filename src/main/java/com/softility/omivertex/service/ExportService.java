package com.softility.omivertex.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.softility.omivertex.web.dto.AssociateResponse;
import com.softility.omivertex.web.error.BadRequestException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

/** Renders the associate roster (with derived staffing fields) as xlsx, csv, pdf or docx. */
@Service
public class ExportService {

    public record ExportFile(String filename, String contentType, byte[] bytes) {
    }

    private static final String[] HEADERS = {
            "Associate Name", "Email", "Company", "Location", "Onshore/Offshore",
            "Designation", "Customer", "Project", "Billable", "Status"
    };

    private final AssociateService associateService;

    public ExportService(AssociateService associateService) {
        this.associateService = associateService;
    }

    public ExportFile export(String format) {
        List<AssociateResponse> roster = associateService.list(null, null, null, null, null, null);
        return switch (format == null ? "" : format.toLowerCase()) {
            case "xlsx" -> new ExportFile("associates.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx(roster));
            case "csv" -> new ExportFile("associates.csv", "text/csv", csv(roster));
            case "pdf" -> new ExportFile("associates.pdf", "application/pdf", pdf(roster));
            case "docx" -> new ExportFile("associates.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx(roster));
            default -> throw new BadRequestException("Unsupported export format; use xlsx, csv, pdf or docx");
        };
    }

    private static String[] rowOf(AssociateResponse a) {
        return new String[]{
                a.name(), a.email(), a.company(), orDash(a.location()),
                a.workMode().name(), orDash(a.designation()),
                a.currentClient() == null ? "—" : a.currentClient(),
                a.currentProject() == null ? "—" : a.currentProject(),
                a.currentProjectId() == null ? "BENCH" : (a.billable() ? "B" : "NB"),
                a.status().name()
        };
    }

    private static String orDash(String v) {
        return v == null || v.isBlank() ? "—" : v;
    }

    private byte[] xlsx(List<AssociateResponse> roster) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Associates");
            CellStyle headStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            headStyle.setFont(bold);
            headStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row head = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = head.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headStyle);
            }
            int r = 1;
            for (AssociateResponse a : roster) {
                Row row = sheet.createRow(r++);
                String[] values = rowOf(a);
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c]);
                }
            }
            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Excel export failed", e);
        }
    }

    private byte[] csv(List<AssociateResponse> roster) {
        StringBuilder sb = new StringBuilder(String.join(",", HEADERS)).append('\n');
        for (AssociateResponse a : roster) {
            String[] values = rowOf(a);
            for (int c = 0; c < values.length; c++) {
                if (c > 0) sb.append(',');
                String v = values[c];
                sb.append(v.contains(",") || v.contains("\"") ? '"' + v.replace("\"", "\"\"") + '"' : v);
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] pdf(List<AssociateResponse> roster) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 24);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("OmiVertex — Associate Roster",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(15, 23, 42)));
            doc.add(title);
            Paragraph sub = new Paragraph("Softility · exported " + LocalDate.now(),
                    FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 116, 139)));
            sub.setSpacingAfter(12);
            doc.add(sub);

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.6f, 2.2f, 1.1f, 1.3f, 1.3f, 1.5f, 1.3f, 1.7f, 0.9f, 1.0f});
            var headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            for (String h : HEADERS) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(new Color(37, 99, 235));
                cell.setPadding(5);
                cell.setBorderColor(new Color(226, 232, 240));
                table.addCell(cell);
            }
            var bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(15, 23, 42));
            boolean stripe = false;
            for (AssociateResponse a : roster) {
                for (String v : rowOf(a)) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, bodyFont));
                    cell.setPadding(4.5f);
                    cell.setBorderColor(new Color(226, 232, 240));
                    if (stripe) cell.setBackgroundColor(new Color(248, 250, 252));
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    table.addCell(cell);
                }
                stripe = !stripe;
            }
            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PDF export failed", e);
        }
    }

    private byte[] docx(List<AssociateResponse> roster) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun run = title.createRun();
            run.setText("OmiVertex — Associate Roster");
            run.setBold(true);
            run.setFontSize(16);

            XWPFParagraph sub = doc.createParagraph();
            XWPFRun subRun = sub.createRun();
            subRun.setText("Softility · exported " + LocalDate.now());
            subRun.setColor("64748B");
            subRun.setFontSize(9);

            XWPFTable table = doc.createTable(roster.size() + 1, HEADERS.length);
            XWPFTableRow head = table.getRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                XWPFTableCell cell = head.getCell(c);
                cell.setColor("2563EB");
                XWPFParagraph p = cell.getParagraphs().get(0);
                XWPFRun r = p.createRun();
                r.setText(HEADERS[c]);
                r.setBold(true);
                r.setColor("FFFFFF");
                r.setFontSize(8);
            }
            for (int i = 0; i < roster.size(); i++) {
                String[] values = rowOf(roster.get(i));
                XWPFTableRow row = table.getRow(i + 1);
                for (int c = 0; c < values.length; c++) {
                    XWPFParagraph p = row.getCell(c).getParagraphs().get(0);
                    XWPFRun r = p.createRun();
                    r.setText(values[c]);
                    r.setFontSize(8);
                }
            }
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Word export failed", e);
        }
    }
}
