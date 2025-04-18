package com.datapipeline.generator;

import com.datapipeline.common.Event;
import com.datapipeline.common.SchemaDefinition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DataGeneratorTest {

    @Test
    public void testGenerateEvent() {
        // Create a schema with a few columns
        SchemaDefinition schema = new SchemaDefinition();
        schema.addColumn(SchemaDefinition.ColumnDefinition.builder()
                .name("test_column_1")
                .type(SchemaDefinition.ColumnType.STRING)
                .cardinality(10)
                .build());
        schema.addColumn(SchemaDefinition.ColumnDefinition.builder()
                .name("test_column_2")
                .type(SchemaDefinition.ColumnType.INTEGER)
                .cardinality(5)
                .build());
        
        // Create generator
        DataGenerator generator = new DataGenerator(schema);
        
        // Generate an event
        Event event = generator.generateEvent();
        
        // Verify the event
        assertNotNull("Event should not be null", event);
        assertNotNull("Event GUID should not be null", event.getEventGuid());
        assertTrue("Event timestamp should be positive", event.getTimestamp() > 0);
        
        // Verify fields
        assertNotNull("Event fields should not be null", event.getFields());
        assertEquals("Event should have 2 fields", 2, event.getFields().size());
        assertTrue("Event should have test_column_1", event.getFields().containsKey("test_column_1"));
        assertTrue("Event should have test_column_2", event.getFields().containsKey("test_column_2"));
    }
    
    @Test
    public void testGenerateBatch() {
        // Create a simple schema
        SchemaDefinition schema = new SchemaDefinition();
        schema.addColumn(SchemaDefinition.ColumnDefinition.builder()
                .name("test_column")
                .type(SchemaDefinition.ColumnType.STRING)
                .cardinality(10)
                .build());
        
        // Create generator
        DataGenerator generator = new DataGenerator(schema);
        
        // Generate a batch of events
        int batchSize = 100;
        List<Event> events = generator.generateBatch(batchSize);
        
        // Verify the batch
        assertNotNull("Events list should not be null", events);
        assertEquals("Events list should have the requested size", batchSize, events.size());
        
        // Verify that all events have unique GUIDs
        Set<String> guids = events.stream()
                .map(Event::getEventGuid)
                .collect(Collectors.toSet());
        assertEquals("All events should have unique GUIDs", batchSize, guids.size());
    }
    
    @Test
    public void testGenerateEvents() {
        // Create a simple schema
        SchemaDefinition schema = new SchemaDefinition();
        schema.addColumn(SchemaDefinition.ColumnDefinition.builder()
                .name("test_column")
                .type(SchemaDefinition.ColumnType.STRING)
                .cardinality(10)
                .build());
        
        // Create generator
        DataGenerator generator = new DataGenerator(schema);
        
        // Create a collector for events
        List<Event> collectedEvents = new ArrayList<>();
        
        // Generate events
        int totalEvents = 50;
        generator.generateEvents(totalEvents, collectedEvents::add);
        
        // Verify the collected events
        assertEquals("Should have generated the requested number of events", 
                totalEvents, collectedEvents.size());
        
        // Verify that all events have unique GUIDs
        Set<String> guids = collectedEvents.stream()
                .map(Event::getEventGuid)
                .collect(Collectors.toSet());
        assertEquals("All events should have unique GUIDs", totalEvents, guids.size());
    }
    
    @Test
    public void testECommerceSchema() {
        // Create e-commerce schema
        SchemaDefinition schema = SchemaDefinition.createECommerceSchema();
        
        // Create generator
        DataGenerator generator = new DataGenerator(schema);
        
        // Generate an event
        Event event = generator.generateEvent();
        
        // Verify the event
        assertNotNull("Event should not be null", event);
        assertNotNull("Event GUID should not be null", event.getEventGuid());
        assertTrue("Event timestamp should be positive", event.getTimestamp() > 0);
        
        // Verify all required e-commerce fields exist
        assertTrue("Event should have user_id field", event.getFields().containsKey("user_id"));
        assertTrue("Event should have country field", event.getFields().containsKey("country"));
        assertTrue("Event should have device_type field", event.getFields().containsKey("device_type"));
        assertTrue("Event should have action field", event.getFields().containsKey("action"));
        assertTrue("Event should have page field", event.getFields().containsKey("page"));
        assertTrue("Event should have category field", event.getFields().containsKey("category"));
        assertTrue("Event should have value field", event.getFields().containsKey("value"));
        assertTrue("Event should have session_duration field", event.getFields().containsKey("session_duration"));
        assertTrue("Event should have is_returning field", event.getFields().containsKey("is_returning"));
        
        // Verify is_returning field is a boolean
        Object isReturning = event.getFields().get("is_returning");
        assertTrue("is_returning field should be a boolean", isReturning instanceof Boolean);
        
        // Print the generated event for debugging
        System.out.println("Generated e-commerce event: " + event.toJson());
    }
} 