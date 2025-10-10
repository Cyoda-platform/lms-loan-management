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
 * ABOUTME: This processor records the first approver (maker) for the GL batch.
 * 
 * Execution Mode: SYNC
 * Transition: prepared -> maker_approved
 * 
 * Logic:
 * 1. Extract user ID from request context (or security context)
 * 2. Record maker user ID
 * 3. Record approval timestamp
 * 4. Record user role
 */
@Component
public class RecordMakerApproval implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecordMakerApproval.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecordMakerApproval(SerializerFactory serializerFactory) {
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

        logger.debug("Recording maker approval for GLBatch: {}", batch.getBatchId());

        // Initialize approvals if not present
        if (batch.getApprovals() == null) {
            batch.setApprovals(new GLBatch.Approvals());
        }

        GLBatch.Approvals approvals = batch.getApprovals();

        // In a real implementation, extract user ID from security context
        // For now, use a placeholder
        String makerUserId = "maker-user-" + System.currentTimeMillis();
        
        approvals.setMakerUserId(makerUserId);
        approvals.setMakerApprovedAt(LocalDateTime.now());
        approvals.setMakerRole("Finance Manager");

        logger.info("Maker approval recorded for batch {} by user {}", 
                   batch.getBatchId(), makerUserId);

        return entityWithMetadata;
    }
}

