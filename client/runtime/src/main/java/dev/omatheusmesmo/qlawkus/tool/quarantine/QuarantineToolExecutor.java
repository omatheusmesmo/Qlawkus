package dev.omatheusmesmo.qlawkus.tool.quarantine;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reports each call's outcome to the {@link ToolQuarantine}. A call that throws or returns an error
 * result is a failure; a call the model got wrong (an illegal argument, arguments that are not valid
 * JSON, a validation error, anywhere in the cause chain) is not, because the tool is fine and the next
 * call can succeed. Both interface methods are overridden for the same reason as in the metering
 * decorator: {@code DefaultToolExecutor} implements each separately.
 */
public class QuarantineToolExecutor implements ToolExecutor {

    private final String toolName;
    private final ToolExecutor delegate;
    private final ToolQuarantine quarantine;

    public QuarantineToolExecutor(String toolName, ToolExecutor delegate, ToolQuarantine quarantine) {
        this.toolName = toolName;
        this.delegate = delegate;
        this.quarantine = quarantine;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        return observed(() -> delegate.execute(request, memoryId), result -> false);
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        return observed(() -> delegate.executeWithContext(request, context), ToolExecutionResult::isError);
    }

    private <T> T observed(Supplier<T> call, java.util.function.Predicate<T> isError) {
        T result;
        try {
            result = call.get();
        } catch (RuntimeException e) {
            if (!misuse(e)) {
                quarantine.recordFailure(toolName);
            }
            throw e;
        }
        if (result != null && isError.test(result)) {
            quarantine.recordFailure(toolName);
        } else {
            quarantine.recordSuccess(toolName);
        }
        return result;
    }

    static boolean misuse(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException || cause instanceof ToolArgumentsException
                    || cause instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }
}
