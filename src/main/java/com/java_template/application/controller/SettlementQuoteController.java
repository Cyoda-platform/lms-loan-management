package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.settlement_quote.version_1.SettlementQuote;
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
 * ABOUTME: REST controller for SettlementQuote entity operations, providing endpoints
 * for managing early settlement quotes throughout their lifecycle.
 *
 * <p>Key endpoints:</p>
 * <ul>
 *   <li>GET /ui/settlement-quotes - List all quotes with pagination and filtering</li>
 *   <li>GET /ui/settlement-quotes/{id} - Get quote by technical UUID</li>
 *   <li>GET /ui/settlement-quotes/business/{quoteId} - Get quote by business ID</li>
 *   <li>GET /ui/settlement-quotes/loan/{loanId} - Get all quotes for a specific loan</li>
 *   <li>POST /ui/settlement-quotes/{id}/accept - Accept a quote</li>
 *   <li>POST /ui/settlement-quotes/{id}/regenerate - Regenerate an expired quote</li>
 *   <li>PUT /ui/settlement-quotes/{id} - Update quote with optional transition</li>
 *   <li>DELETE /ui/settlement-quotes/{id} - Delete quote</li>
 * </ul>
 *
 * <p>Note: Settlement quotes are typically created via the Loan controller endpoint:
 * POST /ui/loans/{id}/settlement-quote</p>
 */
@RestController
@RequestMapping("/ui/settlement-quotes")
@CrossOrigin(origins = "*")
public class SettlementQuoteController {

    private static final Logger logger = LoggerFactory.getLogger(SettlementQuoteController.class);
    private final EntityCrudOperations<SettlementQuote> crudOps;

    public SettlementQuoteController(EntityService entityService, ObjectMapper objectMapper) {
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                SettlementQuote.ENTITY_NAME,
                SettlementQuote.ENTITY_VERSION,
                SettlementQuote.class,
                "quoteId"
        );
    }

    /**
     * Get settlement quote by technical UUID
     * GET /ui/settlement-quotes/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> getQuoteById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get settlement quote by business identifier
     * GET /ui/settlement-quotes/business/{quoteId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{quoteId}")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> getQuoteByBusinessId(
            @PathVariable String quoteId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(quoteId, pointInTime);
    }

    /**
     * Get settlement quote change history metadata
     * GET /ui/settlement-quotes/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getQuoteChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Get all settlement quotes for a specific loan
     * GET /ui/settlement-quotes/loan/{loanId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/loan/{loanId}")
    public ResponseEntity<List<EntityWithMetadata<SettlementQuote>>> getQuotesForLoan(
            @PathVariable String loanId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.search("loanId", loanId, pointInTime);
    }

    /**
     * Update settlement quote with optional workflow transition
     * PUT /ui/settlement-quotes/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> updateQuote(
            @PathVariable UUID id,
            @RequestBody SettlementQuote quote,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, quote, transition);
    }

    /**
     * List all settlement quotes with pagination and optional filtering
     * GET /ui/settlement-quotes?page=0&size=20&loanId=LOAN123&state=quoted&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<SettlementQuote>>> listQuotes(
            Pageable pageable,
            @RequestParam(required = false) String loanId,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (loanId != null && !loanId.trim().isEmpty()) {
            filters.add(FieldFilter.equals("loanId", loanId));
        }

        return crudOps.list(pageable, filters, state, pointInTime);
    }

    /**
     * Accept a settlement quote
     * POST /ui/settlement-quotes/{id}/accept
     *
     * <p>Transitions the quote from 'quoted' to 'accepted' state.
     * The AcceptSettlementQuote processor will record acceptance timestamp and user.</p>
     */
    @PostMapping("/{id}/accept")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> acceptQuote(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "accept_quote");
    }

    /**
     * Regenerate an expired settlement quote
     * POST /ui/settlement-quotes/{id}/regenerate
     *
     * <p>Transitions the quote from 'expired' back to 'calculating' state
     * to recalculate the settlement amount with current loan data.</p>
     */
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> regenerateQuote(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "regenerate_quote");
    }

    /**
     * Fix validation error on settlement quote
     * POST /ui/settlement-quotes/{id}/fix
     *
     * <p>Transitions the quote from 'validation_error' back to 'initial' state
     * after clearing the validation error reason.</p>
     */
    @PostMapping("/{id}/fix")
    public ResponseEntity<EntityWithMetadata<SettlementQuote>> fixValidationError(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "FIX");
    }

    /**
     * Delete settlement quote by technical UUID
     * DELETE /ui/settlement-quotes/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuote(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete settlement quote by business identifier
     * DELETE /ui/settlement-quotes/business/{quoteId}
     */
    @DeleteMapping("/business/{quoteId}")
    public ResponseEntity<Void> deleteQuoteByBusinessId(@PathVariable String quoteId) {
        return crudOps.deleteByBusinessId(quoteId);
    }

    /**
     * Delete all settlement quotes (DANGEROUS - use with caution)
     * DELETE /ui/settlement-quotes
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllQuotes() {
        return crudOps.deleteAll();
    }
}

