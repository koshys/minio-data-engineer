package com.datapipeline.consumer;

import com.datapipeline.common.AppConfig;
import com.datapipeline.common.Event;
import com.datapipeline.common.PipelineProfiler;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;

/**
 * Data class for storing aggregated event information
 */
@Data
class AggregatedEvent {
    private String key;  // The aggregation key (window:fieldName:fieldValue)
    private int count;
    private int distinctUserIdCount;  // Count of unique user IDs
    private double sumAmount;
    private double minAmount;
    private double maxAmount;
    private long firstTimestamp;
    private long lastTimestamp;
    private Set<String> distinctUserIds = new HashSet<>();  // Set to track unique user IDs

    // Ensure proper JSON serialization of the Set
    public Set<String> getDistinctUserIds() {
        return distinctUserIds;
    }

    public void setDistinctUserIds(Set<String> distinctUserIds) {
        this.distinctUserIds = distinctUserIds != null ? distinctUserIds : new HashSet<>();
    }
}

/**
 * Consumes events from Kafka and writes them to MinIO as JSON files.
 */
@Slf4j
public class DirectKafkaEventProcessor {
    // Constants
    private static final int FLUSH_THRESHOLD = 226; 
    private static final int POLL_TIMEOUT_MS = 1000;
    private static final int DEDUP_WINDOW_SIZE = 10000;  // Event Size of the deduplication window
    
    // Configuration
    private final AppConfig config = AppConfig.getInstance();
    
    
    private KafkaConsumer<String, String> consumer;
    
    // S3 client for MinIO
    private S3Client s3Client;
    
    // Processing state
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, AggregatedEvent> aggregationBuffer = new ConcurrentHashMap<>();
    private final DateTimeFormatter pathFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");
    
    // ObjectMapper for JSON conversion
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper aggObjectMapper = new ObjectMapper();
    
    // Thread-safe set with automatic oldest entry removal when size exceeds DEDUP_WINDOW_SIZE
    private final Set<String> processedEventGuids = Collections.synchronizedSet(
        new LinkedHashSet<String>() {
            @Override
            public boolean add(String e) {
                boolean added = super.add(e);
                if (size() > DEDUP_WINDOW_SIZE) {
                    remove(iterator().next());
                }
                return added;
            }
        }
    );
    
    private PipelineProfiler profiler;
    

    public DirectKafkaEventProcessor() {
        log.info("Initializing DirectKafkaEventProcessor");
        
    }
    

    private void initS3Client() {
        try {
            // Create S3 client for MinIO
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    config.getIceberg().getS3AccessKey(),
                    config.getIceberg().getS3SecretKey());
            
            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(config.getIceberg().getS3Endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(Region.US_EAST_1) // MinIO requires a region but doesn't use it
                    .httpClient(UrlConnectionHttpClient.builder().build())
                    .forcePathStyle(true) // Required for MinIO compatibility
                    .build();
            
            log.info("S3 client initialized for endpoint: {}", config.getIceberg().getS3Endpoint());
        } catch (Exception e) {
            log.error("Error initializing S3 client", e);
            throw new RuntimeException("Failed to initialize S3 client", e);
        }
    }
    

