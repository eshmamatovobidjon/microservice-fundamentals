package com.learn.resource_processor.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ResourceProducer {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final KafkaTemplate<String, Long> kafkaTemplate;
    private final String resourceCreatedTopic;

    public ResourceProducer(KafkaTemplate<String, Long> kafkaTemplate,
                            @Value("${kafka.topic.resource-processed}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.resourceCreatedTopic = topic;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void sendId(Long id) {
        try {
            String traceId = MDC.get(TRACE_ID_MDC_KEY);

            ProducerRecord<String, Long> record = new ProducerRecord<>(resourceCreatedTopic, id);

            if (traceId != null) {
                List<Header> headers = new ArrayList<>();
                headers.add(new RecordHeader(TRACE_ID_HEADER,
                        traceId.getBytes(StandardCharsets.UTF_8)));
                headers.forEach(record.headers()::add);
                log.info("Sending message with traceId: {}", traceId);
            }

            var result = kafkaTemplate.send(record).get();

            log.info("Sent ID={} to topic={}, partition={}, offset={}",
                    id,
                    resourceCreatedTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            log.error("Failed to send ID={} to topic={}. Reason: {}",
                    id, resourceCreatedTopic, e.getMessage(), e);
            throw new RuntimeException("Failed to send message to Kafka", e);
        }
    }
}
