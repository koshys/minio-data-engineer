# MinIO Data Pipeline Performance Demo - Day 1

## Overview

This demo demonstrates the power and performance of MinIO when used as a storage layer for high-throughput data pipelines. You'll explore how different pipeline configurations affect the performance of raw and aggregated data writes to MinIO, and gain insights into optimizing your own data lake architectures.

## Prerequisites

- Java 17 or later
- Maven 3.6 or later
- Docker (for running MinIO)
- Git (to clone this repository)

## Setup

- The setup is already done for you

## Building and Running
1. `cd local-data-pipeline` {{ Execute }}
2. `python3 -m venv localpy` {{ Execute }}
3. Run the pipeline:
`./run-pipeline.sh --clean` {{ Execute }}

The `run-pipeline.sh` script will:
1. Stop any existing containers
2. Build the application
3. Start kafka, MinIO, Prometheus and Grafana services
4. Generate test data
5. Start the pipeline
6. Display logs

You can then go to 
1. MinIO UI at http://localhost:9091 at port 80:9091
2. Kafka UI at http://localhost:8081 at port 80:8081
3. Prometheus UI at http://localhost:9070 at port 80:9070
4. Grafana UI at http://localhost:3001 at port 80:3001




## Configuration Parameters

For each demo exercise, you'll need to modify the configuration parameters in the following files:

1. `src/main/java/com/datapipeline/common/AppConfig.java`:
   - `eventsToGenerate`: Number of synthetic events to generate
   - `maxPollRecords`: Maximum number of records to poll from Kafka at once, and write to MinIO Raw and Aggregate Dataset


2. `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   - `POLL_TIMEOUT_MS`: Timeout for Kafka consumer polling in milliseconds
   - `FLUSH_THRESHOLD`: Minimum Number of data grains produced before writing to MinIO Aggregate Dataset

A quicker way to update then is to use 
```
 ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> < {10 < FLUSH_THRESHOLD < 225} >
```

## Demo Exercises

### Exercise 1: Baseline Performance

First, let's establish a baseline by configuring the pipeline with default parameters:

1. Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 1_000_000; //Number of synthetic events to generate
   private int maxPollRecords = 500;  // 500 | 1000 | 10000 Maximum number of records to poll from Kafka at once, and write to MinIO Raw and Aggregate Dataset
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000; //Timeout for Kafka consumer polling in milliseconds
   private static final int FLUSH_THRESHOLD = 225; //Minimum Number of data grains produced before writing to MinIO Aggregate Dataset
   ```

   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 1_000_000 500 1000 225` {{ Execute }}

3. Run the pipeline:
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}


This will:
- Generate `1,000,000` synthetic events
- Configure Kafka consumer to poll up to `500` records at a time
- Set a poll timeout of `1000`ms
- Flush data to MinIO after a minimum of `225` records

After the run completes, examine the output metrics:

```
MinIO Raw Write Performance:
- Total PUTs: xxx
- PUTs/Second: xxx/s
- Total Bytes: xxx
- Bytes/Second: xxx/s
- Raw PUT Latency (ms):
  - Max: xxx
  - Mean: xxx
  - p25: xxx
  - p50: xxx
  - p75: xxx
  - p90: xxx
  - p95: xxx
- Raw PUT size (bytes):
  - Max: xxx
  - Mean: xxx
  - p25: xxx
  - p50: xxx
  - p75: xxx
  - p90: xxx
  - p95: xxx  

MinIO Aggregate Write Performance:
- Total PUTs: xxx
- PUTs/Second: xxx/s
- Total Bytes: xxx
- Bytes/Second: xxx/s
- Agg PUT Latency (ms):
  - Max: xxx
  - Mean: xxx
  - p25: xxx
  - p50: xxx
  - p75: xxx
  - p90: xxx
  - p95: xxx
- Agg PUT size (bytes):
  - Max: xxx
  - Mean: xxx
  - p25: xxx
  - p50: xxx
  - p75: xxx
  - p90: xxx
  - p95: xxx    
```

Record these values to compare with subsequent runs.

you can use the following to query
`docker exec -it duckdb-client duckdb` {{ execute }}

### Exercise 2: Varying Event Generation

Let's modify the number of events to see how it affects throughput: to 5 million events

1. Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 5_000_000; //Number of synthetic events to generate
   private int maxPollRecords = 500;  // 500 | 1000 | 10000 Maximum number of records to poll from Kafka at once, and write to MinIO Raw and Aggregate Dataset
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000; //Timeout for Kafka consumer polling in milliseconds
   private static final int FLUSH_THRESHOLD = 225; //Minimum Number of data grains produced before writing to MinIO Aggregate Dataset
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 5_000_000 500 1000 225` {{ Execute }}

