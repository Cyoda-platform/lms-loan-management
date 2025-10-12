package com.java_template.application.entity.gl_batch.version_1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This entity represents a batch of summarized accounting entries
 * prepared at the end of a month for posting to the General Ledger.
 * 
 * It includes header information, control totals, and an embedded list of GL lines.
 * GL lines are stored as an embedded array within the GLBatch entity, not as separate entities.
 */
@Data
public class GLBatch implements CyodaEntity {
    public static final String ENTITY_NAME = GLBatch.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    @NotNull(message = "Batch ID is required")
    @NotBlank(message = "Batch ID cannot be blank")
    @JsonProperty("batchId")
    private String batchId;

    // Required core business fields
    @NotNull(message = "Period is required")
    @NotBlank(message = "Period cannot be blank")
    @JsonProperty("period")
    private String period; // e.g., "2025-09" for September 2025

    @JsonProperty("status")
    private String status; // Managed by workflow state

    @JsonProperty("exportFormat")
    private String exportFormat; // e.g., "CSV", "JSON"

    // Control totals
    @Valid
    @JsonProperty("controlTotals")
    private ControlTotals controlTotals;

    // Embedded GL lines (not separate entities)
    @Valid
    @JsonProperty("glLines")
    private List<GLLine> glLines;

    // Approval tracking
    @Valid
    @JsonProperty("approvals")
    private Approvals approvals;

    // Export tracking
    @JsonProperty("exportFilePath")
    private String exportFilePath;

    @JsonProperty("exportedAt")
    private LocalDateTime exportedAt;

    @JsonProperty("acknowledgmentReceivedAt")
    private LocalDateTime acknowledgmentReceivedAt;

    @JsonProperty("archivedAt")
    private LocalDateTime archivedAt;

    // Validation error tracking
    @JsonProperty("validationErrorReason")
    private String validationErrorReason;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return batchId != null && !batchId.trim().isEmpty() &&
               period != null && !period.trim().isEmpty();
    }

    /**
     * Returns a detailed reason why the entity is invalid.
     * This method should only be called when isValid() returns false.
     *
     * @param metadata the entity metadata
     * @return a human-readable string describing the validation failure
     */
    public String getValidationFailureReason(EntityMetadata metadata) {
        if (batchId == null || batchId.trim().isEmpty()) {
            return "Batch ID is required";
        }
        if (period == null || period.trim().isEmpty()) {
            return "Period is required";
        }
        return "GLBatch entity validation failed for unknown reason";
    }

    /**
     * Nested class for GL line (embedded within GLBatch, not a separate entity)
     */
    @Data
    public static class GLLine {
        @JsonProperty("glLineId")
        private String glLineId;

        @JsonProperty("glAccount")
        private String glAccount; // e.g., "1100-Interest-Receivable"

        @JsonProperty("description")
        private String description;

        @JsonProperty("type")
        private String type; // "DEBIT" or "CREDIT"

        @JsonProperty("amount")
        private BigDecimal amount;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("product")
        private String product; // Optional: for product-level breakdown

        @JsonProperty("costCenter")
        private String costCenter; // Optional: for cost center breakdown
    }

    /**
     * Nested class for control totals
     */
    @Data
    public static class ControlTotals {
        @JsonProperty("totalDebits")
        private BigDecimal totalDebits;

        @JsonProperty("totalCredits")
        private BigDecimal totalCredits;

        @JsonProperty("lineCount")
        private Integer lineCount;

        @JsonProperty("isBalanced")
        private Boolean isBalanced; // totalDebits == totalCredits
    }

    /**
     * Nested class for approval tracking (maker/checker pattern)
     */
    @Data
    public static class Approvals {
        @JsonProperty("makerUserId")
        private String makerUserId;

        @JsonProperty("makerApprovedAt")
        private LocalDateTime makerApprovedAt;

        @JsonProperty("makerRole")
        private String makerRole;

        @JsonProperty("checkerUserId")
        private String checkerUserId;

        @JsonProperty("checkerApprovedAt")
        private LocalDateTime checkerApprovedAt;

        @JsonProperty("checkerRole")
        private String checkerRole;
    }
}

