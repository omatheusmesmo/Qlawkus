package dev.omatheusmesmo.qlawkus.tools.google.sheets.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateValuesResponse(
        int updatedCells,
        int updatedRows,
        int updatedColumns
) {
}
