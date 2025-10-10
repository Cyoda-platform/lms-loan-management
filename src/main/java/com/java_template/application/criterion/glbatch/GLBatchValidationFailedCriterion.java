package com.java_template.application.criterion.glbatch;

import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: This criterion is the inverse of GLBatchValidationCriterion.
 * It returns success when validation FAILS, triggering the error path.
 * 
 * This is a pure function with no side effects.
 */
@Component
public class GLBatchValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final GLBatchValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public GLBatchValidationFailedCriterion(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.validationCriterion = new GLBatchValidationCriterion(serializerFactory, entityService);
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if GLBatch validation FAILED for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(GLBatch.class, this::validateEntityFailed)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    public EvaluationOutcome validateEntityFailed(
            CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context) {
        
        // Run the validation criterion
        EvaluationOutcome validationOutcome = validationCriterion.validateEntity(context);

        // Return the inverse: success if validation failed, failure if validation succeeded
        if (validationOutcome.isFailure()) {
            logger.debug("GLBatch validation failed - triggering error path");
            return EvaluationOutcome.success();
        } else {
            logger.debug("GLBatch validation succeeded - not triggering error path");
            return EvaluationOutcome.fail(
                "Validation succeeded",
                com.java_template.common.serializer.StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }
    }
}

