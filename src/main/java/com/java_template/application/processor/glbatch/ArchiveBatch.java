package com.java_template.application.processor.glbatch;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ABOUTME: This processor archives the GL batch for long-term retention.
 * 
 * Execution Mode: SYNC
 * Transition: posted -> archived
 * 
 * Logic:
 * 1. Record archival timestamp
 * 2. Move to archival storage (if applicable)
 * 3. Update retention metadata
 */
@Component
public class ArchiveBatch implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveBatch.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveBatch(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(GLBatch.class)
                .validate(this::isValidEntityWithMetadata, "Invalid GLBatch entity wrapper")
                .map(this::processBusinessLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<GLBatch> entityWithMetadata) {
        return entityWithMetadata != null &&
               entityWithMetadata.entity() != null &&
               entityWithMetadata.entity().isValid(entityWithMetadata.metadata());
    }

    private EntityWithMetadata<GLBatch> processBusinessLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<GLBatch> context) {

        EntityWithMetadata<GLBatch> entityWithMetadata = context.entityResponse();
        GLBatch batch = entityWithMetadata.entity();

        logger.debug("Archiving GLBatch: {}", batch.getBatchId());

        // Record archival timestamp
        batch.setArchivedAt(LocalDateTime.now());

        // In a real implementation:
        // 1. Move export file to archival storage (e.g., S3 Glacier)
        // 2. Update retention policy metadata
        // 3. Send archival notification
        // 4. Update compliance records

        logger.info("Batch {} archived successfully at {}", 
                   batch.getBatchId(), 
                   batch.getArchivedAt());
        logger.info("Retention period: 7 years (per financial regulations)");

        return entityWithMetadata;
    }
}

