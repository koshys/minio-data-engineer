package com.datapipeline.generator;

import com.datapipeline.common.AppConfig;
import com.datapipeline.common.Event;
import com.datapipeline.common.SchemaDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Generates synthetic data based on a schema definition.
 */
@Slf4j
public class DataGenerator {
    private final SchemaDefinition schema;
    private final Random random = new Random();
    private final AppConfig config = AppConfig.getInstance();
    private final AtomicInteger generatedCount = new AtomicInteger(0);
    
    public DataGenerator(SchemaDefinition schema) {
        this.schema = schema;
    }
    
    /**
     * Generates a single event based on the schema.
     */
    public Event generateEvent() {
        Map<String, Object> fields = new HashMap<>();
        
        // Generate values for each column in the schema
        for (SchemaDefinition.ColumnDefinition column : schema.getColumns()) {
            fields.put(column.getName(), column.generateValue(random));
        }
        
        return new Event(fields);
    }
    
    /**
     * Generates a batch of events.
     */
    public List<Event> generateBatch(int batchSize) {
        List<Event> events = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            events.add(generateEvent());
        }
        return events;
    }
    
    /**
     * Generates events in parallel and passes them to the consumer.
     */
    public void generateEvents(int totalEvents, Consumer<Event> eventConsumer) {
        int threadsCount = config.getGenerationThreads();
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch latch = new CountDownLatch(threadsCount);
        
        int eventsPerThread = totalEvents / threadsCount;
        int remainder = totalEvents % threadsCount;
        
        for (int i = 0; i < threadsCount; i++) {
            int threadEvents = eventsPerThread + (i == 0 ? remainder : 0);
            
            executor.submit(() -> {
                try {
                    for (int j = 0; j < threadEvents; j++) {
                        Event event = generateEvent();
                        eventConsumer.accept(event);
                        int count = generatedCount.incrementAndGet();
                        if (count % 1000 == 0) {
                            log.info("Generated {} events", count);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Event generation interrupted", e);
        } finally {
            executor.shutdown();
        }
        
        log.info("Completed generating {} events", generatedCount.get());
    }
} 