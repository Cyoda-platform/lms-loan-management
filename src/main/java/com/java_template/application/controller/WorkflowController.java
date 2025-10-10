package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.exception.WorkflowExportException;
import com.java_template.common.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * ABOUTME: REST controller for workflow management operations, providing endpoints
 * to export workflow configurations from the Cyoda platform.
 *
 * <p>This controller exposes workflow-related operations that interact with Cyoda's
 * workflow management API. It allows the UI to retrieve workflow definitions for
 * specific entity models.</p>
 *
 * <p><strong>Endpoints:</strong></p>
 * <ul>
 *   <li>GET /ui/workflows/{entityName} - Export workflows for an entity model</li>
 * </ul>
 *
 */
@RestController
@RequestMapping("/ui/workflows")
@CrossOrigin(origins = "*")
public class WorkflowController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);
    private static final Integer DEFAULT_MODEL_VERSION = 1;

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Export workflows for a specific entity model.
     *
     * @param entityName   the name of the entity model
     * @param modelVersion the version of the entity model (default: 1)
     * @return ResponseEntity containing workflow JSON
     * @throws WorkflowExportException if export fails
     */
    @GetMapping("/{entityName}")
    public ResponseEntity<JsonNode> exportWorkflows(
            @PathVariable String entityName,
            @RequestParam(required = false, defaultValue = "1") Integer modelVersion) {

        logger.info("Received request to export workflows for entity: {} (version: {})",
                entityName, modelVersion);

        // Use default version if not provided
        Integer version = modelVersion != null ? modelVersion : DEFAULT_MODEL_VERSION;

        // Call service to export workflows (exceptions handled by GlobalExceptionHandler)
        JsonNode workflowJson = workflowService.exportWorkflows(entityName, version);

        logger.info("Successfully exported workflows for entity: {} (version: {})",
                entityName, version);

        return ResponseEntity.ok(workflowJson);
    }

}

