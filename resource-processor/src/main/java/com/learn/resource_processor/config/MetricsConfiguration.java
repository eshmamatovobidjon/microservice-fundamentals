package com.learn.resource_processor.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class MetricsConfiguration {

    private final AtomicInteger activeProcessingTasks = new AtomicInteger(0);
    private final AtomicInteger totalProcessedResources = new AtomicInteger(0);
    private final AtomicInteger failedProcessingTasks = new AtomicInteger(0);

    @Bean
    public Counter resourceProcessedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("resource.processor.processed.total")
                .description("Total number of resources processed")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

    @Bean
    public Counter resourceProcessingFailedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("resource.processor.failed.total")
                .description("Total number of failed resource processing attempts")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

    @Bean
    public Timer resourceProcessingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("resource.processor.processing.time")
                .description("Time taken to process a resource")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

    @Bean
    public Counter kafkaMessagesSentCounter(MeterRegistry meterRegistry) {
        return Counter.builder("kafka.messages.sent.total")
                .description("Total number of Kafka messages sent")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

    @Bean
    public Counter kafkaMessagesReceivedCounter(MeterRegistry meterRegistry) {
        return Counter.builder("kafka.messages.received.total")
                .description("Total number of Kafka messages received")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

//    @Bean
//    public Gauge activeProcessingTasksGauge2(MeterRegistry meterRegistry) {
//        return Gauge.builder("resource.processor.active.tasks")
//                .description("Number of currently active processing tasks")
//                .tag("service", "resource-processor")
//                .register(meterRegistry, this, MetricsConfiguration::getActiveProcessingTasks);
//    }

    @Bean
    public Gauge activeProcessingTasksGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("resource.processor.active.tasks", activeProcessingTasks, AtomicInteger::get)
                .description("Number of currently active processing tasks")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }


    @Bean
    public Counter httpRequestsCounter(MeterRegistry meterRegistry) {
        return Counter.builder("http.requests.total")
                .description("Total number of HTTP requests")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

    @Bean
    public Timer httpRequestTimer(MeterRegistry meterRegistry) {
        return Timer.builder("http.request.duration")
                .description("HTTP request duration")
                .tag("service", "resource-processor")
                .register(meterRegistry);
    }

    // Getter methods for metrics
    public double getActiveProcessingTasks() {
        return activeProcessingTasks.get();
    }

    public void incrementActiveProcessingTasks() {
        activeProcessingTasks.incrementAndGet();
    }

    public void decrementActiveProcessingTasks() {
        activeProcessingTasks.decrementAndGet();
    }

    public void incrementTotalProcessedResources() {
        totalProcessedResources.incrementAndGet();
    }

    public void incrementFailedProcessingTasks() {
        failedProcessingTasks.incrementAndGet();
    }
}
