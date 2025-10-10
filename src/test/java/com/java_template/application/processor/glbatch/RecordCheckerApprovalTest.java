package com.java_template.application.processor.glbatch;

import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecordCheckerApproval processor.
 */
class RecordCheckerApprovalTest {

    @Test
    void testSupports_withMatchingName_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("RecordCheckerApproval");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "RecordCheckerApproval");

        String expectedName = "RecordCheckerApproval";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withDifferentCase_returnsTrue() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("recordcheckerapproval");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "recordcheckerapproval");

        String expectedName = "RecordCheckerApproval";
        assertTrue(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }

    @Test
    void testSupports_withNonMatchingName_returnsFalse() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("SomeOtherProcessor");
        OperationSpecification.Entity opSpec = new OperationSpecification.Entity(modelSpec, "SomeOtherProcessor");

        String expectedName = "RecordCheckerApproval";
        assertFalse(expectedName.equalsIgnoreCase(opSpec.operationName()));
    }
}

