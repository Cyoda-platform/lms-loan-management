package com.java_template.application.criterion.accrual;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.AccrualState;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Criterion to determine if a POSTED accrual requires rebooking due to business data changes.
 *
 * This criterion uses a generic approach that compares the current version of the accrual
 * with its prior version to detect material changes in business data fields.
 *
 * A rebook is required when:
 * - The accrual is in POSTED state
 * - The accrual has been posted before (has prior versions in transaction history)
 * - Business data fields have changed materially between versions (e.g., principalSnapshot, APR)
 * - The delta exceeds the materiality threshold
 *
 * The criterion prevents rebooking in these cases:
 * - First-time posting (no prior versions exist)
 * - Replacement accruals (those created during a rebook process, identified by supersedesAccrualId)
 *
 * This criterion triggers the supersedence workflow where:
 * 1. The current accrual transitions to SUPERSEDED state
 * 2. A new accrual is created with REVERSAL entries for the old amounts
 * 3. The new accrual includes REPLACEMENT entries with corrected amounts
 *
 * This is a pure function with no side effects.
 */
@Component
public class RequiresRebookCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;

    // Threshold for materiality - differences below this are ignored
    private static final BigDecimal MATERIALITY_THRESHOLD = new BigDecimal("0.01");

    public RequiresRebookCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking RequiresRebook criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Accrual.class, this::validateRequiresRebook)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "RequiresRebook".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates whether the accrual requires rebooking due to underlying data changes.
     *
     * @param context The criterion evaluation context containing the accrual
     * @return EvaluationOutcome.success() if rebook is required, otherwise failure
     */
    private EvaluationOutcome validateRequiresRebook(CriterionSerializer.CriterionEntityEvaluationContext<Accrual> context) {
        Accrual accrual = context.entityWithMetadata().entity();
        AccrualState state = AccrualState.valueOf(context.entityWithMetadata().metadata().getState());

        // Check if entity is null (structural validation)
        if (accrual == null) {
            logger.warn("Accrual entity is null");
            return EvaluationOutcome.fail("Accrual entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Only POSTED accruals can be rebooked
        if (state != AccrualState.POSTED) {
            logger.debug("Accrual {} is not in POSTED state (current: {}), rebook not applicable",
                accrual.getAccrualId(), state);
            return EvaluationOutcome.fail(
                String.format("Accrual is not in POSTED state (current: %s)", state),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Don't rebook replacement accruals (those created during a rebook process)
        // This prevents infinite rebook loops
        if (accrual.getSupersedesAccrualId() != null && !accrual.getSupersedesAccrualId().trim().isEmpty()) {
            logger.debug("Accrual {} is a replacement accrual (supersedes: {}), rebook not applicable",
                accrual.getAccrualId(), accrual.getSupersedesAccrualId());
            return EvaluationOutcome.fail(
                String.format("Accrual is a replacement accrual (supersedes: %s)", accrual.getSupersedesAccrualId()),
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Get entity change history to detect if this is first-time posting
        java.util.UUID technicalId = context.entityWithMetadata().metadata().getId();
        List<EntityChangeMeta> changeHistory = entityService.getEntityChangesMetadata(technicalId);

        // If there's only one change (CREATE), this is first-time posting - don't rebook
        if (changeHistory == null || changeHistory.size() <= 1) {
            logger.debug("Accrual {} has no prior versions (first-time posting), rebook not applicable",
                accrual.getAccrualId());
            return EvaluationOutcome.fail(
                "Accrual has no prior versions (first-time posting)",
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
            );
        }

        // Get the prior version of the accrual to compare business data
        // The second-to-last change represents the prior version
        EntityChangeMeta priorChange = changeHistory.get(changeHistory.size() - 2);
        Date priorPointInTime = priorChange.getTimeOfChange();

        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        EntityWithMetadata<Accrual> priorAccrualWithMetadata = entityService.getById(
            technicalId,
            accrualModelSpec,
            Accrual.class,
            priorPointInTime
        );

        if (priorAccrualWithMetadata == null) {
            throw new IllegalStateException(String.format("Prior version of accrual %s not found at %s",
                accrual.getAccrualId(), priorPointInTime));
        }

        Accrual priorAccrual = priorAccrualWithMetadata.entity();

        // Compare business data between current and prior versions
        // Check if principal snapshot has changed materially
        BigDecimal currentPrincipal = accrual.getPrincipalSnapshot() != null ?
            accrual.getPrincipalSnapshot().getAmount() : BigDecimal.ZERO;
        BigDecimal priorPrincipal = priorAccrual.getPrincipalSnapshot() != null ?
            priorAccrual.getPrincipalSnapshot().getAmount() : BigDecimal.ZERO;

        if (currentPrincipal.compareTo(priorPrincipal) != 0) {
            BigDecimal principalDelta = currentPrincipal.subtract(priorPrincipal).abs();
            if (principalDelta.compareTo(MATERIALITY_THRESHOLD) > 0) {
                logger.info("Rebook required for accrual {}: principal snapshot changed from {} to {}",
                    accrual.getAccrualId(), priorPrincipal, currentPrincipal);
                return EvaluationOutcome.success();
            }
        }

        // TODO: Add APR change detection
        // TODO: Add day count convention change detection
        // TODO: Add other business field comparisons as needed

        logger.debug("No material changes detected in business data for accrual {}, rebook not required",
            accrual.getAccrualId());
        return EvaluationOutcome.fail(
            "No material changes detected in business data",
            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
        );
    }
}

