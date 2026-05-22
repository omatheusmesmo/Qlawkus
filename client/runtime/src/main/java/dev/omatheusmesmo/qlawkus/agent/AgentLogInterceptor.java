package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Logged
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
class AgentLogInterceptor {

  @AroundInvoke
  Object logInvocation(InvocationContext context) throws Exception {
    String method = context.getMethod().getName();
    Object[] params = context.getParameters();

    Log.infof("Thought ▸ %s invoked", method);

    long start = System.currentTimeMillis();
    try {
      Object result = context.proceed();
      long elapsed = System.currentTimeMillis() - start;
      Log.infof("Thought ▸ %s completed in %dms", method, elapsed);
      return result;
    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - start;
      Log.errorf("Thought ▸ %s failed in %dms — %s", method, elapsed, e.getMessage());
      throw e;
    }
  }
}
