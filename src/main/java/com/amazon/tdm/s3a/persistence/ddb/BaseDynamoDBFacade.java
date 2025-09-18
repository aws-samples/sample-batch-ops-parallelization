package com.amazon.tdm.s3a.persistence.ddb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDeleteExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;
import lombok.extern.log4j.Log4j2;

import java.util.function.Function;

@Log4j2
public class BaseDynamoDBFacade {

    private static final DynamoDBMapperConfig CONSISTENT_READ_CONFIG = DynamoDBMapperConfig.builder()
        .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
        .build();

    private static final DynamoDBMapperConfig UPDATE_SKIP_NULL_ATTRIBUTES_CONFIG = DynamoDBMapperConfig.builder()
        .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)
        .build();

    private final DynamoDBMapper mapper;

    public BaseDynamoDBFacade(final DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Create the given entity.
     */

    public <T> void create(final T entity,
                           final Function<T, DynamoDBSaveExpression> expressionBuilder) {
        mapper.save(entity, expressionBuilder.apply(entity));
    }

    /**
     * Get the given entity.
     */
    public <T> T get(final T primaryKeyObject, final Function<T, String> describer) {
        T entity = mapper.load(primaryKeyObject);
        if (entity == null) {
            entity = mapper.load(primaryKeyObject, CONSISTENT_READ_CONFIG);
        }
        return entity;
    }

    /**
     * Get the given entity.
     */
    public <T> PaginatedScanList<T> list(final Class<T> primaryKeyObject, final DynamoDBScanExpression dynamoDBScanExpression) {
        return mapper.scan(primaryKeyObject, dynamoDBScanExpression);
    }

    /**
     * Update the given entity.
     */
    public <T> void update(final T entity,
                           final Function<T, DynamoDBSaveExpression> expressionBuilder) {
        mapper.save(entity, expressionBuilder.apply(entity), UPDATE_SKIP_NULL_ATTRIBUTES_CONFIG);
    }

    /**
     * Hard delete the given entity.
     */
    public <T> void delete(final T entity,
                           final Function<T, DynamoDBDeleteExpression> expressionBuilder) {
        mapper.delete(entity, expressionBuilder.apply(entity));
    }

}
