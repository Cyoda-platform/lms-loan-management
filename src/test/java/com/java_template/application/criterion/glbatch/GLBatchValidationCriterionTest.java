package com.java_template.application.criterion.glbatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.gl_batch.version_1.GLBatch;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.service.EntityService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GLBatchValidationCriterion.
 */
@ExtendWith(MockitoExtension.class)
class GLBatchValidationCriterionTest {

    @Mock
    private EntityService entityService;

    @Mock
    private SerializerFactory serializerFactory;

    private GLBatchValidationCriterion criterion;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        CriterionSerializer serializer = new JacksonCriterionSerializer(objectMapper);
        when(serializerFactory.getDefaultCriteriaSerializer()).thenReturn(serializer);

        criterion = new GLBatchValidationCriterion(serializerFactory, entityService);
    }

    @Test
    @DisplayName("supports() should return true for matching operation name")
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("GLBatchValidationCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "GLBatchValidationCriterion");

        assertTrue(criterion.supports(opSpec));
    }

    @Test
    @DisplayName("supports() should return true for case-insensitive match")
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("glbatchvalidationcriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "glbatchvalidationcriterion");

        assertTrue(criterion.supports(opSpec));
    }

    @Test
    @DisplayName("supports() should return false for non-matching operation name")
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherCriterion");

        assertFalse(criterion.supports(opSpec));
    }

    @Test
    @DisplayName("validateEntity() should succeed for valid batch with valid period")
    void testValidateEntity_withValidBatch_succeeds() throws Exception {
        // Given
        GLBatch batch = createValidBatch("2025-09");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // Mock no existing batches for this period
        when(entityService.findAll(any(ModelSpec.class), eq(GLBatch.class)))
            .thenReturn(Collections.emptyList());

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.validateEntity(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    @Test
    @DisplayName("validateEntity() should fail for null entity")
    void testValidateEntity_withNullEntity_fails() {
        // Given
        EntityWithMetadata<GLBatch> entityWithMetadata = new EntityWithMetadata<>(null, new EntityMetadata());

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.validateEntity(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("GLBatch entity is null"));
    }

    @Test
    @DisplayName("validateEntity() should fail for invalid period format")
    void testValidateEntity_withInvalidPeriodFormat_fails() throws Exception {
        // Given
        GLBatch batch = createValidBatch("2025/09"); // Invalid format
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.validateEntity(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Period must be in YYYY-MM format"));
    }

    @Test
    @DisplayName("validateEntity() should fail for null period")
    void testValidateEntity_withNullPeriod_fails() throws Exception {
        // Given
        GLBatch batch = createValidBatch(null);
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.validateEntity(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        // Entity is not valid due to null period, so it fails at isValid check
        assertTrue(failOutcome.formatReason().contains("not valid") ||
                   failOutcome.formatReason().contains("Period must be in YYYY-MM format"));
    }

    @Test
    @DisplayName("validateEntity() should fail when period already processed")
    void testValidateEntity_withDuplicatePeriod_fails() throws Exception {
        // Given
        GLBatch newBatch = createValidBatch("2025-09");
        newBatch.setBatchId("batch-new");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(newBatch);

        // Mock existing batch with same period
        GLBatch existingBatch = createValidBatch("2025-09");
        existingBatch.setBatchId("batch-existing");
        List<EntityWithMetadata<GLBatch>> existingBatches = List.of(
            createEntityWithMetadata(existingBatch)
        );

        when(entityService.findAll(any(ModelSpec.class), eq(GLBatch.class)))
            .thenReturn(existingBatches);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.validateEntity(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Period 2025-09 has already been processed"));
    }

    @Test
    @DisplayName("validateEntity() should succeed when same batch ID exists (update scenario)")
    void testValidateEntity_withSameBatchId_succeeds() throws Exception {
        // Given
        GLBatch batch = createValidBatch("2025-09");
        batch.setBatchId("batch-123");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // Mock existing batch with same ID and period (update scenario)
        List<EntityWithMetadata<GLBatch>> existingBatches = List.of(
            createEntityWithMetadata(batch)
        );

        when(entityService.findAll(any(ModelSpec.class), eq(GLBatch.class)))
            .thenReturn(existingBatches);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.validateEntity(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    // Helper methods

    private GLBatch createValidBatch(String period) {
        GLBatch batch = new GLBatch();
        batch.setBatchId(UUID.randomUUID().toString());
        batch.setPeriod(period);
        batch.setStatus("DRAFT");
        batch.setExportFormat("CSV");
        batch.setGlLines(new ArrayList<>());
        return batch;
    }

    private EntityWithMetadata<GLBatch> createEntityWithMetadata(GLBatch batch) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setState("initial");
        return new EntityWithMetadata<>(batch, metadata);
    }
}

