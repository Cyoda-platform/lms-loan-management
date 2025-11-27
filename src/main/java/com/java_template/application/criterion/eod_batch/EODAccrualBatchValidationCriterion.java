package com.java_template.application.criterion.eod_batch;

import com.java_template.application.criterion.accrual.IsBusinessDayCriterion;
import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.*;
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
 * ABOUTME: Composite criterion that validates all EOD accrual batch start requirements.
 * Combines IsBusinessDay, NoActiveBatchForDate, and UserHasPermission checks.
 */
@Component
public class EODAccrualBatchValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final IsBusinessDayCriterion isBusinessDayCriterion;
    private final NoActiveBatchForDateCriterion noActiveBatchForDateCriterion;
    private final UserHasPermissionCriterion userHasPermissionCriterion;
    private final String className = this.getClass().getSimpleName();

    public EODAccrualBatchValidationCriterion(
            SerializerFactory serializerFactory,
            EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.isBusinessDayCriterion = new IsBusinessDayCriterion(serializerFactory);
        this.noActiveBatchForDateCriterion = new NoActiveBatchForDateCriterion(serializerFactory, entityService);
        this.userHasPermissionCriterion = new UserHasPermissionCriterion(serializerFactory);
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EODAccrualBatchValidation criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(EODAccrualBatch.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates all EOD batch start requirements.
     * This method is public to allow the negative criterion and error processor to call it.
     */
    public EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> context) {
        EODAccrualBatch batch = context.entityWithMetadata().entity();

        // Check -1: Validate batch entity is not null
        if (batch == null) {
            logger.error("Batch entity is null in validation context");
            return EvaluationOutcome.fail("Batch entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        logger.info("Starting validation for batch: {} with asOfDate: {}, mode: {}, initiatedBy: {}",
            batch.getBatchId(), batch.getAsOfDate(), batch.getMode(), batch.getInitiatedBy());

        // Check 0: Validate batchId is present before other checks
        if (batch.getBatchId() == null) {
            logger.warn("BatchId is null for batch with asOfDate: {}. This indicates the batch was not properly initialized.", batch.getAsOfDate());
            return EvaluationOutcome.fail(
                "Batch ID is required for validation. The batch entity must be initialized with a batchId before validation.",
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        // Check 1: Validate asOfDate is present before other checks
        if (batch.getAsOfDate() == null) {
            logger.warn("AsOfDate is null for batch: {}", batch.getBatchId());
            return EvaluationOutcome.fail("AsOfDate is required", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check 2: Is business day
        logger.info("Check 2: Validating business day for batch: {} with asOfDate: {}", batch.getBatchId(), batch.getAsOfDate());
        EvaluationOutcome businessDayOutcome;
        try {
            CriterionSerializer.CriterionEntityEvaluationContext<com.java_template.application.entity.accrual.version_1.Accrual> accrualContext =
                createAccrualContextFromBatch(context);
            businessDayOutcome = isBusinessDayCriterion.validateBusinessDay(accrualContext);
        } catch (Exception e) {
            logger.error("Error during business day validation for batch: {}", batch.getBatchId(), e);
            return EvaluationOutcome.fail(
                "Business day validation failed with error: " + e.getMessage(),
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        if (businessDayOutcome != null && businessDayOutcome.isFailure()) {
            logger.warn("Check 2 FAILED: Business day validation failed for batch: {} - {}",
                batch.getBatchId(), businessDayOutcome);
            return businessDayOutcome;
        }
        logger.info("Check 2 PASSED: Business day validation succeeded for batch: {}", batch.getBatchId());

        // Check 3: No active batch for date
        logger.info("Check 3: Validating no active batch for date for batch: {}", batch.getBatchId());
        EvaluationOutcome noActiveBatchOutcome;
        try {
            noActiveBatchOutcome = noActiveBatchForDateCriterion.validateNoActiveBatch(context);
        } catch (Exception e) {
            logger.error("Error during no active batch validation for batch: {}", batch.getBatchId(), e);
            return EvaluationOutcome.fail(
                "No active batch validation failed with error: " + e.getMessage(),
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        if (noActiveBatchOutcome != null && noActiveBatchOutcome.isFailure()) {
            logger.warn("Check 3 FAILED: No active batch validation failed for batch: {} - {}",
                batch.getBatchId(), noActiveBatchOutcome);
            return noActiveBatchOutcome;
        }
        logger.info("Check 3 PASSED: No active batch validation succeeded for batch: {}", batch.getBatchId());

        // Check 4: User has permission
        logger.info("Check 4: Validating user permission for batch: {} with initiatedBy: {}",
            batch.getBatchId(), batch.getInitiatedBy());
        EvaluationOutcome userPermissionOutcome;
        try {
            userPermissionOutcome = userHasPermissionCriterion.validateUserPermission(context);
        } catch (Exception e) {
            logger.error("Error during user permission validation for batch: {}", batch.getBatchId(), e);
            return EvaluationOutcome.fail(
                "User permission validation failed with error: " + e.getMessage(),
                StandardEvalReasonCategories.STRUCTURAL_FAILURE
            );
        }

        if (userPermissionOutcome != null && userPermissionOutcome.isFailure()) {
            logger.warn("Check 4 FAILED: User permission validation failed for batch: {} - {}",
                batch.getBatchId(), userPermissionOutcome);
            return userPermissionOutcome;
        }
        logger.info("Check 4 PASSED: User permission validation succeeded for batch: {}", batch.getBatchId());

        logger.info("ALL CHECKS PASSED: All EOD batch validations passed for batch: {}", batch.getBatchId());
        return EvaluationOutcome.success();
    }

    /**
     * Creates an Accrual-like context from the batch context for business day validation.
     * IsBusinessDayCriterion expects an Accrual entity, so we create a minimal Accrual
     * with just the asOfDate field populated.
     */
    private CriterionSerializer.CriterionEntityEvaluationContext<Accrual> createAccrualContextFromBatch(
            CriterionSerializer.CriterionEntityEvaluationContext<EODAccrualBatch> batchContext) {

        EODAccrualBatch batch = batchContext.entityWithMetadata().entity();

        // Create a minimal Accrual entity with just the asOfDate
        Accrual tempAccrual = new Accrual();
        tempAccrual.setAsOfDate(batch.getAsOfDate());
        tempAccrual.setAccrualId("temp-for-validation");

        // Create metadata wrapper
        EntityWithMetadata<Accrual> accrualWithMetadata = new EntityWithMetadata<>(
            tempAccrual,
            batchContext.entityWithMetadata().metadata()
        );

        return new CriterionSerializer.CriterionEntityEvaluationContext<>(
            batchContext.request(),
            accrualWithMetadata
        );
    }
}

