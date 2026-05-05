package dev.omatheusmesmo.qlawkus.tools.google.sheets.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SheetValues(
        String range,
        String majorDimension,
        List<List<String>> values
) {
}
