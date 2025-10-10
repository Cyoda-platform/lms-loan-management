package com.java_template.application.criterion.glbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ABOUTME: This criterion validates new GLBatch entities during creation,
 * ensuring the period is valid and not already processed.
 * 
 * Validation checks:
 * 1. Period format is valid (YYYY-MM)
 * 2. Period has not already been processed (no existing batch for same period)
 * 3. All required fields are present
 * 
 * This is a pure function with no side effects.
 */
@Component
public class GLBatchValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Pattern for period validation: YYYY-MM format
    private static final Pattern PERIOD_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    public GLBatchValidationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking GLBatch validation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(GLBatch.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context) {
        GLBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("GLBatch entity is null");
            return EvaluationOutcome.fail("GLBatch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (!batch.isValid(context.entityWithMetadata().metadata())) {
            logger.warn("GLBatch entity is not valid: {}", batch.getBatchId());
            return EvaluationOutcome.fail("GLBatch entity is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate period format
        if (batch.getPeriod() == null || !PERIOD_PATTERN.matcher(batch.getPeriod()).matches()) {
            logger.warn("Invalid period format for batch {}: {}", batch.getBatchId(), batch.getPeriod());
            return EvaluationOutcome.fail(
                "Period must be in YYYY-MM format (e.g., 2025-09)",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Check if period has already been processed
        if (isPeriodAlreadyProcessed(batch.getPeriod(), batch.getBatchId())) {
            logger.warn("Period {} has already been processed", batch.getPeriod());
            return EvaluationOutcome.fail(
                String.format("Period %s has already been processed", batch.getPeriod()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.debug("GLBatch validation passed for batch: {}", batch.getBatchId());
        return EvaluationOutcome.success();
    }

    /**
     * Checks if a period has already been processed by querying existing GLBatch entities.
     * 
     * @param period The period to check (e.g., "2025-09")
     * @param currentBatchId The current batch ID (to exclude from check)
     * @return true if period already processed, false otherwise
     */
    private boolean isPeriodAlreadyProcessed(String period, String currentBatchId) {
        try {
            ModelSpec batchModelSpec = new ModelSpec()
                .withName(GLBatch.ENTITY_NAME)
                .withVersion(GLBatch.ENTITY_VERSION);

            // Query all GL batches
            List<EntityWithMetadata<GLBatch>> batches = 
                entityService.findAll(batchModelSpec, GLBatch.class);

            // Check if any batch (other than current) has the same period
            return batches.stream()
                .map(EntityWithMetadata::entity)
                .filter(batch -> !batch.getBatchId().equals(currentBatchId))
                .anyMatch(batch -> period.equals(batch.getPeriod()));

        } catch (Exception e) {
            logger.error("Error querying GL batches for period {}: {}", period, e.getMessage(), e);
            // In case of error, allow the batch to proceed (fail open)
            return false;
        }
    }
}

