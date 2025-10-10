package com.java_template.application.controller.exception;

import com.java_template.common.exception.WorkflowExportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * ABOUTME: Global exception handler for all REST controllers.
 * 
 * <p>This class provides centralized exception handling across all controllers
 * using Spring's @RestControllerAdvice. It converts exceptions into proper
 * HTTP error responses with ProblemDetail format.</p>
 * 
 * <p><strong>Handled Exceptions:</strong></p>
 * <ul>
 *   <li>WorkflowExportException - Workflow export failures</li>
 *   <li>Exception - Generic fallback for unexpected errors</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles WorkflowExportException thrown by workflow operations.
     * Converts the exception into a proper HTTP error response with ProblemDetail.
     *
     * @param e the workflow export exception
     * @param request the web request
     * @return ResponseEntity containing ProblemDetail
     */
    @ExceptionHandler(WorkflowExportException.class)
    public ResponseEntity<ProblemDetail> handleWorkflowExportException(
            WorkflowExportException e,
            WebRequest request) {

        // Extract path from request
        String path = request.getDescription(false).replace("uri=", "");

        // Log the error once at the global handler level
        logger.error("Workflow export failed. Path: {}, Error: {}", path, e.getMessage());

        // Determine HTTP status based on the exception's HTTP status code
        HttpStatus status = determineHttpStatus(e.getHttpStatusCode());

        // Create ProblemDetail with appropriate status and message
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problemDetail.setTitle("Workflow Export Failed");

        if (e.getHttpStatusCode() != null) {
            problemDetail.setProperty("cyodaStatusCode", e.getHttpStatusCode());
        }

        return ResponseEntity.status(status).body(problemDetail);
    }

    /**
     * Handles generic exceptions that are not specifically handled elsewhere.
     * This is a fallback handler for unexpected errors.
     *
     * @param e the exception
     * @param request the web request
     * @return ResponseEntity containing ProblemDetail
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception e,
            WebRequest request) {

        // Extract path from request
        String path = request.getDescription(false).replace("uri=", "");

        // Log the unexpected error
        logger.error("Unexpected error occurred. Path: {}, Error: {}", path, e.getMessage(), e);

        // Create ProblemDetail for internal server error
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please contact support if the problem persists."
        );
        problemDetail.setTitle("Internal Server Error");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
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

        // Try to resolve the status code to a standard HttpStatus
        HttpStatus status = HttpStatus.resolve(cyodaStatusCode);

        // If resolved successfully, return it; otherwise default to INTERNAL_SERVER_ERROR
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

