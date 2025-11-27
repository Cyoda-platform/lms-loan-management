package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatchState;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Criterion to ensure only one active batch exists per AsOfDate.
 *
 * <p>This criterion validates that no other batch is currently processing
 * for the same asOfDate. A batch is considered active if it is in any
 * non-terminal state (not COMPLETED, FAILED, or CANCELED).</p>
 *
 * <p>This prevents concurrent batch runs for the same business date,
 * which could lead to duplicate accruals or data inconsistencies.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class NoActiveBatchForDateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    private static final Set<String> TERMINAL_WORKFLOW_STATES = Set.of(
        "COMPLETED", "FAILED", "CANCELED", "ERROR"
    );

    public NoActiveBatchForDateCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking NoActiveBatchForDate criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateNoActiveBatch)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "NoActiveBatchForDate".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that no other active batch exists for the same asOfDate.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if no active batch exists, otherwise failure with reason
     */
    EvaluationOutcome validateNoActiveBatch(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        LocalDate asOfDate = batch.getAsOfDate();

        // Check if asOfDate is null
        if (asOfDate == null) {
            logger.warn("AsOfDate is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("AsOfDate is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if batchId is null (should be initialized before validation)
        if (batch.getBatchId() == null) {
            logger.warn("BatchId is null for batch with asOfDate: {}. This indicates the batch was not properly initialized.", asOfDate);
            return EvaluationOutcome.fail(
                "Batch ID is required for validation. The batch entity must be initialized with a batchId before validation.",
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        logger.info("Checking for active batches with asOfDate: {} (current batch: {})", asOfDate, batch.getBatchId());

        // Query for all batches with the same asOfDate
        ModelSpec batchModelSpec = new ModelSpec()
            .withName(EODAccrualBatch.ENTITY_NAME)
            .withVersion(EODAccrualBatch.ENTITY_VERSION);

        List<EntityWithMetadata<EODAccrualBatch>> existingBatchesWithMetadata =
            entityService.findAll(batchModelSpec, EODAccrualBatch.class);

        logger.info("Found {} total batches in system", existingBatchesWithMetadata.size());

        // Filter for batches with same asOfDate and non-terminal states
        // Safely handle null batchIds in existing batches as well
        List<EntityWithMetadata<EODAccrualBatch>> conflictingBatches = existingBatchesWithMetadata.stream()
            .filter(b -> {
                // Skip batches with null asOfDate
                if (b.entity().getAsOfDate() == null) {
                    logger.debug("Skipping batch with null asOfDate in validation check");
                    return false;
                }
                return asOfDate.equals(b.entity().getAsOfDate());
            })
            .filter(b -> {
                // Exclude current batch (safely handle null batchIds)
                if (b.entity().getBatchId() == null) {
                    logger.debug("Skipping batch with null batchId in validation check");
                    return false;
                }
                return !batch.getBatchId().equals(b.entity().getBatchId());
            })
            .filter(b -> !TERMINAL_WORKFLOW_STATES.contains(b.metadata().getState()))
            .toList();

        long activeBatchCount = conflictingBatches.size();

        if (activeBatchCount > 0) {
            logger.warn("Found {} active batch(es) for asOfDate {} (excluding current batch {})",
                activeBatchCount, asOfDate, batch.getBatchId());

            // Log details of conflicting batches
            for (EntityWithMetadata<EODAccrualBatch> conflictingBatch : conflictingBatches) {
                logger.warn("  Conflicting batch: id={}, batchId={}, state={}, asOfDate={}",
                    conflictingBatch.metadata().getId(),
                    conflictingBatch.entity().getBatchId(),
                    conflictingBatch.metadata().getState(),
                    conflictingBatch.entity().getAsOfDate());
            }

            return EvaluationOutcome.fail(
                String.format("Another active batch already exists for asOfDate %s", asOfDate),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        logger.info("No active batches found for asOfDate {} (batch: {})", asOfDate, batch.getBatchId());
        return EvaluationOutcome.success();
    }
}

