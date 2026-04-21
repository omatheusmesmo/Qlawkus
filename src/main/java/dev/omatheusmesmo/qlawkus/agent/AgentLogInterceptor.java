package dev.omatheusmesmo.qlawkus.agent;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.interceptor.InterceptorBinding;

@Inherited
@InterceptorBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
@interface Logged {
}

@Logged
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
class AgentLogInterceptor {

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception {
        String method = context.getMethod().getName();
        Object[] params = context.getParameters();

        Log.infof("AgentService invocation: %s with %d parameters", method, params.length);

        long start = System.currentTimeMillis();
        try {
            Object result = context.proceed();
            long elapsed = System.currentTimeMillis() - start;
            Log.infof("AgentService completed: %s in %dms", method, elapsed);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Log.errorf("AgentService failed: %s in %dms — %s", method, elapsed, e.getMessage());
            throw e;
        }
    }
}
