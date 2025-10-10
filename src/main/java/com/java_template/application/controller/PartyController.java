package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.controller.support.EntityCrudOperations;
import com.java_template.application.controller.support.EntityCrudOperations.FieldFilter;
import com.java_template.application.entity.party.version_1.Party;
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
 * ABOUTME: REST controller for Party entity operations, providing CRUD endpoints
 * for managing legal entities (borrowers, lenders, agents) in the loan system.
 */
@RestController
@RequestMapping("/ui/parties")
@CrossOrigin(origins = "*")
public class PartyController {

    private static final Logger logger = LoggerFactory.getLogger(PartyController.class);
    private final EntityCrudOperations<Party> crudOps;

    public PartyController(EntityService entityService, ObjectMapper objectMapper) {
        this.crudOps = new EntityCrudOperations<>(
                entityService,
                objectMapper,
                logger,
                Party.ENTITY_NAME,
                Party.ENTITY_VERSION,
                Party.class,
                "partyId"
        );
    }

    /**
     * Create a new party
     * POST /ui/parties
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Party>> createParty(@RequestBody Party party) {
        return crudOps.create(party, Party::getPartyId);
    }

    /**
     * Get party by technical UUID
     * GET /ui/parties/{id}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getById(id, pointInTime);
    }

    /**
     * Get party by business identifier
     * GET /ui/parties/business/{partyId}?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/business/{partyId}")
    public ResponseEntity<EntityWithMetadata<Party>> getPartyByBusinessId(
            @PathVariable String partyId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getByBusinessId(partyId, pointInTime);
    }

    /**
     * Get party change history metadata
     * GET /ui/parties/{id}/changes?pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getPartyChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.getChangesMetadata(id, pointInTime);
    }

    /**
     * Update party with optional workflow transition
     * PUT /ui/parties/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Party>> updateParty(
            @PathVariable UUID id,
            @RequestBody Party party,
            @RequestParam(required = false) String transition) {
        return crudOps.update(id, party, transition);
    }

    /**
     * List all parties with pagination and optional filtering
     * GET /ui/parties?page=0&size=20&status=ACTIVE&jurisdiction=GB&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Party>>> listParties(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) OffsetDateTime pointInTime) {

        List<FieldFilter> filters = new ArrayList<>();
        if (jurisdiction != null && !jurisdiction.trim().isEmpty()) {
            filters.add(FieldFilter.equals("jurisdiction", jurisdiction));
        }

        return crudOps.list(pageable, filters, status, pointInTime);
    }

    /**
     * Search parties by name
     * GET /ui/parties/search?name=searchTerm&pointInTime=2025-10-03T10:15:30Z
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Party>>> searchPartiesByName(
            @RequestParam String name,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        return crudOps.search("legalName", name, pointInTime);
    }

    /**
     * Deactivate party
     * POST /ui/parties/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<EntityWithMetadata<Party>> deactivateParty(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "deactivate_party");
    }

    /**
     * Reactivate party
     * POST /ui/parties/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Party>> reactivateParty(@PathVariable UUID id) {
        return crudOps.executeTransition(id, "reactivate_party");
    }

    /**
     * Delete party by technical UUID
     * DELETE /ui/parties/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParty(@PathVariable UUID id) {
        return crudOps.deleteById(id);
    }

    /**
     * Delete party by business identifier
     * DELETE /ui/parties/business/{partyId}
     */
    @DeleteMapping("/business/{partyId}")
    public ResponseEntity<Void> deletePartyByBusinessId(@PathVariable String partyId) {
        return crudOps.deleteByBusinessId(partyId);
    }

    /**
     * Delete all parties (DANGEROUS - use with caution)
     * DELETE /ui/parties
     */
    @DeleteMapping
    public ResponseEntity<String> deleteAllParties() {
        return crudOps.deleteAll();
    }
}
