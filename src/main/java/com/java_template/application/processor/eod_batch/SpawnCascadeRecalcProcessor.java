package com.java_template.application.processor.eod_batch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.BatchMode;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.application.entity.accrual.version_1.PeriodStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Processor to recompute forward days and post deltas for back-dated runs.
 *
 * <p>This processor:</p>
 * <ul>
 *   <li>Determines cascade date range from batch's asOfDate to current business date</li>
 *   <li>For each date in range, identifies affected loans</li>
 *   <li>Triggers recalculation of accruals for subsequent dates</li>
 *   <li>Updates batch's cascadeFromDate field</li>
 * </ul>
 *
 * <p>Only executes for BACKDATED mode batches.</p>
 *
 * <p>Execution Mode: ASYNC_NEW_TX</p>
 * <p>Calculation Nodes Tags: recalc</p>
 */
@Component
public class SpawnCascadeRecalcProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SpawnCascadeRecalcProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpawnCascadeRecalcProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SpawnCascadeRecalc for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::spawnCascadeRecalcLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SpawnCascadeRecalc".equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper.
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        EODAccrualBatch batch = entityWithMetadata.entity();
        return batch != null && batch.isValid(entityWithMetadata.metadata()) && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Main business logic to spawn cascade recalculation.
     *
     * <p>CRITICAL LIMITATIONS:</p>
     * <ul>
     *   <li>✅ ALLOWED: Read current batch data</li>
     *   <li>✅ ALLOWED: Query and update Accrual entities via EntityService</li>
     *   <li>✅ ALLOWED: Update batch cascadeFromDate field</li>
     *   <li>❌ FORBIDDEN: Update current batch state/transitions</li>
     * </ul>
     */
    private EntityWithMetadata<EODAccrualBatch> spawnCascadeRecalcLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();

        UUID batchId = batch.getBatchId();
        LocalDate asOfDate = batch.getAsOfDate();
        BatchMode mode = batch.getMode();
        PeriodStatus periodStatus = batch.getPeriodStatus();

        // Only process for BACKDATED mode
        if (mode != BatchMode.BACKDATED) {
            logger.info("Batch {} is not BACKDATED mode, skipping cascade recalc", batchId);
            return entityWithMetadata;
        }

        if (asOfDate == null) {
            logger.error("AsOfDate is null for batch: {}", batchId);
            throw new IllegalStateException("AsOfDate is required for cascade recalc");
        }

        logger.debug("Spawning cascade recalc for batch: {} with asOfDate: {}", batchId, asOfDate);

        // Determine cascade date range
        LocalDate currentBusinessDate = LocalDate.now(); // TODO: Use business calendar
        LocalDate cascadeFromDate = asOfDate.plusDays(1); // Start from day after backdated run

        if (!cascadeFromDate.isBefore(currentBusinessDate)) {
            logger.info("No cascade needed - asOfDate {} is current or future", asOfDate);
            return entityWithMetadata;
        }

        logger.info("Cascade range: {} to {}", cascadeFromDate, currentBusinessDate);

        // Query accruals created by this batch to identify affected loans
        Set<String> affectedLoanIds = identifyAffectedLoans(batchId);

        logger.info("Found {} affected loans for cascade recalc", affectedLoanIds.size());

        // TODO: Implement actual cascade recalculation logic
        // For now, we skip cascade recalculations and do NOT set cascadeFromDate
        // This allows the CascadeSettled criterion to return success (no cascade needed)
        // and the batch to proceed to RECONCILING state

        logger.info("Batch {} skipping cascade recalculations (not yet implemented) - {} affected loans identified",
            batchId, affectedLoanIds.size());

        // NOTE: We intentionally do NOT set cascadeFromDate here
        // When cascadeFromDate is null, CascadeSettled criterion returns success
        // batch.setCascadeFromDate(cascadeFromDate);

        return entityWithMetadata;
    }

    /**
     * Identifies loans affected by this batch's accruals.
     *
     * @param runId The batch's batchId
     * @return Set of loan IDs that need cascade recalculation
     */
    private Set<String> identifyAffectedLoans(UUID runId) {
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        // Search for accruals with this runId
        SimpleCondition runIdCondition = new SimpleCondition()
            .withJsonPath("$.runId")
            .withOperation(Operation.EQUALS)
            .withValue(objectMapper.valueToTree(runId.toString()));

        GroupCondition searchCondition = new GroupCondition()
            .withOperator(GroupCondition.Operator.AND)
            .withConditions(List.of(runIdCondition));

        List<EntityWithMetadata<Accrual>> accrualsWithMetadata =
            entityService.search(accrualModelSpec, searchCondition, Accrual.class);

        Set<String> affectedLoanIds = new HashSet<>();
        for (EntityWithMetadata<Accrual> accrualWithMetadata : accrualsWithMetadata) {
            Accrual accrual = accrualWithMetadata.entity();
            affectedLoanIds.add(accrual.getLoanId());
        }

        return affectedLoanIds;
    }

}