3. Run the pipeline and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Let's modify the number of events to see how it affects throughput: to 10 million events
4.  
   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000; //Number of synthetic events to generate
   private int maxPollRecords = 500;  // 500 | 1000 | 10000 Maximum number of records to poll from Kafka at once, and write to MinIO Raw and Aggregate Dataset
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000; //Timeout for Kafka consumer polling in milliseconds  
   private static final int FLUSH_THRESHOLD = 225; //Minimum Number of data grains produced before writing to MinIO Aggregate Dataset
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 500 1000 225` {{ Execute }}
   

5. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Questions to consider:
- Does the PUTs/second rate stay consistent as event volume increases?
- How does the latency distribution change with higher event counts?

you can use the following to query
`docker exec -it duckdb-client duckdb` {{ execute }}

### Exercise 3: Poll Record Size Impact

Now let's experiment with different poll record sizes
, which determines how many records are processed before writing to MinIO RAW dataset:

Small Poll Size
1.    Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000; //Number of synthetic events to generate
   private int maxPollRecords = 10;  // 500 | 1000 | 10000 Maximum number of records to poll from Kafka at once, and write to MinIO Raw and Aggregate Dataset
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000;   //Timeout for Kafka consumer polling in milliseconds  
   private static final int FLUSH_THRESHOLD = 225; //Minimum Number of data grains produced before writing to MinIO Aggregate Dataset
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10 1000 225` {{ Execute }}
   

2. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}


Medium Poll Size
3.   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000; //Number of synthetic events to generate
   private int maxPollRecords = 1000;  // 500 | 1000 | 10000 Maximum number of records to poll from Kafka at once, and write to MinIO Raw and Aggregate Dataset
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000;   //Timeout for Kafka consumer polling in milliseconds  
   private static final int FLUSH_THRESHOLD = 225; //Minimum Number of data grains produced before writing to MinIO Aggregate Dataset
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 1000 1000 225` {{ Execute }}
   


4. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Large Poll Size

6.   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000;   
   private static final int FLUSH_THRESHOLD = 225;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 1000 225` {{ Execute }}
   

7.  Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Questions to consider:
- How does poll size affect throughput?
- What's the relationship between poll size and latency?
- Is there a "sweet spot" for your environment?

you can use the following to query
`docker exec -it duckdb-client duckdb` {{ execute }}

### Exercise 4: Flush Threshold Optimization

The flush threshold determines the minimum data grains produced before writing to MinIO Aggregate dataset:

Small batch Size
1.     Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000;   
   private static final int FLUSH_THRESHOLD = 30;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 1000 30` {{ Execute }}
   

2. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}


Medium batch Size
3.   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000;   
   private static final int FLUSH_THRESHOLD = 100;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 1000 100` {{ Execute }}
   


4. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Large Batch Size

6.   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1000;   
   private static final int FLUSH_THRESHOLD = 225;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 1000 225` {{ Execute }}
   

7.  Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Questions to consider:
- How does batch size affect total PUT count?
- What's the impact on PUT latency?
- What's the trade-off between latency and throughput?

you can use the following to query
`docker exec -it duckdb-client duckdb` {{ execute }}

### Exercise 5: Poll Timeout Effects

The poll timeout affects how long the consumer waits for records to get processed:

Small Poll Timeout
1.     Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 10;   
   private static final int FLUSH_THRESHOLD = 225;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 10 225` {{ Execute }}
   

2. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}


