package com.java_template.application.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.exception.WorkflowExportException;
import com.java_template.common.service.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
     * @param entityName the name of the entity model
     * @param modelVersion the version of the entity model (default: 1)
     * @return ResponseEntity containing workflow JSON or ProblemDetail on error
     */
    @GetMapping("/{entityName}")
    public ResponseEntity<?> exportWorkflows(
            @PathVariable String entityName,
            @RequestParam(required = false, defaultValue = "1") Integer modelVersion) {

        logger.info("Received request to export workflows for entity: {} (version: {})",
            entityName, modelVersion);

        try {
            // Use default version if not provided
            Integer version = modelVersion != null ? modelVersion : DEFAULT_MODEL_VERSION;

            // Call service to export workflows
            JsonNode workflowJson = workflowService.exportWorkflows(entityName, version);

            logger.info("Successfully exported workflows for entity: {} (version: {})",
                entityName, version);

            return ResponseEntity.ok(workflowJson);

        } catch (WorkflowExportException e) {
            // Log the error once at the controller level
            logger.error("Failed to export workflows for entity: {} (version: {}). Error: {}",
                entityName, modelVersion, e.getMessage());

            // Determine HTTP status based on the exception's HTTP status code
            HttpStatus status = determineHttpStatus(e.getHttpStatusCode());

            // Create ProblemDetail with appropriate status and message
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
            problemDetail.setTitle("Workflow Export Failed");
            problemDetail.setProperty("entityName", entityName);
            problemDetail.setProperty("modelVersion", modelVersion);

            if (e.getHttpStatusCode() != null) {
                problemDetail.setProperty("cyodaStatusCode", e.getHttpStatusCode());
            }

            return ResponseEntity.status(status).body(problemDetail);
        }
    }

    /**
     * Determines the appropriate HTTP status code based on the Cyoda API status code.
     *
     * @param cyodaStatusCode the HTTP status code from Cyoda API, or null
     * @return the appropriate HTTP status code for the response
     */
    private HttpStatus determineHttpStatus(Integer cyodaStatusCode) {
        if (cyodaStatusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        if (cyodaStatusCode == 404) {
            return HttpStatus.NOT_FOUND;
        } else if (cyodaStatusCode == 401 || cyodaStatusCode == 403) {
            return HttpStatus.UNAUTHORIZED;
        } else if (cyodaStatusCode >= 400 && cyodaStatusCode < 500) {
            return HttpStatus.BAD_REQUEST;
        } else if (cyodaStatusCode >= 500) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Health check endpoint for workflow controller
     * GET /ui/workflows/health
     * 
     * @return Simple health check response
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Workflow controller is healthy");
    }
}

