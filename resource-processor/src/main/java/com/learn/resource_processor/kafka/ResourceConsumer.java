package com.learn.resource_processor.kafka;

import com.learn.resource_processor.service.ResourceProcessorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class ResourceConsumer {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final ResourceProcessorService resourceProcessorService;

    public ResourceConsumer(ResourceProcessorService resourceProcessorService) {
        this.resourceProcessorService = resourceProcessorService;
    }

    @KafkaListener(topics = "resource-created", groupId = "resource-processor-group")
    public void consume(ConsumerRecord<String, Long> record) {
        try {
            String traceId = null;
            Header traceIdHeader = record.headers().lastHeader(TRACE_ID_HEADER);
            if (traceIdHeader != null) {
                traceId = new String(traceIdHeader.value(), StandardCharsets.UTF_8);
                MDC.put(TRACE_ID_MDC_KEY, traceId);
                log.info("Received message with traceId: {}", traceId);
            }

            Long resourceId = record.value();
            log.info("Received resourceId: {}", resourceId);
            resourceProcessorService.process(resourceId);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}