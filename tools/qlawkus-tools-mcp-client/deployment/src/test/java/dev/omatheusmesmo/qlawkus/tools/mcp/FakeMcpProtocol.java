package dev.omatheusmesmo.qlawkus.tools.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;

/** Small test server protocol; the code under test remains the released upstream MCP transport. */
final class FakeMcpProtocol {
    static final ObjectMapper JSON = JsonMapper.builder().build();
    static final String MODERN = "2026-07-28";
    static final String LEGACY = "2025-11-25";
    static final String STDERR_SENTINEL = "MCP_FIXTURE_STDERR_DO_NOT_LOG";
    volatile int toolCount = 4;
    volatile String output;

    ObjectNode reply(JsonNode request, Map<String, String> environment) {
        if (!request.has("id")) {
            return null;
        }
        ObjectNode response = JSON.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", request.get("id"));
        ObjectNode result = response.putObject("result");
        switch (request.path("method").asString()) {
            case "server/discover" -> {
                result.put("resultType", "complete");
                result.putArray("supportedVersions").add(MODERN);
                result.putObject("capabilities").putObject("tools");
                result.putObject("_meta").putObject("io.modelcontextprotocol/serverInfo")
                        .put("name", "qlawkus-fixture").put("version", "1.0");
            }
            case "initialize" -> {
                result.put("protocolVersion", LEGACY);
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", "qlawkus-fixture").put("version", "1.0");
            }
            case "tools/list" -> {
                var tools = result.putArray("tools");
                for (int i = 0; i < toolCount; i++) {
                    String name = switch (i) {
                        case 0 -> "echo";
                        case 1 -> "slow";
                        case 2 -> "environment";
                        case 3 -> "pid";
                        default -> "extra" + i;
                    };
                    var tool = tools.addObject().put("name", name).put("description", "Fixture " + name);
                    var schema = tool.putObject("inputSchema").put("type", "object");
                    schema.putObject("properties").putObject("value").put("type", "string");
                }
            }
            case "tools/call" -> {
                String name = request.path("params").path("name").asString();
                String text = switch (name) {
                    case "environment" -> environmentCheck(environment);
                    case "pid" -> Long.toString(ProcessHandle.current().pid());
                    default -> output == null ? "echo:" + request.path("params").path("arguments").path("value").asString()
                            : output;
                };
                result.putArray("content").addObject().put("type", "text").put("text", text);
            }
            case "ping" -> {
            }
            default -> {
                response.remove("result");
                response.putObject("error").put("code", -32601).put("message", "Unknown fixture method");
            }
        }
        return response;
    }

    static ObjectNode subscriptionAck(JsonNode request) {
        ObjectNode ack = JSON.createObjectNode().put("jsonrpc", "2.0")
                .put("method", "notifications/subscriptions/acknowledged");
        ObjectNode params = ack.putObject("params");
        params.putObject("_meta").set("io.modelcontextprotocol/subscriptionId", request.get("id"));
        params.putObject("notifications");
        return ack;
    }

    private static String environmentCheck(Map<String, String> environment) {
        Set<String> fixedAllowed = Set.of("PATH", "HOME", "USER", "LANG", "TZ", "TMPDIR", "MCP_FIXTURE_KEY");
        boolean filtered = environment.entrySet().stream().allMatch(entry -> fixedAllowed.contains(entry.getKey())
                || entry.getKey().startsWith("LC_") || entry.getValue().isEmpty());
        return "filtered=" + filtered + ";explicit=" + "fixture-process-key".equals(environment.get("MCP_FIXTURE_KEY"));
    }
}
