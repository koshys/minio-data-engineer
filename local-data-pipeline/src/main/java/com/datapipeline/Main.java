package com.datapipeline;

import com.datapipeline.common.AppConfig;
import com.datapipeline.common.SchemaDefinition;
import com.datapipeline.consumer.DirectKafkaEventProcessor;
import com.datapipeline.generator.DataGenerator;
import com.datapipeline.producer.KafkaEventProducer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main entry point for the data pipeline application.
 */
@Slf4j
public class Main {
    private static final AppConfig config = AppConfig.getInstance();
    
    public static void main(String[] args) {
        log.info("Starting Local Data Pipeline");
        
        try {
            if (args.length > 0) {
                String command = args[0].toLowerCase();
                
                switch (command) {
                    case "generate":
                        generateData();
                        break;
                    case "process":
                        processData();
                        break;
                    case "all":
                        runFullPipeline();
                        break;
                    default:
                        printUsage();
                        break;
                }
            } else {
                printUsage();
            }
           
        } finally {
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java -jar local-data-pipeline.jar <command>");
        System.out.println("Commands:");
        System.out.println("  generate - Generate synthetic data and send to Kafka");
        System.out.println("  process  - Process data from Kafka and write to MinIO");
        System.out.println("  all      - Run the full pipeline");
    }
    
    /**
     * Generates synthetic data and sends it to Kafka.
     */
    private static void generateData() {
        log.info("Generating synthetic data and sending to Kafka");
        
        
        SchemaDefinition schema = SchemaDefinition.createECommerceSchema();
        
        
        DataGenerator generator = new DataGenerator(schema);
        
        
        try (KafkaEventProducer producer = new KafkaEventProducer()) {
            // Generate events and send to Kafka
            generator.generateEvents(config.getEventsToGenerate(), producer::sendEvent);
        }
        
        log.info("Data generation completed");
    }
    
    /**
     * Processes data from Kafka and writes to MinIO.
     */
    private static void processData() {
        log.info("Starting data processing from Kafka to MinIO");
        
        try {
            DirectKafkaEventProcessor processor = new DirectKafkaEventProcessor();
            
            // Register shutdown hook to gracefully stop the processor
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutdown hook triggered, stopping services...");
                processor.stop();
            }));
            
            // Start processing
            processor.process();
        } catch (Exception e) {
            log.error("Error processing data", e);
        }
    }
    
    /**
     * Runs the full pipeline: generate and process.
     */
    private static void runFullPipeline() {
        log.info("Running full pipeline");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Start direct Kafka processor in a separate thread
        executor.submit(() -> {
            try {
                DirectKafkaEventProcessor processor = new DirectKafkaEventProcessor();
                
                // Register shutdown hook to gracefully stop the processor
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("Shutdown hook triggered, stopping services...");
                    processor.stop();
                }));
                
                // Start processing
                processor.process();
            } catch (Exception e) {
                log.error("Error in Kafka processor", e);
            } finally {
                latch.countDown();
            }
        });
        
        // Give processor some time to start
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Generate data
        generateData();
        
        // Wait for processing to complete
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("Full pipeline execution completed");
        
        // Shutdown executor
        executor.shutdown();
    }
} 