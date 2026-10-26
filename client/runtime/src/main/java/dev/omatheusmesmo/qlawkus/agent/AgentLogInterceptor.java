package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.util.Set;

/**
 * Opens an {@link AgentTurn} around the two {@link AgentService} entry points that take a conversation
 * id and answer in one go or as text: {@code chatSync} and {@code chat}. {@code chatEvents} is not here
 * because {@link AgentStream} already wraps it with the richer stream seam. A {@code chat} turn closes
 * when its text stream terminates, not when the method returns.
 */
@Logged
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
class AgentLogInterceptor {

    private static final Set<String> TURN_METHODS = Set.of("chatSync", "chat");
    private static final String STREAMED_METHOD = "chat";

    @Inject
    AgentDeliveryContext delivery;

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception {
        if (!TURN_METHODS.contains(context.getMethod().getName())) {
            return context.proceed();
        }
        String conversation = (String) context.getParameters()[0];
        boolean streamed = STREAMED_METHOD.equals(context.getMethod().getName());
        AgentTurn turn = AgentTurn.open(conversation, AgentTurn.channelOf(conversation, delivery), streamed);
        Object result;
        try (AgentTurn.Binding ignored = turn.bind()) {
            result = context.proceed();
        } catch (Exception e) {
            turn.close("error");
            Log.errorf(e, "Agent turn failed");
            throw e;
        }
        if (result instanceof Multi<?> text) {
            return text.onTermination().invoke((failure, cancelled) ->
                    turn.close(AgentTurn.outcomeOf(failure, cancelled)));
        }
        turn.close("ok");
        return result;
    }
}
