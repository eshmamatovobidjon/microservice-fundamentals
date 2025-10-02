package com.learn.resource_processor.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("within(com.learn.resourceprocessor..*)")
    public void applicationPackagePointcut() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void springControllerPointcut() {
    }

    @Pointcut("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public void kafkaListenerPointcut() {
    }

    @Around("applicationPackagePointcut() && springControllerPointcut()")
    public Object logAroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("methodType", "REST_CONTROLLER");

        long startTime = System.currentTimeMillis();

        try {
            log.info("REST Request: {}.{}() with arguments: {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    Arrays.toString(joinPoint.getArgs()));

            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("REST Response: {}.{}() executed in {}ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime);

            return result;
        } catch (Exception ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("REST Exception: {}.{}() failed after {}ms with error: {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime,
                    ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove("correlationId");
            MDC.remove("methodType");
        }
    }

    @Around("applicationPackagePointcut() && kafkaListenerPointcut()")
    public Object logAroundKafkaListener(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("methodType", "KAFKA_LISTENER");

        long startTime = System.currentTimeMillis();

        try {
            log.info("Kafka Message Received: {}.{}() with payload: {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    Arrays.toString(joinPoint.getArgs()));

            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Kafka Message Processed: {}.{}() completed in {}ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime);

            return result;
        } catch (Exception ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Kafka Processing Error: {}.{}() failed after {}ms with error: {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime,
                    ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove("correlationId");
            MDC.remove("methodType");
        }
    }

    @Around("execution(* com.learn.resourceprocessor.service..*(..))")
    public Object logAroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }
        MDC.put("methodType", "SERVICE");

        long startTime = System.currentTimeMillis();

        try {
            log.debug("Service Method: {}.{}() started",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName());

            Object result = joinPoint.proceed();

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Service Method: {}.{}() completed in {}ms",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime);

            return result;
        } catch (Exception ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Service Exception: {}.{}() failed after {}ms with error: {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime,
                    ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove("methodType");
        }
    }

    @AfterThrowing(pointcut = "applicationPackagePointcut()", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        log.error("Exception in {}.{}() with cause = {}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                ex.getCause() != null ? ex.getCause() : "NULL", ex);
    }
}