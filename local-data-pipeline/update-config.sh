#!/bin/bash

set -e

if [ "$#" -ne 4 ]; then
  echo "Usage: ./update-config.sh <new_events_value> <new_poll_value> <poll_timeout_ms> < {10 < flush_threshold < 225} >"
  exit 1
fi

# Correct file paths based on actual package structure
FILE1=src/main/java/com/datapipeline/common/AppConfig.java
FILE2=src/main/java/com/datapipeline/consumer/DirectKafkaEventProcessor.java
NEW_EVENTS_VALUE="$1"
NEW_POLL_VALUE="$2"
POLL_TIMEOUT_MS="$3"
FLUSH_THRESHOLD="$4"

# Check if the files exist
if [ ! -f "$FILE1" ]; then
  echo "Error: File not found: $FILE1"
  exit 1
fi
if [ ! -f "$FILE2" ]; then
  echo "Error: File not found: $FILE2"
  exit 1
fi

if (( FLUSH_THRESHOLD < 10 || FLUSH_THRESHOLD > 225 )); then
  echo "Error: FLUSH_THRESHOLD must be between 10 and 225"
  exit 1
fi

echo "Updating configuration values:"
echo "- eventsToGenerate: $NEW_EVENTS_VALUE"
echo "- maxPollRecords: $NEW_POLL_VALUE"
echo "- POLL_TIMEOUT_MS: $POLL_TIMEOUT_MS"
echo "- FLUSH_THRESHOLD: $FLUSH_THRESHOLD"

if [[ "$(uname)" == "Darwin" ]]; then
  sed -i '' -e "s/\(private int eventsToGenerate = \)[0-9_]*;/\1${NEW_EVENTS_VALUE};/" "$FILE1"
  sed -i '' -e "s/\(private int maxPollRecords = \)[0-9_]*;/\1${NEW_POLL_VALUE};/" "$FILE1"
  sed -i '' -e "s/\(private static final int POLL_TIMEOUT_MS = \)[0-9_]*;/\1${POLL_TIMEOUT_MS};/" "$FILE2"
  sed -i '' -e "s/\(private static final int FLUSH_THRESHOLD = \)[0-9_]*;/\1${FLUSH_THRESHOLD};/" "$FILE2"
else
  # Linux version
  sed -i -e "s/\(private int eventsToGenerate = \)[0-9_]*;/\1${NEW_EVENTS_VALUE};/" "$FILE1"
  sed -i -e "s/\(private int maxPollRecords = \)[0-9_]*;/\1${NEW_POLL_VALUE};/" "$FILE1"
  sed -i -e "s/\(private static final int POLL_TIMEOUT_MS = \)[0-9_]*;/\1${POLL_TIMEOUT_MS};/" "$FILE2"
  sed -i -e "s/\(private static final int FLUSH_THRESHOLD = \)[0-9_]*;/\1${FLUSH_THRESHOLD};/" "$FILE2"
fi

echo "✅ Configuration files updated successfully"
echo "AppConfig.java: eventsToGenerate=$NEW_EVENTS_VALUE, maxPollRecords=$NEW_POLL_VALUE"
echo "DirectKafkaEventProcessor.java: POLL_TIMEOUT_MS=$POLL_TIMEOUT_MS, FLUSH_THRESHOLD=$FLUSH_THRESHOLD"
