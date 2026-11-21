package dev.omatheusmesmo.qlawkus.tools.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** A genuine child process fixture: newline-delimited MCP JSON-RPC over stdin/stdout. */
public final class FakeMcpStdioServer {
    private FakeMcpStdioServer() {
    }

    public static void main(String[] args) throws Exception {
        FakeMcpProtocol protocol = new FakeMcpProtocol();
        System.err.println(FakeMcpProtocol.STDERR_SENTINEL);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8)) {
            String line;
            while ((line = input.readLine()) != null) {
                var message = FakeMcpProtocol.JSON.readTree(line);
                if (message.path("method").asText().equals("subscriptions/listen")) {
                    output.println(FakeMcpProtocol.subscriptionAck(message));
                    continue;
                }
                var response = protocol.reply(message, System.getenv());
                if (response != null) {
                    output.println(response);
                }
            }
        }
    }
}
