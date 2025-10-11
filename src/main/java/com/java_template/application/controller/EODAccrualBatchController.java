package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
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

    public EODAccrualBatchController(EntityService entityService, ObjectMapper objectMapper) {
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
     * Create a new EOD accrual batch
     * POST /ui/eod-batches
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<EODAccrualBatch>> createBatch(@RequestBody EODAccrualBatch batch) {
        return crudOps.createWithoutDuplicateCheck(batch, null);
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

