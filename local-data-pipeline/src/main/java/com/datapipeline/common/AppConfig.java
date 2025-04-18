package com.datapipeline.common;

import lombok.Data;

import java.time.Duration;
import java.util.Properties;

/**
 * Central configuration class for the data pipeline application.
 */
@Data
public class AppConfig {
    // General application settings
    private String applicationId = "local-data-pipeline";
    private int eventsToGenerate = 20_000_000;
    private int generationThreads = 4;
    private boolean isDockerDeployment = false;
    
    // Data schema settings
    private Schema schema = new Schema();
    
    // Kafka settings
    private Kafka kafka = new Kafka();
    
    // Flink settings
    private Flink flink = new Flink();
    
    // Iceberg settings
    private Iceberg iceberg = new Iceberg();
    
    // DuckDB settings
    private DuckDB duckDB = new DuckDB();
    
    @Data
    public static class Schema {
        private int numColumns = 10;
        private int minCardinality = 5;
        private int maxCardinality = 100;
        private int recordsPerBatch = 100;
    }
    
    @Data
    public static class Kafka {
        private String bootstrapServers = "localhost:29092";
        private String topic = "events";
        private int partitions = 4;
        private short replicationFactor = 1;
        private int batchSize = 16384;
        private int lingerMs = 100;
        private int bufferMemory = 33554432; // 32MB
        private int maxPollRecords = 10000; // 500 | 1000 | 10000
        
        public Properties getProducerProps() {
            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrapServers);
            props.put("acks", "all");
            props.put("retries", 3);
            props.put("batch.size", batchSize);
            props.put("linger.ms", lingerMs);
            props.put("buffer.memory", bufferMemory);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            return props;
        }
        
        public Properties getConsumerProps() {
            Properties props = new Properties();
            props.put("bootstrap.servers", bootstrapServers);
            props.put("group.id", "data-pipeline-consumer");
            props.put("auto.offset.reset", "earliest");
            props.put("enable.auto.commit", "false");
            props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            props.put("max.poll.records",maxPollRecords);
            return props;
        }
    }
    
    @Data
    public static class Flink {
        private String jobName = "Event Aggregation";
        private Duration windowSize = Duration.ofSeconds(10);
        private int parallelism = 4;
        private String checkpointDir = "file:///tmp/flink-checkpoints";
        private Duration checkpointInterval = Duration.ofSeconds(30);
    }
    
    @Data
    public static class Iceberg {
        private String warehouseLocation = "s3a://warehouse/";
        private String catalogName = "iceberg_catalog";
        private String databaseName = "events_db";
        private String tableName = "events_agg";
        private String s3Endpoint = "http://minio:9000";
        private String s3AccessKey = "minioadmin";
        private String s3SecretKey = "minioadmin";
        private String compressionCodec = "snappy";
        private String s3Region = "us-east-1";
        private String s3Bucket = "warehouse";
        private String warehousePath = "iceberg/warehouse";
    }
    
    @Data
    public static class DuckDB {
        private String databasePath = "/data/duckdb";
        private int port = 9999;
    }
    
    // Singleton instance
    private static final AppConfig INSTANCE = new AppConfig();
    
    private AppConfig() {
        // Check for Docker deployment
        String dockerEnv = System.getenv("DOCKER_DEPLOYMENT");
        if (dockerEnv != null && dockerEnv.equalsIgnoreCase("true")) {
            isDockerDeployment = true;
            
            // Override settings from environment variables when in Docker
            String kafkaServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
            if (kafkaServers != null && !kafkaServers.isEmpty()) {
                kafka.bootstrapServers = kafkaServers;
            }
            
            String minioEndpoint = System.getenv("MINIO_ENDPOINT");
            if (minioEndpoint != null && !minioEndpoint.isEmpty()) {
                iceberg.s3Endpoint = minioEndpoint;
            }
            
            String minioAccessKey = System.getenv("MINIO_ACCESS_KEY");
            if (minioAccessKey != null && !minioAccessKey.isEmpty()) {
                iceberg.s3AccessKey = minioAccessKey;
            }
            
            String minioSecretKey = System.getenv("MINIO_SECRET_KEY");
            if (minioSecretKey != null && !minioSecretKey.isEmpty()) {
                iceberg.s3SecretKey = minioSecretKey;
            }
        }
    }
    
    public static AppConfig getInstance() {
        return INSTANCE;
    }
} 