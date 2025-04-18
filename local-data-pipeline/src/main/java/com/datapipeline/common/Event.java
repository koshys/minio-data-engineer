package com.datapipeline.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an event in the data pipeline.
 * This class uses a dynamic structure to support arbitrary columns.
 */
@Data
@NoArgsConstructor
public class Event implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final transient ObjectMapper MAPPER = new ObjectMapper();
    
    // Primary key - unique identifier for the event
    private String eventGuid;
    
    // Timestamp when the event was generated
    private long timestamp;
    
    // Dynamic fields storage
    @JsonIgnore
    private Map<String, Object> fields = new HashMap<>();
    
    /**
     * Creates a new event with a random UUID and current timestamp.
     */
    public Event(Map<String, Object> fields) {
        this.eventGuid = UUID.randomUUID().toString();
        this.timestamp = Instant.now().toEpochMilli();
        if (fields != null) {
            this.fields.putAll(fields);
        }
    }
    
    /**
     * Getter for dynamic fields to support Jackson serialization.
     */
    @JsonAnyGetter
    public Map<String, Object> getFields() {
        return fields;
    }
    
    /**
     * Setter for dynamic fields to support Jackson deserialization.
     */
    @JsonAnySetter
    public void setField(String name, Object value) {
        fields.put(name, value);
    }
    
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event to JSON", e);
        }
    }
    
    public static Event fromJson(String json) {
        try {
            return MAPPER.readValue(json, Event.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event from JSON", e);
        }
    }
} 