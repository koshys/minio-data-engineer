package com.datapipeline.producer;

import com.datapipeline.common.AppConfig;
import com.datapipeline.common.Event;
import com.datapipeline.common.PipelineProfiler;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Produces events to a Kafka topic.
 */
@Slf4j
public class KafkaEventProducer implements AutoCloseable {
    private final AppConfig config = AppConfig.getInstance();
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final PipelineProfiler profiler;
    private final String topic;
    
    public KafkaEventProducer() {
        this.topic = config.getKafka().getTopic();
        this.producer = new KafkaProducer<>(config.getKafka().getProducerProps());
        this.objectMapper = new ObjectMapper();
        this.profiler = new PipelineProfiler(PipelineProfiler.EventType.PRODUCER);
        createTopicIfNotExists();
    }
    
    /**
     * Creates the Kafka topic if it doesn't exist.
     */
    private void createTopicIfNotExists() {
        Properties props = new Properties();
        props.put("bootstrap.servers", config.getKafka().getBootstrapServers());
        
        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic newTopic = new NewTopic(
                topic,
                config.getKafka().getPartitions(),
                config.getKafka().getReplicationFactor()
            );
            
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();  
            log.info("Topic {} created successfully", topic);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                log.info("Topic {} already exists", topic);
            } else {
                log.error("Error creating topic {}", topic, e);
                throw new RuntimeException("Error creating Kafka topic", e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while creating topic {}", topic, e);
            throw new RuntimeException("Interrupted while creating Kafka topic", e);
        }
    }
    
    /**
     * Sends an event to the Kafka topic.
     */
    public void sendEvent(Event event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                config.getKafka().getTopic(),
                event.getEventGuid(),
                json
            );
            
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Error sending event to Kafka", exception);
                } else {
                    profiler.recordProducerEvent();
                }
            });
        } catch (Exception e) {
            log.error("Error serializing event", e);
        }
    }
    
    /**
     * Flushes and closes the producer.
     */
    @Override
    public void close() {
        if (producer != null) {
            producer.flush();
            producer.close();
        }
        profiler.close();
    }
}