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

/**
 * ABOUTME: This processor sends the export file to the downstream GL system.
 * 
 * Execution Mode: ASYNC_NEW_TX
 * Transition: maker_approved -> exported
 * 
 * Logic:
 * 1. Read export file from storage
 * 2. Send to GL system via API or file transfer
 * 3. Handle retries with exponential backoff
 * 
 * In a real implementation, this would integrate with the GL system API.
 */
@Component
public class SendToGLSystem implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendToGLSystem.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendToGLSystem(SerializerFactory serializerFactory) {
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

        logger.debug("Sending batch to GL system: {}", batch.getBatchId());

        // Verify export file exists
        if (batch.getExportFilePath() == null || batch.getExportFilePath().trim().isEmpty()) {
            throw new IllegalStateException("Export file path not set - GenerateExportFile should have been called first");
        }

        // In a real implementation:
        // 1. Read file from storage
        // 2. Call GL system API or transfer file via SFTP
        // 3. Handle authentication
        // 4. Handle retries (configured with EXPONENTIAL_BACKOFF in workflow)
        // 5. Log transmission details

        logger.info("Simulating file transfer to GL system...");
        logger.info("File: {}", batch.getExportFilePath());
        logger.info("Period: {}", batch.getPeriod());
        logger.info("Control Totals: Debits={}, Credits={}", 
                   batch.getControlTotals().getTotalDebits(),
                   batch.getControlTotals().getTotalCredits());

        // Simulate successful transmission
        logger.info("Batch {} successfully sent to GL system", batch.getBatchId());

        // Note: The acknowledgment will be received separately and trigger
        // the exported -> posted transition via GLAcknowledgmentReceived criterion

        return entityWithMetadata;
    }
}

