package com.example.todoapp2;

import java.util.Arrays;
import java.util.List;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class LoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(* *(..)) && @within(org.springframework.stereotype.Service)")
    public void serviceMethod() {
    }

    @Before("serviceMethod()")
    public void logBefore(JoinPoint call) {
        if (log.isInfoEnabled()) {
            Class<?>[] types = ((MethodSignature) call.getSignature()).getParameterTypes();
            var arguments = Arrays.stream(types).map(type -> type.getSimpleName() + "=[redacted]").toList();
            log.info("Service start method={} arguments={}", call.getSignature().toShortString(), arguments);
        }
    }

    @AfterReturning(pointcut = "serviceMethod()", returning = "result")
    public void logReturn(JoinPoint call, Object result) {
        if (log.isInfoEnabled()) {
            log.info("Service success method={} result={}", call.getSignature().toShortString(),
                    resultSummary((MethodSignature) call.getSignature(), result));
        }
    }

    @AfterThrowing(pointcut = "serviceMethod()", throwing = "failure")
    public void logFailure(JoinPoint call, Throwable failure) {
        String message = switch (failure) {
            case TodoNotFoundException ignored -> "ToDoが見つかりません";
            case AccessDeniedException ignored -> "この操作を実行する権限がありません";
            case IllegalArgumentException ignored -> "引数の検証に失敗しました";
            default -> "サービス処理に失敗しました";
        };
        log.warn("Service failure method={} exception={} message={}", call.getSignature().toShortString(),
                failure.getClass().getSimpleName(), message);
    }

    private String resultSummary(MethodSignature method, Object result) {
        if (method.getReturnType() == void.class) {
            return "void";
        }
        if (result == null) {
            return "null";
        }
        if (result instanceof List<?> list) {
            return "List(size=" + list.size() + ")";
        }
        return method.getReturnType().getSimpleName() + "=[redacted]";
    }
}
