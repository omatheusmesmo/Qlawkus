package dev.omatheusmesmo.qlawkus.tools.google.sheets.model;

import java.util.List;

public record UpdateValuesRequest(
        List<List<String>> values
) {
}
