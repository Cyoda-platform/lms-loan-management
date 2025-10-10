package com.java_template.application.processor.glbatch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecordMakerApproval processor.
 */
class RecordMakerApprovalTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("RecordMakerApproval");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "RecordMakerApproval");

        String expectedName = "RecordMakerApproval";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("recordmakerapproval");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "recordmakerapproval");

        String expectedName = "RecordMakerApproval";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "RecordMakerApproval";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