Medium Poll Timeout
3.   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 100;
   private static final int FLUSH_THRESHOLD = 200;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 100 200` {{ Execute }}
   


4. Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}

Large Poll Timeout

6.   Edit `src/main/java/com/datapipeline/common/AppConfig.java`:
   ```java
   // Modify these constants
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = 10000;  // 500 | 1000 | 10000
   ```
   
   Edit `src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java`:
   ```java
   // Find and modify this constant
   private static final int POLL_TIMEOUT_MS = 1500;   
   private static final int FLUSH_THRESHOLD = 225;
   ```
   ```
   ./update-config.sh <eventsToGenerate> <maxPollRecords> <POLL_TIMEOUT_MS> <FLUSH_THRESHOLD>
   ```

   `./update-config.sh 10_000_000 10000 1500 225` {{ Execute }}
   

7.  Run the pipeline again and record the metrics.
`./run-pipeline.sh` {{ Execute }}
`docker logs -f data-pipeline` {{ Execute }}


Questions to consider:
- How does poll timeout affect overall pipeline performance?
- Is there an optimal timeout for your throughput goals?

you can use the following to query
`docker exec -it duckdb-client duckdb` {{ execute }}

### Exercise 6: Combined Parameter Optimization

Based on your findings from the previous exercises, try to find the optimal combination of parameters:

1. Edit `AppConfig.java` with your best parameters:
   ```java
   private int eventsToGenerate = 10_000_000;
   private int maxPollRecords = [YOUR_BEST];  // 500 | 1000 | 10000   
   ```

2. Edit `DirectKafkaEventProcessor.java` with your best flush threshold:
   ```java
   private static final int FLUSH_THRESHOLD = [YOUR_BEST];
   private static final int POLL_TIMEOUT_MS = [YOUR_BEST];   
   ```

3. Run and record metrics.

## Data Analysis

Throughout these exercises, collect the following metrics for each run:

| Parameter Combination | Events/sec | Total PUTs | PUTs/sec | Total Bytes | Bytes/sec | Raw PUT Latency (ms) |  Raw PUT Size (bytes) | Agg PUTs | Agg PUTs/sec | Agg Bytes | Agg Bytes/sec | Agg Latency (ms) | Agg Size (bytes) |
|-------------------|-----------|-----------|----------|------------|-----------|---------------------|----------------------|---------|-------------|-----------|--------------|-----------------|------------------|
| Baseline          |           |           |          |            |           | max/mean/p50/p95    | max/mean/p50/p95     |         |             |           |              | max/mean/p50/p95| max/mean/p50/p95 |
| 50K events        |           |           |          |            |           | max/mean/p50/p95    | max/mean/p50/p95     |         |             |           |              | max/mean/p50/p95| max/mean/p50/p95 |
| 100K events       |           |           |          |            |           | max/mean/p50/p95    | max/mean/p50/p95     |         |             |           |              | max/mean/p50/p95| max/mean/p50/p95 |
| ...               |           |           |          |            |           | max/mean/p50/p95    | max/mean/p50/p95     |         |             |           |              | max/mean/p50/p95| max/mean/p50/p95 |

Here's an example of completed metrics for one configuration:

| Parameter Combination | Events/sec | Total PUTs | PUTs/sec | Total Bytes | Bytes/sec | Raw PUT Latency (ms) |  Raw PUT Size (bytes) | Agg PUTs | Agg PUTs/sec | Agg Bytes | Agg Bytes/sec | Agg Latency (ms) | Agg Size (bytes) |
|-------------------|-----------|-----------|----------|------------|-----------|---------------------|----------------------|---------|-------------|-----------|--------------|-----------------|------------------|
| 10K events, 100 poll, 100ms timeout, 1K flush | 4,500 | 1,200 | 120 | 5.2 MB | 520 KB/s | 12/5.3/4.1/8.7 | 4.5KB/4.1KB/4.0KB/5.0KB | 10 | 1 | 5.2 MB | 520 KB/s | 38/25/22/33 | 540KB/520KB/500KB/530KB |

You might notice patterns like:
- Higher flush thresholds result in fewer PUTs but larger PUT sizes
- Lower poll timeouts can increase events/second but might increase latency variability
- Higher poll record sizes improve throughput but may impact p95 latencies

you can use the following to query
`docker exec -it duckdb-client duckdb` {{ execute }}

## Key Insights

After completing all exercises, answer these questions:


Use the localpy as your py interpreter on vscode and start the kernel. 
run through the notebook notebook/duckdb_metrics_analysis.ipynb

1. What parameter combination provided the highest raw PUTs/second to MinIO?
2. What parameter combination provided the best p95 latency?
3. How did increasing the flush threshold affect:
   - The number of PUTs?
   - The size of each PUT?
   - The overall throughput?
4. What's the relationship between poll record size and overall pipeline throughput?
5. What parameter settings would you recommend for:
   - A latency-sensitive application?
   - A throughput-maximizing application?
   - A balanced approach?

## Conclusion

This demo demonstrates the performance capabilities of MinIO when used in data pipeline architectures. By understanding how different configuration parameters affect performance, you can optimize your own data pipelines for your specific requirements.

## References

- [MinIO Documentation](https://min.io/docs/minio/linux/index.html)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Data Pipeline Best Practices](https://min.io/resources/whitepapers) 