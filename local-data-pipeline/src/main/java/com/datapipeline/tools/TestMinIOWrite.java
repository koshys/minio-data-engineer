package com.datapipeline.tools;

import com.datapipeline.common.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Test class for writing aggregated JSON data to MinIO
 */
public class TestMinIOWrite {
    private static final Logger log = LoggerFactory.getLogger(TestMinIOWrite.class);
    private static final DateTimeFormatter pathFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH");

    public static void main(String[] args) {
        try {
            log.info("Starting TestMinIOWrite...");
            
            // Get application config
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
                    .forcePathStyle(true) // Required for MinIO
                    .build();
            
            log.info("S3 client initialized for endpoint: {}", config.getIceberg().getS3Endpoint());
            
            // Create sample aggregated data
            List<Map<String, Object>> jsonRecords = new ArrayList<>();
            
            // Create a few sample records
            for (int i = 0; i < 5; i++) {
                Map<String, Object> record = new HashMap<>();
                String window = LocalDateTime.now(ZoneOffset.UTC).format(pathFormatter);
                record.put("window", window);
                record.put("field", "product");
                record.put("value", "product_" + i);
                record.put("count", 100 + i);
                record.put("sumAmount", 1000.0 + (i * 100));
                record.put("avgAmount", 10.0 + i);
                record.put("minAmount", 5.0);
                record.put("maxAmount", 15.0 + i);
                record.put("firstTimestamp", System.currentTimeMillis() - 3600000);
                record.put("lastTimestamp", System.currentTimeMillis());
                jsonRecords.add(record);
            }
            
            // Current timestamp for filename
            String timestamp = Instant.now().toString().replace(":", "-");
            String randomId = UUID.randomUUID().toString().substring(0, 8);
            String filename = String.format("test_agg_%s_%s.json", timestamp, randomId);
            
            // Create date path for organizing data - YYYY/MM/DD/HH
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String datePath = now.format(pathFormatter);
            
            // Construct S3 key with date path
            String s3Key = String.format("%s/%s", datePath, filename);
            
            // Convert to JSON
            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writeValueAsString(jsonRecords);
            
            log.info("Writing JSON data: {}", jsonData);
            
            // Upload to S3
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(config.getIceberg().getS3Bucket())
                    .key(s3Key)
                    .contentType("application/json")
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromString(jsonData));
            
            log.info("Successfully wrote test aggregation to: s3://{}/{}", 
                    config.getIceberg().getS3Bucket(), s3Key);
            
        } catch (Exception e) {
            log.error("Error in TestMinIOWrite: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }
} 