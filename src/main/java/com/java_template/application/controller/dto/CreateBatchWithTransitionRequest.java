package com.java_template.application.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.application.entity.accrual.version_1.EODAccrualBatch;
import lombok.Data;

/**
 * DTO for creating an EOD accrual batch with an immediate workflow transition.
 * 
 * <p>This request structure allows the UI to create a batch and immediately trigger
 * a workflow transition (e.g., "START") in a single API call, rather than requiring
 * two separate calls (create + transition).</p>
 * 
 * <p>Example usage in request body:</p>
 * <pre>
 * {
 *   "batch": {
 *     "asOfDate": "2025-10-21",
 *     "mode": "TODAY",
 *     "initiatedBy": "user123",
 *     "metrics": {}
 *   },
 *   "transitionRequest": {
 *     "name": "START",
 *     "comment": "Starting daily accrual run"
 *   },
 *   "engineOptions": {
 *     "simulate": false,
 *     "maxSteps": 50
 *   }
 * }
 * </pre>
 * 
 * @see TransitionRequest
 * @see EngineOptions
 * @see EODAccrualBatch
 */
@Data
public class CreateBatchWithTransitionRequest {
    
    /**
     * The EOD accrual batch entity to create.
     * Must contain all required fields per EODAccrualBatch validation rules.
     */
    @JsonProperty("batch")
    private EODAccrualBatch batch;
    
    /**
     * Optional workflow transition to trigger immediately after creation.
     * If null, the batch will remain in its initial state (REQUESTED).
     */
    @JsonProperty("transitionRequest")
    private TransitionRequest transitionRequest;
    
    /**
     * Optional workflow engine execution options.
     * Currently not implemented in the underlying workflow engine.
     * 
     * @see EngineOptions for implementation status
     */
    @JsonProperty("engineOptions")
    private EngineOptions engineOptions;
}