    private void initKafkaConsumer() {
        try {
            // Configure Kafka consumer properties
            Properties consumerProps = config.getKafka().getConsumerProps();
            
            // Enable JMX metrics for monitoring
            consumerProps.put("metric.reporters", "org.apache.kafka.common.metrics.JmxReporter");
            consumerProps.put("metrics.recording.level", "INFO");
            consumerProps.put("metrics.num.samples", "2");
            consumerProps.put("metrics.sample.window.ms", "30000");
            consumerProps.put("jmx.port", "9999"); // Add JMX port
            consumerProps.put("jmx.authenticate", "false"); // Disable authentication for local development
            consumerProps.put("jmx.ssl", "false"); // Disable SSL for local development
            
            // Create Kafka consumer
            consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Collections.singletonList(config.getKafka().getTopic()));
            
            // Get initial metrics
            Map<String, Long> metrics = getConsumerMetrics();
            log.info("Initial consumer metrics - Current offset: {}, End offset: {}, Lag: {}", 
                    metrics.get("currentOffset"), metrics.get("endOffset"), metrics.get("lag"));
            
            log.info("Kafka consumer initialized and subscribed to topic: {}", config.getKafka().getTopic());
        } catch (Exception e) {
            log.error("Error initializing Kafka consumer", e);
            throw new RuntimeException("Failed to initialize Kafka consumer", e);
        }
    }
    
    /**
     * Gets current consumer metrics including offset and lag information
     */
    private Map<String, Long> getConsumerMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        try {
            // Get current partition assignments
            Set<TopicPartition> partitions = consumer.assignment();
            if (!partitions.isEmpty()) {
                // Get current positions
                Map<TopicPartition, Long> currentPositions = new HashMap<>();
                for (TopicPartition partition : partitions) {
                    currentPositions.put(partition, consumer.position(partition));
                }
                
                // Get end positions
                Map<TopicPartition, Long> endPositions = consumer.endOffsets(partitions);
                
                // Calculate total lag
                long totalLag = 0;
                for (TopicPartition partition : partitions) {
                    long lag = endPositions.get(partition) - currentPositions.get(partition);
                    totalLag += lag;
                }
                
                metrics.put("currentOffset", currentPositions.values().stream().mapToLong(Long::longValue).sum());
                metrics.put("endOffset", endPositions.values().stream().mapToLong(Long::longValue).sum());
                metrics.put("lag", totalLag);
            }
        } catch (Exception e) {
            log.error("Error getting consumer metrics", e);
        }
        return metrics;
    }
    
    /**
     * Checks if the topic is empty
     */
    private boolean isTopicEmpty() {
        try {
            String topic = config.getKafka().getTopic();

            List<PartitionInfo> partitions = consumer.partitionsFor(topic);

            List<TopicPartition> topicPartitions = partitions.stream()
                    .map(pi -> new TopicPartition(topic, pi.partition()))
                    .collect(Collectors.toList());

            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(topicPartitions);
            
            // Check each partition's current position against its end offset
            for (TopicPartition tp : topicPartitions) {
                long currentPosition = consumer.position(tp);
                long endOffset = endOffsets.get(tp);
                if (currentPosition < endOffset) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Error checking if topic is empty: {}", e.getMessage());
            return false;
        }        
    }
    

    public void process() {
        try {
            log.info("Starting Kafka consumer for topic: {}", config.getKafka().getTopic());
            initS3Client();
            initKafkaConsumer();
            this.profiler = new PipelineProfiler(
                FLUSH_THRESHOLD,
                POLL_TIMEOUT_MS,
                PipelineProfiler.EventType.CONSUMER,
                PipelineProfiler.EventType.MINIO_RAW, 
                PipelineProfiler.EventType.MINIO_AGG
            );
            
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS));
                
                if (!records.isEmpty()) {
                    profiler.recordConsumerEvents(records.count());
                    log.debug("Received {} records from Kafka", records.count());

                    List<Event> rawEvents = new ArrayList<>();
                    List<AggregatedEvent> aggregatedEvents = new ArrayList<>();
                    
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            Event event = processRecord(record);
                            if (event != null) {
                                rawEvents.add(event);
                                processEventForAggregation(event);
                            }
                        } catch (Exception e) {
                            log.error("Error processing record: {}", record.key(), e);
                        }
                    }
                    
                    
                    if (!rawEvents.isEmpty()) {
                        writeRawEventsToJson(rawEvents);
                    }

                    // Check if we should flush the aggregation buffer
                    boolean shouldFlush = aggregationBuffer.size() >= FLUSH_THRESHOLD;
                    boolean isTopicEmpty = isTopicEmpty();
                    // Write aggregated events if we have any
                    log.debug("Aggregation buffer size: {}, aggregationBuffer.isEmpty: {}, shouldFlush: {}, isTopicEmpty: {}, condition() {}", 
                        aggregationBuffer.size(), aggregationBuffer.isEmpty(), shouldFlush, isTopicEmpty, 
                        (!aggregationBuffer.isEmpty() && shouldFlush) || isTopicEmpty
                        );
                    
                    if (
                        (!aggregationBuffer.isEmpty() && shouldFlush) || 
                        isTopicEmpty
                        ) {
                        aggregatedEvents.addAll(aggregationBuffer.values());
                        
                        writeAggregatedEventsToJson(aggregatedEvents, Instant.now().toString().replace(":", "-"));
                        aggregationBuffer.clear();
                        log.debug("writeAggregatedEventsToJson done");
                    }
                    
                    // Commit offsets
                    consumer.commitSync();

                    if (isTopicEmpty) {
                        this.profiler.close();
                    }

                } 
                
            }
        } catch (Exception e) {
            log.error("Error in Kafka consumer", e);
        } finally {
            // Write any remaining aggregated events before shutting down
            log.debug("Call to finally block Aggregation buffer size: {}", aggregationBuffer.size());            
            if (!aggregationBuffer.isEmpty()) {
                List<AggregatedEvent> remainingEvents = new ArrayList<>(aggregationBuffer.values());
                writeAggregatedEventsToJson(remainingEvents, Instant.now().toString().replace(":", "-"));
                aggregationBuffer.clear();
            }
            
            if (consumer != null) {
                consumer.close();
                log.info("Kafka consumer closed");
            }
            
            log.info("Closing PipelineProfiler...");
            profiler.close();
            log.info("PipelineProfiler closed");
        }
        
        log.info("Kafka consumer stopped");
    }
    
    /**
     * Processes a Kafka record into an Event.
     */
    private Event processRecord(ConsumerRecord<String, String> record) {
        try {
            // Parse record value as JSON
            Event event = objectMapper.readValue(record.value(), Event.class);
            
            // Set timestamp if not present
            if (event.getTimestamp() <= 0) {
                event.setTimestamp(System.currentTimeMillis());
            }
            
            return event;
        } catch (Exception e) {
            log.error("Error parsing record: {}", record.value(), e);
            return null;
        }
    }
    

    private void writeRawEventsToJson(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }

        try {
            // Create a unique filename with timestamp
            String timestamp = Instant.now().toString().replace(":", "-");
            writeRawEventsToJson(events, timestamp);
        } catch (Exception e) {
            log.error("Failed to write raw events to MinIO", e);
        }
    }
    

    private void writeRawEventsToJson(List<Event> events, String timestamp) {
        if (events.isEmpty()) {
            return;
        }

        try {
            // Include UUID in filename to ensure uniqueness even for same-millisecond events
            String filename = String.format("events_%s_%s.json", 
                timestamp, UUID.randomUUID().toString().substring(0, 8));
            
            // Partition by year/month/day/hour for efficient querying and to prevent too many files in one directory
            String s3Key = String.format("raw-json/%s/%s/%s/%s/%s",
                "year=" + timestamp.substring(0, 4),
                "month=" + timestamp.substring(5, 7),
                "day=" + timestamp.substring(8, 10),
                "hour=" + timestamp.substring(11, 13),
                filename);

            String jsonContent = objectMapper.writeValueAsString(events);
            byte[] content = jsonContent.getBytes(StandardCharsets.UTF_8);

            Instant s3StartTime = Instant.now();
            s3Client.putObject(PutObjectRequest.builder()
                .bucket(config.getIceberg().getS3Bucket())
                .key(s3Key)
                .contentType("application/json")
                .build(), 
                RequestBody.fromBytes(content));
            Duration latency = Duration.between(s3StartTime, Instant.now());
            profiler.recordMinioRawPutLatency(latency);
            profiler.recordMinioRawPut(content.length);
            profiler.recordMinioRawPutEvents(events.size());
            log.debug("Wrote {} raw events to MinIO: {}", events.size(), s3Key);
        } catch (Exception e) {
            log.error("Failed to write raw events to MinIO", e);    
        }
    }
    

    private void processEventForAggregation(Event event) {
        try {
            // Skip if event was already processed within our deduplication window
            if (!processedEventGuids.add(event.getEventGuid())) {
                log.debug("Skipping duplicate event for aggregation: {}", event.getEventGuid());
                return;
            }

            LocalDateTime eventTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.getTimestamp()),
                ZoneOffset.UTC
            );
            
            LocalDateTime windowStart = eventTime.truncatedTo(ChronoUnit.HOURS);
            
            String userId = event.getFields().getOrDefault(
                "user_id", "unknown").toString();
            
            for (Map.Entry<String, Object> field : event.getFields().entrySet()) {
                String fieldName = field.getKey();
                
                if (!fieldName.equals("country") && !fieldName.equals("device_type") && 
                    !fieldName.equals("action") && !fieldName.equals("page") &&
                    !fieldName.equals("category")) {
                    continue;
                }
                
                Object fieldValue = field.getValue();
                if (fieldValue == null) {
                    continue;
                }   
                // single dimension 
                String key = String.format("%s::%s:%s", 
                    windowStart.format(pathFormatter), 
                    fieldName, fieldValue
                );

                // multi dimension
                // String key = String.format("%s::%s:%s::%s:%s::%s:%s::%s:%s::%s:%s", 
                //         windowStart.format(pathFormatter),
                //         "country", event.getFields().get("country"),
                //         "device_type", event.getFields().get("device_type"),
                //         "action", event.getFields().get("action"),
                //         "page", event.getFields().get("page"),
                //         "category", event.getFields().get("category")
                // );

                Object amountObj = event.getFields().get("value");
                double amount = amountObj instanceof Number ? 
                    ((Number) amountObj).doubleValue() : 0.0;     
                    
                // Use computeIfAbsent for atomic initialization of new aggregations
                AggregatedEvent agg = aggregationBuffer.computeIfAbsent(key, k -> {
                    AggregatedEvent newAgg = new AggregatedEvent();
                    newAgg.setKey(key);
                    newAgg.setFirstTimestamp(event.getTimestamp());
                    newAgg.setLastTimestamp(event.getTimestamp());
                    newAgg.setMinAmount(amount);
                    newAgg.setMaxAmount(amount);
                    return newAgg;
                });
                
                agg.setCount(agg.getCount() + 1);
                agg.getDistinctUserIds().add(userId);
                agg.setDistinctUserIdCount(agg.getDistinctUserIds().size());
                agg.setSumAmount(agg.getSumAmount() + amount);
                agg.setMinAmount(Math.min(agg.getMinAmount(), amount));
                agg.setMaxAmount(Math.max(agg.getMaxAmount(), amount));
                agg.setLastTimestamp(event.getTimestamp());
                
                aggregationBuffer.put(key, agg);
                log.debug("processEventForAggregation Aggregation buffer size: {}", aggregationBuffer.size());
            }
            profiler.recordMinioAggPutEvents(1);
        } catch (Exception e) {
            log.error("Error processing event for aggregation: {}", event.getEventGuid(), e);
        }
    }
    

    private void writeAggregatedEventsToJson(List<AggregatedEvent> aggregatedEvents, String timestamp) {
        log.debug("writeAggregatedEventsToJson aggregatedEvents.size(): {}", aggregatedEvents.size());

        if (aggregatedEvents.isEmpty()) {
            log.debug("writeAggregatedEventsToJson aggregatedEvents.isEmpty()");
            return;
        }

        try {
            log.debug("writeAggregatedEventsToJson try{}");
            // Include UUID in filename to ensure uniqueness even for same-millisecond events
            String filename = String.format("events_agg_%s_%s.json", 
                timestamp, UUID.randomUUID().toString().substring(0, 8));
            
            // Partition by year/month/day/hour for efficient querying and to prevent too many files in one directory
            String s3Key = String.format("agg-01-basic-json/%s/%s/%s/%s/%s", 
                "year=" + timestamp.substring(0, 4),
                "month=" + timestamp.substring(5, 7),
                "day=" + timestamp.substring(8, 10),
                "hour=" + timestamp.substring(11, 13),
                filename);

            log.debug("writeAggregatedEventsToJson before streaming write{}");

            // Use ByteArrayOutputStream to collect the bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();            
            // Create a JsonGenerator that writes directly to the output stream
            try (JsonGenerator jsonGenerator = aggObjectMapper.getFactory().createGenerator(outputStream, JsonEncoding.UTF8)) {
                // Start array
                jsonGenerator.writeStartArray();
                
                // Write each event
                for (AggregatedEvent event : aggregatedEvents) {
                    aggObjectMapper.writeValue(jsonGenerator, event);
                }
                
                // End array
                jsonGenerator.writeEndArray();
            }            
            // Get the bytes directly from the output stream
            byte[] content = outputStream.toByteArray();
            log.debug("writeAggregatedEventsToJson content.length: {}", content.length);

            Instant s3StartTime = Instant.now();
            log.debug("writeAggregatedEventsToJson before putObject{}");  
            try {              
            s3Client.putObject(PutObjectRequest.builder()
                .bucket(config.getIceberg().getS3Bucket())
                .key(s3Key)
                .contentType("application/json")
                .build(), 
                    RequestBody.fromBytes(content));
            } catch (Exception e) {
                log.error("Failed to write aggregated events to MinIO: {}", e.getMessage(), e);
                throw e;
            }
            log.debug("writeAggregatedEventsToJson after putObject{}");                
            Duration latency = Duration.between(s3StartTime, Instant.now());
            profiler.recordMinioAggPutLatency(latency);
            profiler.recordMinioAggPut(content.length);

            log.debug("Wrote {} aggregated events at bytes {} to MinIO: {}", aggregatedEvents.size(), content.length, s3Key);
        } catch (Exception e) {
            log.error("Failed to write aggregated events to MinIO: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Stops the processor.
     */
    public void stop() {
        running.set(false);
        log.info("Stop signal received, processor will exit after current batch");
    }
} 