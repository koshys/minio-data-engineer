# Local Data Pipeline

A local development environment for processing events from Kafka and writing them to MinIO (S3-compatible storage).

## Features

- Consumes events from Kafka
- Writes raw events to MinIO in JSON format
- Aggregates events by:
  - Time window (hourly)
  - Country
  - Product ID
  - Category
  - Action
- Tracks metrics:
  - Event counts
  - Distinct user counts
  - Sum, min, and max amounts
  - First and last timestamps
- Deduplicates events within a sliding window of 10,000 events
- Exposes Kafka consumer metrics via JMX
- Partitions data by year/month/day/hour for efficient querying

## Prerequisites

- Docker and Docker Compose
- Java 17 or later
- Maven 3.8 or later


## Configuration

The application uses environment variables for configuration. Create a `.env` file in the project root:

```env
# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC=events
KAFKA_CONSUMER_GROUP=data-pipeline-consumer

# MinIO Configuration
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=warehouse
```

## Building and Running

0. `cd /home/minio/minio/labs/minio-for-datalakes/local-data-pipeline` {{ Execute }}
1. Run the pipeline:
`./run-pipeline.sh` {{ Execute }}


The `run-pipeline.sh` script will:
1. Stop any existing containers
2. Build the application
3. Start infrastructure services
4. Generate test data
5. Start the pipeline
6. Display logs

## Data Organization

### Raw Events
- Path: `raw-json/year=YYYY/month=MM/day=DD/hour=HH/events_TIMESTAMP_UUID.json`
- Contains raw events as received from Kafka
- No deduplication applied

### Aggregated Events
- Path: `agg-01-basic-json/year=YYYY/month=MM/day=DD/hour=HH/events_agg_TIMESTAMP_UUID.json`
- Aggregated by:
  - Time window (hourly)
  - Country
  - Product ID
  - Category
  - Action
- Includes metrics:
  - Event count
  - Distinct user count
  - Sum, min, max amounts
  - First and last timestamps
  - List of distinct user IDs

## Monitoring

- Kafka consumer metrics are exposed via JMX on port 9999
- Logs are written to both console and file
- MinIO UI available at http://localhost:9001

## Troubleshooting

1. Check logs:
```bash
docker-compose logs -f
```

2. Verify MinIO access:
```bash
mc alias set myminio http://localhost:9000 minioadmin minioadmin
mc ls myminio/warehouse/ -r --versions
You can check the MinIO UI at http://localhost:9001
```

3. Check Kafka consumer metrics:
```bash
You can check the Kafka UI at http://localhost:8080
```

## Development

To modify the aggregation logic or add new features, or change the schema, or generate way more events and see:
1. Update the java classes
2. Rebuild the application
3. Restart the pipeline

## License
MIT License 