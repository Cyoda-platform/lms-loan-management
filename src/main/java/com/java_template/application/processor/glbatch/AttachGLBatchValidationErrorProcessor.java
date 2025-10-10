package com.java_template.application.processor.glbatch;

import com.java_template.application.criterion.glbatch.GLBatchValidationCriterion;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Processor that attaches validation error reasons to GLBatch entities
 * when GLBatchValidationCriterion fails. This processor re-runs the validation
 * logic to capture the specific failure reason and stores it in the entity's
 * validationErrorReason field.
 * 
 * Execution Mode: SYNC
 * Transition: initial -> validation_error
 */
@Component
public class AttachGLBatchValidationErrorProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final GLBatchValidationCriterion validationCriterion;
    private final String className = this.getClass().getSimpleName();

    public AttachGLBatchValidationErrorProcessor(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.validationCriterion = new GLBatchValidationCriterion(serializerFactory, entityService);
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Attaching validation error reason to GLBatch entity: {}", request.getEntityId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(GLBatch.class)
            .map(this::attachValidationError)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EntityWithMetadata<GLBatch> attachValidationError(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<GLBatch> context) {

        EntityWithMetadata<GLBatch> entityWithMetadata = context.entityResponse();
        GLBatch batch = entityWithMetadata.entity();

        // Create a criterion context to run validation
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> criterionContext =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);

        EvaluationOutcome outcome = validationCriterion.validateEntity(criterionContext);

        if (outcome.isFailure()) {
            EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
            String errorReason = failOutcome.formatReason();
            
            batch.setValidationErrorReason(errorReason);
            logger.info("Attached validation error reason to GLBatch {}: {}", batch.getBatchId(), errorReason);
        } else {
            logger.warn("Validation passed for GLBatch {}, but error processor was called.", batch.getBatchId());
        }

        return entityWithMetadata;
    }
}

