package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.accrual.version_1.Accrual;
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
 * ABOUTME: REST controller for Accrual entity operations, providing CRUD endpoints
 * for managing daily interest accruals on loans throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/accruals")
@CrossOrigin(origins = "*")
public class AccrualController {

    private static final Logger logger = LoggerFactory.getLogger(AccrualController.class);
    private final EntityCrudOperations<Accrual> crudOps;

    public AccrualController(EntityService entityService, ObjectMapper objectMapper) {
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                Accrual.ENTITY_NAME,
                Accrual.ENTITY_VERSION,
                Accrual.class,
                "accrualId"  // Accruals have business ID but don't check duplicates on create
        );
    }

    /**
     * Create a new accrual
     * POST /ui/accruals
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Accrual>> createAccrual(@RequestBody Accrual accrual) {
        return crudOps.create(accrual, Accrual::getAccrualId);
    }

    /**
     * Create multiple accruals in batch
     * POST /ui/accruals/batch?transactionWindow=100&transactionTimeoutMs=30000
     */
    @PostMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<Accrual>>> createAccruals(
            @RequestBody List<Accrual> accruals,
            @RequestParam(required = false) Integer transactionWindow,
            @RequestParam(required = false) Long transactionTimeoutMs) {
        return crudOps.createAll(accruals, transactionWindow, transactionTimeoutMs);
    }

    /**
     * Get accrual by technical UUID
     * GET /ui/accruals/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Accrual>> getAccrualById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get accrual by business identifier
     * GET /ui/accruals/business/{accrualId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{accrualId}")
    public ResponseEntity<EntityWithMetadata<Accrual>> getAccrualByBusinessId(
            @PathVariable String accrualId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(accrualId, pointInTime);
    }

    /**
     * Get accrual change history metadata
     * GET /ui/accruals/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getAccrualChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Update accrual with optional workflow transition
     * PUT /ui/accruals/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Accrual>> updateAccrual(
            @PathVariable UUID id,
            @RequestBody Accrual accrual,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, accrual, transition);
    }

    /**
     * Update multiple accruals in batch
     * PUT /ui/accruals/batch?transition=TRANSITION_NAME&transactionWindow=100&transactionTimeoutMs=30000
     */
    @PutMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<Accrual>>> updateAccruals(
            @RequestBody List<Accrual> accruals,
            @RequestParam(required = false) String transition,
            @RequestParam(required = false) Integer transactionWindow,
            @RequestParam(required = false) Long transactionTimeoutMs) {
        return crudOps.updateAll(accruals, transition, transactionWindow, transactionTimeoutMs);
    }

    /**
     * List all accruals with pagination and optional filtering
     * GET /ui/accruals?page=0&size=20&state=POSTED&loanId=LOAN-123&asOfDate=2025-10-07&runId=RUN-001&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Accrual>>> listAccruals(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) LocalDate asOfDate,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (loanId != null && !loanId.trim().isEmpty()) {
            filters.add(FieldFilter.equals("loanId", loanId));
        }
        if (asOfDate != null) {
            filters.add(FieldFilter.equals("asOfDate", asOfDate.toString()));
        }
        if (runId != null && !runId.trim().isEmpty()) {
            filters.add(FieldFilter.equals("runId", runId));
        }

        return crudOps.list(pageable, filters, state, pointInTime);
    }

    /**
     * Delete accrual by technical UUID
     * DELETE /ui/accruals/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccrual(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete accrual by business identifier
     * DELETE /ui/accruals/business/{accrualId}
     */
    @DeleteMapping("/business/{accrualId}")
    public ResponseEntity<Void> deleteAccrualByBusinessId(@PathVariable String accrualId) {
        return crudOps.deleteByBusinessId(accrualId);
    }

    /**
     * Delete all accruals (DANGEROUS - use with caution)
     * DELETE /ui/accruals
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllAccruals() {
        return crudOps.deleteAll();
    }
}

