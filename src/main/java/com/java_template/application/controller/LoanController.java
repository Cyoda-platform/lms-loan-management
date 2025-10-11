package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.loan.version_1.Loan;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
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
 * ABOUTME: REST controller for Loan entity operations, providing CRUD endpoints
 * and business actions for managing commercial loans throughout their lifecycle.
 */
@RestController
@RequestMapping("/ui/loans")
@CrossOrigin(origins = "*")
public class LoanController {

    private static final Logger logger = LoggerFactory.getLogger(LoanController.class);
    private final EntityCrudOperations<Loan> crudOps;

    public LoanController(EntityService entityService, ObjectMapper objectMapper) {
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                Loan.ENTITY_NAME,
                Loan.ENTITY_VERSION,
                Loan.class,
                "loanId"
        );
    }

    /**
     * Create a new loan
     * POST /ui/loans
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Loan>> createLoan(@Valid @RequestBody Loan loan) {
        return crudOps.create(loan, Loan::getLoanId, l -> {
            // Calculate maturity date if not provided
            if (l.getMaturityDate() == null && l.getFundingDate() != null && l.getTermMonths() != null) {
                l.setMaturityDate(l.getFundingDate().plusMonths(l.getTermMonths()));
            }
        });
    }

    /**
     * Create multiple loans in batch
     * POST /ui/loans/batch?transactionWindow=100&transactionTimeoutMs=30000
     */
    @PostMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> createLoans(
            @Valid @RequestBody List<Loan> loans,
            @RequestParam(required = false) Integer transactionWindow,
            @RequestParam(required = false) Long transactionTimeoutMs) {
        return crudOps.createAll(loans, transactionWindow, transactionTimeoutMs);
    }

    /**
     * Get loan by technical UUID
     * GET /ui/loans/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get loan by business identifier
     * GET /ui/loans/business/{loanId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{loanId}")
    public ResponseEntity<EntityWithMetadata<Loan>> getLoanByBusinessId(
            @PathVariable String loanId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(loanId, pointInTime);
    }

    /**
     * Get loan change history metadata
     * GET /ui/loans/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getLoanChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Update loan with optional workflow transition
     * PUT /ui/loans/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Loan>> updateLoan(
            @PathVariable UUID id,
            @Valid @RequestBody Loan loan,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, loan, transition);
    }

    /**
     * Update multiple loans in batch
     * PUT /ui/loans/batch?transition=TRANSITION_NAME&transactionWindow=100&transactionTimeoutMs=30000
     */
    @PutMapping("/batch")
    public ResponseEntity<List<EntityWithMetadata<Loan>>> updateLoans(
            @Valid @RequestBody List<Loan> loans,
            @RequestParam(required = false) String transition,
            @RequestParam(required = false) Integer transactionWindow,
            @RequestParam(required = false) Long transactionTimeoutMs) {
        return crudOps.updateAll(loans, transition, transactionWindow, transactionTimeoutMs);
    }

    /**
     * List all loans with pagination and optional filtering
     * GET /ui/loans?page=0&size=20&state=ACTIVE&partyId=PARTY123&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Loan>>> listLoans(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (partyId != null && !partyId.trim().isEmpty()) {
            filters.add(FieldFilter.equals("partyId", partyId));
        }

        return crudOps.list(pageable, filters, state, pointInTime);
    }

    /**
     * Submit loan for approval
     * POST /ui/loans/{id}/submit-for-approval
     */
    @PostMapping("/{id}/submit-for-approval")
    public ResponseEntity<EntityWithMetadata<Loan>> submitForApproval(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "submit_for_approval");
    }

    /**
     * Approve loan
     * POST /ui/loans/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<EntityWithMetadata<Loan>> approveLoan(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "approve_loan");
    }

    /**
     * Reject loan
     * POST /ui/loans/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<EntityWithMetadata<Loan>> rejectLoan(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "reject_loan");
    }

    /**
     * Fund loan
     * POST /ui/loans/{id}/fund
     */
    @PostMapping("/{id}/fund")
    public ResponseEntity<EntityWithMetadata<Loan>> fundLoan(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "fund_loan");
    }

    /**
     * Generate settlement quote
     * POST /ui/loans/{id}/settlement-quote
     */
    @PostMapping("/{id}/settlement-quote")
    public ResponseEntity<EntityWithMetadata<Loan>> generateSettlementQuote(
            @PathVariable UUID id,
            @RequestParam LocalDate settlementDate) {
        // Note: In a real implementation, you would pass the settlement date to the processor
        return crudOps.executeTransition(id, "generate_settlement_quote");
    }

    /**
     * Delete loan by technical UUID
     * DELETE /ui/loans/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLoan(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete loan by business identifier
     * DELETE /ui/loans/business/{loanId}
     */
    @DeleteMapping("/business/{loanId}")
    public ResponseEntity<Void> deleteLoanByBusinessId(@PathVariable String loanId) {
        return crudOps.deleteByBusinessId(loanId);
    }

    /**
     * Delete all loans (DANGEROUS - use with caution)
     * DELETE /ui/loans
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllLoans() {
        return crudOps.deleteAll();
    }
}
