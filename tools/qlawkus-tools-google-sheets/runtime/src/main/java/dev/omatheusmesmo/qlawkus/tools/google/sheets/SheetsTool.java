package dev.omatheusmesmo.qlawkus.tools.google.sheets;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.SheetValues;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.UpdateValuesRequest;
import dev.omatheusmesmo.qlawkus.tools.google.sheets.model.UpdateValuesResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@ClawTool
@ApplicationScoped
public class SheetsTool {

    @Inject
    GoogleSheetsConfig config;

    @Inject
    @RestClient
    GoogleSheetsRestClient sheetsClient;

    @Tool("Read data from a Google Sheets range. Provide spreadsheet ID and A1 notation range (e.g. 'Sheet1!A1:D10').")
    public String readSheet(
            @P("Spreadsheet ID from the Google Sheets URL") String spreadsheetId,
            @P("Cell range in A1 notation, e.g. 'Sheet1!A1:D10'") String range) {

        try {
            SheetValues result = sheetsClient.getValues(spreadsheetId, range);

            if (result.values() == null || result.values().isEmpty()) {
                return "No data found in range " + range + ".";
            }

            StringBuilder output = new StringBuilder();
            output.append("Range: ").append(result.range()).append("\n");

            for (List<String> row : result.values()) {
                output.append(row.stream().map(v -> v != null ? v : "").collect(Collectors.joining(" | ")));
                output.append("\n");
            }

            return output.toString().trim();
        } catch (Exception e) {
            Log.errorf(e, "Failed to read sheet %s range %s", spreadsheetId, range);
            return "Error reading sheet: " + e.getMessage();
        }
    }

    @Tool("Write data to a Google Sheets range. Provide spreadsheet ID, A1 notation range, and values as rows.")
    public String writeSheet(
            @P("Spreadsheet ID from the Google Sheets URL") String spreadsheetId,
            @P("Target cell range in A1 notation, e.g. 'Sheet1!A1:D5'") String range,
            @P("Data rows as comma-separated values, one row per line. E.g. 'Name,Age\\nAlice,30'") String values) {

        try {
            List<List<String>> rows = parseValues(values);
            UpdateValuesRequest request = new UpdateValuesRequest(rows);

            UpdateValuesResponse response = sheetsClient.updateValues(
                    spreadsheetId, range, config.valueInputOption(), request);

            return String.format("Updated %d cells across %d rows, %d columns.",
                    response.updatedCells(), response.updatedRows(), response.updatedColumns());
        } catch (Exception e) {
            Log.errorf(e, "Failed to write sheet %s range %s", spreadsheetId, range);
            return "Error writing sheet: " + e.getMessage();
        }
    }

    @Tool("Update a single cell in Google Sheets. Provide spreadsheet ID, cell reference (e.g. 'Sheet1!B2'), and the new value.")
    public String updateCell(
            @P("Spreadsheet ID from the Google Sheets URL") String spreadsheetId,
            @P("Cell reference in A1 notation, e.g. 'Sheet1!B2'") String cell,
            @P("New value for the cell") String value) {

        try {
            UpdateValuesRequest request = new UpdateValuesRequest(
                    Collections.singletonList(Collections.singletonList(value)));

            UpdateValuesResponse response = sheetsClient.updateValues(
                    spreadsheetId, cell, config.valueInputOption(), request);

            return String.format("Cell %s updated. %d cell changed.", cell, response.updatedCells());
        } catch (Exception e) {
            Log.errorf(e, "Failed to update cell %s in sheet %s", cell, spreadsheetId);
            return "Error updating cell: " + e.getMessage();
        }
    }

    private List<List<String>> parseValues(String values) {
        String[] lines = values.split("\\n");
        List<List<String>> rows = new ArrayList<>();

        for (String line : lines) {
            String[] cells = line.split(",");
            List<String> row = new ArrayList<>();
            for (String cell : cells) {
                row.add(cell.trim());
            }
            rows.add(row);
        }

        return rows;
    }
}
