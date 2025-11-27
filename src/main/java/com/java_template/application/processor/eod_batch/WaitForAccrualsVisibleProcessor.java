package com.java_template.application.processor.eod_batch;

import com.java_template.application.entity.accrual.version_1.Accrual;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Processor that waits for spawned accruals to become visible in the database.
 *
 * <p>This processor addresses a transaction visibility issue where accruals created by
 * SpawnAccrualsForEligibleLoansProcessor (running in ASYNC_NEW_TX mode) are not immediately
 * visible to subsequent queries. This processor polls the database until the expected number
 * of accruals are visible, ensuring the workflow can proceed safely.</p>
 *
 * <p>The processor:</p>
 * <ul>
 *   <li>Reads the expected accrual count from batch.metrics.accrualsCreated</li>
 *   <li>Queries for accruals with matching runId</li>
 *   <li>If all expected accruals are visible, returns success</li>
 *   <li>If not all accruals are visible yet, returns failure (workflow will retry)</li>
 * </ul>
 *
 * <p>Execution Mode: SYNC (to ensure it runs in the same transaction context)</p>
 * <p>Calculation Nodes Tags: cyoda_application</p>
 */
@Component
public class WaitForAccrualsVisibleProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WaitForAccrualsVisibleProcessor.class);
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public WaitForAccrualsVisibleProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WaitForAccrualsVisible for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(EODAccrualBatch.class)
            .validate(this::isValidEntityWithMetadata, "Invalid batch entity")
            .map(this::waitForAccrualsLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opsSpec) {
        return "WaitForAccrualsVisible".equalsIgnoreCase(opsSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<EODAccrualBatch> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("EntityWithMetadata or entity is null");
            return false;
        }
        EODAccrualBatch batch = entityWithMetadata.entity();
        if (batch.getBatchId() == null) {
            logger.error("Batch ID is null");
            return false;
        }
        return true;
    }

    private EntityWithMetadata<EODAccrualBatch> waitForAccrualsLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<EODAccrualBatch> context) {

        EntityWithMetadata<EODAccrualBatch> entityWithMetadata = context.entityResponse();
        EODAccrualBatch batch = entityWithMetadata.entity();
        UUID batchId = batch.getBatchId();

        // Get expected accrual count from batch metrics
        Integer expectedAccruals = batch.getMetrics() != null ? batch.getMetrics().getAccrualsCreated() : null;

        if (expectedAccruals == null || expectedAccruals == 0) {
            logger.debug("Batch {} has no accruals to wait for (expected: {})", batchId, expectedAccruals);
            return entityWithMetadata;
        }

        logger.debug("Waiting for {} accruals to become visible for batch {}", expectedAccruals, batchId);

        // Query for accruals with matching runId
        ModelSpec accrualModelSpec = new ModelSpec()
            .withName(Accrual.ENTITY_NAME)
            .withVersion(Accrual.ENTITY_VERSION);

        List<EntityWithMetadata<Accrual>> allAccrualsWithMetadata =
            entityService.findAll(accrualModelSpec, Accrual.class);

        List<EntityWithMetadata<Accrual>> batchAccruals = allAccrualsWithMetadata.stream()
            .filter(a -> {
                Accrual accrual = a.entity();
                return accrual != null && batchId.toString().equals(accrual.getRunId());
            })
            .toList();

        int foundAccruals = batchAccruals.size();

        logger.debug("Found {}/{} accruals for batch {} (searched {} total accruals)",
            foundAccruals, expectedAccruals, batchId, allAccrualsWithMetadata.size());

        if (foundAccruals >= expectedAccruals) {
            logger.info("All {} accruals are now visible for batch {}", expectedAccruals, batchId);
            return entityWithMetadata;
        } else {
            // Not all accruals are visible yet - throw exception so workflow will retry
            String message = String.format(
                "Waiting for accruals to become visible: found %d/%d for batch %s",
                foundAccruals, expectedAccruals, batchId
            );
            logger.debug(message);
            throw new IllegalStateException(message);
        }
    }
}

