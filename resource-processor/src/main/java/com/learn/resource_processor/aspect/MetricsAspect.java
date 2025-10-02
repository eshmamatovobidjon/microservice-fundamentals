package com.learn.resource_processor.aspect;

import com.learn.resource_processor.config.MetricsConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsAspect {

    private final Counter resourceProcessedCounter;
    private final Counter resourceProcessingFailedCounter;
    private final Timer resourceProcessingTimer;
    private final Counter kafkaMessagesSentCounter;
    private final Counter kafkaMessagesReceivedCounter;
    private final Counter httpRequestsCounter;
    private final Timer httpRequestTimer;
    private final MetricsConfiguration metricsConfiguration;

    @Pointcut("@annotation(org.springframework.web.bind.annotation.RequestMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public void httpEndpointPointcut() {
    }

    @Pointcut("@annotation(org.springframework.kafka.annotation.KafkaListener)")
    public void kafkaListenerPointcut() {
    }

    @Pointcut("execution(* com.learn.resourceprocessor.service.ResourceProcessingService.processResource(..))")
    public void resourceProcessingPointcut() {
    }

    @Around("httpEndpointPointcut()")
    public Object measureHttpRequests(ProceedingJoinPoint joinPoint) throws Throwable {
        return httpRequestTimer.recordCallable(() -> {
            try {
                httpRequestsCounter.increment();
                return joinPoint.proceed();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
    }

    @Around("kafkaListenerPointcut()")
    public Object measureKafkaMessages(ProceedingJoinPoint joinPoint) throws Throwable {
        kafkaMessagesReceivedCounter.increment();

        try {
            Object result = joinPoint.proceed();
            // Assuming successful processing means we'll send a message
            kafkaMessagesSentCounter.increment();
            return result;
        } catch (Exception ex) {
            log.error("Kafka message processing failed", ex);
            throw ex;
        }
    }

    @Around("resourceProcessingPointcut()")
    public Object measureResourceProcessing(ProceedingJoinPoint joinPoint) throws Throwable {
        metricsConfiguration.incrementActiveProcessingTasks();

        return resourceProcessingTimer.recordCallable(() -> {
            try {
                Object result = joinPoint.proceed();
                resourceProcessedCounter.increment();
                metricsConfiguration.incrementTotalProcessedResources();
                return result;
            } catch (Throwable throwable) {
                resourceProcessingFailedCounter.increment();
                metricsConfiguration.incrementFailedProcessingTasks();
                throw new RuntimeException(throwable);
            } finally {
                metricsConfiguration.decrementActiveProcessingTasks();
            }
        });
    }

    @Around("execution(* com.learn.resourceprocessor.service.KafkaProducerService.sendMessage(..))")
    public Object measureKafkaProducer(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            kafkaMessagesSentCounter.increment();
            return result;
        } catch (Exception ex) {
            log.error("Kafka message sending failed", ex);
            throw ex;
        }
    }
}