package com.softility.omivertex.service;

import com.softility.omivertex.web.error.BadRequestException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an uploaded spreadsheet into neutral rows — the file-format concern, kept
 * out of {@link ImportService} so the importer only deals in domain logic.
 * Every row is a map keyed by the upper-cased column header; sheet keys are
 * lower-cased sheet names.
 */
@Component
public class SpreadsheetParser {

    /** All sheets of an .xlsx workbook. */
    public Map<String, List<Map<String, String>>> parseWorkbook(MultipartFile file) {
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            DataFormatter fmt = new DataFormatter();
            Map<String, List<Map<String, String>>> sheets = new LinkedHashMap<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                Sheet sheet = wb.getSheetAt(i);
                Iterator<Row> it = sheet.iterator();
                if (!it.hasNext()) continue;
                List<String> headers = new ArrayList<>();
                for (var cell : it.next()) {
                    headers.add(fmt.formatCellValue(cell).trim().toUpperCase());
                }
                List<Map<String, String>> rows = new ArrayList<>();
                while (it.hasNext()) {
                    Row row = it.next();
                    Map<String, String> values = new HashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        values.put(headers.get(c), fmt.formatCellValue(row.getCell(c)).trim());
                    }
                    rows.add(values);
                }
                sheets.put(sheet.getSheetName().trim().toLowerCase(), rows);
            }
            if (sheets.isEmpty()) {
                sheets.put("sheet1", List.of());
            }
            return sheets;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Could not read Excel file: " + e.getMessage());
        }
    }

    /** Rows of a single .csv file. */
    public List<Map<String, String>> parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return List.of();
            String[] headers = splitCsv(headerLine);
            for (int i = 0; i < headers.length; i++) headers[i] = headers[i].trim().toUpperCase();
            List<Map<String, String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cells = splitCsv(line);
                Map<String, String> values = new HashMap<>();
                for (int c = 0; c < headers.length && c < cells.length; c++) {
                    values.put(headers[c], cells[c].trim());
                }
                rows.add(values);
            }
            return rows;
        } catch (Exception e) {
            throw new BadRequestException("Could not read CSV file: " + e.getMessage());
        }
    }

    /** Splits a CSV line honoring double-quoted fields. */
    private String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(String[]::new);
    }
}
