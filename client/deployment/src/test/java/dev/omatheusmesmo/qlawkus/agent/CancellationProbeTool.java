package dev.omatheusmesmo.qlawkus.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.QlawTool;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@QlawTool
@ApplicationScoped
public class CancellationProbeTool {

    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    static volatile CountDownLatch gate;

    static volatile String heldStep = "first";

    @Tool("Test probe: records one named step of the cancellation probe.")
    public String probeStep(String step) throws InterruptedException {
        EVENTS.add("start:" + step);
        CountDownLatch held = gate;
        if (held != null && heldStep.equals(step)) {
            held.await(15, TimeUnit.SECONDS);
        }
        EVENTS.add("end:" + step);
        return "step " + step + " done";
    }
}
