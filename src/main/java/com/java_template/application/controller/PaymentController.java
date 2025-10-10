package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Payment entity operations, providing endpoints
 * for recording and managing borrower payments throughout the payment lifecycle.
 */
@RestController
@RequestMapping("/ui/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);
    private final EntityCrudOperations<Payment> crudOps;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentController(EntityService entityService, ObjectMapper objectMapper) {
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                Payment.ENTITY_NAME,
                Payment.ENTITY_VERSION,
                Payment.class,
                "paymentId"
        );
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Record a new payment
     * POST /ui/payments
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Payment>> recordPayment(@RequestBody Payment payment) {
        return crudOps.create(payment, Payment::getPaymentId, p -> {
            // Set received date to today if not provided
            if (p.getReceivedDate() == null) {
                p.setReceivedDate(LocalDate.now());
            }
            // Set value date to received date if not provided
            if (p.getValueDate() == null) {
                p.setValueDate(p.getReceivedDate());
            }
        });
    }

    /**
     * Get payment by technical UUID
     * GET /ui/payments/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get payment by business identifier
     * GET /ui/payments/business/{paymentId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{paymentId}")
    public ResponseEntity<EntityWithMetadata<Payment>> getPaymentByBusinessId(
            @PathVariable String paymentId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(paymentId, pointInTime);
    }

    /**
     * Get payment change history metadata
     * GET /ui/payments/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getPaymentChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Get payments for a specific loan
     * GET /ui/payments/loan/{loanId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/loan/{loanId}")
    public ResponseEntity<List<EntityWithMetadata<Payment>>> getPaymentsForLoan(
            @PathVariable String loanId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.search("loanId", loanId, pointInTime);
    }

    /**
     * Update payment with optional workflow transition
     * PUT /ui/payments/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Payment>> updatePayment(
            @PathVariable UUID id,
            @RequestBody Payment payment,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, payment, transition);
    }

    /**
     * List all payments with pagination and optional filtering
     * GET /ui/payments?page=0&size=20&loanId=LOAN123&status=POSTED&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Payment>>> listPayments(
            Pageable pageable,
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (loanId != null && !loanId.trim().isEmpty()) {
            filters.add(FieldFilter.equals("loanId", loanId));
        }

        return crudOps.list(pageable, filters, status, pointInTime);
    }

    /**
     * Manually match payment to loan
     * POST /ui/payments/{id}/match
     */
    @PostMapping("/{id}/match")
    public ResponseEntity<EntityWithMetadata<Payment>> matchPayment(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "manual_match");
    }

    /**
     * Return unmatched payment
     * POST /ui/payments/{id}/return
     */
    @PostMapping("/{id}/return")
    public ResponseEntity<EntityWithMetadata<Payment>> returnPayment(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "return_payment");
    }

    /**
     * Delete payment by technical UUID
     * DELETE /ui/payments/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayment(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete payment by business identifier
     * DELETE /ui/payments/business/{paymentId}
     */
    @DeleteMapping("/business/{paymentId}")
    public ResponseEntity<Void> deletePaymentByBusinessId(@PathVariable String paymentId) {
        return crudOps.deleteByBusinessId(paymentId);
    }

    /**
     * Delete all payments (DANGEROUS - use with caution)
     * DELETE /ui/payments
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllPayments() {
        return crudOps.deleteAll();
    }
}
