package com.warehouse.assistant.core.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs every Spring AI {@link Tool} invocation fired by the LLM for the
 * Cezeri assistant (WMS & Store profiles).
 *
 * <p>Each call produces two log lines at INFO level:
 * <pre>
 *   [Tool#42] → WmsStockTools.searchStocks(warehouseId=3, sku="ABC-12", page=0)
 *   [Tool#42] ← WmsStockTools.searchStocks ✓ 127ms  result=List[5]
 * </pre>
 * An id is included so concurrent invocations are easy to correlate in the
 * log. Failures emit an ERROR with the exception message and the same id.
 *
 * <p>The pointcut matches any method annotated with {@code @Tool} located in
 * the assistant package, so new tools are picked up automatically without
 * needing to touch this class.
 */
@Aspect
@Component
public class ToolCallLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger("CezeriTool");
    private static final AtomicLong CALL_ID = new AtomicLong();
    private static final int MAX_ARG_LEN = 120;
    private static final int MAX_RESULT_LEN = 400;

    @Around("execution(* com.warehouse.assistant..*(..)) && @annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint pjp) throws Throwable {
        long id = CALL_ID.incrementAndGet();
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        String toolName = method.getAnnotation(Tool.class).name();
        if (toolName == null || toolName.isBlank()) toolName = methodName;

        String argSummary = summarizeArgs(sig.getParameterNames(), pjp.getArgs());
        log.info("[Tool#{}] → {}.{}({})   [tool={}]", id, className, methodName, argSummary, toolName);

        long t0 = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.info("[Tool#{}] ← {}.{} ✓ {}ms  result={}",
                    id, className, methodName, ms, summarizeResult(result));
            return result;
        } catch (Throwable ex) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            log.error("[Tool#{}] ← {}.{} ✗ {}ms  error: {}",
                    id, className, methodName, ms, ex.getMessage());
            throw ex;
        }
    }

    private String summarizeArgs(String[] names, Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            if (names != null && i < names.length) sb.append(names[i]).append('=');
            sb.append(safe(args[i]));
        }
        return sb.toString();
    }

    private String summarizeResult(Object result) {
        if (result == null) return "null";
        if (result instanceof Collection<?> c) return "List[" + c.size() + "]";
        if (result instanceof Map<?, ?> m) return "Map[" + m.size() + "]";
        if (result instanceof Object[] a) return "Array[" + a.length + "]";
        String s = String.valueOf(result);
        return truncate(s, MAX_RESULT_LEN);
    }

    private String safe(Object v) {
        if (v == null) return "null";
        if (v instanceof CharSequence) return "\"" + truncate(v.toString(), MAX_ARG_LEN) + "\"";
        if (v instanceof Collection<?> c) return "[" + c.size() + " items]";
        if (v instanceof Object[] a) return "[" + a.length + " items]";
        if (v instanceof Map<?, ?> m) return "{" + m.size() + " keys}";
        if (v.getClass().isArray()) return Arrays.deepToString(new Object[]{v});
        return truncate(String.valueOf(v), MAX_ARG_LEN);
    }

    private String truncate(String s, int max) {
        if (s == null) return "null";
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        if (oneLine.length() <= max) return oneLine;
        return oneLine.substring(0, max) + "…(" + (oneLine.length() - max) + " more chars)";
    }
}
