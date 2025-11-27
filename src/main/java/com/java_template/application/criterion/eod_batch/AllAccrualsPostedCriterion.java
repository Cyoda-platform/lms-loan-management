package com.java_template.application.criterion.eod_batch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.AccrualState;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Criterion to verify that all fan-out Accruals are POSTED or in terminal states.
 *
 * <p>This criterion checks that all accruals spawned by this batch (identified by runId)
 * have completed processing. An accrual is considered complete if it is in one of the
 * terminal states: POSTED, FAILED, CANCELED, or SUPERSEDED.</p>
 *
 * <p>This ensures the batch doesn't proceed to the next phase until all individual
 * accrual calculations and postings are finished.</p>
 *
 * <p>This is a pure function with no side effects.</p>
 */
@Component
public class AllAccrualsPostedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    private static final Set<String> TERMINAL_STATES = Set.of(
        AccrualState.POSTED.name(),
        AccrualState.FAILED.name(),
        AccrualState.CANCELED.name(),
        AccrualState.SUPERSEDED.name()
    );

    public AllAccrualsPostedCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking AllAccrualsPosted criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateAllAccrualsPosted)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AllAccrualsPosted".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates that all accruals for this batch are in terminal states.
     *
     * @param context The criterion evaluation context containing the batch
     * @return EvaluationOutcome.success() if all accruals are complete, otherwise failure with reason
     */
    private EvaluationOutcome validateAllAccrualsPosted(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (batch == null) {
            logger.warn("EODAccrualBatch entity is null");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        UUID batchId = batch.getBatchId();

        // Check if batchId is null
        if (batchId == null) {
            logger.warn("BatchId is null for batch");
            return EvaluationOutcome.fail("Batch ID is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        try {
            // Query for all accruals with this batch's runId
            ModelSpec accrualModelSpec = new ModelSpec()
                .withName(Accrual.ENTITY_NAME)
                .withVersion(Accrual.ENTITY_VERSION);

            List<EntityWithMetadata<Accrual>> allAccrualsWithMetadata =
                entityService.findAll(accrualModelSpec, Accrual.class);

            logger.debug("Found {} total accruals in system for batch check", allAccrualsWithMetadata.size());

            // Filter for accruals belonging to this batch (with metadata)
            List<EntityWithMetadata<Accrual>> relevantAccrualsWithMetadata = allAccrualsWithMetadata.stream()
                .filter(a -> {
                    Accrual accrual = a.entity();
                    boolean matches = accrual != null && batchId.toString().equals(accrual.getRunId());
                    if (accrual != null && !matches) {
                        logger.trace("Accrual {} has runId {} (looking for {})",
                            accrual.getAccrualId(), accrual.getRunId(), batchId.toString());
                    }
                    return matches;
                })
                .toList();

            if (relevantAccrualsWithMetadata.isEmpty()) {
                // Check if batch metrics indicate accruals should exist
                Integer expectedAccruals = batch.getMetrics() != null ? batch.getMetrics().getAccrualsCreated() : null;

                if (expectedAccruals != null && expectedAccruals > 0) {
                    // Batch says accruals were created, but we can't find them yet
                    logger.debug("Batch {} reports {} accruals created, but none found yet in query (searched {} total accruals). Accruals may still be committing.",
                        batchId, expectedAccruals, allAccrualsWithMetadata.size());
                    return EvaluationOutcome.fail(
                        String.format("Batch reports %d accruals created, but they are not yet visible in the database", expectedAccruals),
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                    );
                } else {
                    // No accruals found and batch doesn't report any created yet
                    logger.debug("No accruals found for batch: {} (searched {} total accruals)",
                        batchId, allAccrualsWithMetadata.size());
                    return EvaluationOutcome.fail(
                        "No accruals have been created for this batch yet",
                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                    );
                }
            }

            // Check if all accruals are in terminal states
            long totalAccruals = relevantAccrualsWithMetadata.size();
            long terminalAccruals = relevantAccrualsWithMetadata.stream()
                .filter(a -> TERMINAL_STATES.contains(a.getState()))
                .count();

            if (terminalAccruals < totalAccruals) {
                long pendingAccruals = totalAccruals - terminalAccruals;
                logger.debug("Batch {} has {} pending accruals out of {} total",
                    batchId, pendingAccruals, totalAccruals);
                return EvaluationOutcome.fail(
                    String.format("%d of %d accruals are still processing", pendingAccruals, totalAccruals),
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                );
            }

            // Count by state for logging
            long posted = relevantAccrualsWithMetadata.stream().filter(a -> AccrualState.valueOf(a.getState()) == AccrualState.POSTED).count();
            long failed = relevantAccrualsWithMetadata.stream().filter(a -> AccrualState.valueOf(a.getState()) == AccrualState.FAILED).count();
            long canceled = relevantAccrualsWithMetadata.stream().filter(a -> AccrualState.valueOf(a.getState()) == AccrualState.CANCELED).count();
            long superseded = relevantAccrualsWithMetadata.stream().filter(a -> AccrualState.valueOf(a.getState()) == AccrualState.SUPERSEDED).count();

            logger.info("All {} accruals for batch {} are complete: {} posted, {} failed, {} canceled, {} superseded",
                totalAccruals, batchId, posted, failed, canceled, superseded);

            return EvaluationOutcome.success();

        } catch (Exception e) {
            logger.error("Error querying for batch accruals: {}", e.getMessage(), e);
            return EvaluationOutcome.fail(
                "Failed to check accrual status: " + e.getMessage(),
                StandardEvalReasonCategories.DATA_QUALITY_FAILURE
            );
        }
    }
}

