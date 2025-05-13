
INSTALL httpfs;
LOAD httpfs;


INSTALL json;
LOAD json;


SET s3_endpoint='${MINIO_ENDPOINT}';
SET s3_access_key_id='${MINIO_ACCESS_KEY}';
SET s3_secret_access_key='${MINIO_SECRET_KEY}';
SET s3_url_style='path';
SET s3_use_ssl=false;


CREATE SCHEMA IF NOT EXISTS minio_stats;


CREATE OR REPLACE VIEW minio_stats.pipeline_metrics AS
SELECT 
  config,
  runtime.timestamp,
  runtime.duration_ms AS runtime_ms,
  
  -- Producer metrics
  producer.duration_ms AS producer_duration_ms,
  producer.events AS producer_events,
  producer.events_per_second AS producer_events_per_second,
  
  -- Consumer metrics
  consumer.duration_ms AS consumer_duration_ms,
  consumer.events AS consumer_events,
  consumer.events_per_second AS consumer_events_per_second,
  
  -- MinIO Raw metrics
  minio_raw.duration_ms AS minio_raw_duration_ms,
  minio_raw.puts AS minio_raw_puts,
  minio_raw.puts_per_second AS minio_raw_puts_per_second,
  minio_raw.bytes_per_second AS minio_raw_bytes_per_second,
  minio_raw.bytes AS minio_raw_bytes,
  minio_raw.size.max_bytes AS minio_raw_size_max_bytes,
  minio_raw.size.mean_bytes AS minio_raw_size_mean_bytes,
  minio_raw.size.percentiles.p25 AS minio_raw_size_percentiles_p25,
  minio_raw.size.percentiles.p50 AS minio_raw_size_percentiles_p50,
  minio_raw.size.percentiles.p75 AS minio_raw_size_percentiles_p75,
  minio_raw.size.percentiles.p90 AS minio_raw_size_percentiles_p90,
  minio_raw.size.percentiles.p95 AS minio_raw_size_percentiles_p95,
  minio_raw.latency.mean_ms AS minio_raw_latency_mean_ms,
  minio_raw.latency.max_ms AS minio_raw_latency_max_ms,
  minio_raw.latency.percentiles.p25 AS minio_raw_latency_percentiles_p25,
  minio_raw.latency.percentiles.p50 AS minio_raw_latency_percentiles_p50,
  minio_raw.latency.percentiles.p75 AS minio_raw_latency_percentiles_p75,
  minio_raw.latency.percentiles.p90 AS minio_raw_latency_percentiles_p90,
  minio_raw.latency.percentiles.p95 AS minio_raw_latency_percentiles_p95,
  
  -- MinIO Agg metrics
  minio_agg.duration_ms AS minio_agg_duration_ms,
  minio_agg.puts AS minio_agg_puts,
  minio_agg.puts_per_second AS minio_agg_puts_per_second,
  minio_agg.bytes_per_second AS minio_agg_bytes_per_second,
  minio_agg.bytes AS minio_agg_bytes,
  minio_agg.size.mean_bytes AS minio_agg_size_mean_bytes,
  minio_agg.size.percentiles.p25 AS minio_agg_size_percentiles_p25,
  minio_agg.size.percentiles.p50 AS minio_agg_size_percentiles_p50,
  minio_agg.size.percentiles.p75 AS minio_agg_size_percentiles_p75,
  minio_agg.size.percentiles.p90 AS minio_agg_size_percentiles_p90,
  minio_agg.size.percentiles.p95 AS minio_agg_size_percentiles_p95,
  minio_agg.latency.mean_ms AS minio_agg_latency_mean_ms,
  minio_agg.latency.max_ms AS minio_agg_latency_max_ms,
  minio_agg.latency.percentiles.p25 AS minio_agg_latency_percentiles_p25,
  minio_agg.latency.percentiles.p50 AS minio_agg_latency_percentiles_p50,
  minio_agg.latency.percentiles.p75 AS minio_agg_latency_percentiles_p75,
  minio_agg.latency.percentiles.p90 AS minio_agg_latency_percentiles_p90,
  minio_agg.latency.percentiles.p95 AS minio_agg_latency_percentiles_p95

FROM read_json_auto('s3://${MINIO_BUCKET}/stats/pipeline_metrics_*.json');

-- Help message
.print '======================================================================================'
.print 'DuckDB client connected to MinIO at ${MINIO_ENDPOINT}'
.print 'Usage examples:'
.print '  - View all metrics: SELECT * FROM minio_stats.pipeline_metrics;'
.print '  - Query specific metrics: SELECT timestamp, producer_events_per_second, consumer_events_per_second FROM minio_stats.pipeline_metrics;'
.print '======================================================================================' 