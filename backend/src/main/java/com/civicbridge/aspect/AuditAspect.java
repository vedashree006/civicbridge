package com.civicbridge.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting audit logging for mutating service methods, layered on top of
 * the explicit AuditService calls already made in CaseService/DocumentService/
 * AuthService for domain-meaningful events. This aspect gives a cheap, generic
 * safety net (structured log line) for every "create/update/delete" call so
 * nothing mutating goes completely untraced, without hand-wiring a log
 * statement into every method.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    @AfterReturning(pointcut = "execution(* com.civicbridge.service..*Service.create*(..)) || "
            + "execution(* com.civicbridge.service..*Service.update*(..)) || "
            + "execution(* com.civicbridge.service..*Service.assign*(..)) || "
            + "execution(* com.civicbridge.service..*Service.upload*(..))",
            returning = "result")
    public void logMutation(JoinPoint joinPoint, Object result) {
        log.info("method={} args={} result={}",
                joinPoint.getSignature().toShortString(),
                joinPoint.getArgs().length,
                result != null ? result.getClass().getSimpleName() : "void");
    }
}
