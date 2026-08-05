package dev.omatheusmesmo.qlawkus.tools.brag.devui;

import dev.omatheusmesmo.qlawkus.tools.brag.BragEntry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs the Brag card, listing what the agent has recorded as achievements. Reads run inside an
 * explicit transaction because the entries are Panache entities and the Dev UI's JSON-RPC calls
 * arrive outside any request scope that would otherwise start one.
 */
public class BragDevUIJsonRPCService {

    /** Active entries, oldest first - the same ordering the export endpoint uses. */
    public List<Map<String, Object>> getEntries() {
        return QuarkusTransaction.requiringNew().call(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (BragEntry entry : BragEntry.listAllActiveByDateAsc()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", String.valueOf(entry.date));
                row.put("achievement", orEmpty(entry.achievement));
                row.put("impact", orEmpty(entry.impact));
                row.put("repo", orEmpty(entry.repo));
                entries.add(row);
            }
            return entries;
        });
    }

    private static String orEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
