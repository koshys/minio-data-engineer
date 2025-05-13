#!/bin/bash

# Colors for terminal output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Default settings
CLEAN_VOLUMES=false

# Parse command line arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --clean|-c) CLEAN_VOLUMES=true ;;
        --help|-h) 
            echo "Usage: $0 [options]"
            echo "Options:"
            echo "  --clean, -c     Clean all volumes for a fresh start"
            echo "  --help, -h      Show this help message"
            exit 0
            ;;
        *) echo -e "${RED}Unknown parameter: $1${NC}"; exit 1 ;;
    esac
    shift
done

# Stop previous data-pipeline if running
echo -e "${GREEN}Stopping previous containers...${NC}"
if $CLEAN_VOLUMES; then
    echo -e "${YELLOW}Cleaning all volumes for a fresh start...${NC}"
    docker-compose down -v
else
    docker-compose down
fi

# Build the application
echo -e "${GREEN}Building the application...${NC}"
./mvnw clean test -Dtest=DataGeneratorTest
./mvnw clean package -DskipTests

# Start infrastructure
echo -e "${GREEN}Starting infrastructure...${NC}"
# docker-compose up -d zookeeper kafka kafka-ui minio minio-setup

# Wait for Kafka to be ready
# echo -e "${GREEN}Waiting for Kafka to be ready...${NC}"
# sleep 10

# Start data-pipeline with proper logging
echo -e "${GREEN}Starting data pipeline...${NC}"
docker-compose up -d
sleep 20
# Wait for everything to be set up
echo -e "${GREEN}Waiting for all services to be ready...${NC}"
sleep 5

# Generate data
# echo -e "${GREEN}Generating data...${NC}"
# docker exec -it data-pipeline java -Dlog4j2.configurationFile=/app/log4j2.xml -Dlog4j2.debug=true -jar /app/local-data-pipeline.jar generate

# Display logs
# echo -e "${GREEN}Displaying data-pipeline logs...${NC}"
# docker logs data-pipeline

# Check for data in MinIO
echo -e "${GREEN}Checking for data in MinIO...${NC}"
docker exec -it minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker exec -it minio mc ls local/warehouse --recursive


echo -e "${GREEN}Pipeline execution completed.${NC}"
echo -e "You can check the MinIO UI at http://localhost:9091"
echo -e "You can check the Kafka UI at http://localhost:8081"
echo -e "You can check the Prometheus UI at http://localhost:9070"
echo -e "You can check the Grafana UI at http://localhost:3001"
if $CLEAN_VOLUMES; then
    echo -e "${YELLOW}Note: Pipeline was started with clean volumes${NC}"
fi 
