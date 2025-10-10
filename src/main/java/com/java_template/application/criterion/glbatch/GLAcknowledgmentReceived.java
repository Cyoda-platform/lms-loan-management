package com.java_template.application.criterion.glbatch;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: This criterion checks if the GL system has acknowledged receipt of the batch.
 * 
 * The acknowledgment can be received via:
 * - Polling the GL system API
 * - Event-based notification
 * - File-based acknowledgment
 * 
 * This is a pure function with no side effects.
 * Used for automatic transition: exported -> posted
 */
@Component
public class GLAcknowledgmentReceived implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GLAcknowledgmentReceived(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking GL acknowledgment received criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(GLBatch.class, this::checkAcknowledgmentReceived)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    public EvaluationOutcome checkAcknowledgmentReceived(
            CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context) {
        
        GLBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("GLBatch entity is null");
            return EvaluationOutcome.fail("GLBatch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if acknowledgment timestamp is set
        if (batch.getAcknowledgmentReceivedAt() == null) {
            logger.debug("GLBatch {} has not received acknowledgment yet", batch.getBatchId());
            return EvaluationOutcome.fail(
                "GL system acknowledgment not yet received",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("GLBatch {} received acknowledgment at {}", 
                    batch.getBatchId(), 
                    batch.getAcknowledgmentReceivedAt());
        return EvaluationOutcome.success();
    }
}

