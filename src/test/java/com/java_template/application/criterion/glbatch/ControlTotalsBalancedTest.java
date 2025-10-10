package com.java_template.application.criterion.glbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ControlTotalsBalanced criterion.
 */
@ExtendWith(MockitoExtension.class)
class ControlTotalsBalancedTest {

    @Mock
    private SerializerFactory serializerFactory;

    private ControlTotalsBalanced criterion;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        CriterionSerializer serializer = new JacksonCriterionSerializer(objectMapper);
        when(serializerFactory.getDefaultCriteriaSerializer()).thenReturn(serializer);
        
        criterion = new ControlTotalsBalanced(serializerFactory);
    }

    @Test
    @DisplayName("supports() should return true for matching operation name")
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("ControlTotalsBalanced");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "ControlTotalsBalanced");

        assertTrue(criterion.supports(opSpec));
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should succeed when debits equal credits")
    void testCheckControlTotalsBalanced_withBalancedTotals_succeeds() {
        // Given
        GLBatch batch = createBatchWithControlTotals(
            new BigDecimal("1000.00"),
            new BigDecimal("1000.00"),
            10
        );
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should fail when debits do not equal credits")
    void testCheckControlTotalsBalanced_withUnbalancedTotals_fails() {
        // Given
        GLBatch batch = createBatchWithControlTotals(
            new BigDecimal("1000.00"),
            new BigDecimal("900.00"),
            10
        );
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Batch is not balanced"));
        assertTrue(failOutcome.formatReason().contains("1000.00"));
        assertTrue(failOutcome.formatReason().contains("900.00"));
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should fail when control totals are null")
    void testCheckControlTotalsBalanced_withNullControlTotals_fails() {
        // Given
        GLBatch batch = new GLBatch();
        batch.setBatchId("batch-123");
        batch.setControlTotals(null);
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Control totals are missing"));
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should fail when total debits are null")
    void testCheckControlTotalsBalanced_withNullDebits_fails() {
        // Given
        GLBatch batch = new GLBatch();
        batch.setBatchId("batch-123");
        GLBatch.ControlTotals controlTotals = new GLBatch.ControlTotals();
        controlTotals.setTotalDebits(null);
        controlTotals.setTotalCredits(new BigDecimal("1000.00"));
        batch.setControlTotals(controlTotals);
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Total debits or credits are missing"));
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should fail when total credits are null")
    void testCheckControlTotalsBalanced_withNullCredits_fails() {
        // Given
        GLBatch batch = new GLBatch();
        batch.setBatchId("batch-123");
        GLBatch.ControlTotals controlTotals = new GLBatch.ControlTotals();
        controlTotals.setTotalDebits(new BigDecimal("1000.00"));
        controlTotals.setTotalCredits(null);
        batch.setControlTotals(controlTotals);
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Total debits or credits are missing"));
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should fail for null entity")
    void testCheckControlTotalsBalanced_withNullEntity_fails() {
        // Given
        EntityWithMetadata<GLBatch> entityWithMetadata = new EntityWithMetadata<>(null, new EntityMetadata());

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("GLBatch entity is null"));
    }

    @Test
    @DisplayName("checkControlTotalsBalanced() should succeed with zero totals")
    void testCheckControlTotalsBalanced_withZeroTotals_succeeds() {
        // Given
        GLBatch batch = createBatchWithControlTotals(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0
        );
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkControlTotalsBalanced(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    // Helper methods

    private GLBatch createBatchWithControlTotals(BigDecimal totalDebits, BigDecimal totalCredits, int lineCount) {
        GLBatch batch = new GLBatch();
        batch.setBatchId(UUID.randomUUID().toString());
        batch.setPeriod("2025-09");
        
        GLBatch.ControlTotals controlTotals = new GLBatch.ControlTotals();
        controlTotals.setTotalDebits(totalDebits);
        controlTotals.setTotalCredits(totalCredits);
        controlTotals.setLineCount(lineCount);
        controlTotals.setIsBalanced(totalDebits.compareTo(totalCredits) == 0);
        
        batch.setControlTotals(controlTotals);
        return batch;
    }

    private EntityWithMetadata<GLBatch> createEntityWithMetadata(GLBatch batch) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setState("prepared");
        return new EntityWithMetadata<>(batch, metadata);
    }
}

