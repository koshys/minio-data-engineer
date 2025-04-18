# Local Dependencies

This directory contains local dependencies that are not available in Maven Central or other public repositories.

## Required JAR files

1. `flink-connector-kafka-1.18.1.jar` - Apache Flink Kafka connector for Flink 1.18.1
2. `iceberg-flink-runtime-1.18-1.4.3.jar` - Apache Iceberg Flink runtime for Flink 1.18 and Iceberg 1.4.3

## How to install these dependencies

```bash
# Install Flink Kafka connector
mvn install:install-file \
  -Dfile=flink-connector-kafka-1.18.1.jar \
  -DgroupId=org.apache.flink \
  -DartifactId=flink-connector-kafka \
  -Dversion=1.18.1 \
  -Dpackaging=jar \
  -DgeneratePom=true

# Install Iceberg Flink runtime
mvn install:install-file \
  -Dfile=iceberg-flink-runtime-1.18-1.4.3.jar \
  -DgroupId=org.apache.iceberg \
  -DartifactId=iceberg-flink-runtime-1.18 \
  -Dversion=1.4.3 \
  -Dpackaging=jar \
  -DgeneratePom=true
```

## How to download these dependencies

you can download them from:

1. Flink Kafka connector: https://flink.apache.org/downloads/
2. Iceberg Flink runtime: https://iceberg.apache.org/releases/ 