package com.datapipeline.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
    private final int flushThreshold;
    private final int pollTimeoutMS;
    
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
        this(226, 1000, eventTypes); //Default
    }
    
    public PipelineProfiler(int flushThreshold, int pollTimeoutMS, EventType... eventTypes) {
        this.registry = new SimpleMeterRegistry();
        this.startTime = Instant.now();
        
        // Store configuration parameters
        this.flushThreshold = flushThreshold;
        this.pollTimeoutMS = pollTimeoutMS;
        
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
        
        // Save metrics to MinIO
        try {
            save();
            log.info("Performance metrics saved to MinIO");
        } catch (Exception e) {
            log.error("Failed to save performance metrics to MinIO", e);
        }
    }
    
    /**
     * Saves the pipeline metrics as JSON to MinIO
     */
    public void save() {
        try {
            AppConfig config = AppConfig.getInstance();
            
            // Create S3 client for MinIO
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    config.getIceberg().getS3AccessKey(),
                    config.getIceberg().getS3SecretKey());
            
            S3Client s3Client = S3Client.builder()
                    .endpointOverride(URI.create(config.getIceberg().getS3Endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.US_EAST_1) // MinIO requires a region but doesn't use it
                    .httpClient(UrlConnectionHttpClient.builder().build())
                    .forcePathStyle(true) // Required for MinIO compatibility
                    .build();
            
            log.debug("S3 client initialized for saving pipeline metrics");
            
            // Create metrics map
            Map<String, Object> metricsMap = new HashMap<>();
            Map<String, Object> runtimeMap = new HashMap<>();
            Map<String, Object> producerMap = new HashMap<>();
            Map<String, Object> consumerMap = new HashMap<>();
            Map<String, Object> minioRawMap = new HashMap<>();
            Map<String, Object> minioAggMap = new HashMap<>();
            
            // Runtime metrics
            Duration runtime = Duration.between(startTime, Instant.now());
            runtimeMap.put("timestamp", startTime.toString());
            runtimeMap.put("duration_ms", runtime.toMillis());
            metricsMap.put("runtime", runtimeMap);
            log.info(config.getEventsToGenerate() + "::" + config.getKafka().getMaxPollRecords() + "::" + this.flushThreshold + "::" + this.pollTimeoutMS);
            metricsMap.put("config", config.getEventsToGenerate() + "::" + 
                                         config.getKafka().getMaxPollRecords() + "::" +
                                         this.pollTimeoutMS + "::" +
                                         this.flushThreshold
                           );
            
            // Producer metrics
            if (producerStartTime != null) {
                Duration runtimeProducer = Duration.between(producerStartTime, Instant.now());
                double runtimeProducerSeconds = runtimeProducer.toMillis() / 1000.0;
                producerMap.put("events", producerEvents.count());
                producerMap.put("events_per_second", producerEvents.count() / runtimeProducerSeconds);
                producerMap.put("duration_ms", runtimeProducer.toMillis());
                metricsMap.put("producer", producerMap);
            }
            
            // Consumer metrics
            if (consumerStartTime != null) {
                Duration runtimeConsumer = Duration.between(consumerStartTime, Instant.now());
                double runtimeConsumerSeconds = runtimeConsumer.toMillis() / 1000.0;
                consumerMap.put("events", consumerEvents.count());
                consumerMap.put("events_per_second", consumerEvents.count() / runtimeConsumerSeconds);
                consumerMap.put("duration_ms", runtimeConsumer.toMillis());
                metricsMap.put("consumer", consumerMap);
            }
            
            // MinIO Raw metrics
            if (minioRawStartTime != null) {
                Duration runtimeMinioRaw = Duration.between(minioRawStartTime, Instant.now());
                double runtimeMinioRawSeconds = runtimeMinioRaw.toMillis() / 1000.0;
                
                minioRawMap.put("puts", minioRawPuts.count());
                minioRawMap.put("puts_per_second", minioRawPuts.count() / runtimeMinioRawSeconds);
                minioRawMap.put("bytes", minioRawBytes.count());
                minioRawMap.put("bytes_per_second", minioRawBytes.count() / runtimeMinioRawSeconds);
                minioRawMap.put("events", minioRawPutEvents.count());
                minioRawMap.put("duration_ms", runtimeMinioRaw.toMillis());
                
                // Latency percentiles
                Map<String, Object> latencyMap = new HashMap<>();
                latencyMap.put("max_ms", minioRawLatency.max(TimeUnit.MILLISECONDS));
                latencyMap.put("mean_ms", minioRawLatency.mean(TimeUnit.MILLISECONDS));
                
                Map<String, Double> percentilesMap = new HashMap<>();
                for (ValueAtPercentile p : minioRawLatency.takeSnapshot().percentileValues()) {
                    percentilesMap.put("p" + (int)(p.percentile() * 100), p.value(TimeUnit.MILLISECONDS));
                }
                latencyMap.put("percentiles", percentilesMap);
                minioRawMap.put("latency", latencyMap);
                
                // Size percentiles
                Map<String, Object> sizeMap = new HashMap<>();
                sizeMap.put("max_bytes", minioRawSize.max(TimeUnit.NANOSECONDS));
                sizeMap.put("mean_bytes", minioRawSize.mean(TimeUnit.NANOSECONDS));
                
                Map<String, Double> sizePercentilesMap = new HashMap<>();
                for (ValueAtPercentile p : minioRawSize.takeSnapshot().percentileValues()) {
                    sizePercentilesMap.put("p" + (int)(p.percentile() * 100), p.value(TimeUnit.NANOSECONDS));
                }
                sizeMap.put("percentiles", sizePercentilesMap);
                minioRawMap.put("size", sizeMap);
                
                metricsMap.put("minio_raw", minioRawMap);
            }
            
            // MinIO Aggregated metrics
            if (minioAggPutsStartTime != null) {
                Duration runtimeMinioAgg = Duration.between(minioAggPutsStartTime, Instant.now());
                double runtimeMinioAggSeconds = runtimeMinioAgg.toMillis() / 1000.0;
                
                minioAggMap.put("puts", minioAggPuts.count());
                minioAggMap.put("puts_per_second", minioAggPuts.count() / runtimeMinioAggSeconds);
                minioAggMap.put("bytes", minioAggBytes.count());
                minioAggMap.put("bytes_per_second", minioAggBytes.count() / runtimeMinioAggSeconds);
                minioAggMap.put("events", minioAggPutEvents.count());
                minioAggMap.put("duration_ms", runtimeMinioAgg.toMillis());
                
                // Latency percentiles
                Map<String, Object> latencyMap = new HashMap<>();
                latencyMap.put("max_ms", minioAggLatency.max(TimeUnit.MILLISECONDS));
                latencyMap.put("mean_ms", minioAggLatency.mean(TimeUnit.MILLISECONDS));
                
                Map<String, Double> percentilesMap = new HashMap<>();
                for (ValueAtPercentile p : minioAggLatency.takeSnapshot().percentileValues()) {
                    percentilesMap.put("p" + (int)(p.percentile() * 100), p.value(TimeUnit.MILLISECONDS));
                }
                latencyMap.put("percentiles", percentilesMap);
                minioAggMap.put("latency", latencyMap);
                
                // Size percentiles
                Map<String, Object> sizeMap = new HashMap<>();
                sizeMap.put("max_bytes", minioAggSize.max(TimeUnit.NANOSECONDS));
                sizeMap.put("mean_bytes", minioAggSize.mean(TimeUnit.NANOSECONDS));
                
                Map<String, Double> sizePercentilesMap = new HashMap<>();
                for (ValueAtPercentile p : minioAggSize.takeSnapshot().percentileValues()) {
                    sizePercentilesMap.put("p" + (int)(p.percentile() * 100), p.value(TimeUnit.NANOSECONDS));
                }
                sizeMap.put("percentiles", sizePercentilesMap);
                minioAggMap.put("size", sizeMap);
                
                metricsMap.put("minio_agg", minioAggMap);
            }
            
            
            ObjectMapper mapper = new ObjectMapper();
            String jsonMetrics = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metricsMap);
            
            
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            String timestamp = now.format(formatter);
            String metricsFilename = "stats/pipeline_metrics_" + timestamp + ".json";
            
            
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(config.getIceberg().getS3Bucket())
                    .key(metricsFilename)
                    .contentType("application/json")
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonMetrics));
            log.info("Pipeline metrics saved to s3://{}/{}", config.getIceberg().getS3Bucket(), metricsFilename);
            
        } catch (Exception e) {
            log.error("Error saving metrics to MinIO", e);
            throw new RuntimeException("Failed to save metrics to MinIO", e);
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