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
 * ABOUTME: This processor records the second approver (checker) for the GL batch.
 * 
 * Execution Mode: SYNC
 * Transition: maker_approved -> exported
 * 
 * Logic:
 * 1. Extract user ID from request context (or security context)
 * 2. Verify checker is different from maker (enforced by criterion)
 * 3. Record checker user ID
 * 4. Record approval timestamp
 * 5. Record user role
 */
@Component
public class RecordCheckerApproval implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecordCheckerApproval.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RecordCheckerApproval(SerializerFactory serializerFactory) {
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

        logger.debug("Recording checker approval for GLBatch: {}", batch.getBatchId());

        // Approvals should already exist from maker approval
        if (batch.getApprovals() == null) {
            throw new IllegalStateException("Approvals not initialized - maker approval should have been recorded first");
        }

        GLBatch.Approvals approvals = batch.getApprovals();

        // In a real implementation, extract user ID from security context
        // For now, use a placeholder (different from maker)
        String checkerUserId = "checker-user-" + System.currentTimeMillis();
        
        // Verify checker is different from maker
        if (approvals.getMakerUserId() != null && approvals.getMakerUserId().equals(checkerUserId)) {
            throw new IllegalStateException("Checker must be different from maker");
        }

        approvals.setCheckerUserId(checkerUserId);
        approvals.setCheckerApprovedAt(LocalDateTime.now());
        approvals.setCheckerRole("Finance Controller");

        logger.info("Checker approval recorded for batch {} by user {} (maker was {})", 
                   batch.getBatchId(), checkerUserId, approvals.getMakerUserId());

        return entityWithMetadata;
    }
}

