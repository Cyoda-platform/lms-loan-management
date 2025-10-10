package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
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
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public GLBatchController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new GL batch
     * POST /ui/gl-batches
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<GLBatch>> createBatch(@RequestBody GLBatch batch) {
        try {
            EntityWithMetadata<GLBatch> response = entityService.create(batch);
            logger.info("GLBatch created with ID: {} for period: {}", response.metadata().getId(), batch.getPeriod());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create GL batch: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch by technical UUID
     * GET /ui/gl-batches/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getBatchById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<GLBatch> response = entityService.getById(id, modelSpec, GLBatch.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve GL batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch by business identifier (batchId)
     * GET /ui/gl-batches/business/{batchId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{batchId}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getBatchByBusinessId(
            @PathVariable String batchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<GLBatch> response = entityService.findByBusinessId(
                    modelSpec, batchId, "batchId", GLBatch.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve GL batch with business ID '%s': %s", batchId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch by period
     * GET /ui/gl-batches/period/{period}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/period/{period}")
    public ResponseEntity<EntityWithMetadata<GLBatch>> getBatchByPeriod(
            @PathVariable String period,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            SimpleCondition periodCondition = new SimpleCondition()
                    .withJsonPath("$.period")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(period));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(periodCondition));

            List<EntityWithMetadata<GLBatch>> batches = entityService.search(
                    modelSpec, groupCondition, GLBatch.class);

            if (batches.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            // Return the first (should be only one due to period uniqueness validation)
            return ResponseEntity.ok(batches.get(0));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve GL batch for period '%s': %s", period, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get batch change history metadata
     * GET /ui/gl-batches/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getBatchChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            List<EntityChangeMeta> changes =
                    entityService.getEntityChangesMetadata(id, pointInTimeDate);
            return ResponseEntity.ok(changes);
        } catch (Exception e) {
            // Check if it's a NOT_FOUND error (entity doesn't exist)
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve change history for GL batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
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
        try {
            EntityWithMetadata<GLBatch> response = entityService.update(id, batch, transition);
            logger.info("GLBatch updated with ID: {} for period: {}", id, batch.getPeriod());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update GL batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
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
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (period != null && !period.trim().isEmpty()) {
                SimpleCondition periodCondition = new SimpleCondition()
                        .withJsonPath("$.period")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(period));
                conditions.add(periodCondition);
            }

            if (conditions.isEmpty() && (state == null || state.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, GLBatch.class, pointInTimeDate));
            } else {
                // For filtered results, get all matching results then manually paginate
                List<EntityWithMetadata<GLBatch>> batches;
                if (conditions.isEmpty()) {
                    batches = entityService.findAll(modelSpec, GLBatch.class, pointInTimeDate);
                } else {
                    GroupCondition groupCondition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    batches = entityService.search(modelSpec, groupCondition, GLBatch.class, pointInTimeDate);
                }

                // Filter by state if provided (state is in metadata, not entity)
                if (state != null && !state.trim().isEmpty()) {
                    batches = batches.stream()
                            .filter(batch -> state.equals(batch.metadata().getState()))
                            .toList();
                }

                // Manually paginate the filtered results
                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), batches.size());
                List<EntityWithMetadata<GLBatch>> pageContent = start < batches.size()
                    ? batches.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<GLBatch>> page = new PageImpl<>(pageContent, pageable, batches.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list GL batches: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete batch by technical UUID
     * DELETE /ui/gl-batches/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBatch(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("GLBatch deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete GL batch with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete batch by business identifier
     * DELETE /ui/gl-batches/business/{batchId}
     */
    @DeleteMapping("/business/{batchId}")
    public ResponseEntity<Void> deleteBatchByBusinessId(@PathVariable String batchId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            boolean deleted = entityService.deleteByBusinessId(modelSpec, batchId, "batchId", GLBatch.class);

            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            logger.info("GLBatch deleted with business ID: {}", batchId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete GL batch with business ID '%s': %s", batchId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete all batches (DANGEROUS - use with caution)
     * DELETE /ui/gl-batches
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllBatches() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(GLBatch.ENTITY_NAME).withVersion(GLBatch.ENTITY_VERSION);
            Integer deletedCount = entityService.deleteAll(modelSpec);
            logger.warn("Deleted all GLBatches - count: {}", deletedCount);
            return ResponseEntity.ok().body(String.format("Deleted %d GL batches", deletedCount));
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete all GL batches: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

