package com.datapipeline.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PipelineProfiler implements AutoCloseable {

    public enum EventType {
        PRODUCER,
        CONSUMER,
        MINIO_RAW,
        MINIO_AGG
    }

    private final MeterRegistry registry;

    private Instant startTime;
    
    // Producer metrics
    private Instant producerStartTime;    
    private Counter producerEvents;

    
    // Consumer metrics
    private Instant consumerStartTime;
    private Counter consumerEvents;

    // MinIO metrics
    private Instant minioRawStartTime;
    private Counter minioRawPuts;
    private Counter minioRawPutEvents;
    private Counter minioRawBytes;
    private Timer minioRawLatency;
    private Timer minioRawSize;

    private Instant minioAggPutsStartTime;
    private Counter minioAggPuts;
    private Counter minioAggPutEvents;
    private Counter minioAggBytes;
    private Timer minioAggLatency;
    private Timer minioAggSize;
    
    public PipelineProfiler(EventType... eventTypes) {
        this.registry = new SimpleMeterRegistry();
        this.startTime = Instant.now();
        
        // Always initialize all counters
        this.producerEvents = registry.counter("producer.events");

        this.consumerEvents = registry.counter("consumer.events");

        this.minioRawPuts = registry.counter("minio.raw.puts");
        this.minioRawPutEvents = registry.counter("minio.raw.put.events");
        this.minioRawBytes = registry.counter("minio.raw.bytes");
        this.minioRawLatency = Timer.builder("minio.raw.latency")
            .description("MinIO raw PUT latency")
            .publishPercentileHistogram()
            .publishPercentiles(0.25, 0.5, 0.75, 0.9, 0.95)
            .register(registry);
        this.minioRawSize = Timer.builder("minio.raw.size")
            .description("MinIO raw PUT size")
            .publishPercentileHistogram()
            .publishPercentiles(0.25, 0.5, 0.75, 0.9, 0.95)
            .register(registry);            

        this.minioAggPuts = registry.counter("minio.agg.puts"); 
        this.minioAggPutEvents = registry.counter("minio.agg.put.events");
        this.minioAggBytes = registry.counter("minio.agg.bytes");                    
        this.minioAggLatency = Timer.builder("minio.agg.latency")
            .description("MinIO aggregated PUT latency")
            .publishPercentileHistogram()
            .publishPercentiles(0.25, 0.5, 0.75, 0.9, 0.95)
            .register(registry);
            
        this.minioAggSize = Timer.builder("minio.agg.size")
            .description("MinIO aggregated PUT size")
            .publishPercentileHistogram()
            .publishPercentiles(0.25, 0.5, 0.75, 0.9, 0.95)
            .register(registry);
        
        // Initialize start times as null
        this.producerStartTime = null;
        this.consumerStartTime = null;
        this.minioRawStartTime = null;
        this.minioAggPutsStartTime = null;
        
        // Initialize start times based on provided event types
        if (eventTypes != null) {
            for (EventType type : eventTypes) {
                switch (type) {
                    case PRODUCER:
                        this.producerStartTime = Instant.now();
                        break;
                    case CONSUMER:
                        this.consumerStartTime = Instant.now();
                        break;
                    case MINIO_RAW:
                        this.minioRawStartTime = Instant.now();
                        break;
                    case MINIO_AGG:
                        this.minioAggPutsStartTime = Instant.now();
                        break;
                }
            }
        }
        
        log.info("PipelineProfiler Instantiated with event types: {}", eventTypes);
    }
    
    // Producer metrics
    public void recordProducerEvent() {
        producerEvents.increment();
        log.debug("Producer event recorded. Total: {}", producerEvents.count());
    }
    
    public void recordProducerEvents(int count) {
        producerEvents.increment(count);
        log.debug("Producer events recorded. Count: {}, Total: {}", count, producerEvents.count());
    }
    
    // Consumer metrics
    public void recordConsumerEvent() {
        consumerEvents.increment();
        log.debug("Consumer event recorded. Total: {}", consumerEvents.count());
    }
    
    public void recordConsumerEvents(int count) {
        consumerEvents.increment(count);
        log.debug("Consumer events recorded. Count: {}, Total: {}", count, consumerEvents.count());
    }
    
    // MinIO metrics
    public void recordMinioRawPut(long bytes) {
        minioRawPuts.increment();
        minioRawBytes.increment(bytes);
        minioRawSize.record(Duration.ofNanos(bytes));
        log.debug("MinIO raw PUT recorded. Bytes: {}, Total PUTs: {}", bytes, minioRawPuts.count());
    }

    public void recordMinioRawPutEvents(int count) {
        minioRawPutEvents.increment(count);
        log.debug("MinIO raw PUT events recorded. Total Events PUT: {}", minioRawPutEvents.count());
    }
    
    public void recordMinioRawPutLatency(Duration duration) {
        minioRawLatency.record(duration);
    }
    
    public void recordMinioAggPut(long bytes) {
        minioAggPuts.increment();
        minioAggBytes.increment(bytes);
        minioAggSize.record(Duration.ofNanos(bytes));
        log.debug("MinIO agg PUT recorded. Bytes: {}, Total PUTs: {}", bytes, minioAggPuts.count());
    }

    public void recordMinioAggPutEvents(int count) {
        minioAggPutEvents.increment(count);
        log.debug("MinIO agg PUT events recorded. Total Events PUT: {}", minioAggPutEvents.count());
    }
    
    public void recordMinioAggPutLatency(Duration duration) {
        minioAggLatency.record(duration);
    }
    
    @Override
    public void close() {
        log.info("PipelineProfiler.close() called");
        
        Duration runtime = Duration.between(startTime, Instant.now());
        
        log.info("=== Pipeline Performance Profile ===");
        log.info("Total Runtime: {}", formatDuration(runtime));
        log.info("");
        
        if (producerStartTime != null) {
            log.info("Producer Performance:");
            log.info("- Total Events Written: {}", formatNumber(producerEvents.count()));
            Duration runtimeProducerEvents = Duration.between(producerStartTime, Instant.now());
            double runtimeProducerEventsSeconds = runtimeProducerEvents.toMillis() / 1000.0;        
            log.info("- Events/Second: {}/s", formatNumber(producerEvents.count() / runtimeProducerEventsSeconds));
            log.info("");
        }
        
        if (consumerStartTime != null) {
            log.info("Consumer Performance:");
            log.info("- Total Events Read: {}", formatNumber(consumerEvents.count()));
            Duration runtimeConsumerEvents = Duration.between(consumerStartTime, Instant.now());
            double runtimeConsumerEventsSeconds = runtimeConsumerEvents.toMillis() / 1000.0;         
            log.info("- Events/Second: {}/s", formatNumber(consumerEvents.count() / runtimeConsumerEventsSeconds));
            log.info("");
        }
        
        if (minioRawStartTime != null) {
            log.info("MinIO Raw Write Performance:");
            log.info("- Total PUTs: {}", formatNumber(minioRawPuts.count()));
            Duration runtimeMinioRaw = Duration.between(minioRawStartTime, Instant.now());
            double runtimeMinioRawSeconds = runtimeMinioRaw.toMillis() / 1000.0;         
            log.info("- PUTs/Second: {}/s", formatNumber(minioRawPuts.count() / runtimeMinioRawSeconds));
            log.info("- Total Bytes: {}", formatBytes(minioRawBytes.count()));
            log.info("- Bytes/Second: {}/s", formatBytes(minioRawBytes.count() / runtimeMinioRawSeconds));
            log.info("- Total Events PUT: {}", formatNumber(minioRawPutEvents.count()));
            // Log percentile metrics for raw operations
            log.info("- Raw PUT Latency (ms):");
            log.info("  - Max: {}", formatNumber(minioRawLatency.max(TimeUnit.MILLISECONDS)));
            log.info("  - Mean: {}", formatNumber(minioRawLatency.mean(TimeUnit.MILLISECONDS)));
            for (ValueAtPercentile p : minioRawLatency.takeSnapshot().percentileValues()) {
                log.info("  - p{}: {}", (int)(p.percentile() * 100), formatNumber(p.value(TimeUnit.MILLISECONDS)));
            }
            
            log.info("- Raw PUT Size (bytes):");
            log.info("  - Max: {}", formatBytes(minioRawSize.max(TimeUnit.NANOSECONDS)));
            log.info("  - Mean: {}", formatBytes(minioRawSize.mean(TimeUnit.NANOSECONDS)));
            for (ValueAtPercentile p : minioRawSize.takeSnapshot().percentileValues()) {
                log.info("  - p{}: {}", (int)(p.percentile() * 100), formatBytes(p.value(TimeUnit.NANOSECONDS)));
            }
            log.info("");
        }
        
        if (minioAggPutsStartTime != null) {
            log.info("MinIO Aggregated Write Performance:");
            log.info("- Total PUTs: {}", formatNumber(minioAggPuts.count()));
            Duration runtimeMinioAgg = Duration.between(minioAggPutsStartTime, Instant.now());
            double runtimeMinioAggSeconds = runtimeMinioAgg.toMillis() / 1000.0;                 
            log.info("- PUTs/Second: {}/s", formatNumber(minioAggPuts.count() / runtimeMinioAggSeconds));
            log.info("- Total Bytes: {}", formatBytes(minioAggBytes.count()));
            log.info("- Bytes/Second: {}/s", formatBytes(minioAggBytes.count() / runtimeMinioAggSeconds));
            log.info("- Total Events PUT: {}", formatNumber(minioAggPutEvents.count()));
            // Log percentile metrics for aggregated operations
            log.info("- Agg PUT Latency (ms):");
            log.info("  - Max: {}", formatNumber(minioAggLatency.max(TimeUnit.MILLISECONDS)));
            log.info("  - Mean: {}", formatNumber(minioAggLatency.mean(TimeUnit.MILLISECONDS)));
            for (ValueAtPercentile p : minioAggLatency.takeSnapshot().percentileValues()) {
                log.info("  - p{}: {}", (int)(p.percentile() * 100), formatNumber(p.value(TimeUnit.MILLISECONDS)));
            }
            
            log.info("- Agg PUT Size (bytes):");
            log.info("  - Max: {}", formatBytes(minioAggSize.max(TimeUnit.NANOSECONDS)));
            log.info("  - Mean: {}", formatBytes(minioAggSize.mean(TimeUnit.NANOSECONDS)));
            for (ValueAtPercentile p : minioAggSize.takeSnapshot().percentileValues()) {
                log.info("  - p{}: {}", (int)(p.percentile() * 100), formatBytes(p.value(TimeUnit.NANOSECONDS)));
            }
        }
    }
    
    private String formatNumber(double number) {
        return String.format("%,.2f", number);
    }
    
    private String formatBytes(double bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%,.2f %s", size, units[unitIndex]);
    }
    
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        long millis = duration.toMillisPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds %dms", hours, minutes, seconds, millis);
        } else if (minutes > 0) {
            return String.format("%dm %ds %dms", minutes, seconds, millis);
        } else if (seconds > 0) {
            return String.format("%ds %dms", seconds, millis);
        } else {
            return String.format("%dms", millis);
        }
    }
} 