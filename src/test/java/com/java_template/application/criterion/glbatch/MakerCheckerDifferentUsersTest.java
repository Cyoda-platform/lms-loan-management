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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MakerCheckerDifferentUsers criterion.
 */
@ExtendWith(MockitoExtension.class)
class MakerCheckerDifferentUsersTest {

    @Mock
    private SerializerFactory serializerFactory;

    private MakerCheckerDifferentUsers criterion;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        CriterionSerializer serializer = new JacksonCriterionSerializer(objectMapper);
        when(serializerFactory.getDefaultCriteriaSerializer()).thenReturn(serializer);
        
        criterion = new MakerCheckerDifferentUsers(serializerFactory);
    }

    @Test
    @DisplayName("supports() should return true for matching operation name")
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("MakerCheckerDifferentUsers");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "MakerCheckerDifferentUsers");

        assertTrue(criterion.supports(opSpec));
    }

    @Test
    @DisplayName("checkDifferentUsers() should succeed when maker and checker are different")
    void testCheckDifferentUsers_withDifferentUsers_succeeds() {
        // Given
        GLBatch batch = createBatchWithApprovals("user-maker", "user-checker");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    @Test
    @DisplayName("checkDifferentUsers() should fail when maker and checker are the same")
    void testCheckDifferentUsers_withSameUser_fails() {
        // Given
        GLBatch batch = createBatchWithApprovals("user-123", "user-123");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Maker and checker must be different users"));
    }

    @Test
    @DisplayName("checkDifferentUsers() should succeed when checker not yet set")
    void testCheckDifferentUsers_withNoChecker_succeeds() {
        // Given
        GLBatch batch = createBatchWithApprovals("user-maker", null);
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    @Test
    @DisplayName("checkDifferentUsers() should succeed when checker is empty string")
    void testCheckDifferentUsers_withEmptyChecker_succeeds() {
        // Given
        GLBatch batch = createBatchWithApprovals("user-maker", "");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isSuccess());
    }

    @Test
    @DisplayName("checkDifferentUsers() should fail when approvals are null")
    void testCheckDifferentUsers_withNullApprovals_fails() {
        // Given
        GLBatch batch = new GLBatch();
        batch.setBatchId("batch-123");
        batch.setApprovals(null);
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Approvals are missing"));
    }

    @Test
    @DisplayName("checkDifferentUsers() should fail when maker user ID is null")
    void testCheckDifferentUsers_withNullMaker_fails() {
        // Given
        GLBatch batch = createBatchWithApprovals(null, "user-checker");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Maker user ID is missing"));
    }

    @Test
    @DisplayName("checkDifferentUsers() should fail when maker user ID is empty")
    void testCheckDifferentUsers_withEmptyMaker_fails() {
        // Given
        GLBatch batch = createBatchWithApprovals("", "user-checker");
        EntityWithMetadata<GLBatch> entityWithMetadata = createEntityWithMetadata(batch);

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("Maker user ID is missing"));
    }

    @Test
    @DisplayName("checkDifferentUsers() should fail for null entity")
    void testCheckDifferentUsers_withNullEntity_fails() {
        // Given
        EntityWithMetadata<GLBatch> entityWithMetadata = new EntityWithMetadata<>(null, new EntityMetadata());

        // When
        CriterionSerializer.CriterionEntityEvaluationContext<GLBatch> context =
            new CriterionSerializer.CriterionEntityEvaluationContext<>(null, entityWithMetadata);
        EvaluationOutcome outcome = criterion.checkDifferentUsers(context);

        // Then
        assertTrue(outcome.isFailure());
        EvaluationOutcome.Fail failOutcome = (EvaluationOutcome.Fail) outcome;
        assertTrue(failOutcome.formatReason().contains("GLBatch entity is null"));
    }

    // Helper methods

    private GLBatch createBatchWithApprovals(String makerUserId, String checkerUserId) {
        GLBatch batch = new GLBatch();
        batch.setBatchId(UUID.randomUUID().toString());
        batch.setPeriod("2025-09");
        
        GLBatch.Approvals approvals = new GLBatch.Approvals();
        approvals.setMakerUserId(makerUserId);
        approvals.setMakerApprovedAt(LocalDateTime.now());
        approvals.setMakerRole("Finance Manager");
        
        if (checkerUserId != null) {
            approvals.setCheckerUserId(checkerUserId);
            approvals.setCheckerApprovedAt(LocalDateTime.now());
            approvals.setCheckerRole("Finance Controller");
        }
        
        batch.setApprovals(approvals);
        return batch;
    }

    private EntityWithMetadata<GLBatch> createEntityWithMetadata(GLBatch batch) {
        EntityMetadata metadata = new EntityMetadata();
        metadata.setId(UUID.randomUUID());
        metadata.setState("maker_approved");
        return new EntityWithMetadata<>(batch, metadata);
    }
}

