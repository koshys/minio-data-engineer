package com.datapipeline.common;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Defines the schema for synthetic data generation.
 */
public class SchemaDefinition {
    private final List<ColumnDefinition> columns = new ArrayList<>();
    private final Random random = new Random();
    
    /**
     * Adds a column to the schema.
     */
    public SchemaDefinition addColumn(ColumnDefinition column) {
        columns.add(column);
        return this;
    }
    
    /**
     * Gets all columns in the schema.
     */
    public List<ColumnDefinition> getColumns() {
        return columns;
    }
    
    /**
     * Creates a default schema based on the application configuration.
     */
    public static SchemaDefinition createDefault() {
        AppConfig config = AppConfig.getInstance();
        SchemaDefinition schema = new SchemaDefinition();
        
        // Add columns based on configuration
        for (int i = 0; i < config.getSchema().getNumColumns(); i++) {
            int cardinality = randomCardinality(
                config.getSchema().getMinCardinality(),
                config.getSchema().getMaxCardinality()
            );
            
            schema.addColumn(ColumnDefinition.builder()
                .name("column_" + i)
                .type(ColumnType.STRING)
                .cardinality(cardinality)
                .build());
        }
        
        return schema;
    }
    
    /**
     * Creates an e-commerce schema with specific business fields.
     */
    public static SchemaDefinition createECommerceSchema() {
        SchemaDefinition schema = new SchemaDefinition();
        
        // Add user_id field (STRING with medium cardinality)
        schema.addColumn(ColumnDefinition.builder()
            .name("user_id")
            .type(ColumnType.STRING)
            .cardinality(1_000_000) // Higher cardinality for unique users
            .build());
            
        // Add country field (STRING with low cardinality)
        schema.addColumn(ColumnDefinition.builder()
            .name("country")
            .type(ColumnType.STRING)
            .cardinality(193) // Limited number of countries
            .build());
        
        // Add device_type field
        schema.addColumn(ColumnDefinition.builder()
            .name("device_type")
            .type(ColumnType.STRING)
            .cardinality(3) // mobile, desktop, tablet
            .build());
        
        // Add action field
        schema.addColumn(ColumnDefinition.builder()
            .name("action")
            .type(ColumnType.STRING)
            .cardinality(5) // click, view, purchase, etc.
            .build());
        
        // Add page field
        schema.addColumn(ColumnDefinition.builder()
            .name("page")
            .type(ColumnType.STRING)
            .cardinality(10) // homepage, product, cart, etc.
            .build());
        
        // Add category field
        schema.addColumn(ColumnDefinition.builder()
            .name("category")
            .type(ColumnType.STRING)
            .cardinality(15) // electronics, clothing, etc.
            .build());
        
        // Add value field
        schema.addColumn(ColumnDefinition.builder()
            .name("value")
            .type(ColumnType.DOUBLE)
            .cardinality(100) // Range of possible values
            .build());
        
        // Add session_duration field
        schema.addColumn(ColumnDefinition.builder()
            .name("session_duration")
            .type(ColumnType.INTEGER)
            .cardinality(300) // Range of durations in seconds
            .build());
        
        // Add is_returning field
        schema.addColumn(ColumnDefinition.builder()
            .name("is_returning")
            .type(ColumnType.BOOLEAN)
            .cardinality(2) // true or false
            .build());
        
        return schema;
    }
    
    private static int randomCardinality(int min, int max) {
        return min + (int)(Math.random() * ((max - min) + 1));
    }
    
    /**
     * Represents a column in the schema.
     */
    @Data
    @Builder
    public static class ColumnDefinition {
        private String name;
        private ColumnType type;
        private int cardinality;
        
        /**
         * Generates a value for this column based on its definition.
         */
        public Object generateValue(Random random) {
            switch (type) {
                case STRING:
                    return name + "_" + (random.nextInt(cardinality) + 1);
                case INTEGER:
                    return random.nextInt(cardinality);
                case DOUBLE:
                    return random.nextDouble() * cardinality;
                case BOOLEAN:
                    return random.nextBoolean();
                default:
                    throw new IllegalStateException("Unsupported column type: " + type);
            }
        }
    }
    
    /**
     * Supported column data types.
     */
    public enum ColumnType {
        STRING,
        INTEGER,
        DOUBLE,
        BOOLEAN
    }
} 