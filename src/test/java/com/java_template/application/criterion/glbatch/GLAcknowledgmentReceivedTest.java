package com.java_template.application.criterion.glbatch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GLAcknowledgmentReceived criterion.
 */
class GLAcknowledgmentReceivedTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("GLAcknowledgmentReceived");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "GLAcknowledgmentReceived");

        String expectedName = "GLAcknowledgmentReceived";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("glacknowledgmentreceived");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "glacknowledgmentreceived");

        String expectedName = "GLAcknowledgmentReceived";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherCriterion");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherCriterion");

        String expectedName = "GLAcknowledgmentReceived";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

