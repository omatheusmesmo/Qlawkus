package dev.omatheusmesmo.qlawkus.it.langfuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@QuarkusTestResource(ExportFixture.class)
class LangfuseExportIT {

    @TestHTTPResource("/test/turn")
    URI turn;

    @Test
    void exportsModelAndBothToolSpansWithoutContentInLangfuse() throws Exception {
        URI conversation = URI.create(turn + "?id=" + UUID.randomUUID());
        try (var http = HttpClient.newHttpClient()) {
            for (int i = 0; i < 2; i++) {
                var request = HttpRequest.newBuilder(conversation).timeout(Duration.ofSeconds(20)).build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                assertEquals("private-completion-fixture", response.body());
            }
        }

        List<Span> remote = readSpans(ExportFixture.LANGFUSE, "tool globFiles");
        List<Span> local = readSpans(ExportFixture.TEMPO, "langchain4j.tools.globFiles");
        assertEquals("Basic cGstZml4dHVyZTpzay1maXh0dXJl", ExportFixture.AUTH.poll());

        var fileTools = remote.stream().filter(span -> span.getName().equals("tool globFiles")).toList();
        String session = attribute(fileTools.getFirst(), "session.id");
        assertTrue(session.matches("[a-f0-9]{64}"));
        assertEquals(2, fileTools.stream().map(Span::getTraceId).distinct().count());
        for (Span fileTool : fileTools) {
            ByteString traceId = fileTool.getTraceId();
            var trace = remote.stream().filter(span -> span.getTraceId().equals(traceId)).toList();
            assertEquals(session, attribute(fileTool, "session.id"));
            assertEquals("owner", attribute(fileTool, "user.id"));
            assertEquals("interactive", attribute(fileTool, "qlawkus.workload"));
            var tags = fileTool.getAttributesList().stream().filter(value -> value.getKey().equals("langfuse.trace.tags"))
                    .findFirst().orElseThrow().getValue().getArrayValue().getValuesList();
            assertEquals(List.of("interactive"), tags.stream().map(value -> value.getStringValue()).toList());
            assertTrue(trace.stream().anyMatch(span -> span.getName().equals("tool respondWithVoice")));
            assertTrue(trace.stream().anyMatch(span -> attribute(span, "gen_ai.response.model").equals("fixture-model")));
            assertTrue(local.stream().anyMatch(span -> span.getSpanId().equals(fileTool.getSpanId())));
        }
        for (String content : List.of("private-prompt-fixture", "private-completion-fixture", "private-glob-fixture",
                "private-conversation-fixture", "File glob search is disabled")) {
            assertFalse(remote.toString().contains(content), "Langfuse export contains " + content);
        }
        assertTrue(local.toString().contains("private-glob-fixture"), "local trace lacks tool arguments");
        assertTrue(local.toString().contains("File glob search is disabled"), "local trace lacks tool output");
    }

    private static List<Span> readSpans(BlockingQueue<ExportTraceServiceRequest> sink, String tool) throws InterruptedException {
        List<Span> spans = new ArrayList<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (spans.stream().filter(span -> span.getName().equals(tool)).count() < 2) {
            var request = sink.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            assertNotNull(request, () -> "Missing " + tool + " among " + spans.stream().map(Span::getName).toList());
            for (var resource : request.getResourceSpansList()) {
                for (var scope : resource.getScopeSpansList()) {
                    spans.addAll(scope.getSpansList());
                }
            }
        }
        return spans;
    }

    private static String attribute(Span span, String key) {
        return span.getAttributesList().stream().filter(value -> value.getKey().equals(key))
                .findFirst().map(value -> value.getValue().getStringValue()).orElse("");
    }
}
