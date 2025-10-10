package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for GLBatch entity operations, providing CRUD endpoints
 * for managing month-end GL batch processing throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/gl-batches")
@CrossOrigin(origins = "*")
public class GLBatchController {

    private static final Logger logger = LoggerFactory.getLogger(GLBatchController.class);
    private final EntityCrudOperations<GLBatch> crudOps;

    public GLBatchController(EntityService entityService, ObjectMapper objectMapper) {
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                GLBatch.ENTITY_NAME,
                GLBatch.ENTITY_VERSION,
                GLBatch.class,
                null  // GLBatch uses auto-generated IDs
        );
    }

    /**
     * Create a new GL batch
     * POST /ui/gl-batches
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<GLBatch>> createBatch(@RequestBody GLBatch batch) {
        return crudOps.createWithoutDuplicateCheck(batch, null);
    }

    /**
     * Get batch by technical UUID
     * GET /ui/gl-batches/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getBatchById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get batch by business identifier (batchId)
     * GET /ui/gl-batches/business/{batchId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{batchId}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getBatchByBusinessId(
            @PathVariable String batchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(batchId, pointInTime);
    }

    /**
     * Get batch by period
     * GET /ui/gl-batches/period/{period}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/period/{period}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getBatchByPeriod(
            @PathVariable String period,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        List<EntityWithMetadata<GLBatch>> batches = crudOps.search("period", period, pointInTime).getBody();
        if (batches == null || batches.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Return the first (should be only one due to period uniqueness validation)
        return ResponseEntity.ok(batches.get(0));
    }

    /**
     * Get batch change history metadata
     * GET /ui/gl-batches/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getBatchChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Update batch with optional workflow transition
     * PUT /ui/gl-batches/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> updateBatch(
            @PathVariable UUID id,
            @RequestBody GLBatch batch,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, batch, transition);
    }

    /**
     * List all batches with pagination and optional filtering
     * GET /ui/gl-batches?page=0&size=20&state=exported&period=2025-09&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<GLBatch>>> listBatches(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (period != null && !period.trim().isEmpty()) {
            filters.add(FieldFilter.equals("period", period));
        }

        return crudOps.list(pageable, filters, state, pointInTime);
    }

    /**
     * Delete batch by technical UUID
     * DELETE /ui/gl-batches/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete batch by business identifier
     * DELETE /ui/gl-batches/business/{batchId}
     */
    @DeleteMapping("/business/{batchId}")
    public ResponseEntity<Void> deleteBatchByBusinessId(@PathVariable String batchId) {
        return crudOps.deleteByBusinessId(batchId);
    }

    /**
     * Delete all batches (DANGEROUS - use with caution)
     * DELETE /ui/gl-batches
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllBatches() {
        return crudOps.deleteAll();
    }
}

