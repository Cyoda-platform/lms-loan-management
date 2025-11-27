package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.dto.CreateBatchWithTransitionRequest;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.accrual.version_1.BatchMetrics;
import com.java_template.application.entity.accrual.version_1.BatchMode;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for EODAccrualBatch entity operations, providing CRUD endpoints
 * for managing end-of-day accrual batch runs throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/eod-batches")
@CrossOrigin(origins = "*")
public class EODAccrualBatchController {

    private static final Logger logger = LoggerFactory.getLogger(EODAccrualBatchController.class);
    private final EntityCrudOperations<EODAccrualBatch> crudOps;
    private final EntityService entityService;

    public EODAccrualBatchController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                EODAccrualBatch.ENTITY_NAME,
                EODAccrualBatch.ENTITY_VERSION,
                EODAccrualBatch.class,
                "batchId"  // EODAccrualBatch has business ID but doesn't check duplicates on create
        );
    }

    /**
     * Create a new EOD accrual batch (simple format - backward compatible).
     * POST /ui/eod-batches
     *
     * <p>Accepts a direct EODAccrualBatch JSON at the root level.</p>
     * <p>The batch will be created in the initial state (REQUESTED).</p>
     *
     * <p>Example request body:</p>
     * <pre>
     * {
     *   "asOfDate": "2025-10-21",
     *   "mode": "TODAY",
     *   "initiatedBy": "user123",
     *   "metrics": {}
     * }
     * </pre>
     *
     * <p>To create a batch and immediately trigger a workflow transition,
     * use the /ui/eod-batches/with-transition endpoint instead.</p>
     *
     * @param batch The batch entity to create
     * @return ResponseEntity with created batch and metadata
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> createBatch(@RequestBody EODAccrualBatch batch) {
        // Initialize batchId if not provided
        if (batch.getBatchId() == null) {
            batch.setBatchId(java.util.UUID.randomUUID());
            logger.debug("Generated batchId: {}", batch.getBatchId());
        }

        // Initialize initiatedBy if not provided (required field)
        if (batch.getInitiatedBy() == null || batch.getInitiatedBy().trim().isEmpty()) {
            batch.setInitiatedBy("system");
            logger.debug("Set default initiatedBy: system for batch {}", batch.getBatchId());
        }

        // Initialize metrics if not provided (required field)
        if (batch.getMetrics() == null) {
            batch.setMetrics(new BatchMetrics());
            logger.debug("Initialized empty metrics for batch {}", batch.getBatchId());
        }

        return crudOps.createWithoutDuplicateCheck(batch, null);
    }

    /**
     * Create a new EOD accrual batch with immediate workflow transition.
     * POST /ui/eod-batches/with-transition
     *
     * <p>This endpoint accepts a wrapped request structure that includes:</p>
     * <ul>
     *   <li>The batch entity to create</li>
     *   <li>An optional workflow transition to execute immediately after creation</li>
     *   <li>Optional engine options (not yet implemented in workflow engine)</li>
     * </ul>
     *
     * <p>Example request body:</p>
     * <pre>
     * {
     *   "batch": {
     *     "asOfDate": "2025-10-21",
     *     "mode": "TODAY",
     *     "initiatedBy": "user123",
     *     "metrics": {}
     *   },
     *   "transitionRequest": {
     *     "name": "START",
     *     "comment": "Starting daily accrual run"
     *   },
     *   "engineOptions": {
     *     "simulate": false,
     *     "maxSteps": 50
     *   }
     * }
     * </pre>
     *
     * <p>If the transition fails, the batch will still be created but will remain
     * in the initial state (REQUESTED). The response will contain the created batch.</p>
     *
     * @param request The wrapped request containing batch, transition, and options
     * @return ResponseEntity with created (and possibly transitioned) batch and metadata
     */
    @PostMapping("/with-transition")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> createBatchWithTransition(
            @RequestBody CreateBatchWithTransitionRequest request) {

        // Extract the batch entity from the request
        EODAccrualBatch batch = request.getBatch();

        if (batch == null) {
            logger.error("Batch entity is null in request");
            return ResponseEntity.badRequest().build();
        }

        // Initialize batchId if not provided
        if (batch.getBatchId() == null) {
            batch.setBatchId(java.util.UUID.randomUUID());
            logger.debug("Generated batchId: {}", batch.getBatchId());
        }

        // Initialize initiatedBy if not provided (required field)
        if (batch.getInitiatedBy() == null || batch.getInitiatedBy().trim().isEmpty()) {
            batch.setInitiatedBy("system");
            logger.debug("Set default initiatedBy: system for batch {}", batch.getBatchId());
        }

        // Initialize metrics if not provided (required field)
        if (batch.getMetrics() == null) {
            batch.setMetrics(new BatchMetrics());
            logger.debug("Initialized empty metrics for batch {}", batch.getBatchId());
        }

        // Create the batch entity
        EntityWithMetadata<EODAccrualBatch> createdBatch = crudOps.createWithoutDuplicateCheck(batch, null).getBody();

        if (createdBatch == null) {
            logger.error("Failed to create batch");
            return ResponseEntity.internalServerError().build();
        }

        // If a transition is requested, execute it
        if (request.getTransitionRequest() != null && request.getTransitionRequest().getName() != null) {
            String transitionName = request.getTransitionRequest().getName();
            logger.info("Executing transition '{}' on newly created batch {}",
                    transitionName, createdBatch.metadata().getId());

            try {
                // Execute the transition using the entity service
                EntityWithMetadata<EODAccrualBatch> transitionedBatch = entityService.update(
                        createdBatch.metadata().getId(),
                        createdBatch.entity(),
                        transitionName
                );

                logger.info("Successfully transitioned batch {} to state: {}",
                        transitionedBatch.metadata().getId(),
                        transitionedBatch.metadata().getState());

                return ResponseEntity.ok(transitionedBatch);
            } catch (Exception e) {
                logger.error("Failed to execute transition '{}' on batch {}: {}",
                        transitionName, createdBatch.metadata().getId(), e.getMessage(), e);
                // Return the created batch even if transition fails
                // The batch exists but is in the initial state
                return ResponseEntity.ok(createdBatch);
            }
        }

        // No transition requested, return the created batch
        return ResponseEntity.ok(createdBatch);
    }

    /**
     * Create multiple EOD accrual batches
     * POST /ui/eod-batches/batch?transactionWindow=100&transactionTimeoutMs=30000
     */
    @PostMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<EODAccrualBatch>>> createBatches(
            @RequestBody List<EODAccrualBatch> batches,
            @RequestParam(required = false) Integer transactionWindow,
            @RequestParam(required = false) Long transactionTimeoutMs) {
        return crudOps.createAll(batches, transactionWindow, transactionTimeoutMs);
    }

    /**
     * Get batch by technical UUID
     * GET /ui/eod-batches/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> getBatchById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get batch by business identifier
     * GET /ui/eod-batches/business/{batchId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{batchId}")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> getBatchByBusinessId(
            @PathVariable UUID batchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(batchId.toString(), pointInTime);
    }

    /**
     * Get batch change history metadata
     * GET /ui/eod-batches/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getBatchChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Update batch with optional workflow transition
     * PUT /ui/eod-batches/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> updateBatch(
            @PathVariable UUID id,
            @RequestBody EODAccrualBatch batch,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, batch, transition);
    }

    /**
     * Update multiple batches in batch
     * PUT /ui/eod-batches/batch?transition=TRANSITION_NAME&transactionWindow=100&transactionTimeoutMs=30000
     */
    @PutMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<EODAccrualBatch>>> updateBatches(
            @RequestBody List<EODAccrualBatch> batches,
            @RequestParam(required = false) String transition,
            @RequestParam(required = false) Integer transactionWindow,
            @RequestParam(required = false) Long transactionTimeoutMs) {
        return crudOps.updateAll(batches, transition, transactionWindow, transactionTimeoutMs);
    }

    /**
     * List all batches with pagination and optional filtering
     * GET /ui/eod-batches?page=0&size=20&state=COMPLETED&asOfDate=2025-10-07&mode=TODAY&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<EODAccrualBatch>>> listBatches(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) BatchMode mode,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (asOfDate != null) {
            filters.add(FieldFilter.equals("asOfDate", asOfDate.toString()));
        }
        if (mode != null) {
            filters.add(FieldFilter.equals("mode", mode.name()));
        }

        return crudOps.list(pageable, filters, state, pointInTime);
    }

    /**
     * Delete batch by technical UUID
     * DELETE /ui/eod-batches/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete batch by business identifier
     * DELETE /ui/eod-batches/business/{batchId}
     */
    @DeleteMapping("/business/{batchId}")
    public ResponseEntity<Void> deleteBatchByBusinessId(@PathVariable UUID batchId) {
        return crudOps.deleteByBusinessId(batchId.toString());
    }

    /**
     * Delete all batches (DANGEROUS - use with caution)
     * DELETE /ui/eod-batches
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllBatches() {
        return crudOps.deleteAll();
    }
}

